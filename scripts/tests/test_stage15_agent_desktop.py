"""Exercise desktop-launcher subprocess transport without a GUI or real credential."""

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


LAUNCHER = Path(__file__).resolve().parents[1] / "stage15/run-installed-agent-desktop.py"
TOKEN = "synthetic-test-credential-not-a-real-token"


@unittest.skipUnless(sys.platform == "linux", "Linux desktop subprocess contract")
class DesktopLauncherTest(unittest.TestCase):
    def run_case(self, helper_exit):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            binary = root / "zenity"
            binary.write_text("#!/usr/bin/python3\nimport sys\n"
                              f"if '--password' in sys.argv: print({TOKEN!r})\n")
            binary.chmod(0o755)
            helper = root / "helper.py"
            helper.write_text(
                "import json,os,pathlib,sys\n"
                "token=sys.stdin.readline().strip()\n"
                f"assert token == {TOKEN!r}\n"
                "assert token not in str(sys.argv)\n"
                "assert token not in str(dict(os.environ))\n"
                "print('defensive-redaction-check: '+token,flush=True)\n"
                "print('Confirm the Minecraft window is visibly usable',flush=True)\n"
                "print('Agent loop passed. Close the installed Copperbench',flush=True)\n"
                f"if {helper_exit} == 0:\n"
                " pathlib.Path(sys.argv[sys.argv.index('--output')+1]).write_text("
                "json.dumps({'syntheticHelperResult':True}))\n"
                f"sys.exit({helper_exit})\n")
            workspace = root / "workspace.mcreator"
            workspace.write_text("{}")
            output = root / "result"
            command = [sys.executable, str(LAUNCHER), "--helper", str(helper),
                       "--workspace", str(workspace), "--candidate-sha256", "a" * 64,
                       "--output-directory", str(output)]
            environment = dict(os.environ, PATH=str(root) + os.pathsep + os.environ["PATH"])
            completed = subprocess.run(command, env=environment, capture_output=True, text=True, timeout=20)
            self.assertEqual(completed.returncode, helper_exit, completed.stderr)
            self.assertNotIn(TOKEN, completed.stdout + completed.stderr)
            self.assertNotIn(TOKEN, (output / "helper.log").read_text())
            self.assertIn("[REDACTED]", (output / "helper.log").read_text())
            result = json.loads((output / "desktop-launcher-result.json").read_text())
            self.assertEqual(result["helperExitCode"], helper_exit)
            self.assertFalse(result["formalSupportClaim"])
            self.assertEqual([event["stage"] for event in result["notificationEvents"]],
                             ["minecraft-close-requested", "copperbench-close-requested", "verifier-finished"])
            self.assertEqual((output / "external-agent-result.json").exists(), helper_exit == 0)
            before = (output / "helper.log").read_bytes()
            replay = subprocess.run(command, env=environment, capture_output=True, timeout=20)
            self.assertNotEqual(replay.returncode, 0)
            self.assertEqual((output / "helper.log").read_bytes(), before)

    def test_success_preserves_helper_result_and_redacts_transport(self):
        self.run_case(0)

    def test_failure_remains_failure_and_existing_evidence_is_preserved(self):
        self.run_case(7)


if __name__ == "__main__":
    unittest.main()
