from __future__ import annotations

import base64
import hashlib
import json
from io import BytesIO
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "assets/branding/copperbench-icon-source.png"
LOCK = ROOT / "compliance/branding-assets.lock.json"
RES = ROOT / "src/main/resources/net/mcreator/ui/res"
WINDOWS_INSTALLER = ROOT / "platform/windows/installer"
WINDOWS_MSIX = ROOT / "platform/windows/msix"
MAC = ROOT / "platform/mac"

CHARCOAL = (24, 29, 36, 255)
CHARCOAL_LIGHT = (43, 51, 61, 255)
LIME = (170, 239, 25, 255)
CYAN = (0, 213, 216, 255)
HIGHLIGHT = (238, 244, 247, 255)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_source() -> Image.Image:
    if not SOURCE.is_file():
        raise FileNotFoundError(f"Missing branding source: {SOURCE}")

    with Image.open(SOURCE) as source:
        source = source.convert("RGBA")

    # Ignore near-transparent generation noise before calculating the visual bounds.
    alpha_mask = source.getchannel("A").point(lambda alpha: 255 if alpha >= 8 else 0)
    bounds = alpha_mask.getbbox()
    if bounds is None:
        raise ValueError(f"Branding source is fully transparent: {SOURCE}")
    return source.crop(bounds)


def render_icon(size: int, padding_ratio: float = 0.035) -> Image.Image:
    source = load_source()
    available = max(1, round(size * (1 - (2 * padding_ratio))))
    source.thumbnail((available, available), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.alpha_composite(source, ((size - source.width) // 2, (size - source.height) // 2))
    return canvas


def render_on_canvas(
    canvas_size: tuple[int, int],
    icon_size: int,
    background: tuple[int, int, int, int],
    position: tuple[int, int] | None = None,
) -> Image.Image:
    canvas = Image.new("RGBA", canvas_size, background)
    icon = render_icon(icon_size)
    point = position or ((canvas_size[0] - icon_size) // 2, (canvas_size[1] - icon_size) // 2)
    canvas.alpha_composite(icon, point)
    return canvas


def png_data_uri(image: Image.Image) -> str:
    buffer = BytesIO()
    image.save(buffer, format="PNG", optimize=True)
    encoded = base64.b64encode(buffer.getvalue()).decode("ascii")
    return f"data:image/png;base64,{encoded}"


def write_svg_assets() -> None:
    icon_uri = png_data_uri(render_icon(512))
    logo_uri = png_data_uri(render_icon(256))
    icon = f"""<svg width="128pt" height="128pt" viewBox="0 0 160 160" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
  <image width="160" height="160" preserveAspectRatio="xMidYMid meet" href="{icon_uri}" xlink:href="{icon_uri}"/>
</svg>
"""
    logo = f"""<svg width="350pt" height="63pt" viewBox="0 0 700 126" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
  <defs>
    <linearGradient id="accent" x1="0" x2="1">
      <stop offset="0" stop-color="#aaef19"/>
      <stop offset="1" stop-color="#00d5d8"/>
    </linearGradient>
  </defs>
  <image x="3" y="3" width="120" height="120" preserveAspectRatio="xMidYMid meet" href="{logo_uri}" xlink:href="{logo_uri}"/>
  <text x="138" y="79" fill="#eef4f7" font-family="Segoe UI,Arial,sans-serif" font-size="57" font-weight="700">COPPERBENCH</text>
  <rect x="141" y="96" width="500" height="7" rx="3.5" fill="url(#accent)"/>
</svg>
"""
    attribution = """<svg width="90pt" height="24pt" viewBox="0 0 360 96" xmlns="http://www.w3.org/2000/svg">
  <text x="0" y="68" fill="#eef4f7" font-family="Segoe UI,Arial,sans-serif" font-size="54" font-weight="600">GPL-3.0</text>
</svg>
"""
    (RES / "icon.svg").write_text(icon, encoding="utf-8", newline="\n")
    (RES / "icon_eap.svg").write_text(icon, encoding="utf-8", newline="\n")
    (RES / "logo.svg").write_text(logo, encoding="utf-8", newline="\n")
    (RES / "pylo.svg").write_text(attribution, encoding="utf-8", newline="\n")


def write_raster_assets() -> None:
    icon = render_icon(1024)
    icon_256 = icon.resize((256, 256), Image.Resampling.LANCZOS)
    for name in ("icon.png", "icon_eap.png", "fallback.png"):
        icon_256.save(RES / name, optimize=True)
    for name in ("icon.ico", "icon_eap.ico"):
        icon.save(
            RES / name,
            sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)],
        )

    splash = Image.new("RGBA", (1280, 760), CHARCOAL)
    draw = ImageDraw.Draw(splash)
    for x in range(-760, 1280, 80):
        draw.line((x, 760, x + 760, 0), fill=CHARCOAL_LIGHT, width=2)
    draw.rectangle((0, 0, 20, 760), fill=LIME)
    draw.rectangle((20, 0, 26, 760), fill=CYAN)
    splash.alpha_composite(render_icon(520), (750, 20))
    splash.convert("RGB").save(RES / "splash.png", optimize=True)

    msix_sizes = {
        "Square150x150Logo.png": 150,
        "Square44x44Logo.png": 44,
        "StoreLogo.png": 50,
    }
    for name, size in msix_sizes.items():
        render_icon(size, padding_ratio=0.06).save(WINDOWS_MSIX / name, optimize=True)

    for name in ("installer.ico", "uninstaller.ico"):
        icon.save(WINDOWS_INSTALLER / name, sizes=[(16, 16), (32, 32), (48, 48), (64, 64)])
    render_on_canvas((150, 57), 54, CHARCOAL).convert("RGB").save(WINDOWS_INSTALLER / "installer.bmp")
    side = Image.new("RGBA", (164, 314), CHARCOAL)
    side_draw = ImageDraw.Draw(side)
    side_draw.rectangle((0, 0, 7, 314), fill=LIME)
    side_draw.rectangle((7, 0, 11, 314), fill=CYAN)
    side.alpha_composite(render_icon(142), (17, 16))
    side.convert("RGB").save(WINDOWS_INSTALLER / "installer_side.bmp")

    for name in ("mcreator.icns", "mcreator_eap.icns", "mcreatorapp.icns", "mcreatorapp_eap.icns", "volume.icns"):
        icon.save(MAC / name, format="ICNS")
    disk = Image.new("RGBA", (1440, 915), CHARCOAL)
    disk_draw = ImageDraw.Draw(disk)
    disk_draw.rectangle((0, 0, 18, 915), fill=LIME)
    disk_draw.rectangle((18, 0, 26, 915), fill=CYAN)
    disk.alpha_composite(render_icon(610), (415, 70))
    disk.convert("RGB").save(MAC / "diskimage.png", optimize=True)


def update_asset_lock() -> None:
    lock = json.loads(LOCK.read_text(encoding="utf-8"))
    lock["sourceAsset"] = SOURCE.relative_to(ROOT).as_posix()
    lock["sourceSha256"] = sha256(SOURCE)
    lock["derivation"] = "Deterministic crop, resize, and composition with Pillow; no generative edits"
    for asset in lock["assets"]:
        path = ROOT / asset["path"]
        if not path.is_file():
            raise FileNotFoundError(f"Generated asset missing: {path}")
        asset["replacementSha256"] = sha256(path)
    LOCK.write_text(json.dumps(lock, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__":
    write_svg_assets()
    write_raster_assets()
    update_asset_lock()
    print(f"Copperbench brand assets generated from {SOURCE.relative_to(ROOT).as_posix()}")
    print(f"Source SHA-256: {sha256(SOURCE)}")
