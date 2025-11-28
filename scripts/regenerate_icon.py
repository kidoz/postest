"""
Regenerate the application icon (transparent PNG with rounded border) without external tools.
Usage: python3 scripts/regenerate_icon.py
Writes: src/main/resources/icon.png (512x512)
"""

import struct
import zlib
from pathlib import Path

W = H = 512

# Color constants
BG_START = (30, 41, 59)   # #1e293b
BG_END = (15, 23, 42)     # #0f172a
STROKE = (51, 65, 85)     # #334155
P_MAIN_A = (34, 211, 238) # #22d3ee
P_MAIN_B = (6, 182, 212)  # #06b6d4
ARROW_A = (249, 115, 22)  # #f97316
ARROW_B = (251, 146, 60)  # #fb923c
RESP_COLOR = (167, 139, 250)  # #a78bfa


def set_px(buf: bytearray, x: int, y: int, rgba: tuple[int, int, int, int]) -> None:
    if 0 <= x < W and 0 <= y < H:
        idx = (y * W + x) * 4
        buf[idx:idx+4] = bytes(rgba)


def in_round_rect(x: int, y: int, w: int, h: int, r: int) -> bool:
    if x < 0 or y < 0 or x >= w or y >= h:
        return False
    dx = dy = 0
    if x < r:
        dx = r - x
    elif x >= w - r:
        dx = x - (w - r - 1)
    if y < r:
        dy = r - y
    elif y >= h - r:
        dy = y - (h - r - 1)
    return dx * dx + dy * dy <= r * r


def fill_round_rect_grad(buf: bytearray, x0: int, y0: int, w: int, h: int, radius: int, start: tuple[int, int, int], end: tuple[int, int, int]) -> None:
    for y in range(h):
        for x in range(w):
            if not in_round_rect(x, y, w, h, radius):
                continue
            t = (x + y) / (w + h)
            r = int(start[0] * (1 - t) + end[0] * t)
            g = int(start[1] * (1 - t) + end[1] * t)
            b = int(start[2] * (1 - t) + end[2] * t)
            set_px(buf, x0 + x, y0 + y, (r, g, b, 255))


def stroke_round_rect(buf: bytearray, x0: int, y0: int, w: int, h: int, radius: int, stroke_w: int, color: tuple[int, int, int]) -> None:
    r_c, g_c, b_c = color
    inner_r = max(0, radius - stroke_w)
    for y in range(h):
        for x in range(w):
            if not in_round_rect(x, y, w, h, radius):
                continue
            if stroke_w > 0 and in_round_rect(x, y, w, h, inner_r):
                continue
            set_px(buf, x0 + x, y0 + y, (r_c, g_c, b_c, 255))


def grad(a: tuple[int, int, int], b: tuple[int, int, int], t: float) -> tuple[int, int, int]:
    return (
        int(a[0] * (1 - t) + b[0] * t),
        int(a[1] * (1 - t) + b[1] * t),
        int(a[2] * (1 - t) + b[2] * t),
    )


def write_png(path: Path, w: int, h: int, data: bytearray) -> None:
    def chunk(name: bytes, payload: bytes) -> bytes:
        crc = zlib.crc32(name + payload) & 0xFFFFFFFF
        return struct.pack(">I", len(payload)) + name + payload + struct.pack(">I", crc)

    sig = b"\x89PNG\r\n\x1a\n"
    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    rows = b"".join(b"\x00" + data[y * w * 4:(y + 1) * w * 4] for y in range(h))
    idat = zlib.compress(rows, 9)
    with path.open("wb") as f:
        f.write(sig)
        f.write(chunk(b"IHDR", ihdr))
        f.write(chunk(b"IDAT", idat))
        f.write(chunk(b"IEND", b""))


def main() -> None:
    buf = bytearray(W * H * 4)  # transparent background

    # Background with stroke
    fill_round_rect_grad(buf, 32, 32, 448, 448, 56, BG_START, BG_END)
    stroke_round_rect(buf, 32, 32, 448, 448, 56, 3, STROKE)

    # Stylized P
    bx, by = 112, 112
    # vertical bar
    for y in range(288):
        r, g, b = grad(P_MAIN_A, P_MAIN_B, y / 288)
        for x in range(48):
            set_px(buf, bx + x, by + y, (r, g, b, 255))
    # top bar
    for y in range(48):
        r, g, b = grad(P_MAIN_A, P_MAIN_B, y / 48)
        for x in range(160):
            set_px(buf, bx + 32 + x, by + y, (r, g, b, 255))
    # right connector
    for y in range(112):
        r, g, b = grad(P_MAIN_A, P_MAIN_B, y / 112)
        for x in range(48):
            set_px(buf, bx + 144 + x, by + 32 + y, (r, g, b, 255))
    # middle bar
    for y in range(48):
        r, g, b = grad(P_MAIN_A, P_MAIN_B, y / 48)
        for x in range(128):
            set_px(buf, bx + 32 + x, by + 112 + y, (r, g, b, 255))

    # Request arrow (right)
    ax, ay = 340, 180
    for x in range(80):
        r, g, b = grad(ARROW_A, ARROW_B, x / 79 if 79 else 0)
        for y in range(16):
            set_px(buf, ax + x, ay + 16 + y, (r, g, b, 255))
    for y in range(48):
        maxx = 80 + y * (32 / 24) if y <= 24 else 112 - (y - 24) * (32 / 24)
        for x in range(80, int(maxx) + 1):
            r, g, b = grad(ARROW_A, ARROW_B, min(max((x - 80) / 32, 0), 1))
            set_px(buf, ax + x, ay + y, (r, g, b, 255))

    # Response arrow (left)
    bx2, by2 = 340, 300
    for y in range(48):
        minx = 32 - y * (32 / 24) if y <= 24 else (y - 24) * (32 / 24)
        for x in range(int(minx), 33):
            set_px(buf, bx2 + x, by2 + y, (*RESP_COLOR, 255))
    for x in range(80):
        for y in range(16):
            set_px(buf, bx2 + 32 + x, by2 + 16 + y, (*RESP_COLOR, 255))

    out_path = Path("src/main/resources/icon.png")
    out_path.parent.mkdir(parents=True, exist_ok=True)
    write_png(out_path, W, H, buf)
    print(f"Regenerated {out_path} (transparent padding, visible stroke corners)")


if __name__ == "__main__":
    main()
