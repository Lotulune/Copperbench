"""One-time calibration from screenshots whose labels/targets the planner has inspected."""
import hashlib
import json
from pathlib import Path

from PIL import Image

from .menu_flow import MenuProfile


def build_profile(spec, output):
    """No OCR or inferred labels: the caller supplies reviewed regions and the exact world name."""
    roles = {"title": {"singleplayer", "quit"}, "world_list": {"heading", "create"},
             "pause": {"heading", "save"}, "playing": {"hud_left", "hud_right"}}
    if set(spec.get("states", {})) != set(roles) or any(
            set(spec["states"][state].get("markers", {})) != names for state, names in roles.items()):
        raise ValueError("Calibration needs only the documented state and marker names")
    output = Path(output)
    output.mkdir(parents=True, exist_ok=False)
    data = {"schema_version": 1, "world_name": spec["world_name"],
            "capture_size": spec["capture_size"], "world_search_box": spec["world_search_box"],
            "empty_below_entry": spec["empty_below_entry"], "states": {}, "source_frames": {}}
    for state, state_spec in spec["states"].items():
        source = Path(state_spec["source"]).resolve(strict=True)
        source_bytes = source.read_bytes()
        data["source_frames"][state] = {"path": str(source), "sha256": hashlib.sha256(source_bytes).hexdigest()}
        data["states"][state] = {"markers": {}}
        if state_spec.get("title_suffix"):
            data["states"][state]["title_suffix"] = state_spec["title_suffix"]
        with Image.open(source) as image:
            if list(image.size) != spec["capture_size"]:
                raise ValueError("All reviewed frames must have capture_size dimensions")
            markers = dict(state_spec["markers"])
            if state == "world_list":
                markers["world_entry"] = spec["world_entry"]
            for role, marker in markers.items():
                box = marker["box"]
                x, y, w, h = box
                if min(x, y) < 0 or min(w, h) < 1 or x+w > image.width or y+h > image.height:
                    raise ValueError("Marker outside source image")
                filename = f"{state}-{role}.png"
                path = output / filename
                image.crop((x, y, x+w, y+h)).convert("RGB").save(path)
                value = {"box": box, "image": filename, "mode": marker.get("mode", "ink"),
                         "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
                if role == "world_entry":
                    data["world_entry"] = value
                else:
                    data["states"][state]["markers"][role] = value
    path = output / "profile.json"
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    profile = MenuProfile(path)
    # Confirm each declared screen is distinguishable before accepting the calibration.
    for state, state_spec in spec["states"].items():
        observed = {"image": {"path": state_spec["source"]},
                    "window": {"title": "Minecraft" + state_spec.get("title_suffix", "")}}
        if profile.classify(observed) != state:
            raise ValueError(f"Calibration markers cannot uniquely recognize {state}")
    profile.check_world({"image": {"path": spec["states"]["world_list"]["source"]}})
    return {"profile_path": str(path.resolve()), "profile_sha256": profile.fingerprint,
            "world_name": profile.name, "source_frames": data["source_frames"], "calibration_only": True}
