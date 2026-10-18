#!/usr/bin/env python3
"""Measure whether a fresh client can recover polling for the same generation task."""

from __future__ import annotations

import argparse
import csv
import os
import time
import urllib.parse
from datetime import datetime
from pathlib import Path
from typing import Any

from run_generation_benchmark import ApiClient, ApiError, PROMPTS, preview_url, unwrap_data, utc_now


CSV_FIELDS = [
    "run_id",
    "mode",
    "app_id",
    "created_task_id",
    "recovered_task_id",
    "disconnect_seconds",
    "same_task_recovered",
    "terminal_status",
    "preview_url",
    "preview_http_status",
    "preview_success",
    "started_at_utc",
    "reconnected_at_utc",
    "finished_at_utc",
    "error",
]


def append_row(path: Path, row: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    exists = path.exists()
    with path.open("a", encoding="utf-8", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=CSV_FIELDS)
        if not exists:
            writer.writeheader()
        writer.writerow({field: row.get(field, "") for field in CSV_FIELDS})
        output.flush()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=list(PROMPTS), default="vue_project")
    parser.add_argument("--runs", type=int, default=5)
    parser.add_argument("--disconnect-seconds", type=float, default=20.0)
    parser.add_argument("--poll-interval-seconds", type=float, default=2.0)
    parser.add_argument("--task-timeout-seconds", type=float, default=600.0)
    parser.add_argument(
        "--api-base",
        default=os.getenv("BENCHMARK_API_BASE", "https://ai.lijinan.cn/api"),
    )
    parser.add_argument(
        "--preview-base",
        default=os.getenv("BENCHMARK_PREVIEW_BASE", "https://ai.lijinan.cn/api/static"),
    )
    parser.add_argument("--output", type=Path)
    parser.add_argument(
        "--confirm-paid-ai-calls",
        action="store_true",
        help="required acknowledgement that this run invokes the configured paid AI provider",
    )
    return parser.parse_args()


def run_scenario(args: argparse.Namespace, account: str, password: str, run_id: str) -> dict[str, Any]:
    row: dict[str, Any] = {
        "run_id": run_id,
        "mode": args.mode,
        "disconnect_seconds": args.disconnect_seconds,
        "same_task_recovered": False,
        "terminal_status": "client_error",
        "preview_success": False,
        "started_at_utc": utc_now(),
        "error": "",
    }
    try:
        first_client = ApiClient(args.api_base)
        first_client.login(account, password)
        app_id = unwrap_data(
            first_client.request_json(
                "/app/add", method="POST", body={"initPrompt": PROMPTS[args.mode]}
            )
        )
        if not app_id:
            raise ApiError("app creation returned no app id")
        row["app_id"] = app_id

        app = unwrap_data(first_client.request_json(f"/app/get/vo?id={app_id}")) or {}
        actual_mode = app.get("codeGenType")
        if actual_mode != args.mode:
            raise ApiError(f"requested {args.mode}, routed to {actual_mode or 'unknown'}")

        created = unwrap_data(
            first_client.request_json(
                "/app/chat/gen/task",
                method="POST",
                body={"appId": app_id, "message": PROMPTS[args.mode]},
            )
        ) or {}
        created_task_id = created.get("id")
        if not created_task_id:
            raise ApiError("task creation returned no task id")
        row["created_task_id"] = created_task_id

        # Drop all client-side cookies and state by discarding first_client.
        del first_client
        time.sleep(args.disconnect_seconds)

        second_client = ApiClient(args.api_base)
        second_client.login(account, password)
        row["reconnected_at_utc"] = utc_now()
        latest = unwrap_data(
            second_client.request_json(
                f"/app/chat/gen/task/latest?appId={urllib.parse.quote(str(app_id))}"
            )
        ) or {}
        recovered_task_id = latest.get("id")
        row["recovered_task_id"] = recovered_task_id or ""
        row["same_task_recovered"] = recovered_task_id == created_task_id
        if recovered_task_id != created_task_id:
            raise ApiError(
                f"latest task mismatch: created={created_task_id}, recovered={recovered_task_id}"
            )

        deadline = time.monotonic() + args.task_timeout_seconds
        while time.monotonic() < deadline:
            task = unwrap_data(
                second_client.request_json(f"/app/chat/gen/task/{created_task_id}")
            ) or {}
            status = task.get("status", "unknown")
            if status in {"succeeded", "failed"}:
                row["terminal_status"] = status
                row["error"] = task.get("errorMessage") or ""
                if status == "succeeded":
                    url = preview_url(args.preview_base, args.mode, app_id)
                    http_status = second_client.preview_status(url)
                    row["preview_url"] = url
                    row["preview_http_status"] = http_status
                    row["preview_success"] = 200 <= http_status < 400
                break
            time.sleep(args.poll_interval_seconds)
        else:
            row["terminal_status"] = "timeout"
            row["error"] = f"task did not finish within {args.task_timeout_seconds:g}s"
    except Exception as error:
        row["error"] = str(error)[:1000]
    finally:
        row["finished_at_utc"] = utc_now()
    return row


def main() -> int:
    args = parse_args()
    if not 1 <= args.runs <= 30:
        raise SystemExit("--runs must be between 1 and 30")
    if args.disconnect_seconds < 0:
        raise SystemExit("--disconnect-seconds must be non-negative")
    if not args.confirm_paid_ai_calls:
        raise SystemExit("refusing to call the AI provider without --confirm-paid-ai-calls")
    account = os.getenv("BENCHMARK_USER_ACCOUNT")
    password = os.getenv("BENCHMARK_USER_PASSWORD")
    if not account or not password:
        raise SystemExit("set BENCHMARK_USER_ACCOUNT and BENCHMARK_USER_PASSWORD")

    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    output = args.output or Path(f"docs/benchmarks/raw/reconnect-{timestamp}.csv")
    successful_recoveries = 0
    for index in range(1, args.runs + 1):
        row = run_scenario(args, account, password, f"{timestamp}-{index:03d}")
        append_row(output, row)
        if row["same_task_recovered"] and row["terminal_status"] in {"succeeded", "failed"}:
            successful_recoveries += 1
        print(
            f"run={index}/{args.runs} recovered={row['same_task_recovered']} "
            f"status={row['terminal_status']} task={row.get('created_task_id', '')}",
            flush=True,
        )
    print(output)
    print(f"same-task terminal recoveries: {successful_recoveries}/{args.runs}")
    return 0 if successful_recoveries == args.runs else 2


if __name__ == "__main__":
    raise SystemExit(main())
