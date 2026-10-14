"""Linux regression: closing the setup terminal must leave the launched app alive.

Usage: python3 launch_detach_test.py /path/to/jdk /path/to/copperbench-setup.jar
"""
import errno
import os
from pathlib import Path
import pty
import select
import subprocess
import sys
import tempfile
import time

jdk, jar = map(lambda value: Path(value).resolve(), sys.argv[1:3])
with tempfile.TemporaryDirectory(prefix="copperbench-launch-test-") as directory:
    root = Path(directory)
    source = root / "InstallerLaunchProbe.java"
    source.write_text("""package dev.copperbench.installer;
import java.nio.file.Path;
public class InstallerLaunchProbe {
  public static void main(String[] args) throws Exception {
    Process app = PlatformInstall.launchApplication(Path.of(args[0]));
    System.out.println("STARTED " + app.pid());
  }
}
""", encoding="utf-8")
    subprocess.run([jdk / "bin/javac", "--release", "17", "-cp", str(jar), "-d", root, source], check=True)
    launcher = root / "copperbench.sh"
    launcher.write_text("#!/bin/sh\nsleep 1\nprintf 'survived\\n' > survived.txt\nprintf 'startup output\\n'\n", encoding="utf-8")
    launcher.chmod(0o755)
    pid, terminal = pty.fork()
    if pid == 0:
        os.environ["XDG_STATE_HOME"] = str(root / "state")
        os.execv(str(jdk / "bin/java"), [str(jdk / "bin/java"), "-cp", f"{root}:{jar}",
                                      "dev.copperbench.installer.InstallerLaunchProbe", str(root)])
    output = b""
    try:
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline and b"STARTED " not in output:
            if select.select([terminal], [], [], 0.1)[0]:
                try:
                    part = os.read(terminal, 4096)
                except OSError as error:
                    if error.errno == errno.EIO:
                        break
                    raise
                if not part:
                    break
                output += part
        assert b"STARTED " in output, output.decode(errors="replace")
        # Wait for setup to exit, then close its controlling terminal while the app is sleeping.
        _, status = os.waitpid(pid, 0)
        assert os.waitstatus_to_exitcode(status) == 0, output.decode(errors="replace")
    finally:
        os.close(terminal)
    deadline = time.monotonic() + 5
    while not (root / "survived.txt").exists() and time.monotonic() < deadline:
        time.sleep(0.05)
    assert (root / "survived.txt").read_text() == "survived\n", "App died with the setup terminal"
    assert "startup output" in (root / "state/copperbench/startup.log").read_text()
    print("PASS: app survives setup terminal closure and retains startup output")
