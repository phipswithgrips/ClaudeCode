"""Erzeugt alle App-Symbole aus einer SVG-Vorlage.

    python3 icons/make_icons.py a      # Variante a.svg

Ausgabe: Windows-ICO, Fenster-PNG, Android-Mipmaps und adaptives Symbol.
Benötigt: pip install cairosvg pillow
"""
import io
import re
import sys
from pathlib import Path

import cairosvg
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
variant = sys.argv[1] if len(sys.argv) > 1 else "a"
svg = (ROOT / "icons" / f"{variant}.svg").read_text()


def render(src: str, size: int) -> Image.Image:
    return Image.open(io.BytesIO(cairosvg.svg2png(bytestring=src.encode(), output_width=size, output_height=size))).convert("RGBA")


# Windows und Fenster
big = render(svg, 256)
big.save(ROOT / "icons" / "cookfolio.ico", sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
res = ROOT / "composeApp" / "src" / "desktopMain" / "resources"
res.mkdir(parents=True, exist_ok=True)
big.save(res / "icon.png")

# Android: klassische Mipmaps (abgerundetes Quadrat) und adaptives Symbol
android = ROOT / "composeApp" / "src" / "androidMain" / "res"
bg = re.search(r'<rect width="256" height="256" rx="56" fill="(#[0-9A-Fa-f]{6})"/>', svg).group(1)
foreground_svg = re.sub(r'<rect width="256" height="256" rx="56" fill="#[0-9A-Fa-f]{6}"/>', "", svg)
# Vordergrund in die 66-%-Schutzzone des adaptiven Symbols verkleinern
foreground_svg = foreground_svg.replace('viewBox="0 0 256 256">', 'viewBox="-40 -40 336 336">')
for density, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
    d = android / f"mipmap-{density}"
    d.mkdir(parents=True, exist_ok=True)
    render(svg, px).save(d / "ic_launcher.png")
    render(svg.replace('rx="56"', 'rx="128"'), px).save(d / "ic_launcher_round.png")
    render(foreground_svg, px * 108 // 48).save(d / "ic_launcher_foreground.png")
anydpi = android / "mipmap-anydpi-v26"
anydpi.mkdir(parents=True, exist_ok=True)
adaptive = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
    <monochrome android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
"""
(anydpi / "ic_launcher.xml").write_text(adaptive)
(anydpi / "ic_launcher_round.xml").write_text(adaptive)
values = android / "values"
(values / "ic_launcher_background.xml").write_text(
    f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">{bg}</color>\n</resources>\n'
)
print(f"Symbole aus {variant}.svg erzeugt (Hintergrund {bg})")
