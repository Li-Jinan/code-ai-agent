#!/usr/bin/env python3
"""Summarize raw generation benchmark CSV without inventing missing samples."""

from __future__ import annotations

import argparse
import csv
import math
from collections import defaultdict
from pathlib import Path
from statistics import mean


MODES = ("html", "multi_file", "vue_project")


def number(value: str) -> float | None:
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def nearest_rank_p95(values: list[float]) -> float:
    ordered = sorted(values)
    return ordered[max(0, math.ceil(0.95 * len(ordered)) - 1)]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("--output-md", type=Path, default=Path("docs/benchmarks/results.md"))
    parser.add_argument("--output-csv", type=Path, default=Path("docs/benchmarks/summary.csv"))
    parser.add_argument("--minimum-runs", type=int, default=20)
    parser.add_argument("--allow-incomplete", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    with args.input.open(encoding="utf-8", newline="") as source:
        rows = list(csv.DictReader(source))

    grouped: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        if row.get("actual_mode") in MODES and row.get("terminal_status") in {
            "succeeded",
            "failed",
            "timeout",
        }:
            grouped[row["actual_mode"]].append(row)

    insufficient = {mode: len(grouped[mode]) for mode in MODES if len(grouped[mode]) < args.minimum_runs}
    if insufficient and not args.allow_incomplete:
        detail = ", ".join(f"{mode}={count}" for mode, count in insufficient.items())
        raise SystemExit(
            f"insufficient samples ({detail}); collect at least {args.minimum_runs} per mode "
            "or use --allow-incomplete for a draft only"
        )

    summary_rows: list[dict[str, str]] = []
    for mode in MODES:
        samples = grouped[mode]
        durations = [
            value
            for row in samples
            if (value := number(row.get("server_run_ms", ""))) is not None
        ]
        succeeded = [row for row in samples if row.get("terminal_status") == "succeeded"]
        preview_ok = [row for row in succeeded if row.get("preview_success", "").lower() == "true"]
        summary_rows.append(
            {
                "mode": mode,
                "runs": str(len(samples)),
                "succeeded": str(len(succeeded)),
                "avg_server_run_ms": f"{mean(durations):.0f}" if durations else "",
                "p95_server_run_ms": f"{nearest_rank_p95(durations):.0f}" if durations else "",
                "preview_success_rate": f"{len(preview_ok) / len(samples):.2%}" if samples else "",
            }
        )

    args.output_csv.parent.mkdir(parents=True, exist_ok=True)
    with args.output_csv.open("w", encoding="utf-8", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=list(summary_rows[0]))
        writer.writeheader()
        writer.writerows(summary_rows)

    lines = [
        "# 生成效果数据",
        "",
        f"原始数据：`{args.input.as_posix()}`。P95 使用 nearest-rank 计算。",
        "",
        "| 模式 | 样本数 | 成功数 | 平均执行耗时 | P95 执行耗时 | 最终预览成功率 |",
        "| --- | ---: | ---: | ---: | ---: | ---: |",
    ]
    for row in summary_rows:
        avg = f"{int(row['avg_server_run_ms']) / 1000:.1f}s" if row["avg_server_run_ms"] else "—"
        p95 = f"{int(row['p95_server_run_ms']) / 1000:.1f}s" if row["p95_server_run_ms"] else "—"
        lines.append(
            f"| {row['mode']} | {row['runs']} | {row['succeeded']} | {avg} | {p95} | "
            f"{row['preview_success_rate'] or '—'} |"
        )
    lines.extend(
        [
            "",
            "> 只统计 CSV 中真实记录；失败和超时进入样本总数，不从预览成功率分母中删除。",
            "",
        ]
    )
    args.output_md.write_text("\n".join(lines), encoding="utf-8")
    print(args.output_md)
    print(args.output_csv)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
