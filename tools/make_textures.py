"""Draws the mod's 16x16 textures. Standard library only.

Run (there's no Python on the Windows host, so in Docker):
  MSYS_NO_PATHCONV=1 docker run --rm -v "G:/rngambling:/mod" qbot-engine-toolchain python3 /mod/tools/make_textures.py
"""
import os
import random
import struct
import zlib

TEXTURES = "/mod/src/main/resources/assets/rngambling/textures"


def write_png(path, pixels):
    """pixels: 16 rows of 16 (r, g, b) tuples. A pixel of None is see-through."""
    full = os.path.join(TEXTURES, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    rows = [[(0, 0, 0, 0) if px is None else tuple(px) + (255,) for px in row] for row in pixels]
    raw = b"".join(b"\x00" + bytes(c for px in row for c in px) for row in rows)

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    with open(full, "wb") as f:
        f.write(png)


def shade(colour, amount):
    return tuple(max(0, min(255, c + amount)) for c in colour)


def lucky_block():
    """Crimson felt with a gold rim and a gold die face (five pips)."""
    rng = random.Random(7)
    red, gold, gold_dark = (150, 20, 32), (232, 186, 64), (168, 122, 30)
    px = [[shade(red, rng.randint(-10, 10)) for _ in range(16)] for _ in range(16)]
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            px[y][x] = gold
        for (x, y) in ((i, 1), (1, i)):
            if 1 <= i <= 14:
                px[y][x] = shade(gold, 25)
        for (x, y) in ((i, 14), (14, i)):
            if 1 <= i <= 14:
                px[y][x] = gold_dark
    # Five 2x2 pips, each with a darker lower-right corner for depth.
    for (cx, cy) in ((4, 4), (10, 4), (7, 7), (4, 10), (10, 10)):
        px[cy][cx] = shade(gold, 30)
        px[cy][cx + 1] = gold
        px[cy + 1][cx] = gold
        px[cy + 1][cx + 1] = gold_dark
    return px


def casino_token():
    """A round chip: red rim with white notches, a white ring and a gold centre."""
    red, white, gold = (196, 28, 40), (240, 240, 236), (236, 190, 70)
    px = [[None] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r = (dx * dx + dy * dy) ** 0.5
            if r > 7.4:
                continue
            if r > 5.6:
                # Notches: white wedges at eight points around the rim.
                angle = (__import__("math").atan2(dy, dx) + 3.14159) / (2 * 3.14159) * 8
                colour = white if (angle % 1) < 0.35 else red
                px[y][x] = shade(colour, -35) if r > 6.8 else colour
            elif r > 4.6:
                px[y][x] = white
            else:
                px[y][x] = shade(gold, 25) if dx + dy < -2 else gold if dx + dy < 3 else shade(gold, -30)
    return px


write_png("block/lucky_block.png", lucky_block())
write_png("item/casino_token.png", casino_token())
print("textures written")
