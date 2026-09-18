#!/usr/bin/env python3
"""Tally Codex token usage from local rollout logs, filtered by working directory.

Codex records every session as a JSONL "rollout" file under
``~/.codex/sessions/<YYYY>/<MM>/<DD>/rollout-<iso8601>-<uuid>.jsonl``.  Each
``event_msg`` of type ``token_count`` carries a cumulative ``total_token_usage``
object for that rollout file, so a whole file can be summarised by its *last*
such event.

Usage
-----
    python tools/codex_token_usage.py E:/Java/MiXianTu
    python tools/codex_token_usage.py --list-dirs
    python tools/codex_token_usage.py --all --by-dir

Why the accounting is not a naive sum of file totals
---------------------------------------------------
Two properties of the log format make naive summation wrong:

1. **Resumed sessions span several files.**  Resuming forks a new rollout file
   named ``rollout-<ts>-<session-id>_<new-thread-id>.jsonl``.  Its
   ``total_token_usage`` counter *starts from an inherited baseline* rather than
   from zero, so the same tokens appear in the totals of both the old and the
   new file.  This script groups files by session id and counts only each
   file's *growth* (``last - first``), except for the first file of a chain
   whose growth is its final total (a fresh session starts at zero).

2. **Some events are emitted twice.**  Occasionally the same ``token_count``
   payload is written twice within a second with an identical cumulative total.
   Summing the per-call ``last_token_usage`` therefore over-counts, which is
   why this script reads the cumulative ``total_token_usage`` and diffs it
   instead.

Sub-agent rollouts (``thread_source: subagent``) are separate threads with their
own counters and are *not* included in their parent's totals, so they are
counted here as real spend but reported separately.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterator

# --- log parsing -----------------------------------------------------------

# Matches the *total_token_usage* object specifically.  last_token_usage has the
# same shape, so the key prefix is what disambiguates the two.
_TOTAL_USAGE_RE = re.compile(
    r'"total_token_usage"\s*:\s*\{'
    r'\s*"input_tokens"\s*:\s*(\d+)\s*,'
    r'\s*"cached_input_tokens"\s*:\s*(\d+)\s*,'
    r'\s*"cache_write_input_tokens"\s*:\s*(\d+)\s*,'
    r'\s*"output_tokens"\s*:\s*(\d+)\s*,'
    r'\s*"reasoning_output_tokens"\s*:\s*(\d+)\s*,'
    r'\s*"total_tokens"\s*:\s*(\d+)\s*\}'
)

_META_MARKER = '"session_meta"'
_TOTAL_MARKER = '"total_token_usage"'


@dataclass
class Usage:
    """A token-usage record. ``cached`` is a subset of ``input`` and
    ``reasoning`` is a subset of ``output``."""

    input: int = 0
    cached: int = 0
    cache_write: int = 0
    output: int = 0
    reasoning: int = 0
    total: int = 0

    def __iadd__(self, other: "Usage") -> "Usage":
        self.input += other.input
        self.cached += other.cached
        self.cache_write += other.cache_write
        self.output += other.output
        self.reasoning += other.reasoning
        self.total += other.total
        return self

    def grown_since(self, baseline: "Usage") -> "Usage":
        """Tokens consumed on top of ``baseline`` (never negative)."""
        return Usage(
            input=max(0, self.input - baseline.input),
            cached=max(0, self.cached - baseline.cached),
            cache_write=max(0, self.cache_write - baseline.cache_write),
            output=max(0, self.output - baseline.output),
            reasoning=max(0, self.reasoning - baseline.reasoning),
            total=max(0, self.total - baseline.total),
        )


def _usage_from_mapping(obj: dict) -> Usage | None:
    try:
        return Usage(
            input=int(obj.get("input_tokens", 0)),
            cached=int(obj.get("cached_input_tokens", 0)),
            cache_write=int(obj.get("cache_write_input_tokens", 0)),
            output=int(obj.get("output_tokens", 0)),
            reasoning=int(obj.get("reasoning_output_tokens", 0)),
            total=int(obj.get("total_tokens", 0)),
        )
    except (TypeError, ValueError):
        return None


def _braced_object(line: str, start: int) -> str | None:
    """Return the balanced ``{...}`` substring beginning at/after ``start``."""
    open_at = line.find("{", start)
    if open_at < 0:
        return None
    depth = 0
    in_string = False
    escaped = False
    for i in range(open_at, len(line)):
        ch = line[i]
        if in_string:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                in_string = False
            continue
        if ch == '"':
            in_string = True
        elif ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return line[open_at : i + 1]
    return None


def extract_total_usage(line: str) -> Usage | None:
    """Pull the cumulative usage out of one rollout line, or None."""
    if _TOTAL_MARKER not in line:
        return None

    match = _TOTAL_USAGE_RE.search(line)
    if match:
        return Usage(
            input=int(match.group(1)),
            cached=int(match.group(2)),
            cache_write=int(match.group(3)),
            output=int(match.group(4)),
            reasoning=int(match.group(5)),
            total=int(match.group(6)),
        )

    # Fallback for schema drift: locate the key, then parse its object as JSON.
    key_at = line.find(_TOTAL_MARKER)
    blob = _braced_object(line, key_at + len(_TOTAL_MARKER))
    if blob is None:
        return None
    try:
        obj = json.loads(blob)
    except json.JSONDecodeError:
        return None
    return _usage_from_mapping(obj) if isinstance(obj, dict) else None


@dataclass
class FileUsage:
    path: Path
    sort_key: str
    session_id: str = ""
    cwd: str = ""
    source: str = "unknown"
    parent: str | None = None
    first: Usage | None = None
    last: Usage | None = None
    events: int = 0
    unparsed: int = 0

    @property
    def is_subagent(self) -> bool:
        return self.source == "subagent"

    def contribution(self, is_chain_head: bool) -> Usage:
        """Tokens consumed during this file's own lifetime."""
        if self.last is None:
            return Usage()
        baseline = Usage() if is_chain_head or self.first is None else self.first
        return self.last.grown_since(baseline)


def read_session_meta(path: Path, probe_lines: int = 5) -> dict | None:
    """Read the leading ``session_meta`` payload of a rollout file."""
    try:
        with path.open("r", encoding="utf-8", errors="replace") as handle:
            for _ in range(probe_lines):
                line = handle.readline()
                if not line:
                    break
                if _META_MARKER not in line:
                    continue
                try:
                    payload = json.loads(line).get("payload")
                except json.JSONDecodeError:
                    return None
                return payload if isinstance(payload, dict) else None
    except OSError:
        return None
    return None


def scan_file(path: Path, meta: dict) -> FileUsage:
    """Walk one rollout file and capture its first/last cumulative usage."""
    record = FileUsage(path=path, sort_key=path.name)
    record.session_id = str(meta.get("id") or meta.get("session_id") or "")
    record.cwd = str(meta.get("cwd") or "")
    record.source = str(meta.get("thread_source") or "unknown")
    parent = meta.get("parent_thread_id")
    record.parent = str(parent) if parent else None

    try:
        with path.open("r", encoding="utf-8", errors="replace") as handle:
            for line in handle:
                if _TOTAL_MARKER not in line:
                    continue
                usage = extract_total_usage(line)
                if usage is None:
                    record.unparsed += 1
                    continue
                if record.first is None:
                    record.first = usage
                record.last = usage
                record.events += 1
    except OSError:
        pass
    return record


# --- path matching ---------------------------------------------------------


def norm_path(value: str) -> str:
    """Case-insensitive, separator-normalised key for path comparison."""
    return os.path.normcase(os.path.normpath(value.strip().strip('"').strip("'")))


def matches(cwd: str, targets: list[str], subdirs: bool) -> bool:
    if not targets:
        return True
    key = norm_path(cwd)
    for target in targets:
        if key == target:
            return True
        if subdirs and key.startswith(target + os.sep):
            return True
    return False


# --- aggregation -----------------------------------------------------------


@dataclass
class Bucket:
    """Accumulated usage plus provenance counters."""

    total: Usage = field(default_factory=Usage)
    sessions: set[str] = field(default_factory=set)
    files: int = 0
    calls: int = 0
    first_ts: str = ""
    last_ts: str = ""

    def add(self, usage: Usage, session_id: str, calls: int, ts: str = "") -> None:
        self.total += usage
        self.sessions.add(session_id)
        self.files += 1
        self.calls += calls
        if ts:
            if not self.first_ts or ts < self.first_ts:
                self.first_ts = ts
            if not self.last_ts or ts > self.last_ts:
                self.last_ts = ts


def chain_contributions(records: list[FileUsage]) -> Iterator[tuple[FileUsage, Usage]]:
    """Yield each file with the tokens attributable to it.

    Files are grouped by session id and ordered by rollout filename, which
    begins with the creation timestamp.  The earliest file of a chain is
    treated as starting from zero; later files are diffed against their own
    inherited baseline so resumed tokens are counted exactly once.
    """
    by_session: dict[str, list[FileUsage]] = {}
    for record in records:
        by_session.setdefault(record.session_id or record.path.name, []).append(record)

    for group in by_session.values():
        group.sort(key=lambda r: r.sort_key)
        for index, record in enumerate(group):
            yield record, record.contribution(is_chain_head=(index == 0))


def store_roots(sessions_root: Path, include_archived: bool = True) -> list[Path]:
    """Rollout stores to scan.

    Codex keeps live rollouts in ``~/.codex/sessions`` and archived ones in the
    sibling ``~/.codex/archived_sessions``.  Archived threads are still real
    spend, so both are scanned by default.
    """
    roots = [sessions_root]
    archived = sessions_root.parent / "archived_sessions"
    if include_archived and archived.is_dir() and archived != sessions_root:
        roots.append(archived)
    return roots


def collect(
    roots: list[Path],
    targets: list[str],
    subdirs: bool = False,
    since: str | None = None,
    until: str | None = None,
) -> list[FileUsage]:
    """Scan every rollout file, pre-filtering on the cwd in session_meta.

    The whole file is only read when its directory already matched, which keeps
    directory-scoped queries cheap even over a multi-gigabyte sessions tree.
    """
    records: list[FileUsage] = []
    for root in roots:
        for path in sorted(root.rglob("*.jsonl")):
            meta = read_session_meta(path)
            if meta is None:
                continue
            cwd = str(meta.get("cwd") or "")
            if not matches(cwd, targets, subdirs):
                continue
            if since or until:
                stamp = day_from_name(path.name)
                if stamp:
                    if since and stamp < since:
                        continue
                    if until and stamp > until:
                        continue
            records.append(scan_file(path, meta))
    return records


# --- sqlite index ----------------------------------------------------------

_DEFAULT_INDEX = "state_5.sqlite"


def index_records(
    index_path: Path,
    targets: list[str],
    subdirs: bool = False,
    since: str | None = None,
    until: str | None = None,
) -> list[dict]:
    """Read per-thread totals from Codex's ``state_*.sqlite`` thread index.

    This index is authoritative in two ways the rollout files are not:

    * its ``cwd`` is the *project* directory, whereas a rollout's
      ``session_meta.cwd`` can be a scratch folder (Codex Desktop opens
      ``~/Documents/Codex/<date>/<name>`` and drives the real project there);
    * ``tokens_used`` is already a per-thread total, so resume chains need no
      de-duplication.

    It also covers threads whose rollout file has been deleted.
    """
    import sqlite3

    rows: list[dict] = []
    con = sqlite3.connect(f"file:{index_path}?mode=ro", uri=True)
    con.row_factory = sqlite3.Row
    try:
        for r in con.execute(
            "select id, cwd, tokens_used, created_at, archived, title, thread_source, "
            "first_user_message from threads order by created_at"
        ):
            cwd = (r["cwd"] or "").replace("\\\\?\\", "")
            if not matches(cwd, targets, subdirs):
                continue
            stamp = ""
            try:
                stamp = datetime.fromtimestamp(
                    int(r["created_at"]), tz=timezone.utc
                ).strftime("%Y-%m-%d")
            except (TypeError, ValueError, OSError):
                pass
            if since and stamp and stamp < since:
                continue
            if until and stamp and stamp > until:
                continue
            rows.append({
                "id": r["id"],
                "cwd": cwd,
                "tokens_used": int(r["tokens_used"] or 0),
                "created_at": stamp,
                "archived": bool(r["archived"]),
                "title": r["title"] or "",
                "source": r["thread_source"] or "unknown",
                "first_user_message": r["first_user_message"] or "",
            })
    finally:
        con.close()
    return rows


def find_index(sessions_root: Path) -> Path | None:
    """Locate the newest ``state_*.sqlite`` beside the sessions tree."""
    home = sessions_root.parent
    candidates = sorted(home.glob("state_*.sqlite"))
    return candidates[-1] if candidates else None



def day_from_name(name: str) -> str:
    """Extract ``YYYY-MM-DD`` from a rollout filename, or ''."""
    match = re.match(r"rollout-(\d{4}-\d{2}-\d{2})T", name)
    return match.group(1) if match else ""


# --- reporting -------------------------------------------------------------


def human(number: int) -> str:
    return f"{number:,}"


def compact(number: int) -> str:
    if number >= 1_000_000:
        return f"{number / 1_000_000:.2f}M"
    if number >= 1_000:
        return f"{number / 1_000:.1f}K"
    return str(number)


def print_summary(label: str, bucket: Bucket) -> None:
    u = bucket.total
    print(f"  {label:<14} {human(u.total):>16}  ({compact(u.total)})")
    print(f"  {'':<14} {'':>16}")
    print(f"    input        {human(u.input):>16}   (cached: {human(u.cached)})")
    print(f"    output       {human(u.output):>16}   (reasoning: {human(u.reasoning)})")
    if u.cache_write:
        print(f"    cache write  {human(u.cache_write):>16}")
    print(f"    sessions     {len(bucket.sessions):>16}")
    print(f"    rollout files{bucket.files:>16}")
    print(f"    API calls    {human(bucket.calls):>16}")
    if bucket.first_ts:
        print(f"    period       {bucket.first_ts[:10]} -> {bucket.last_ts[:10]}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="codex_token_usage.py",
        description="Tally Codex token usage from local rollout logs.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "examples:\n"
            "  codex_token_usage.py E:/Java/MiXianTu\n"
            "  codex_token_usage.py E:/Java/MiXianTu --subdirs\n"
            "  codex_token_usage.py E:/Java/MiXianTu --json\n"
            "  codex_token_usage.py                  # every project, grouped by directory\n"
            "  codex_token_usage.py --list-dirs\n"
        ),
    )
    parser.add_argument(
        "directories",
        nargs="*",
        metavar="DIR",
        help="working directory/directories to report on (default: every directory)",
    )
    parser.add_argument(
        "--sessions-root",
        default=os.environ.get("CODEX_SESSIONS_ROOT")
        or str(Path.home() / ".codex" / "sessions"),
        help="root of the Codex sessions tree (default: ~/.codex/sessions)",
    )
    parser.add_argument(
        "--subdirs",
        action="store_true",
        help="also count sessions whose cwd is inside a requested directory",
    )
    parser.add_argument(
        "--all",
        action="store_true",
        help="ignore directory filters and summarise every session",
    )
    parser.add_argument(
        "--by-dir",
        action="store_true",
        help="group the result by working directory instead of by session",
    )
    parser.add_argument(
        "--main-only",
        action="store_true",
        help="exclude sub-agent rollouts from the totals",
    )
    parser.add_argument(
        "--no-subagents",
        dest="main_only",
        action="store_true",
        help=argparse.SUPPRESS,
    )
    parser.add_argument(
        "--list-dirs",
        action="store_true",
        help="list every directory seen in the logs, with token totals",
    )
    parser.add_argument("--since", metavar="YYYY-MM-DD", help="only files on/after this day")
    parser.add_argument("--until", metavar="YYYY-MM-DD", help="only files on/before this day")
    parser.add_argument("--json", action="store_true", help="emit JSON instead of text")
    parser.add_argument(
        "--index",
        action="store_true",
        help="read totals from state_*.sqlite instead of scanning rollouts "
             "(catches threads whose rollout cwd is a scratch folder)",
    )
    parser.add_argument(
        "--index-path",
        metavar="FILE",
        help="explicit state_*.sqlite to use with --index",
    )
    parser.add_argument(
        "--no-archived",
        action="store_true",
        help="skip the archived_sessions store",
    )
    parser.add_argument(
        "--quiet", "-q", action="store_true", help="suppress the per-session listing"
    )
    args = parser.parse_args(argv)

    # Thread titles and paths are often non-ASCII; don't die on a legacy console.
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass

    sessions_root = Path(args.sessions_root).expanduser()
    if not sessions_root.is_dir():
        print(f"error: sessions root not found: {sessions_root}", file=sys.stderr)
        return 2

    targets = [norm_path(d) for d in args.directories]
    if targets and args.all:
        parser.error("give either DIR arguments or --all, not both")

    if args.index:
        return report_index(args, sessions_root, targets)

    roots = store_roots(sessions_root, include_archived=not args.no_archived)
    records = collect(roots, targets, args.subdirs, args.since, args.until)
    contributions = list(chain_contributions(records))

    if not contributions:
        scope = ", ".join(args.directories) if args.directories else "(all directories)"
        print(f"No sessions found for {scope} under {', '.join(str(r) for r in roots)}")
        return 1

    if args.main_only:
        contributions = [(r, u) for r, u in contributions if not r.is_subagent]

    main_bucket = Bucket()
    sub_bucket = Bucket()
    per_dir: dict[str, Bucket] = {}

    for record, usage in contributions:
        target = sub_bucket if record.is_subagent else main_bucket
        target.add(usage, record.session_id, record.events)
        per_dir.setdefault(record.cwd or "<none>", Bucket()).add(
            usage, record.session_id, record.events
        )

    grand = Usage()
    grand += main_bucket.total
    grand += sub_bucket.total

    if args.json:
        payload = {
            "sessions_root": str(sessions_root),
            "directories": args.directories,
            "total_tokens": grand.total,
            "main": _bucket_json(main_bucket),
            "subagents": _bucket_json(sub_bucket),
            "by_directory": {
                cwd: _bucket_json(bucket) for cwd, bucket in sorted(per_dir.items())
            },
            "files": [
                {
                    "path": str(record.path),
                    "session_id": record.session_id,
                    "cwd": record.cwd,
                    "source": record.source,
                    "events": record.events,
                    "first_total": record.first.total if record.first else None,
                    "last_total": record.last.total if record.last else None,
                    "contribution": usage.total,
                }
                for record, usage in contributions
            ],
        }
        json.dump(payload, sys.stdout, indent=2)
        print()
        return 0

    scope = ", ".join(args.directories) if args.directories else "all directories"
    print(f"Codex token usage for {scope}")
    print(f"sessions root: {sessions_root}")
    print()

    if args.list_dirs or args.by_dir or not args.directories:
        print(f"{'tokens':>16}  {'calls':>7}  {'sess':>5}  directory")
        print("-" * 78)
        for cwd, bucket in sorted(per_dir.items(), key=lambda kv: -kv[1].total.total):
            print(
                f"{human(bucket.total.total):>16}  {human(bucket.calls):>7}  "
                f"{len(bucket.sessions):>5}  {cwd}"
            )
        print("-" * 78)
        print(f"{human(grand.total):>16}  {human(main_bucket.calls + sub_bucket.calls):>7}  "
              f"{len(main_bucket.sessions | sub_bucket.sessions):>5}  TOTAL")
        print()

    print("=" * 62)
    print(f"TOTAL TOKENS: {human(grand.total)}   ({compact(grand.total)})")
    print("=" * 62)
    print()
    print_summary("main sessions", main_bucket)
    print()
    print_summary("sub-agents", sub_bucket)
    print()
    print("  breakdown of the total (input + output = total; cached/reasoning are")
    print("  already contained in input/output and are the discounted portions):")
    print(f"    input        {human(grand.input):>16}")
    print(f"      cached     {human(grand.cached):>16}")
    print(f"    output       {human(grand.output):>16}")
    print(f"      reasoning  {human(grand.reasoning):>16}")

    # The per-file listing is only useful when the caller narrowed the scope.
    if not args.quiet and args.directories:
        print()
        print("per-session detail (contribution = tokens consumed in that rollout file)")
        print(f"{'contribution':>16}  {'events':>7}  {'src':<8}  session / file")
        print("-" * 100)
        for record, usage in sorted(
            contributions, key=lambda ru: -ru[1].total
        ):
            print(
                f"{human(usage.total):>16}  {record.events:>7}  "
                f"{record.source:<8}  {record.session_id[:8]}  {record.path.name}"
            )

    return 0


def report_index(args, sessions_root: Path, targets: list[str]) -> int:
    """Report using the sqlite thread index rather than the rollout files."""
    index_path = Path(args.index_path).expanduser() if args.index_path else find_index(sessions_root)
    if index_path is None or not index_path.is_file():
        print(
            f"error: no state_*.sqlite index found beside {sessions_root}\n"
            "       run without --index to scan rollout files instead",
            file=sys.stderr,
        )
        return 2

    rows = index_records(index_path, targets, args.subdirs, args.since, args.until)
    if not rows:
        scope = ", ".join(args.directories) if args.directories else "(all directories)"
        print(f"No threads found for {scope} in {index_path.name}")
        return 1

    rows.sort(key=lambda r: -r["tokens_used"])
    total = sum(r["tokens_used"] for r in rows)
    by_month: dict[str, int] = {}
    by_dir: dict[str, int] = {}
    for r in rows:
        by_month[r["created_at"][:7]] = by_month.get(r["created_at"][:7], 0) + r["tokens_used"]
        by_dir[r["cwd"]] = by_dir.get(r["cwd"], 0) + r["tokens_used"]

    if args.json:
        json.dump({
            "index": str(index_path),
            "total_tokens": total,
            "threads": rows,
            "by_month": by_month,
            "by_directory": by_dir,
        }, sys.stdout, indent=2)
        print()
        return 0

    scope = ", ".join(args.directories) if args.directories else "all directories"
    print(f"Codex token usage (thread index) for {scope}")
    print(f"index: {index_path}")
    print()
    print("=" * 62)
    print(f"TOTAL TOKENS: {human(total)}   ({compact(total)})")
    print("=" * 62)
    print()
    print(f"  threads: {len(rows)}   archived: {sum(1 for r in rows if r['archived'])}")
    print()
    print("  by month:")
    for month, value in sorted(by_month.items()):
        print(f"    {month}  {human(value):>16}")
    print()
    print(f"{'tokens':>16}  {'date':<10}  {'src':<9}  title / cwd")
    print("-" * 100)
    for r in rows:
        print(f"{human(r['tokens_used']):>16}  {r['created_at']:<10}  {r['source']:<9}  "
              f"{r['id'][:8]}  {r['title'][:44]}")
        print(f"{'':>16}  {'':<10}  {'':<9}  cwd: {r['cwd']}")
    return 0


def _bucket_json(bucket: Bucket) -> dict:
    u = bucket.total
    return {
        "total_tokens": u.total,
        "input_tokens": u.input,
        "cached_input_tokens": u.cached,
        "cache_write_input_tokens": u.cache_write,
        "output_tokens": u.output,
        "reasoning_output_tokens": u.reasoning,
        "sessions": len(bucket.sessions),
        "rollout_files": bucket.files,
        "api_calls": bucket.calls,
    }


if __name__ == "__main__":
    raise SystemExit(main())
