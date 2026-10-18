#!/usr/bin/env python3
"""Run reproducible generation benchmarks against a deployed code-ai-agent API.

The script deliberately uses only the Python standard library. Credentials are
read from environment variables and are never written to the result CSV.
"""

from __future__ import annotations

import argparse
import csv
import http.cookiejar
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


PROMPTS = {
    "html": (
        "生成一个单页产品发布落地页。必须使用原生 HTML、CSS 和 JavaScript，"
        "所有内容放在单个 index.html 中，不使用 Vue，不拆分多文件。包含首屏、"
        "功能介绍、价格卡片和联系按钮，支持移动端。"
    ),
    "multi_file": (
        "生成一个原生多文件企业官网。使用 HTML、CSS 和 JavaScript，明确拆分为"
        "多个文件和多个页面，不使用 Vue。包含首页、服务介绍、案例和联系我们页面。"
    ),
    "vue_project": (
        "生成一个 Vue 3 管理系统工程，使用组件化页面和复杂交互。包含登录页、"
        "数据看板、订单表格、筛选表单和详情弹窗，必须是可构建的 Vue 项目。"
    ),
}

CSV_FIELDS = [
    "run_id",
    "source_commit",
    "requested_mode",
    "actual_mode",
    "prompt_id",
    "app_id",
    "task_id",
    "started_at_utc",
    "finished_at_utc",
    "client_total_ms",
    "queue_ms",
    "server_run_ms",
    "terminal_status",
    "preview_url",
    "preview_http_status",
    "preview_success",
    "error",
]


class ApiError(RuntimeError):
    pass


class ApiClient:
    def __init__(self, api_base: str, timeout_seconds: float = 30.0) -> None:
        self.api_base = api_base.rstrip("/")
        self.timeout_seconds = timeout_seconds
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.cookies)
        )

    def request_json(
        self,
        path: str,
        *,
        method: str = "GET",
        body: dict[str, Any] | None = None,
        timeout_seconds: float | None = None,
    ) -> dict[str, Any]:
        data = None
        headers = {"Accept": "application/json", "User-Agent": "code-ai-agent-benchmark/1.0"}
        if body is not None:
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json"
        request = urllib.request.Request(
            f"{self.api_base}{path}", data=data, headers=headers, method=method
        )
        try:
            with self.opener.open(
                request, timeout=timeout_seconds or self.timeout_seconds
            ) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            response_body = error.read().decode("utf-8", errors="replace")
            raise ApiError(f"HTTP {error.code}: {response_body[:500]}") from error
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as error:
            raise ApiError(str(error)) from error
        if isinstance(payload, dict) and payload.get("code") not in (None, 0):
            raise ApiError(f"API {payload.get('code')}: {payload.get('message', 'unknown error')}")
        return payload

    def login(self, account: str, password: str) -> None:
        payload = self.request_json(
            "/user/login",
            method="POST",
            body={"userAccount": account, "userPassword": password},
        )
        if not payload.get("data"):
            raise ApiError("login returned no user data")

    def preview_status(self, url: str) -> int:
        request = urllib.request.Request(
            url,
            headers={"Accept": "text/html", "User-Agent": "code-ai-agent-benchmark/1.0"},
            method="GET",
        )
        try:
            with self.opener.open(request, timeout=self.timeout_seconds) as response:
                response.read(256)
                return response.status
        except urllib.error.HTTPError as error:
            return error.code
        except (urllib.error.URLError, TimeoutError):
            return 0


@dataclass(frozen=True)
class Settings:
    runs_per_mode: int
    poll_interval_seconds: float
    task_timeout_seconds: float
    cooldown_seconds: float
    max_attempt_multiplier: int
    api_base: str
    preview_base: str


def parse_server_time(value: Any) -> datetime | None:
    if not value or not isinstance(value, str):
        return None
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None


def diff_ms(start: Any, finish: Any) -> int | None:
    start_time = parse_server_time(start)
    finish_time = parse_server_time(finish)
    if not start_time or not finish_time:
        return None
    return max(0, round((finish_time - start_time).total_seconds() * 1000))


def source_commit() -> str:
    try:
        return subprocess.check_output(
            ["git", "rev-parse", "HEAD"], text=True, stderr=subprocess.DEVNULL
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def unwrap_data(payload: dict[str, Any]) -> Any:
    return payload.get("data") if isinstance(payload, dict) else None


def preview_url(preview_base: str, mode: str, app_id: Any) -> str:
    suffix = "dist/index.html" if mode == "vue_project" else ""
    return f"{preview_base.rstrip('/')}/{mode}_{app_id}/{suffix}"


def append_row(path: Path, row: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    exists = path.exists()
    with path.open("a", encoding="utf-8", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=CSV_FIELDS)
        if not exists:
            writer.writeheader()
        writer.writerow({field: row.get(field, "") for field in CSV_FIELDS})
        output.flush()


def run_one(
    client: ApiClient,
    settings: Settings,
    requested_mode: str,
    run_id: str,
) -> dict[str, Any]:
    row: dict[str, Any] = {
        "run_id": run_id,
        "source_commit": source_commit(),
        "requested_mode": requested_mode,
        "actual_mode": "",
        "prompt_id": requested_mode,
        "app_id": "",
        "task_id": "",
        "started_at_utc": utc_now(),
        "finished_at_utc": "",
        "terminal_status": "client_error",
        "preview_success": False,
        "error": "",
    }
    started = time.monotonic()
    try:
        add_payload = client.request_json(
            "/app/add", method="POST", body={"initPrompt": PROMPTS[requested_mode]}
        )
        app_id = unwrap_data(add_payload)
        if not app_id:
            raise ApiError("app creation returned no app id")
        row["app_id"] = app_id

        app_payload = client.request_json(
            f"/app/get/vo?id={urllib.parse.quote(str(app_id))}"
        )
        app = unwrap_data(app_payload) or {}
        actual_mode = app.get("codeGenType", "")
        row["actual_mode"] = actual_mode
        if actual_mode != requested_mode:
            row["terminal_status"] = "routing_mismatch"
            row["error"] = f"requested {requested_mode}, routed to {actual_mode or 'unknown'}"
            return row

        task_payload = client.request_json(
            "/app/chat/gen/task",
            method="POST",
            body={"appId": app_id, "message": PROMPTS[requested_mode]},
        )
        task = unwrap_data(task_payload) or {}
        task_id = task.get("id")
        if not task_id:
            raise ApiError("task creation returned no task id")
        row["task_id"] = task_id

        deadline = time.monotonic() + settings.task_timeout_seconds
        while time.monotonic() < deadline:
            task_payload = client.request_json(f"/app/chat/gen/task/{task_id}")
            task = unwrap_data(task_payload) or {}
            status = task.get("status", "unknown")
            if status in {"succeeded", "failed"}:
                row["terminal_status"] = status
                row["queue_ms"] = diff_ms(task.get("createTime"), task.get("startTime"))
                row["server_run_ms"] = diff_ms(task.get("startTime"), task.get("finishTime"))
                row["error"] = task.get("errorMessage") or ""
                if status == "succeeded":
                    url = preview_url(settings.preview_base, actual_mode, app_id)
                    status_code = client.preview_status(url)
                    row["preview_url"] = url
                    row["preview_http_status"] = status_code
                    row["preview_success"] = 200 <= status_code < 400
                break
            time.sleep(settings.poll_interval_seconds)
        else:
            row["terminal_status"] = "timeout"
            row["error"] = f"task did not finish within {settings.task_timeout_seconds:g}s"
    except Exception as error:  # one failed row must not discard the batch
        row["error"] = str(error)[:1000]
    finally:
        row["finished_at_utc"] = utc_now()
        row["client_total_ms"] = round((time.monotonic() - started) * 1000)
    return row


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=["all", *PROMPTS], default="all")
    parser.add_argument("--runs-per-mode", type=int, default=20)
    parser.add_argument("--api-base", default=os.getenv("BENCHMARK_API_BASE", "https://ai.lijinan.cn/api"))
    parser.add_argument(
        "--preview-base",
        default=os.getenv("BENCHMARK_PREVIEW_BASE", "https://ai.lijinan.cn/api/static"),
    )
    parser.add_argument("--poll-interval-seconds", type=float, default=2.0)
    parser.add_argument("--task-timeout-seconds", type=float, default=600.0)
    parser.add_argument("--cooldown-seconds", type=float, default=5.0)
    parser.add_argument("--max-attempt-multiplier", type=int, default=3)
    parser.add_argument("--output", type=Path)
    parser.add_argument(
        "--confirm-paid-ai-calls",
        action="store_true",
        help="required acknowledgement that this run invokes the configured paid AI provider",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not 1 <= args.runs_per_mode <= 30:
        raise SystemExit("--runs-per-mode must be between 1 and 30")
    if not args.confirm_paid_ai_calls:
        raise SystemExit("refusing to call the AI provider without --confirm-paid-ai-calls")
    account = os.getenv("BENCHMARK_USER_ACCOUNT")
    password = os.getenv("BENCHMARK_USER_PASSWORD")
    if not account or not password:
        raise SystemExit("set BENCHMARK_USER_ACCOUNT and BENCHMARK_USER_PASSWORD")

    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    output = args.output or Path(f"docs/benchmarks/raw/generation-{timestamp}.csv")
    settings = Settings(
        runs_per_mode=args.runs_per_mode,
        poll_interval_seconds=args.poll_interval_seconds,
        task_timeout_seconds=args.task_timeout_seconds,
        cooldown_seconds=args.cooldown_seconds,
        max_attempt_multiplier=args.max_attempt_multiplier,
        api_base=args.api_base,
        preview_base=args.preview_base,
    )
    client = ApiClient(settings.api_base)
    client.login(account, password)

    modes = list(PROMPTS) if args.mode == "all" else [args.mode]
    for mode in modes:
        completed = 0
        attempts = 0
        max_attempts = settings.runs_per_mode * settings.max_attempt_multiplier
        while completed < settings.runs_per_mode and attempts < max_attempts:
            attempts += 1
            run_id = f"{timestamp}-{mode}-{attempts:03d}"
            row = run_one(client, settings, mode, run_id)
            append_row(output, row)
            if row["actual_mode"] == mode and row["terminal_status"] in {"succeeded", "failed", "timeout"}:
                completed += 1
            print(
                f"[{mode}] attempt={attempts}/{max_attempts} "
                f"completed={completed}/{settings.runs_per_mode} "
                f"status={row['terminal_status']} app={row['app_id']} task={row['task_id']}",
                flush=True,
            )
            if completed < settings.runs_per_mode:
                time.sleep(settings.cooldown_seconds)
        if completed < settings.runs_per_mode:
            print(f"{mode}: only collected {completed} terminal runs; see {output}", file=sys.stderr)
            return 2
    print(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
