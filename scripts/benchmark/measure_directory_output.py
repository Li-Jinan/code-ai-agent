#!/usr/bin/env python3
"""Replay the before/after FileDirReadTool algorithms on one fixed fixture."""

from __future__ import annotations

import argparse
import csv
import math
import os
import subprocess
import tempfile
from datetime import datetime
from pathlib import Path


IGNORED_NAMES = {
    "node_modules", ".git", "dist", "build", ".ds_store", ".env",
    "target", ".mvn", ".idea", ".vscode", "coverage",
}
IGNORED_EXTENSIONS = {".log", ".tmp", ".cache", ".lock"}
MAX_DEPTH = 8
MAX_ENTRIES = 300
MAX_OUTPUT_CHARS = 16_000
SOURCE_PATH = (
    "code-ai-agent-microservice/code-ai-agent-ai/src/main/java/"
    "com/jinan/codeaiagent/ai/tools/FileDirReadTool.java"
)


def ignored(name: str) -> bool:
    normalized = name.lower()
    return normalized in IGNORED_NAMES or any(normalized.endswith(ext) for ext in IGNORED_EXTENSIONS)


def build_fixture(root: Path) -> int:
    count = 0
    for group in range(40):
        folder = root / "src" / f"feature_{group:02d}" / "components"
        folder.mkdir(parents=True, exist_ok=True)
        for item in range(25):
            (folder / f"component_{group:02d}_{item:02d}.vue").write_text("<template />\n", encoding="utf-8")
            count += 1
    for ignored_dir, size in (("node_modules", 500), ("dist", 200)):
        folder = root / ignored_dir / "package"
        folder.mkdir(parents=True, exist_ok=True)
        for item in range(size):
            (folder / f"generated_{item:04d}.js").write_text("export default {}\n", encoding="utf-8")
            count += 1
    return count


def before_output(root: Path) -> tuple[str, int, bool]:
    files = [path for path in root.rglob("*") if path.is_file() and not ignored(path.name)]
    files.sort(key=lambda path: (len(path.relative_to(root).parts) - 1, str(path)))
    result = "项目目录结构:\n"
    for path in files:
        depth = len(path.relative_to(root).parts) - 1
        result += "  " * depth + path.name
    return result, len(files), False


def after_output(root: Path) -> tuple[str, int, bool]:
    result = "项目目录结构:\n"
    entries = 0
    truncated = False
    for current, directories, files in os.walk(root):
        current_path = Path(current)
        relative_current = current_path.relative_to(root)
        depth = len(relative_current.parts)
        directories[:] = sorted(name for name in directories if not ignored(name))
        if depth >= MAX_DEPTH:
            directories[:] = []
        candidates = [(current_path / name, True) for name in directories]
        candidates.extend((current_path / name, False) for name in sorted(files) if not ignored(name))
        for path, is_directory in candidates:
            relative = path.relative_to(root)
            line_depth = max(0, len(relative.parts) - 1)
            line = "  " * line_depth + relative.as_posix() + ("/" if is_directory else "") + "\n"
            if entries >= MAX_ENTRIES or len(result) + len(line) > MAX_OUTPUT_CHARS:
                truncated = True
                break
            result += line
            entries += 1
        if truncated:
            break
    if truncated:
        result += "... 目录内容过多，已省略其余条目\n"
    return result, entries, truncated


def git_source(ref: str) -> str:
    return subprocess.check_output(["git", "show", f"{ref}:{SOURCE_PATH}"], text=True)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--before-ref", default="ea5783b^")
    parser.add_argument("--after-ref", default="ea5783b")
    parser.add_argument("--output", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    before_source = git_source(args.before_ref)
    after_source = git_source(args.after_ref)
    if "FileUtil.loopFiles" not in before_source:
        raise SystemExit(f"{args.before_ref} does not contain the expected unbounded implementation")
    if "MAX_OUTPUT_CHARS" not in after_source or "MAX_ENTRIES" not in after_source:
        raise SystemExit(f"{args.after_ref} does not contain the expected bounded implementation")

    with tempfile.TemporaryDirectory(prefix="dir-tool-benchmark-") as temporary:
        fixture_root = Path(temporary) / "vue_project_1"
        fixture_root.mkdir()
        fixture_files = build_fixture(fixture_root)
        before, before_entries, before_truncated = before_output(fixture_root)
        after, after_entries, after_truncated = after_output(fixture_root)

    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    output = args.output or Path(f"docs/benchmarks/raw/directory-output-{timestamp}.csv")
    output.parent.mkdir(parents=True, exist_ok=True)
    rows = [
        ("before", args.before_ref, before, before_entries, before_truncated),
        ("after", args.after_ref, after, after_entries, after_truncated),
    ]
    with output.open("w", encoding="utf-8", newline="") as target:
        writer = csv.DictWriter(
            target,
            fieldnames=[
                "implementation", "git_ref", "fixture_files", "reported_entries",
                "output_chars", "approx_tokens", "truncated",
            ],
        )
        writer.writeheader()
        for implementation, ref, content, entries, truncated in rows:
            writer.writerow(
                {
                    "implementation": implementation,
                    "git_ref": ref,
                    "fixture_files": fixture_files,
                    "reported_entries": entries,
                    "output_chars": len(content),
                    "approx_tokens": math.ceil(len(content) / 4),
                    "truncated": str(truncated).lower(),
                }
            )
    print(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
