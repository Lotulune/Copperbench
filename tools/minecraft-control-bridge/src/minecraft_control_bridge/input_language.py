"""Reversible target-thread keyboard preparation; never changes system defaults."""
import time

from .errors import BridgeError


class KeyboardGuard:
    def __init__(self, backend):
        self.backend = backend
        self.lease = None

    def prepare(self, window_id, policy, checkpoint):
        before = self.backend.keyboard_status(window_id)
        if not before.get("ime_risk") or before.get("ime_open") is False:
            return {"status": "ready", "before": before, "changed": False}
        if policy != "english":
            raise BridgeError("IME_ACTIVE", "IME layout can consume gameplay keys; use keyboard_policy='english' or switch Minecraft to English input", before)
        # Chinese IMEs commonly provide their own English mode with no separate
        # English HKL installed. Prefer closing that window's IME, then verify it.
        mode = "ime_open" if before.get("ime_open") is True else "layout"
        target = before["layout"] if mode == "ime_open" else next((h for h in self.backend.keyboard_layouts()
                       if (h & 0x3ff) == 9 and not self.backend.layout_is_ime(h)), None)
        if target is None:
            raise BridgeError("ENGLISH_LAYOUT_UNAVAILABLE", "IME state is unknown and no English layout is installed; switch the target manually", before)
        checkpoint()
        # Preserve the original layout even if the acknowledgement times out.
        if self.lease is None or before["layout"] != self.lease["applied"] or mode == "ime_open":
            self.lease = {"window_id": window_id, "before": before, "applied": target, "mode": mode}
        if mode == "ime_open":
            self.backend.request_ime_open(window_id, False)
        else:
            self.backend.request_keyboard_layout(window_id, target)
        deadline = time.monotonic() + .5
        while True:
            checkpoint()
            after = self.backend.keyboard_status(window_id)
            if after["layout"] == target and (not after["ime_risk"] or after.get("ime_open") is False):
                return {"status": "ready", "before": before, "after": after, "changed": True,
                        "method": mode, "restore_on_detach": True}
            if time.monotonic() >= deadline:
                raise BridgeError("IME_SWITCH_UNCONFIRMED", "Target did not confirm English input; no gameplay keys sent", after)
            time.sleep(.02)

    def restore(self):
        lease, self.lease = self.lease, None
        if not lease:
            return {"status": "not_changed"}
        try:
            current = self.backend.keyboard_status(lease["window_id"])
            original = lease["before"]
            if any(current[k] != original[k] for k in ("pid", "process_started", "thread_id")):
                return {"status": "skipped_target_changed"}
            if current["layout"] != lease["applied"]:
                return {"status": "skipped_user_changed_layout"}
            if lease["mode"] == "ime_open":
                if current.get("ime_open") is not False:
                    return {"status": "skipped_user_changed_ime"}
                self.backend.request_ime_open(lease["window_id"], original["ime_open"])
            else:
                self.backend.request_keyboard_layout(lease["window_id"], original["layout"])
            deadline = time.monotonic() + .5
            while time.monotonic() < deadline:
                restored = self.backend.keyboard_status(lease["window_id"])
                if restored["layout"] == original["layout"] and (lease["mode"] == "layout" or restored.get("ime_open") == original["ime_open"]):
                    return {"status": "restored", "layout": original["layout"]}
                time.sleep(.02)
            return {"status": "restore_unconfirmed"}
        except BridgeError as error:
            return {"status": "restore_unavailable", "error": error.as_dict()}
