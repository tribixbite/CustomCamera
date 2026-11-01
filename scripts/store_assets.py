#!/usr/bin/env python3
"""Emit SVG sources for the Play Store icon and feature graphic.

The lens mark uses the same geometry as
app/src/main/res/drawable/ic_launcher_foreground.xml (108-unit viewport,
centre 54), so the store icon matches the installed launcher icon.

Usage: store_assets.py <output-dir>   (writes icon.svg and feature.svg)
"""
import math
import sys
from pathlib import Path

ACCENT = "#FFD54F"
BODY = "#16161A"
APERTURE = "#2C2C34"
BLADE = "#3A3A44"
BACKGROUND = "#111114"


def circle(cx: float, cy: float, r: float) -> str:
    return f"M{cx - r:.2f},{cy:.2f} a{r},{r} 0 1,0 {2 * r},0 a{r},{r} 0 1,0 {-2 * r},0 Z"


def lens_mark(cx: float = 54, cy: float = 54) -> str:
    """SVG group for the lens mark in the 108-unit launcher coordinate space."""
    hexagon = [(cx + 10 * math.cos(math.radians(a + 30)), cy + 10 * math.sin(math.radians(a + 30))) for a in range(0, 360, 60)]
    hex_path = "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in hexagon) + " Z"
    blades = []
    for i in range(6):
        a = math.radians(i * 60 + 30)
        a2 = math.radians(i * 60 + 90 - 25)
        blades.append(
            f"M{cx + 10 * math.cos(a):.2f},{cy + 10 * math.sin(a):.2f} "
            f"L{cx + 19 * math.cos(a2):.2f},{cy + 19 * math.sin(a2):.2f}"
        )
    return (
        f'<path fill="{ACCENT}" d="{circle(cx, cy, 26)}"/>'
        f'<path fill="{BODY}" d="{circle(cx, cy, 20)}"/>'
        f'<path fill="{APERTURE}" d="{hex_path}"/>'
        f'<path fill="none" stroke="{BLADE}" stroke-width="1.6" stroke-linecap="round" d="{" ".join(blades)}"/>'
        f'<path fill="#FFFFFF" fill-opacity="0.8" d="{circle(48.5, 47.5, 3)}"/>'
    )


def icon_svg() -> str:
    # Play applies its own rounded mask to a full-bleed 512 px square, so the
    # launcher's 66-unit safe zone is not needed: crop to 80 units (lens ≈ 65%).
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="14 14 80 80">'
        f'<rect x="14" y="14" width="80" height="80" fill="{BACKGROUND}"/>'
        f"{lens_mark()}"
        "</svg>"
    )


def feature_svg() -> str:
    # 1024x500; keep text clear of the centre-bottom where Play may overlay a play button.
    scale = 300 / 52  # lens diameter 52 units -> 300 px
    tx = 70 + 150 - 54 * scale
    ty = 250 - 54 * scale
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#0B0B0D"/>
      <stop offset="1" stop-color="#1D1D23"/>
    </linearGradient>
    <radialGradient id="glow" cx="0.22" cy="0.5" r="0.45">
      <stop offset="0" stop-color="{ACCENT}" stop-opacity="0.22"/>
      <stop offset="1" stop-color="{ACCENT}" stop-opacity="0"/>
    </radialGradient>
  </defs>
  <rect width="1024" height="500" fill="url(#bg)"/>
  <rect width="1024" height="500" fill="url(#glow)"/>
  <g transform="translate({tx:.2f},{ty:.2f}) scale({scale:.4f})">{lens_mark()}</g>
  <text x="430" y="215" font-family="Roboto Flex" font-weight="800" font-size="76" fill="#F2F2F4">CustomCamera</text>
  <text x="432" y="275" font-family="Roboto Flex" font-weight="500" font-size="30" fill="{ACCENT}">Pro controls · Night &amp; Portrait · QR</text>
  <text x="432" y="320" font-family="Roboto Flex" font-weight="400" font-size="26" fill="#B4B4BC">Private by design. No ads. No network.</text>
</svg>
"""


def main() -> None:
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    (out / "icon.svg").write_text(icon_svg())
    (out / "feature.svg").write_text(feature_svg())


if __name__ == "__main__":
    main()
