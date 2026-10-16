"""Builds the set filmed for the Chiseled Blocks page, as a datapack of the recording world.

    python build_scene.py <world folder>      (run-capture/saves/Footage)

The set is a small classical colonnade drawn at the pixel level (16 pixels to a block): a stepped
podium, fluted columns with their bases and capitals, round arches between them, an entablature
with a blue lapis inlay and a cornice, a back wall with arched niches. Every block that mixes
materials or air becomes a chiseled block written with its grid; whole blocks stay plain blocks.

A second function carves one calcite block in front of the colonnade, stroke by stroke (chisel
cubes of 8 and then 4 pixels, with chiseling particles), into the stair of the logo, then places a
cube of blue pixels above it, as the pixel that left the block.

    function footage:build          places the whole set
    function footage:carve/start    starts the carving, one stroke every STROKE ticks

Author: NokhXyr
"""
import math
import os
import sys

P = 16
ORIGIN = (200, -60, 200)          # block of the set's south west bottom corner
W, D, H = 27 * P, 11 * P, 9 * P   # size in pixels (x east, z south, y up)
STROKE = 10                       # ticks between two strokes of the carving

CALCITE = "minecraft:calcite"
QUARTZ = "minecraft:quartz_block"
LAPIS = "minecraft:lapis_block"
ANDESITE = "minecraft:polished_andesite"
SMOOTH = "minecraft:smooth_stone"
BRICKS = "minecraft:stone_bricks"

COLUMNS_X = [56 + 80 * i for i in range(5)]   # column centres, pixels
COLUMN_Z = 112                                 # the colonnade runs east-west at this z
PODIUM_TOP = 16
SHAFT_TOP = 82
ENT_BOTTOM, ENT_TOP = 96, 120


def podium(x, y, z):
    for k, (y0, y1) in enumerate(((0, 6), (6, 11), (11, 16))):
        inset = 6 * k
        if y0 <= y < y1 and inset <= x < W - inset and inset <= z < D - inset:
            return SMOOTH if k < 2 else ANDESITE
    return None


def column(x, y, z):
    for cx in COLUMNS_X:
        dx, dz = x + 0.5 - cx, z + 0.5 - COLUMN_Z
        r = math.hypot(dx, dz)
        if PODIUM_TOP <= y < PODIUM_TOP + 4 and max(abs(dx), abs(dz)) <= 10:
            return CALCITE                                   # square plinth
        if PODIUM_TOP + 4 <= y < PODIUM_TOP + 8 and r <= 9:
            return CALCITE                                   # round base
        if PODIUM_TOP + 8 <= y < SHAFT_TOP and r <= 7:
            angle = math.atan2(dz, dx)
            if r > 5.6 and math.cos(angle * 16) > 0.35:
                return None                                  # a flute
            return CALCITE
        if SHAFT_TOP <= y < SHAFT_TOP + 6 and r <= 9:
            return CALCITE                                   # echinus
        if SHAFT_TOP + 6 <= y < ENT_BOTTOM and max(abs(dx), abs(dz)) <= 11:
            return QUARTZ                                    # abacus
    return None


def arches(x, y, z):
    if not (60 <= y < ENT_BOTTOM and abs(z + 0.5 - COLUMN_Z) <= 6):
        return None
    for a, b in zip(COLUMNS_X, COLUMNS_X[1:]):
        if a <= x < b:
            mid, radius = (a + b) / 2, (b - a) / 2 - 7
            if math.hypot(x + 0.5 - mid, y + 0.5 - 60) > radius:
                return CALCITE
    return None


def entablature(x, y, z):
    x0, x1 = COLUMNS_X[0] - 14, COLUMNS_X[-1] + 14
    front = COLUMN_Z + 13
    if ENT_BOTTOM <= y < ENT_TOP and x0 <= x < x1 and COLUMN_Z - 13 <= z < front:
        # Blue inlay on the front face: diagonal steps of four pixel squares, in a band.
        if z >= front - 2 and 102 <= y < 114 and ((x - x0) // 4 + (y - 102) // 4) % 4 == 0:
            return LAPIS
        return QUARTZ
    if ENT_TOP <= y < ENT_TOP + 4 and x0 - 2 <= x < x1 + 2 and COLUMN_Z - 15 <= z < front + 2:
        return QUARTZ                                        # cornice
    return None


def back_wall(x, y, z):
    if not (COLUMNS_X[0] - 14 <= x < COLUMNS_X[-1] + 14 and PODIUM_TOP <= y < ENT_TOP and 24 <= z < 40):
        return None
    # Arched niches between the column lines, three pixels deep on the south face.
    for a, b in zip(COLUMNS_X, COLUMNS_X[1:]):
        mid = (a + b) / 2
        if z >= 37 and abs(x + 0.5 - mid) <= 18 and PODIUM_TOP + 8 <= y and (
                y < 70 or math.hypot(x + 0.5 - mid, y + 0.5 - 70) <= 18):
            return None
    return BRICKS


def material(x, y, z):
    for part in (column, arches, entablature, back_wall, podium):
        m = part(x, y, z)
        if m:
            return m
    return None


# ---- Grids and commands -------------------------------------------------------------------

def index(x, y, z):
    return (y << 8) | (z << 4) | x


def grid_snbt(voxels):
    """voxels: list of 4096 material names or None -> the SNBT of the block entity."""
    palette = ["minecraft:air"] + sorted({v for v in voxels if v})
    ids = {name: i for i, name in enumerate(palette)}
    bits = max(1, math.ceil(math.log2(len(palette))))
    per_long = 64 // bits
    longs = [0] * math.ceil(4096 / per_long)
    for i, v in enumerate(voxels):
        longs[i // per_long] |= ids[v or "minecraft:air"] << ((i % per_long) * bits)
    signed = [n - (1 << 64) if n >= 1 << 63 else n for n in longs]
    pal = ",".join('{Name:"%s"}' % name for name in palette)
    data = ",".join(f"{n}L" for n in signed)
    return '{grid:{v:1,palette:[%s],bits:%d,data:[L;%s]},facing:"north"}' % (pal, bits, data)


def block_command(bx, by, bz, voxels):
    present = {v for v in voxels}
    if present == {None}:
        return None
    x, y, z = ORIGIN[0] + bx, ORIGIN[1] + by, ORIGIN[2] + bz
    if None not in present and len(present) == 1:
        return f"setblock {x} {y} {z} {present.pop()}"
    return f"setblock {x} {y} {z} chiseledblocks:chiseled_block{grid_snbt(voxels)}"


def build_commands():
    lines, chiseled = [], 0
    for by in range(H // P):
        for bz in range(D // P):
            for bx in range(W // P):
                voxels = [None] * 4096
                for py in range(P):
                    for pz in range(P):
                        for px in range(P):
                            voxels[index(px, py, pz)] = material(bx * P + px, by * P + py, bz * P + pz)
                cmd = block_command(bx, by, bz, voxels)
                if cmd:
                    lines.append(cmd)
                    chiseled += "chiseled_block" in cmd
    return lines, chiseled


# The carving: one calcite block on the podium, in front of the colonnade.
CARVE = (13, 1, 9)   # block, relative to ORIGIN


def carve_steps():
    """The strokes, each a box of pixels to remove: two cubes of eight, then cubes of four."""
    strokes = [((8, 8, 8), (16, 16, 16)), ((8, 8, 0), (16, 16, 8))]
    for z in range(12, -1, -4):
        strokes.append(((4, 12, z), (8, 16, z + 4)))
    for z in range(12, -1, -4):
        strokes.append(((12, 4, z), (16, 8, z + 4)))
    return strokes


def carve_functions():
    solid = [CALCITE] * 4096
    files = {}
    bx, by, bz = CARVE
    x, y, z = ORIGIN[0] + bx, ORIGIN[1] + by, ORIGIN[2] + bz
    files["carve/start"] = [f"setblock {x} {y} {z} {CALCITE}", f"setblock {x} {y + 1} {z} air",
                            f"schedule function footage:carve/s00 {STROKE}t"]
    strokes = carve_steps()
    for k, (lo, hi) in enumerate(strokes):
        for py in range(lo[1], hi[1]):
            for pz in range(lo[2], hi[2]):
                for px in range(lo[0], hi[0]):
                    solid[index(px, py, pz)] = None
        cx, cy, cz = ((lo[0] + hi[0]) / 32, (lo[1] + hi[1]) / 32, (lo[2] + hi[2]) / 32)
        lines = [block_command(bx, by, bz, solid),
                 f"particle minecraft:block{{block_state:\"{CALCITE}\"}} {x + cx:.2f} {y + cy:.2f} {z + cz:.2f} 0.12 0.12 0.12 0 14 force"]
        nxt = f"carve/s{k + 1:02d}" if k + 1 < len(strokes) else "carve/place"
        lines.append(f"schedule function footage:{nxt} {STROKE}t")
        files[f"carve/s{k:02d}"] = lines
    # The blue pixel: a cube of four lapis pixels in the block above, over the stair.
    above = [None] * 4096
    for py in range(4, 8):
        for pz in range(6, 10):
            for px in range(10, 14):
                above[index(px, py, pz)] = LAPIS
    files["carve/place"] = [block_command(bx, by + 1, bz, above),
                            f"particle minecraft:block{{block_state:\"{LAPIS}\"}} {x + 0.75:.2f} {y + 1.4:.2f} {z + 0.5:.2f} 0.1 0.1 0.1 0 10 force"]
    return files, len(strokes)


def main(world):
    pack = os.path.join(world, "datapacks", "footage")
    fn = os.path.join(pack, "data", "footage", "function")
    os.makedirs(os.path.join(fn, "carve"), exist_ok=True)
    with open(os.path.join(pack, "pack.mcmeta"), "w", encoding="utf-8") as f:
        f.write('{"pack":{"pack_format":48,"description":"Chiseled Blocks footage set"}}\n')
    build, chiseled = build_commands()
    clear = (f"fill {ORIGIN[0] - 4} {ORIGIN[1]} {ORIGIN[2] - 4} {ORIGIN[0] + W // P + 4} {ORIGIN[1] + H // P + 2} "
             f"{ORIGIN[2] + D // P + 4} air")
    with open(os.path.join(fn, "build.mcfunction"), "w", encoding="utf-8") as f:
        f.write("\n".join([clear] + build) + "\n")
    files, strokes = carve_functions()
    for name, lines in files.items():
        with open(os.path.join(fn, name + ".mcfunction"), "w", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")
    print(f"set: {len(build)} blocks ({chiseled} chiseled); carving: {strokes} strokes, {STROKE} ticks apart")
    print(f"carved block at {ORIGIN[0] + CARVE[0]} {ORIGIN[1] + CARVE[1]} {ORIGIN[2] + CARVE[2]}")


if __name__ == "__main__":
    main(sys.argv[1])
