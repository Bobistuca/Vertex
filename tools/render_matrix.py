#!/usr/bin/env python3
"""Renders the compatibility matrix from the smoke test results published by the Smoke workflow.

Usage: render_matrix.py <results-dir> <readme-fragment-out> <detail-out>
The results directory holds the <impl>-<version>.json files of the `smoke-results` branch.
"""
import glob
import json
import os
import re
import sys

IMPLS = [("craftbukkit", "CraftBukkit"), ("spigot", "Spigot"), ("paper", "Paper")]
BRANCHES = [("26.1", "26.1.2"), ("26.2", "26.2"), ("26.3", "26.3")]


def load(results):
    out = {}
    for path in glob.glob(os.path.join(results, "*.json")):
        d = json.load(open(path))
        out[(d["info"]["impl"], d["info"]["version"])] = d
    return out


def cell(d):
    if d is None:
        return "unverified (not run)"
    sid = d["info"].get("server_id", "")
    short = re.sub(r" sha256=\w+", "", sid)
    if d["failed"]:
        return f"**FAILED** {d['failed']} check(s) — {short}"
    packets = next((r for r in d["results"] if r["name"] == "real-packet-observed-by-listener"), None)
    live = "; live client packets observed" if packets and packets["status"] == "pass" else ""
    return f"verified on {d['info']['version']} — {short}{live}"


def main():
    results, readme_out, detail_out = sys.argv[1:4]
    data = load(results)
    lines = ["| Server | 26.1 | 26.2 | 26.3 |", "|---|---|---|---|"]
    for impl, label in IMPLS:
        cells = [cell(data.get((impl, ver))) for _, ver in BRANCHES]
        lines.append(f"| {label} | " + " | ".join(cells) + " |")
    note = [
        "",
        "*verified* = the startup and functionality smoke test passed on that exact server build (Temurin Java ",
        "25.0.4 on the Linux CI runner): plugin load, packet-listener initialization, commands, localized messages, ",
        "aggregation, safe reload, administrative auditing, evidence persistence and clean shutdown. Minecraft 26.1 ",
        "was tested with the 26.1.2 builds; 26.1 and 26.1.1 share the same Spigot/CraftBukkit build but were not ",
        "tested separately, and Paper 26.1/26.1.1 were not tested. Any combination or patch not listed is ",
        "**unverified**. A real protocol client (permissions, alert recipients, a malformed packet reaching the ",
        "listener) was only available for 26.1.2; on 26.2 and 26.3 the packet listener is verified to initialize ",
        "and run, but no client traffic was observed. Not covered by the smoke tests: the live action-bar counter, ",
        "alert sounds, tab completion, the LuckPerms integration and non-Linux hosts.",
    ]
    open(readme_out, "w").write("\n".join(lines + note) + "\n")

    detail = ["# Compatibility and verification record", "",
              "Generated from the `smoke-results` branch written by the Smoke workflow.", ""]
    detail += lines + note + [""]
    for impl, label in IMPLS:
        for _, ver in BRANCHES:
            d = data.get((impl, ver))
            if d is None:
                continue
            i = d["info"]
            detail.append(f"## {label} {ver}")
            detail.append("")
            detail.append(f"- Server build: `{i.get('server_id','')}`")
            first = (i.get("server_version_output", "").split("\n") + [""])[0]
            detail.append(f"- Reported by the server: `{first[:200]}`")
            detail.append(f"- Result: {d['passed']} passed, {d['failed']} failed, {d['skipped']} skipped")
            for r in d["results"]:
                if r["status"] in ("fail", "skipped", "info"):
                    detail.append(f"  - {r['status']}: `{r['name']}` {r['detail'][:150].replace(chr(10), ' ')}")
            detail.append("")
    open(detail_out, "w").write("\n".join(detail) + "\n")


if __name__ == "__main__":
    main()
