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


def lucky_block(red=(150, 20, 32), gold=(232, 186, 64), gold_dark=(168, 122, 30)):
    """Crimson felt with a gold rim and a gold die face (five pips). The colours can be changed
    for the bought variants' item icons."""
    rng = random.Random(7)
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


def write_png_alpha(path, colour, alpha):
    """A 16 x 16 texture of one colour at one opacity (0-255)."""
    full = os.path.join(TEXTURES, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    raw = b"".join(b"\x00" + bytes(tuple(colour) + (alpha,)) * 16 for _ in range(16))

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    with open(full, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


RED, RED_DARK, GOLD, GOLD_DARK = (170, 24, 36), (110, 14, 22), (232, 186, 64), (168, 122, 30)


def cabinet(trim_top=True):
    """Red cabinet with a gold rim: the base of the slot machine's faces."""
    rng = random.Random(3)
    px = [[shade(RED, rng.randint(-6, 6)) for _ in range(16)] for _ in range(16)]
    for i in range(16):
        px[15][i] = RED_DARK
        px[i][0] = GOLD_DARK
        px[i][15] = GOLD_DARK
        if trim_top:
            px[0][i] = GOLD
    return px


def slot_machine_front():
    """Three reels in a window (cherry, 7, bar), a payout tray below."""
    px = cabinet()
    white, black = (236, 232, 220), (30, 24, 24)
    for y in range(3, 10):
        for x in range(2, 14):
            px[y][x] = GOLD if y in (3, 9) or x in (2, 13) else white
    for x in (6, 9):
        for y in range(4, 9):
            px[y][x] = (180, 176, 168)
    # Reel 1: a cherry. Reel 2: a red 7. Reel 3: a bar.
    px[6][4] = px[7][4] = (200, 20, 30); px[6][3] = (200, 20, 30); px[5][4] = (60, 140, 40)
    for (x, y) in ((7, 5), (8, 5), (8, 6), (7, 7), (7, 8)):
        px[y][x] = (210, 20, 30)
    for x in (10, 11, 12):
        px[6][x] = black
        px[5][x] = px[7][x] = (90, 90, 90) if x == 11 else px[5][x]
    # The payout tray.
    for x in range(4, 12):
        px[12][x] = black
        px[13][x] = GOLD_DARK
    return px


def slot_machine_side():
    """Plain cabinet side with the lever's knob."""
    px = cabinet()
    for y in range(4, 11):
        px[y][11] = (150, 150, 150)
    for (x, y) in ((10, 2), (11, 2), (12, 2), (10, 3), (11, 3), (12, 3)):
        px[y][x] = (220, 30, 40)
    return px


def slot_machine_top():
    """A gold-rimmed top with a light in the middle."""
    px = cabinet(trim_top=True)
    for x in range(16):
        px[15][x] = GOLD
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
        px[y][x] = (255, 230, 120)
    return px


def four_leaf_clover():
    green, light, stem = (50, 150, 50), (110, 210, 90), (40, 110, 40)
    px = [[None] * 16 for _ in range(16)]
    for (cx, cy) in ((5, 4), (10, 4), (5, 9), (10, 9)):
        for dy in range(-2, 3):
            for dx in range(-2, 3):
                if dx * dx + dy * dy <= 5:
                    px[cy + dy][cx + dx] = green
        px[cy - 1][cx - 1] = light
    for (x, y) in ((8, 7), (8, 8), (9, 12), (10, 13), (11, 14)):
        px[y][x] = stem
    return px


def golden_horseshoe():
    px = [[None] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 6.5
            r = (dx * dx + dy * dy) ** 0.5
            if 3.6 <= r <= 6.4 and (y <= 7 or abs(dx) >= 3.6):
                px[y][x] = GOLD if dx + dy < 2 else GOLD_DARK
    for y in range(7, 14):
        for x in (2, 3, 12, 13):
            px[y][x] = GOLD if x in (2, 12) else GOLD_DARK
    for (x, y) in ((3, 6), (12, 6), (5, 2), (10, 2), (2, 10), (13, 10)):
        px[y][x] = (120, 90, 30)
    return px


def lucky_seven():
    """A red 7 on a gold coin."""
    px = [[None] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r = (dx * dx + dy * dy) ** 0.5
            if r <= 7.4:
                px[y][x] = GOLD_DARK if r > 6.4 else GOLD
    seven = (200, 20, 30)
    for x in range(4, 12):
        px[3][x] = seven
        px[4][x] = seven
    for i, y in enumerate(range(5, 13)):
        x = 10 - i // 2
        px[y][x] = seven
        px[y][x + 1] = seven
    return px


def dealer_outfit():
    """The Casino Dealer's profession layer (64 x 64 villager layout): black vest over a white
    shirt with a red bow tie, black sleeves, and a green dealer's visor."""
    black, white, tie, visor = (28, 26, 30), (236, 236, 230), (200, 20, 30), (40, 140, 70)
    px = [[None] * 64 for _ in range(64)]

    def fill(x0, y0, x1, y1, colour):
        for y in range(y0, y1):
            for x in range(x0, x1):
                px[y][x] = colour

    def front(x0, y0, rows):
        """A body front 8 wide: vest at the edges, shirt down the middle, bow tie at the top."""
        fill(x0, y0, x0 + 8, y0 + rows, black)
        fill(x0 + 2, y0, x0 + 6, y0 + rows, white)
        fill(x0 + 2, y0, x0 + 6, y0 + 1, tie)
        px[y0 + 1][x0 + 3] = tie
        px[y0 + 1][x0 + 4] = tie
        for y in range(y0 + 4, y0 + rows - 1, 3):
            px[y][x0 + 4] = GOLD

    # Body (8 x 12 x 6 at 16, 20) and the robe over it (8 x 18 x 6 at 0, 38).
    fill(16, 26, 22, 38, black); fill(30, 26, 44, 38, black); front(22, 26, 12)
    fill(0, 44, 6, 62, black); fill(14, 44, 28, 62, black); front(6, 44, 18)
    # Crossed arms (4 x 8 x 4 at 44, 22) and the bar joining them (8 x 4 x 4 at 40, 38): black sleeves.
    fill(44, 22, 60, 34, black); fill(40, 38, 64, 46, black)
    # Visor: a green band around the head's top edge (head 8 x 10 x 8 at 0, 0).
    fill(0, 8, 32, 10, visor)
    fill(8, 0, 16, 2, visor)
    return px


def slot_symbols():
    """The slot machine's six reel symbols side by side (96 x 16), in SlotMachine.Symbol order:
    cherry, lemon, bell, bar, seven, lucky block."""
    px = [[None] * 96 for _ in range(16)]

    def put(i, x, y, colour):
        if 0 <= x < 16 and 0 <= y < 16:
            px[y][i * 16 + x] = colour

    def disc(i, cx, cy, r, colour, light=None):
        for y in range(16):
            for x in range(16):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                    put(i, x, y, light if light and (x - cx) + (y - cy) < -r * 0.7 else colour)

    # Cherry: two red cherries on green stems.
    for (cx, cy) in ((5, 11), (10, 11)):
        disc(0, cx, cy, 3, (200, 20, 30), (255, 120, 120))
    for (x, y) in ((5, 7), (6, 6), (7, 5), (8, 4), (9, 3), (10, 7), (10, 6), (10, 5), (10, 4), (11, 3), (12, 3)):
        put(0, x, y, (60, 140, 40))
    # Lemon: a yellow oval with a leaf.
    for y in range(16):
        for x in range(16):
            if ((x - 7.5) / 6.5) ** 2 + ((y - 8.5) / 4.6) ** 2 <= 1:
                put(1, x, y, (255, 235, 140) if x + y < 12 else (245, 210, 40))
    for (x, y) in ((13, 3), (12, 4), (13, 4), (14, 3)):
        put(1, x, y, (60, 150, 50))
    # Bell: gold dome, wide lip, clapper.
    for y in range(2, 13):
        half = 2 + (y - 2) * 4 // 10
        for x in range(8 - half, 8 + half):
            put(2, x, y, GOLD if x < 8 else GOLD_DARK)
    for x in range(2, 14):
        put(2, x, 12, GOLD_DARK)
    for (x, y) in ((7, 13), (8, 13), (7, 14), (8, 14)):
        put(2, x, y, (120, 90, 30))
    put(2, 6, 4, (255, 240, 180)); put(2, 6, 5, (255, 240, 180))
    # Bar: a black bar with "BAR" suggested by white strokes.
    for y in range(5, 12):
        for x in range(1, 15):
            put(3, x, y, (24, 24, 28))
    for x in range(2, 14):
        put(3, x, 4, (200, 200, 200)); put(3, x, 12, (200, 200, 200))
    for (x, y) in ((3, 6), (3, 7), (3, 8), (3, 9), (3, 10), (4, 6), (4, 8), (4, 10), (5, 7), (5, 9),
                   (7, 7), (7, 8), (7, 9), (7, 10), (8, 6), (9, 7), (9, 8), (9, 9), (9, 10), (8, 8),
                   (11, 6), (11, 7), (11, 8), (11, 9), (11, 10), (12, 6), (13, 7), (12, 8), (13, 9), (13, 10)):
        put(3, x, y, (250, 250, 250))
    # Seven: a bold red 7 with a dark edge.
    for x in range(3, 13):
        put(4, x, 2, (230, 30, 40)); put(4, x, 3, (230, 30, 40)); put(4, x, 4, (150, 10, 20))
    for i, y in enumerate(range(5, 15)):
        x = 11 - i * 2 // 3
        put(4, x, y, (230, 30, 40)); put(4, x - 1, y, (230, 30, 40)); put(4, x - 2, y, (150, 10, 20))
    # Lucky block: the lucky block's own face, small.
    face = lucky_block()
    for y in range(16):
        for x in range(16):
            put(5, x, y, face[y][x])
    return px


FELT, FELT_DARK, WOOD, WOOD_DARK = (24, 104, 52), (14, 72, 34), (100, 60, 30), (70, 40, 20)


def felt():
    rng = random.Random(11)
    px = [[shade(FELT, rng.randint(-5, 5)) for _ in range(16)] for _ in range(16)]
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            px[y][x] = WOOD
    return px


def roulette_top():
    """Green felt with the wheel: red and black pockets around a gold hub."""
    import math
    px = felt()
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r = (dx * dx + dy * dy) ** 0.5
            if r <= 6.6:
                if r > 5.4:
                    px[y][x] = WOOD_DARK
                elif r > 3.2:
                    pocket = int((math.atan2(dy, dx) + math.pi) / (2 * math.pi) * 12)
                    px[y][x] = (190, 24, 40) if pocket % 2 else (26, 26, 30)
                    if pocket == 0:
                        px[y][x] = (30, 150, 70)
                elif r > 1.5:
                    px[y][x] = WOOD
                else:
                    px[y][x] = GOLD
    return px


def blackjack_top():
    """Green felt with the dealer's arc and two cards."""
    px = felt()
    for x in range(2, 14):
        y = 3 + int(((x - 7.5) / 6) ** 2 * 3)
        px[y][x] = (230, 220, 160)
    for (cx, cy) in ((5, 8), (9, 8)):
        for y in range(cy, cy + 5):
            for x in range(cx, cx + 3):
                px[y][x] = (240, 238, 228)
        px[cy + 1][cx + 1] = (190, 24, 40) if cx == 5 else (26, 26, 30)
    return px


def table_side():
    """Dark wood with a gold trim under the felt edge."""
    rng = random.Random(5)
    px = [[shade(WOOD, rng.randint(-8, 8)) for _ in range(16)] for _ in range(16)]
    for x in range(16):
        px[0][x] = FELT
        px[1][x] = FELT_DARK
        px[2][x] = GOLD
        px[15][x] = WOOD_DARK
        for y in range(4, 15, 4):
            px[y][x] = shade(WOOD_DARK, 5)
    return px


def card_suits():
    """Four 8 x 8 suit icons side by side (32 x 8): hearts, diamonds, clubs, spades."""
    red, black = (192, 24, 42), (26, 26, 30)
    rows = {
        "heart":   [".##.##..", "#######.", "#######.", ".#####..", "..###...", "...#....", "........", "........"],
        "diamond": ["...#....", "..###...", ".#####..", "#######.", ".#####..", "..###...", "...#....", "........"],
        "club":    ["..###...", "..###...", "#.###.#.", "#######.", "#.#.#.#.", "...#....", "..###...", "........"],
        "spade":   ["...#....", "..###...", ".#####..", "#######.", "#######.", "..#.#...", "..###...", "........"],
    }
    px = [[None] * 32 for _ in range(8)]
    for i, (name, colour) in enumerate((("heart", red), ("diamond", red), ("club", black), ("spade", black))):
        for y, line in enumerate(rows[name]):
            for x, ch in enumerate(line):
                if ch == "#":
                    px[y][i * 8 + x] = colour
    return px


def token_pile():
    """A short stack of casino chips."""
    red, white, gold = (196, 28, 40), (240, 240, 236), (236, 190, 70)
    px = [[None] * 16 for _ in range(16)]
    for layer in range(5):
        y0 = 12 - layer * 2
        x_shift = (layer % 2) - 0
        for y in (y0, y0 + 1):
            for x in range(3 + x_shift, 13 + x_shift):
                edge = (x - x_shift) in (3, 12)
                stripe = (x - x_shift) % 3 == 0
                px[y][x] = shade(red if not stripe else white, -40 if y == y0 + 1 or edge else 0)
    # The top chip's face.
    for x in range(4, 13):
        px[3][x] = red
        px[2][x] = shade(red, 20)
    for x in range(7, 10):
        px[2][x] = gold
        px[3][x] = gold
    return px


write_png("block/safe_lucky_block.png", lucky_block((30, 70, 150), (200, 210, 225), (130, 140, 160)))
write_png("block/extreme_lucky_block.png", lucky_block((70, 20, 90), (230, 40, 60), (140, 20, 40)))
write_png("block/blessed_lucky_block.png", lucky_block((235, 230, 210), (240, 190, 50), (190, 140, 20)))
write_png("item/token_pile.png", token_pile())
write_png("block/roulette_table_top.png", roulette_top())
write_png("block/blackjack_table_top.png", blackjack_top())
write_png("block/casino_table_side.png", table_side())
write_png("gui/card_suits.png", card_suits())
write_png("block/lucky_block.png", lucky_block())
write_png("gui/slot_symbols.png", slot_symbols())
write_png("block/slot_machine_front.png", slot_machine_front())
write_png("block/slot_machine_side.png", slot_machine_side())
write_png("block/slot_machine_top.png", slot_machine_top())
write_png("item/four_leaf_clover.png", four_leaf_clover())
write_png("item/golden_horseshoe.png", golden_horseshoe())
write_png("item/lucky_seven.png", lucky_seven())
write_png("entity/villager/profession/dealer.png", dealer_outfit())
write_png("entity/zombie_villager/profession/dealer.png", dealer_outfit())
# The lightening overlay for dark disguised blocks: white at 14% (36 of 255), so a face is drawn
# 14% of the way to white. On deepslate that's about as noticeable as the 24% darkening of stone
# (doubled from 7% and 12% after survival play: grass-topped disguises were too hard to spot).

def hot_potato():
    """A baked potato glowing red-hot: orange-brown skin, bright cracks, a spark on top."""
    rng = random.Random(20)
    skin, dark, hot, spark = (196, 120, 52), (130, 70, 30), (255, 210, 80), (255, 250, 200)
    px = [[None] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.5) / 5.6, (y - 8.8) / 4.6
            d = dx * dx + dy * dy
            if d <= 1.0:
                c = shade(skin, rng.randint(-12, 12))
                if d > 0.72 or (x + y) > 20:
                    c = shade(dark, rng.randint(-8, 8))
                px[y][x] = c
    # Glowing cracks.
    for (x, y) in ((5, 8), (6, 9), (7, 9), (8, 8), (9, 9), (10, 10), (6, 11), (7, 12), (9, 6), (10, 7)):
        px[y][x] = hot
    for (x, y) in ((7, 9), (9, 9)):
        px[y][x] = (255, 120, 40)
    # A spark above it.
    for (x, y) in ((11, 2), (12, 3), (11, 3), (10, 3), (11, 4)):
        px[y][x] = spark if (x, y) == (11, 3) else (255, 170, 40)
    px[1][12] = (255, 120, 40)
    return px


def dragon_hatchling():
    """The Dragon Hatchling's 64x64 skin, painted cube by cube to match DragonHatchlingModel's
    texture offsets (vanilla box layout: top and bottom in the first row, then the four sides,
    with the front at (u + d, v + d)). Crimson scales, a gold belly and wing edges, ivory horns."""
    rng = random.Random(16)
    red, red_dark, gold, gold_dark, ivory = (158, 26, 34), (104, 14, 22), (232, 186, 64), (176, 128, 36), (236, 226, 196)
    membrane = (122, 20, 40)
    px = [[None] * 64 for _ in range(64)]

    def scales(colour):
        return lambda x, y: shade(colour, (-14 if (x + 2 * y) % 4 == 0 else 0) + rng.randint(-6, 6))

    def flat(colour):
        return lambda x, y: shade(colour, rng.randint(-6, 6))

    def face(u, v, w, h, paint):
        for y in range(h):
            for x in range(w):
                px[v + y][u + x] = paint(x, y)

    def cube(u, v, w, h, d, top, bottom, sides, front=None, back=None):
        face(u + d, v, w, d, top)
        face(u + d + w, v, w, d, bottom)
        face(u, v + d, d, h, sides)
        face(u + d, v + d, w, h, front or sides)
        face(u + d + w, v + d, d, h, sides)
        face(u + 2 * d + w, v + d, w, h, back or sides)

    # Head (6x5x5 at 0,0): eyes on the front's upper corners, above the snout.
    def head_front(x, y):
        if y == 1 and x in (0, 1, 4, 5):
            return (20, 16, 10) if x in (1, 4) else (255, 214, 60)
        return scales(red)(x, y)
    cube(0, 0, 6, 5, 5, scales(red), flat(gold_dark), scales(red), head_front)
    # Snout (4x3x4 at 24,0): nostrils on the front, a gold jaw underneath.
    def snout_front(x, y):
        return (60, 8, 12) if y == 0 and x in (0, 3) else scales(red)(x, y)
    cube(24, 0, 4, 3, 4, scales(red), flat(gold), scales(red), snout_front)
    # Horns (1x3x1 at 42,0).
    cube(42, 0, 1, 3, 1, flat(ivory), flat(ivory), flat(shade(ivory, -20)))
    # Body (7x6x13 at 0,10): gold belly, a darker stripe down the back.
    def back_top(x, y):
        return scales(red_dark)(x, y) if x in (3,) else scales(red)(x, y)
    def side(x, y):
        return flat(gold)(x, y) if y >= 5 else scales(red)(x, y)
    cube(0, 10, 7, 6, 13, back_top, flat(gold), side)
    # Legs (3x6x3 at 40,10): dark claws at the bottom.
    def leg(x, y):
        return (40, 30, 24) if y == 5 else scales(red)(x, y)
    cube(40, 10, 3, 6, 3, scales(red), flat((40, 30, 24)), leg)
    # Spines (1x2x2 at 52,10).
    cube(52, 10, 1, 2, 2, flat(gold), flat(gold_dark), flat(gold))
    # Tail (3x3x6 at 0,29) and its tip (2x2x5 at 18,29): gold underneath.
    cube(0, 29, 3, 3, 6, scales(red), flat(gold), scales(red))
    cube(18, 29, 2, 2, 5, scales(red), flat(gold), scales(red))
    # Tail fin (1x3x3 at 32,29).
    cube(32, 29, 1, 3, 3, flat(gold), flat(gold), flat(gold_dark))
    # Wings (1x5x8 at 0,40, folded up over the back): membrane with a gold top edge and bones.
    def wing(x, y):
        if y == 0:
            return flat(gold)(x, y)
        if x % 3 == 1:
            return flat(red_dark)(x, y)
        return flat(membrane)(x, y)
    cube(0, 40, 1, 5, 8, flat(gold), flat(membrane), wing, flat(gold_dark), flat(gold_dark))
    return px

write_png_alpha("block/mimic_lighten.png", (255, 255, 255), 36)
write_png("item/luck_glasses.png", luck_glasses_item())
write_png("entity/equipment/humanoid/luck_glasses.png", luck_glasses_worn())
write_png("item/mimic_wand.png", mimic_wand())
write_png("item/casino_token.png", casino_token())
write_png("item/hot_potato.png", hot_potato())
write_png("entity/dragon_hatchling.png", dragon_hatchling())
print("textures written")
