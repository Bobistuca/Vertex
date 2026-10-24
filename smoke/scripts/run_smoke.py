#!/usr/bin/env python3
"""Startup and functionality smoke test of Vertex.jar on one server implementation and Minecraft version.

Every result is measured on a real server process. Nothing is simulated: a check that cannot run is reported as
"skipped" with the reason, never as passed.
"""
import argparse
import json
import os
import pathlib
import re
import secrets
import shutil
import subprocess
import sys
import threading
import time

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from rcon import Rcon  # noqa: E402

SECTION = re.compile("§.")


def strip(text):
    return SECTION.sub("", text)


class Server:
    def __init__(self, workdir, jar, java_opts):
        self.lines = []
        self.lock = threading.Lock()
        self.proc = subprocess.Popen(
            ["java", *java_opts, "-Dcom.mojang.eula.agree=true", "-jar", str(jar), "nogui"],
            cwd=workdir, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, encoding="utf-8", errors="replace")
        threading.Thread(target=self._pump, daemon=True).start()

    def _pump(self):
        for line in self.proc.stdout:
            with self.lock:
                self.lines.append(line.rstrip("\n"))

    def snapshot(self):
        with self.lock:
            return list(self.lines)

    def wait_for(self, pattern, timeout, start=0):
        rx = re.compile(pattern)
        end = time.time() + timeout
        while time.time() < end:
            lines = self.snapshot()
            for i in range(start, len(lines)):
                if rx.search(strip(lines[i])):
                    return i
            if self.proc.poll() is not None and time.time() > end - timeout + 2:
                lines = self.snapshot()
                for i in range(start, len(lines)):
                    if rx.search(strip(lines[i])):
                        return i
                return None
            time.sleep(0.5)
        return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--impl", required=True)
    ap.add_argument("--version", required=True)
    ap.add_argument("--server-jar", required=True)
    ap.add_argument("--vertex-jar", required=True)
    ap.add_argument("--helper-jar", required=True)
    ap.add_argument("--workdir", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--bot-version", default="")
    ap.add_argument("--server-id", default="")
    args = ap.parse_args()

    work = pathlib.Path(args.workdir).resolve()
    if work.exists():
        shutil.rmtree(work)
    (work / "plugins").mkdir(parents=True)
    shutil.copy(args.server_jar, work / "server.jar")
    shutil.copy(args.vertex_jar, work / "plugins" / "Vertex.jar")
    shutil.copy(args.helper_jar, work / "plugins" / "VertexSmokeHelper.jar")
    rcon_password = secrets.token_hex(8)
    (work / "eula.txt").write_text("eula=true\n")
    (work / "server.properties").write_text("\n".join([
        "online-mode=false", "server-port=25565", "enable-rcon=true", "rcon.port=25575",
        f"rcon.password={rcon_password}", "broadcast-rcon-to-ops=false", "view-distance=3",
        "simulation-distance=3", "spawn-protection=0", "max-players=5", "generate-structures=false",
        "allow-nether=false", "motd=Vertex smoke test", "level-seed=1", ""]))

    results = []

    def check(name, ok, detail="", status=None):
        results.append({"name": name, "status": status or ("pass" if ok else "fail"), "detail": str(detail)[:400]})
        print(f"[{results[-1]['status'].upper():7}] {name}: {str(detail)[:200]}", flush=True)

    started = time.time()
    server = Server(work, work / "server.jar", ["-Xms512M", "-Xmx1536M"])
    done = server.wait_for(r"Done \(", 540)
    check("server-start", done is not None, f"{time.time() - started:.0f}s to reach Done")
    info = {"impl": args.impl, "version": args.version, "server_id": args.server_id}
    if done is None:
        info["log_tail"] = [strip(l) for l in server.snapshot()[-40:]]
        finish(results, info, args, server, None, work)
        return

    rcon = None
    for _ in range(30):
        try:
            rcon = Rcon("127.0.0.1", 25575, rcon_password)
            break
        except (OSError, RuntimeError):
            time.sleep(1)
    check("rcon-connect", rcon is not None)

    def run(command):
        try:
            return strip(rcon.command(command))
        except (OSError, RuntimeError) as e:
            return f"<rcon error: {e}>"

    version_out = run("version")
    info["server_version_output"] = version_out[:300]
    print("version:", version_out[:200], flush=True)

    log = [strip(l) for l in server.snapshot()]
    joined = "\n".join(log)
    loaded = re.search(r"Enabling Vertex", joined) is not None
    check("plugin-load", loaded, "Enabling Vertex found in the log" if loaded else "no 'Enabling Vertex' line")
    banner = server.wait_for(r"(Vertex em execução|Vertex is running)", 20)
    check("plugin-enable", banner is not None, "startup banner printed" if banner is not None else "no startup banner")
    bad = [l for l in log if re.search(r"Could not load|Error occurred while enabling Vertex|\bSEVERE\b.*Vertex", l)]
    check("no-load-errors", not bad, bad[:2])

    status = run("vertex status")
    info["status_output"] = status[:3000]
    pm = re.search(r"(?:Observação de pacotes|Packet observation): ([^\n]+)", status)
    check("packet-listener-running", bool(pm) and pm.group(1).startswith("running"), pm.group(1) if pm else status[:200])
    mm = re.search(r"(?:Mensagens|Messaging): ([^\n]+)", status)
    check("messaging-capability", bool(mm), mm.group(1) if mm else "missing", status="info")
    tm = re.search(r"(?:Métricas do servidor|Server metrics): ([^\n]+)", status)
    check("metrics-capability", bool(tm), tm.group(1) if tm else "missing", status="info")
    active = len(re.findall(r"ativa |active ", status))
    check("checks-listed", "AutoClickerPattern" in status and "ReachDistance" in status, f"{active} lines mention active")

    out = run("vertex help")
    check("command-help", "Comandos" in out or "Commands" in out, out[:120])
    out = run("vx help")
    check("command-alias-vx", "Comandos" in out or "Commands" in out, out[:120])
    out = run("vertex inspect Nobody")
    check("command-inspect-unknown-player", "não encontrado" in out or "not found" in out, out[:120])
    out = run("vertex history")
    check("command-history", "Histórico" in out or "History" in out, out[:120])
    out = run("vertex alerts")
    check("command-alerts-console-refused", "jogador" in out or "player" in out, out[:120])
    out = run("vertex nonsense")
    check("command-unknown-sub", "desconhecido" in out or "Unknown" in out, out[:120])

    # --- aggregation through the real pipeline (helper plugin) ---
    helper_done = server.wait_for(r"SMOKE_HELPER_DONE|SMOKE_HELPER_FAIL", 120)
    time.sleep(4)
    log = [strip(l) for l in server.snapshot()]
    check("helper-ran", helper_done is not None and not any("SMOKE_HELPER_FAIL" in l for l in log),
          [l for l in log if "SMOKE_HELPER" in l][:3])
    two_x = [l for l in log if re.search(r"SmokeSubject +• +AutoClickerPattern +• +2x", l)]
    check("aggregation-2x-single-message", len(two_x) == 1, f"{len(two_x)} message(s): {two_x[:1]}")
    reach = [l for l in log if re.search(r"SmokeSubject +• +ReachDistance +• +1x", l)]
    check("duplicate-observation-counted-once", len(reach) == 1, f"{len(reach)} message(s): {reach[:1]}")
    check("alert-uses-identity-format", bool(two_x) and "[Vertex]" in two_x[0], two_x[:1])

    # --- safe reload ---
    cfg = work / "plugins" / "Vertex" / "config.yml"
    original = cfg.read_text(encoding="utf-8") if cfg.exists() else ""
    check("default-config-written", bool(original), str(cfg))
    cfg.write_text(original.replace("aggregation-window-ms: 2000", "aggregation-window-ms: 5"), encoding="utf-8")
    out = run("vertex reload")
    check("reload-invalid-refused", "recusada" in out or "refused" in out, out[:200])
    check("reload-keeps-working-config", "Estado do Vertex" in run("vertex status") or "Vertex status" in run("vertex status"))
    cfg.write_text(original.replace("language: pt_BR", "language: en_US"), encoding="utf-8")
    out = run("vertex reload")
    check("reload-valid-applied", "reloaded successfully" in out or "recarregada" in out, out[:200])
    out = run("vertex help")
    check("language-switch-en_US", "Commands" in out, out[:120])

    # --- optional real client (26.1.x only) ---
    if args.bot_version:
        bot_checks(args, server, run, results, check)
    else:
        check("real-client-packets", False, "no protocol client available for this Minecraft version", status="skipped")

    # --- clean shutdown ---
    run("stop")
    try:
        server.proc.wait(timeout=120)
        exited = True
    except subprocess.TimeoutExpired:
        server.proc.kill()
        exited = False
    check("clean-shutdown", exited and server.proc.returncode == 0, f"exit code {server.proc.returncode}")
    log = [strip(l) for l in server.snapshot()]
    check("shutdown-message", any(re.search(r"Vertex (stopped|encerrado)", l) for l in log))
    traces = [l for l in log if "io.github.bobistuca.vertex" in l and re.search(r"\tat |Exception|Error", l)]
    check("no-vertex-stack-traces", not traces, traces[:3])
    evidence = work / "plugins" / "Vertex" / "data" / "evidence.log"
    rows = evidence.read_text(encoding="utf-8").splitlines() if evidence.exists() else []
    counts = [int(r.split("\t")[8]) for r in rows if r.startswith("V1\t") and "AutoClickerPattern" in r and len(r.split("\t")) > 8]
    check("evidence-persisted-with-accurate-total", 2 in counts, f"{len(rows)} record(s); counts={counts[:5]}")
    finish(results, info, args, server, rows, work)


def bot_checks(args, server, run, results, check):
    node = shutil.which("node")
    script = pathlib.Path(__file__).parent / "bot.js"
    if not node or not (pathlib.Path(__file__).parent / "node_modules").exists():
        check("real-client-packets", False, "node or mineflayer not installed", status="skipped")
        return
    proc = subprocess.Popen([node, str(script), "127.0.0.1", "25565", "VertexBot", args.bot_version],
                            cwd=script.parent, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            text=True, encoding="utf-8", errors="replace")
    lines = []
    lock = threading.Lock()

    def pump():
        for line in proc.stdout:
            with lock:
                lines.append(line.rstrip("\n"))

    threading.Thread(target=pump, daemon=True).start()

    def send(text):
        try:
            proc.stdin.write(text + "\n")
            proc.stdin.flush()
        except OSError:
            pass

    def seen(pattern, since=0, timeout=15):
        rx = re.compile(pattern, re.I)
        end = time.time() + timeout
        while time.time() < end:
            with lock:
                snap = list(lines)
            for i in range(since, len(snap)):
                if rx.search(snap[i]):
                    return i, snap[i]
            time.sleep(0.4)
        return None, None

    def mark():
        with lock:
            return len(lines)

    spawn, line = seen(r"BOT_SPAWN", 0, 90)
    if spawn is None:
        with lock:
            tail = lines[-5:]
        check("real-client-join", False, tail, status="skipped" if any("BOT_ERROR" in t or "BOT_KICKED" in t for t in tail) else "fail")
        proc.kill()
        return
    check("real-client-join", True, f"bot joined with client version {args.bot_version}")
    time.sleep(3)
    run("gamemode survival VertexBot")
    time.sleep(1)

    m = mark(); send("chat /vertex status")
    i, l = seen(r"permiss|permission", m, 12)
    check("permission-ordinary-player-denied-status", i is not None, l)
    m = mark(); send("chat /vertex alerts")
    i, l = seen(r"permiss|permission", m, 12)
    check("permission-survival-player-denied-alerts-toggle", i is not None, l)

    run("gamemode creative VertexBot"); time.sleep(2)
    m = mark(); send("chat /vertex history")
    i, l = seen(r"permiss|permission", m, 12)
    check("creative-does-not-grant-commands", i is not None, l)
    m = mark(); send("chat /vertex alerts")
    i, l = seen(r"alertas pessoais|personal alerts", m, 12)
    check("creative-can-toggle-own-alerts", i is not None, l)
    m = mark(); send("chat /vertex alerts")
    seen(r"alertas pessoais|personal alerts", m, 8)  # switch back on

    m = mark(); run("smoke"); i, l = seen(r"AutoClickerPattern.*2x", m, 20)
    check("creative-recipient-receives-alert", i is not None, l)
    run("gamemode survival VertexBot"); time.sleep(2)
    m = mark(); run("smoke"); i, l = seen(r"AutoClickerPattern.*2x", m, 12)
    check("survival-non-operator-receives-nothing", i is None, l or "no alert received")
    run("op VertexBot"); time.sleep(2)
    m = mark(); run("smoke"); i, l = seen(r"AutoClickerPattern.*2x", m, 20)
    check("operator-receives-alert-in-survival", i is not None, l)
    m = mark(); send("chat /vertex status")
    i, l = seen(r"Estado do Vertex|Vertex status", m, 15)
    check("operator-can-use-status", i is not None, l)
    run("deop VertexBot")

    before = len(server.snapshot())
    send("raw_held 12")
    idx = server.wait_for(r"VertexBot +• +InvalidPacketField", 20, before)
    check("real-packet-observed-by-listener", idx is not None,
          strip(server.snapshot()[idx]) if idx is not None else "no InvalidPacketField for VertexBot in the console log")
    send("quit")
    time.sleep(2)
    proc.kill()


def finish(results, info, args, server, rows, work):
    try:
        server.proc.kill()
    except OSError:
        pass
    summary = {"info": info, "results": results,
               "passed": sum(1 for r in results if r["status"] == "pass"),
               "failed": sum(1 for r in results if r["status"] == "fail"),
               "skipped": sum(1 for r in results if r["status"] == "skipped")}
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8")
    log_path = out.with_suffix(".log")
    log_path.write_text("\n".join(strip(l) for l in server.snapshot()[-600:]), encoding="utf-8")
    print(json.dumps({k: summary[k] for k in ("passed", "failed", "skipped")}), flush=True)
    sys.exit(0 if summary["failed"] == 0 else 1)


if __name__ == "__main__":
    main()
