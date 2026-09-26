"""Private worker entrypoint. stdout is only JSON receipts, stdin is never inherited."""
import argparse
import importlib
import json
import queue
import sys
import threading

from .backends import create_backend
from .client import OPERATIONS
from .engine import Engine
from .errors import BridgeError


def run(backend_factory=create_backend):
    requests = queue.Queue()
    stopped, disconnected = threading.Event(), threading.Event()
    engine = None

    def read():
        try:
            for line in sys.stdin:
                if len(line) > 1024 * 1024:
                    break
                request = json.loads(line)
                if request.get("operation") == "signal_stop":
                    stopped.set()
                else:
                    requests.put(request)
        except (OSError, ValueError):
            pass
        finally:
            disconnected.set()
            stopped.set()

    def send(value):
        print(json.dumps(value, ensure_ascii=True), flush=True)

    try:
        try:
            engine = Engine(backend_factory(), stopped, lambda: not disconnected.is_set())
            send({"ok": True})
        except Exception as exc:
            error = exc if isinstance(exc, BridgeError) else BridgeError("BACKEND_UNAVAILABLE", str(exc))
            send({"ok": False, "error": error.as_dict()})
            return
        threading.Thread(target=read, daemon=True).start()
        while not disconnected.is_set():
            try:
                request = requests.get(timeout=0.02)
            except queue.Empty:
                engine.poll_stop()
                continue
            try:
                operation = request["operation"]
                if operation not in OPERATIONS:
                    raise BridgeError("UNKNOWN_OPERATION", "Unknown operation")
                result = getattr(engine, operation)(**request["arguments"])
                send({"ok": True, "result": result})
            except Exception as exc:
                error = exc if isinstance(exc, BridgeError) else BridgeError("BACKEND_ERROR", str(exc))
                send({"ok": False, "error": error.as_dict()})
    except (BrokenPipeError, OSError):
        pass
    finally:
        if engine:
            engine.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--backend-factory", help="Internal test fixture module:callable; not exposed by MCP")
    args = parser.parse_args()
    factory = create_backend
    if args.backend_factory:
        module, name = args.backend_factory.split(":", 1)
        factory = getattr(importlib.import_module(module), name)
    run(factory)
