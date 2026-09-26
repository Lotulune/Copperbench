import json
import os
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path


class WorkerLifecycleTests(unittest.TestCase):
    def test_abrupt_controller_exit_releases_input_and_finalizes_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            script = root / "controller.py"
            script.write_text('''import json, sys
from pathlib import Path
from minecraft_control_bridge import Bridge
from test_engine import FakeBackend

if __name__ == "__main__":
    b = Bridge(_backend_factory=FakeBackend)
    a = b.attach("100", sys.argv[1])
    sid = a["session_id"]
    b.observe(sid)
    Path(sys.argv[1], "ready.json").write_text(json.dumps(a))
    b.step(sid, "disconnect", [{"type":"hold", "keys":["w"], "duration_ms":1900}])
    b.close()
''', encoding="utf-8")
            env = dict(os.environ, PYTHONPATH=str(Path(__file__).parent.resolve()))
            process = subprocess.Popen([sys.executable, str(script), directory], env=env,
                                       stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
            try:
                deadline = time.monotonic() + 10
                while not (root / "ready.json").exists() and time.monotonic() < deadline:
                    time.sleep(0.02)
                self.assertTrue((root / "ready.json").exists())
                a = json.loads((root / "ready.json").read_text())
                evidence = Path(a["evidence_dir"])
                # Wait for the worker to enter the action before crashing only its controller.
                while time.monotonic() < deadline:
                    if '"action_started"' in (evidence / "actions.jsonl").read_text():
                        break
                    time.sleep(0.02)
                process.terminate()
                process.wait(timeout=5)
                while not (evidence / "hashes.json").exists() and time.monotonic() < deadline:
                    time.sleep(0.02)
                self.assertTrue((evidence / "hashes.json").exists(), "Worker did not finalize after parent died")
                events = [json.loads(line) for line in (evidence / "actions.jsonl").read_text().splitlines()]
                receipt = next(e for e in events if e["type"] == "step_finished")
                self.assertEqual("interrupted", receipt["status"])
                self.assertEqual("CONTROLLER_DISCONNECTED", receipt["error"]["code"])
                self.assertLess(receipt["input_elapsed_ms"], 1000)
            finally:
                if process.poll() is None:
                    process.terminate()
                    process.wait(timeout=5)
                process.stderr.close()


if __name__ == "__main__":
    unittest.main()
