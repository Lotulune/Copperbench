"""Show desktop prompts while running the unchanged installed-Agent verifier."""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import re
from pathlib import Path
import subprocess
import sys


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--helper", type=Path, required=True)
    parser.add_argument("--workspace", type=Path, required=True)
    parser.add_argument("--candidate-sha256", required=True)
    parser.add_argument("--output-directory", type=Path, required=True)
    args = parser.parse_args()
    helper = args.helper.resolve(strict=True)
    workspace = args.workspace.resolve(strict=True)
    output = args.output_directory.resolve()
    # Never overwrite a prior success or failure when the operator replays a gate.
    output.mkdir(parents=True, exist_ok=False)

    credential = subprocess.run([
        "zenity", "--password", "--title=Stage16 d9fb1458 Desktop MCP",
        "--text=Copy the token or MCP configuration from Copperbench AI / MCP and paste it here. "
        "The token stays inside this VM.", "--width=520",
    ], capture_output=True, text=True, check=False)
    if credential.returncode != 0 or not credential.stdout.strip():
        return 2
    pasted = credential.stdout.rstrip("\r\n")
    match = re.search(r"Authorization:\s*Bearer\s+(\S+)", pasted)
    token = match.group(1) if match else pasted
    del pasted, match
    del credential
    notifications: list[subprocess.Popen] = []
    events: list[dict[str, str]] = []

    def notify(stage: str, message: str) -> None:
        events.append({"stage": stage, "at": datetime.now(timezone.utc).isoformat()})
        notifications.append(subprocess.Popen([
            "zenity", "--info", "--title=Stage16 d9fb1458 verification", "--width=560",
            "--timeout=90", "--text=" + message,
        ], stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL))

    with (output / "helper.log").open("x", encoding="utf-8") as log:
        with subprocess.Popen([
            sys.executable, "-u", str(helper), str(workspace),
            "--candidate-sha256", args.candidate_sha256,
            "--output", str(output / "external-agent-result.json"),
        ], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                text=True, start_new_session=True) as process:
            # No controlling TTY: getpass reads this private pipe, never argv/env/disk.
            process.stdin.write(token + "\n")
            process.stdin.close()
            for line in process.stdout:
                safe_line = line.replace(token, "[REDACTED]")
                log.write(safe_line)
                log.flush()
                if line.startswith("Confirm the Minecraft window is visibly usable"):
                    notify("minecraft-close-requested",
                           "Minecraft passed render readiness and stayed running for 10 seconds. "
                           "Confirm the game window is usable, then close Minecraft normally. "
                           "Keep Copperbench open until the next prompt. Do not cancel the task.")
                elif line.startswith("Agent loop passed. Close the installed Copperbench"):
                    notify("copperbench-close-requested",
                           "Agent builds, conflict recovery and Minecraft normal close passed. "
                           "Close the Copperbench WORKSPACE window normally now (within 5 minutes). "
                           "Keep the VM and this verifier running.")
                elif line.startswith("Reopen installed Copperbench now"):
                    notify("copperbench-reopen-requested", "Normal close passed. Reopen the saved Copperbench workspace within 5 minutes.")
                elif line.startswith("Old token rejected by reopened session."):
                    notify("copperbench-second-close-requested", "The new session rejected the previous token with HTTP 401. Close Copperbench normally again.")
            status = process.wait()
    token = ""
    notify("verifier-finished", f"Verifier finished with exit code {status}. "
           "The evidence directory contains the result or failure log.")
    (output / "desktop-launcher-result.json").write_text(json.dumps({
        "schemaVersion": "1.0", "helperExitCode": status,
        "helperPath": str(helper), "workspacePath": str(workspace),
        "candidateSha256": args.candidate_sha256, "notificationEvents": events,
        "formalSupportClaim": False,
    }, indent=2) + "\n", encoding="utf-8")
    return status


if __name__ == "__main__":
    raise SystemExit(main())
