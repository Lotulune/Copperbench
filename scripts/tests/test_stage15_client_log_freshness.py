"""Exercise the installed gate's actual Bash readiness predicate against reused logs."""
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest


@unittest.skipUnless(shutil.which("bash"), "Bash is required")
class ClientLogFreshnessTest(unittest.TestCase):
    def test_previous_render_log_cannot_satisfy_a_new_run(self):
        gate = (Path(__file__).parents[1] / "verify-stage15-linux-installed-guest.sh").read_text()
        condition = re.search(r'  if (\[\[ -f "\$client_log".*?); then', gate, re.S).group(1)
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory) / "latest.log"
            marker = Path(directory) / "started.marker"
            marker.touch()
            content = "\n".join([
                "Loading Minecraft 1.21.1 with Fabric Loader",
                "Backend library: LWJGL version",
                "Reloading ResourceManager:",
                "minecraft:textures/atlas/blocks.png-atlas",
            ])
            log.write_text(content)
            epoch = marker.stat().st_mtime_ns
            env = dict(os.environ, client_log=str(log), run_start_marker=str(marker),
                       loader_marker="Loading Minecraft 1.21.1 with Fabric Loader")

            def ready():
                return subprocess.run(["bash", "-c", condition], env=env, capture_output=True).returncode == 0

            os.utime(log, ns=(epoch - 1_000_000_000, epoch - 1_000_000_000))
            self.assertFalse(ready(), "A complete previous-run log must be rejected")
            os.utime(log, ns=(epoch + 1_000_000_000, epoch + 1_000_000_000))
            self.assertTrue(ready(), "A fresh complete render log must pass")
            log.write_text("Loading Minecraft 1.21.1 with Fabric Loader\n")
            os.utime(log, ns=(epoch + 1_000_000_000, epoch + 1_000_000_000))
            self.assertFalse(ready(), "A fresh startup log without render readiness must be rejected")


if __name__ == "__main__":
    unittest.main()
