"""Bounded Jev advice inside the input worker; network waits never hold input."""
import queue
import threading
import time

from .errors import BridgeError
from .jev import JevClient


def choose_transition(engine, state, goal, transition, deadline):
    result = queue.Queue(maxsize=1)
    remaining = min(4.0, deadline - time.monotonic() - 2.5)
    if remaining < .2:
        raise BridgeError("MENU_FLOW_TIMEOUT", "Insufficient budget for Jev and guarded input")
    candidates = {"PROCEED": transition,
                  "WAIT": "Stop this request without input; the observed state is insufficient",
                  "STOP": "Stop this request; the planned transition is inappropriate"}

    def request():
        try:
            with JevClient(timeout=remaining) as client:
                result.put(client.choose(state, goal, candidates))
        except BridgeError as error:
            result.put(error)
        except Exception:
            result.put(BridgeError("JEV_CONNECTION_FAILED", "Jev failed; no transition dispatched"))

    engine.backend.release_all()
    threading.Thread(target=request, daemon=True).start()
    end = time.monotonic() + remaining
    while True:
        engine._check()  # F8, focus loss, parent disconnect remain responsive during HTTP.
        try:
            choice = result.get_nowait()
            if isinstance(choice, BridgeError):
                raise choice
            return choice
        except queue.Empty:
            if time.monotonic() >= end:
                raise BridgeError("JEV_TIMEOUT", "Jev did not respond within the local decision budget; no input dispatched")
            engine._wait(20, deadline)
