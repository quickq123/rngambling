"""Makes the village casinos: each village type's vanilla Fletcher House, with its fletching
table swapped for a casino game. Five of each: a Slot Machine (facing into the room), a
Roulette Table, a Blackjack Table, a Higher or Lower Table and a Creeper Field. Standard library only.

The vanilla houses are read from Minecraft's jar, extracted to a folder mounted at /vanilla:
  unzip -j <minecraft jar> 'data/minecraft/structure/village/*/houses/*_fletcher_house_1.nbt' -d <folder>
  MSYS_NO_PATHCONV=1 docker run --rm -v "G:/rngambling:/mod" -v "<folder>:/vanilla:ro" qbot-engine-toolchain \
      python3 /mod/tools/make_structures.py
"""
import gzip
import os
import struct

OUT = "/mod/src/main/resources/data/rngambling/structure/village"
VILLAGES = ["plains", "desert", "savanna", "snowy", "taiga"]

# ---- NBT: (type, value) pairs, enough to read a structure file and write it back unchanged. ----
END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)


class Reader:
    def __init__(self, data):
        self.data, self.pos = data, 0

    def take(self, fmt):
        size = struct.calcsize(fmt)
        value = struct.unpack(">" + fmt, self.data[self.pos:self.pos + size])
        self.pos += size
        return value[0]

    def string(self):
        n = self.take("H")
        s = self.data[self.pos:self.pos + n].decode("utf-8")
        self.pos += n
        return s

    def payload(self, kind):
        if kind == BYTE: return self.take("b")
        if kind == SHORT: return self.take("h")
        if kind == INT: return self.take("i")
        if kind == LONG: return self.take("q")
        if kind == FLOAT: return self.take("f")
        if kind == DOUBLE: return self.take("d")
        if kind == BYTE_ARRAY: return [self.take("b") for _ in range(self.take("i"))]
        if kind == STRING: return self.string()
        if kind == LIST:
            inner = self.take("b")
            return (inner, [self.payload(inner) for _ in range(self.take("i"))])
        if kind == COMPOUND:
            out = {}
            while True:
                tag = self.take("b")
                if tag == END:
                    return out
                name = self.string()
                out[name] = (tag, self.payload(tag))
        if kind == INT_ARRAY: return [self.take("i") for _ in range(self.take("i"))]
        if kind == LONG_ARRAY: return [self.take("q") for _ in range(self.take("i"))]
        raise ValueError(kind)


def write(kind, value, out):
    def put(fmt, v): out.extend(struct.pack(">" + fmt, v))
    def put_string(s):
        b = s.encode("utf-8"); put("H", len(b)); out.extend(b)
    if kind == BYTE: put("b", value)
    elif kind == SHORT: put("h", value)
    elif kind == INT: put("i", value)
    elif kind == LONG: put("q", value)
    elif kind == FLOAT: put("f", value)
    elif kind == DOUBLE: put("d", value)
    elif kind == BYTE_ARRAY: put("i", len(value)); [put("b", v) for v in value]
    elif kind == STRING: put_string(value)
    elif kind == LIST:
        inner, items = value
        put("b", inner if items else END); put("i", len(items))
        for item in items: write(inner, item, out)
    elif kind == COMPOUND:
        for name, (tag, v) in value.items():
            put("b", tag); put_string(name); write(tag, v, out)
        put("b", END)
    elif kind == INT_ARRAY: put("i", len(value)); [put("i", v) for v in value]
    elif kind == LONG_ARRAY: put("i", len(value)); [put("q", v) for v in value]


def load(path):
    r = Reader(gzip.decompress(open(path, "rb").read()))
    assert r.take("b") == COMPOUND
    name = r.string()
    return name, r.payload(COMPOUND)


def save(path, name, root):
    out = bytearray([COMPOUND])
    out.extend(struct.pack(">H", len(name.encode()))); out.extend(name.encode())
    write(COMPOUND, root, out)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(gzip.compress(bytes(out)))


# ---- The swap. ----
SIDES = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}


GAMES = {"slots": "rngambling:slot_machine", "roulette": "rngambling:roulette_table", "blackjack": "rngambling:blackjack_table",
         "higher_lower": "rngambling:higher_lower_table", "creeper_field": "rngambling:creeper_field"}


def make_casino(village, game):
    name, root = load(f"/vanilla/{village}_fletcher_house_1.nbt")
    palette = root["palette"][1][1]
    names = [p["Name"][1] for p in palette]
    table = names.index("minecraft:fletching_table")
    air = {i for i, n in enumerate(names) if n in ("minecraft:air", "minecraft:cave_air")}
    blocks = root["blocks"][1][1]
    at = {tuple(b["pos"][1][1]): b["state"][1] for b in blocks}
    table_pos = next(p for p, s in at.items() if s == table)
    entry = {"Name": (STRING, GAMES[game])}
    if game == "slots":
        # Face the side with open space (inside the house); a structure's missing positions count as open.
        facing = next((side for side, (dx, dy, dz) in SIDES.items()
                       if at.get((table_pos[0] + dx, table_pos[1] + dy, table_pos[2] + dz), next(iter(air), -1)) in air), "north")
        entry["Properties"] = (COMPOUND, {"facing": (STRING, facing)})
    palette[table] = entry
    save(f"{OUT}/{village}/casino_{game}.nbt", name, root)
    print(f"{village}: {game} at {table_pos}")


for v in VILLAGES:
    for g in GAMES:
        make_casino(v, g)
