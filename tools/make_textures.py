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
    """pixels: rows of (r, g, b) tuples (usually 16 x 16). A pixel of None is see-through."""
    full = os.path.join(TEXTURES, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    rows = [[(0, 0, 0, 0) if px is None else tuple(px) + (255,) for px in row] for row in pixels]
    raw = b"".join(b"\x00" + bytes(c for px in row for c in px) for row in rows)

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", len(pixels[0]), len(pixels), 8, 6, 0, 0, 0))
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


def mimic_wand():
    """A diagonal wand: dark wood handle, gold band, and a glowing purple-pink tip."""
    wood, wood_dark = (92, 58, 34), (64, 40, 22)
    gold = (232, 186, 64)
    tip, tip_bright = (196, 92, 220), (250, 190, 255)
    px = [[None] * 16 for _ in range(16)]
    # The shaft runs from bottom-left (1, 14) to top-right (11, 4), two pixels thick.
    for i in range(11):
        x, y = 1 + i, 14 - i
        px[y][x] = wood if i < 7 else gold
        if y + 1 < 16:
            px[y + 1][x] = wood_dark if i < 7 else shade(gold, -50)
    # The tip: a small diamond of light with sparkles.
    for (x, y) in ((12, 3), (13, 2), (12, 2), (13, 3), (11, 3), (12, 4), (14, 2), (13, 1)):
        px[y][x] = tip
    px[2][13] = tip_bright
    px[3][12] = tip_bright
    for (x, y) in ((15, 0), (10, 1), (15, 5)):
        px[y][x] = tip_bright
    return px


def luck_glasses_item():
    """Glasses seen from the front: gold frames, emerald lenses."""
    gold, gold_dark = (232, 186, 64), (168, 122, 30)
    lens, lens_bright = (40, 170, 90), (150, 240, 170)
    px = [[None] * 16 for _ in range(16)]
    for (x0, x1) in ((1, 6), (9, 14)):
        for x in range(x0, x1 + 1):
            px[5][x] = gold
            px[10][x] = gold_dark
        for y in range(5, 11):
            px[y][x0] = gold
            px[y][x1] = gold_dark
        for y in range(6, 10):
            for x in range(x0 + 1, x1):
                px[y][x] = lens
        px[6][x0 + 1] = lens_bright
        px[7][x0 + 1] = lens_bright
        px[6][x0 + 2] = lens_bright
    for x in (7, 8):
        px[6][x] = gold
    px[5][0] = gold_dark
    px[5][15] = gold_dark
    return px


def luck_glasses_worn():
    """The 64 x 32 humanoid equipment texture: glasses across the head's front, arms along its sides."""
    gold, gold_dark = (232, 186, 64), (168, 122, 30)
    lens, lens_bright = (40, 170, 90), (150, 240, 170)
    px = [[None] * 64 for _ in range(32)]
    # Head front face: x 8..15, y 8..15. Eyes are on row 12 of the skin (y = 12).
    fx, fy = 8, 8
    for x in range(8):
        px[fy + 3][fx + x] = gold
    for (x0, x1) in ((0, 2), (5, 7)):
        for x in range(x0, x1 + 1):
            px[fy + 4][fx + x] = lens
            px[fy + 5][fx + x] = gold_dark
        px[fy + 4][fx + x0] = lens_bright
    px[fy + 4][fx + 3] = gold
    px[fy + 4][fx + 4] = gold
    # The arms: along the right side (x 0..7) and left side (x 16..23) of the head, row y = 11.
    for x in range(0, 8):
        px[fy + 3][x] = gold_dark
    for x in range(16, 24):
        px[fy + 3][x] = gold_dark
    return px


write_png("block/lucky_block.png", lucky_block())
write_png("item/luck_glasses.png", luck_glasses_item())
write_png("entity/equipment/humanoid/luck_glasses.png", luck_glasses_worn())
write_png("item/mimic_wand.png", mimic_wand())
write_png("item/casino_token.png", casino_token())
print("textures written")
