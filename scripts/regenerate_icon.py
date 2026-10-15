"""Export Postest branding from the canonical SVG, without network access.

Usage: python3 scripts/regenerate_icon.py
Requires: rsvg-convert (librsvg) on PATH.
Outputs: desktop PNG/ICO/ICNS, GitHub light/dark logos and social preview.
"""

import shutil
import struct
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"
IMAGES = ROOT / "docs/images"
SVG_NS = "http://www.w3.org/2000/svg"


def render(svg: Path, width: int, height: int) -> bytes:
    return subprocess.run(
        ["rsvg-convert", "--width", str(width), "--height", str(height), str(svg)],
        check=True,
        capture_output=True,
    ).stdout


def write_ico(images: dict[int, bytes]) -> None:
    sizes = (16, 24, 32, 48, 64, 128, 256)
    offset = 6 + 16 * len(sizes)
    directory = bytearray(struct.pack("<HHH", 0, 1, len(sizes)))
    payload = bytearray()
    for size in sizes:
        png = images[size]
        directory.extend(struct.pack(
            "<BBBBHHII", size % 256, size % 256, 0, 0, 1, 32, len(png), offset
        ))
        payload.extend(png)
        offset += len(png)
    (RESOURCES / "icon.ico").write_bytes(directory + payload)


def write_icns(images: dict[int, bytes]) -> None:
    # Standard sizes plus Retina representations, stored as lossless RGBA PNG.
    types = {
        b"icp4": 16, b"icp5": 32, b"icp6": 64, b"ic07": 128,
        b"ic08": 256, b"ic09": 512, b"ic10": 1024,
        b"ic11": 32, b"ic12": 64, b"ic13": 256, b"ic14": 512,
    }
    payload = b"".join(
        kind + struct.pack(">I", len(images[size]) + 8) + images[size]
        for kind, size in types.items()
    )
    (RESOURCES / "icon.icns").write_bytes(
        b"icns" + struct.pack(">I", len(payload) + 8) + payload
    )


def icon_group() -> str:
    # Embed the source artwork, so GitHub SVGs have no external dependencies.
    source = ET.parse(RESOURCES / "icon.svg").getroot()
    ET.register_namespace("", SVG_NS)
    return "\n".join(
        ET.tostring(element, encoding="unicode")
        for element in source
        if element.tag not in {f"{{{SVG_NS}}}title", f"{{{SVG_NS}}}desc"}
    )


def write_svg(name: str, width: int, height: int, body: str, title: str) -> Path:
    path = IMAGES / name
    path.write_text(
        f'<svg xmlns="{SVG_NS}" width="{width}" height="{height}" '
        f'viewBox="0 0 {width} {height}">\n'
        f'  <title>{title}</title>\n{body}\n</svg>\n',
        encoding="utf-8",
    )
    return path


def write_github_assets() -> None:
    IMAGES.mkdir(parents=True, exist_ok=True)
    artwork = icon_group()
    for theme, ink in (("light", "#1F2430"), ("dark", "#E6E8EF")):
        logo = write_svg(
            f"logo-{theme}.svg", 600, 200,
            f'<g transform="translate(0 0) scale(.390625)" fill="none">{artwork}</g>\n'
            f'<text x="228" y="133" fill="{ink}" '
            'font-family="Avenir Next, Avenir, Arial, sans-serif" font-size="100" '
            'font-weight="600" letter-spacing="-4">postest</text>',
            "Postest",
        )
        logo.with_suffix(".png").write_bytes(render(logo, 1200, 400))

    social = write_svg(
        "github-social-preview.svg", 1280, 640,
        '<rect width="1280" height="640" fill="#0F1117"/>\n'
        '<rect x="64" y="64" width="1152" height="512" rx="32" '
        'fill="#161925" stroke="#29334C" stroke-width="2"/>\n'
        f'<g transform="translate(256 164) scale(.61)" fill="none">{artwork}</g>\n'
        '<text x="618" y="309" fill="#E6E8EF" '
        'font-family="Avenir Next, Avenir, Arial, sans-serif" font-size="112" '
        'font-weight="600" letter-spacing="-4">postest</text>\n'
        '<text x="624" y="375" fill="#A9AFBD" '
        'font-family="Avenir Next, Avenir, Arial, sans-serif" font-size="30">'
        'A modern REST API client</text>\n'
        '<path d="M624 416H688" stroke="#3CD9B4" stroke-width="6" '
        'stroke-linecap="round"/>',
        "Postest — a modern REST API client",
    )
    social.with_suffix(".png").write_bytes(render(social, 1280, 640))


def main() -> None:
    if not shutil.which("rsvg-convert"):
        raise SystemExit("rsvg-convert is required. Install librsvg, then rerun this script.")
    source = RESOURCES / "icon.svg"
    images = {size: render(source, size, size)
              for size in (16, 24, 32, 48, 64, 128, 256, 512, 1024)}
    (RESOURCES / "icon.png").write_bytes(images[1024])
    write_ico(images)
    write_icns(images)
    write_github_assets()
    print("Regenerated application icons and GitHub branding from icon.svg")


if __name__ == "__main__":
    main()
