"""OS-owned lock: automatically released on process exit, never unlink a live lock."""
import hashlib
import os
import re
import tempfile
from pathlib import Path

from .errors import BridgeError


class DesktopLock:
    def __init__(self, namespace="native"):
        display = re.sub(r'\.\d+$', '', os.environ.get('DISPLAY', 'native'))
        identity = f"{namespace}:{os.environ.get('USERNAME', os.environ.get('USER', 'user'))}:{display}"
        name = hashlib.sha256(identity.encode()).hexdigest()[:24]
        self.path = Path(tempfile.gettempdir()) / f"minecraft-control-{name}.lock"
        self.stream = None

    def acquire(self):
        if self.stream:
            return
        stream = self.path.open("a+b")
        try:
            stream.seek(0, 2)
            if not stream.tell():
                stream.write(b"0")
                stream.flush()
            stream.seek(0)
            if os.name == "nt":
                import msvcrt
                msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(stream.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError as error:
            stream.close()
            raise BridgeError("DESKTOP_BUSY", "Another bridge owns this desktop") from error
        self.stream = stream

    def release(self):
        if self.stream:
            self.stream.close()
            self.stream = None
