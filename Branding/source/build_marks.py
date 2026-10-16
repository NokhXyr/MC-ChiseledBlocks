"""Draws the Chiseled Blocks marks and wordmark as pixel art and writes them as crisp SVGs.

    python build_marks.py      (writes mark*.svg and wordmark*.svg next to this file)

The mark is a stone block seen in 2:1 isometric view, four pixels to a side, with a stair cut into
its top right edge; the pixel that left it floats out, blue. The small mark (32 px and below) is the
same block seen from the front. The wordmark is a blocky pixel title in the block's stone, whose
"i" carries the blue pixel as its dot and whose "C" ends in the same stair.

Author: NokhXyr
"""
import os
import random
import sys

SKILL = os.path.join(os.path.expanduser("~"), ".claude", "skills", "brand-logo", "scripts")
sys.path.insert(0, SKILL)
from pixel import cells_svg, ramp, title_cells  # noqa: E402
from pixel_font import GLYPHS  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))

STONE = ramp("#8a8f96", 6)
BLUE = ramp("#3a8fe0", 6)
INK = "#14161c"
PAPER = "#f4f1ea"


def outline(cells, color=INK):
    add = {}
    for (x, y) in list(cells):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = (x + dx, y + dy)
            if p not in cells:
                add[p] = color
    cells.update(add)
    return cells


def fill_polygon(cells, faces, points, color, face_id):
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    for y in range(int(min(ys)), int(max(ys))):
        for x in range(int(min(xs)), int(max(xs))):
            cx, cy = x + 0.5, y + 0.5
            sign, inside = 0, True
            for i in range(len(points)):
                ax, ay = points[i]
                bx, by = points[(i + 1) % len(points)]
                cross = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
                if abs(cross) < 1e-9:
                    continue
                s = 1 if cross > 0 else -1
                if sign == 0:
                    sign = s
                elif s != sign:
                    inside = False
                    break
            if inside:
                cells[(x, y)] = color(x, y) if callable(color) else color
                faces[(x, y)] = face_id


def iso(voxels, a=4, origin=(0, 0), seed=3, speckle=0.03):
    """voxels: {(x, y, z): ramp}, y up. Returns (cells, faces): colors, and which face each pixel
    shows ('t', 'l', 'r' plus the voxel's ramp name), painted far to near."""
    rnd = random.Random(seed)
    ox, oy = origin

    def p(x, y, z):
        return ((x - z) * a + ox, (x + z) * a / 2 - y * a + oy)

    cells, faces = {}, {}
    for (x, y, z) in sorted(voxels, key=lambda v: (v[0] + v[2], v[1])):
        shades, name = voxels[(x, y, z)]

        def tone(i):
            def pick(px, py):
                r = rnd.random()
                return shades[max(0, min(len(shades) - 1, i + (1 if r < speckle else -1 if r < 2 * speckle else 0)))]
            return pick

        top = [p(x, y + 1, z), p(x + 1, y + 1, z), p(x + 1, y + 1, z + 1), p(x, y + 1, z + 1)]
        left = [p(x, y + 1, z + 1), p(x + 1, y + 1, z + 1), p(x + 1, y, z + 1), p(x, y, z + 1)]
        right = [p(x + 1, y + 1, z), p(x + 1, y + 1, z + 1), p(x + 1, y, z + 1), p(x + 1, y, z)]
        if (x, y + 1, z) not in voxels:
            fill_polygon(cells, faces, top, tone(4), name + "t")
        if (x, y, z + 1) not in voxels:
            fill_polygon(cells, faces, left, tone(3), name + "l")
        if (x + 1, y, z) not in voxels:
            fill_polygon(cells, faces, right, tone(1), name + "r")
    return cells, faces


def block_voxels():
    n = 4
    vox = {(x, y, z): (STONE, "s") for x in range(n) for y in range(n) for z in range(n) if (x - z) + y < 5}
    vox[(4.25, 4.75, -1.25)] = (BLUE, "b")
    return vox


def mark(with_outline=True):
    cells, faces = iso(block_voxels(), a=4, origin=(15, 21))
    return (outline(cells) if with_outline else cells), faces


def mono(cells, faces, color=INK):
    """One color: every pixel of the drawing in ink, with a gap where two faces meet, so the block,
    its stair and the floating pixel stay readable without shading."""
    out = {}
    for (x, y), face in faces.items():
        right, down = faces.get((x + 1, y)), faces.get((x, y + 1))
        edge = (right is not None and right != face and face.endswith("t")) or \
               (down is not None and down != face and not down.endswith("t") and face.endswith("t")) or \
               (right is not None and right[-1] != face[-1] and face.endswith("l"))
        if not edge:
            out[(x, y)] = color
    return out


def bevel(cells, shades, mask):
    for (x, y) in mask:
        up, left = (x, y - 1) not in mask, (x - 1, y) not in mask
        down, right = (x, y + 1) not in mask, (x + 1, y) not in mask
        if up or left:
            cells[(x, y)] = shades[4]
        elif down or right:
            cells[(x, y)] = shades[1]
        else:
            cells[(x, y)] = shades[3]
    return cells


def small():
    """The block seen from the front, for 32 px and below: a three step stair in its top right
    corner and the blue pixel leaving it."""
    size, step = 12, 3
    mask = set()
    for y in range(size):
        for x in range(size):
            if (size - 1 - x) // step + y // step < 2:
                continue
            mask.add((x + 1, y + 4))
    cells = bevel({}, STONE, mask)
    cells = bevel(cells, BLUE, {(x, y) for x in range(12, 15) for y in range(0, 3)})
    return outline(cells)


GLYPHS_OWN = {
    # The C ends in a stair at the top, like the cut in the block.
    "C": [".XX..", "X..X.", "X....", "X....", "X....", "X...X", ".XXX."],
    # The lowercase c keeps the plain form (the font would otherwise fall back to the C above).
    "c": [".....", ".....", ".XXX.", "X....", "X....", "X....", ".XXX."],
}


def wordmark(base="#9aa0a8", sub=3, depth=3):
    text = "Chiseled Blocks"
    cells = title_cells(text, base, depth=depth, sub=sub, texture=0.0, spacing=1, glyphs=GLYPHS_OWN,
                        outline=INK, seed=7)
    # The dot of the i: its glyph's top row, in the letter's own column; recolor it blue.
    x = 0
    for ch in text:
        rows = GLYPHS_OWN.get(ch) or GLYPHS.get(ch) or GLYPHS.get(ch.upper())
        if ch == "i":
            dot_x = x + rows[0].index("X")
            break
        x += len(rows[0]) + 1
    stone = ramp(base, 7)
    blue = ramp("#3a8fe0", 7)
    for (cx, cy), color in list(cells.items()):
        if dot_x * sub <= cx < (dot_x + 1) * sub and 0 <= cy < sub + depth and color in stone:
            cells[(cx, cy)] = blue[stone.index(color)]
        elif dot_x * sub <= cx < (dot_x + 1) * sub and 0 <= cy < sub + depth and color != INK:
            # The extruded side of the dot: a darker blue.
            cells[(cx, cy)] = blue[1]
    return cells


def write(name, cells, title):
    with open(os.path.join(HERE, name), "w", encoding="utf-8", newline="\n") as f:
        f.write(cells_svg(cells, title=title))
    print("wrote", name)


if __name__ == "__main__":
    color, faces = mark()
    write("mark.svg", color, "Chiseled Blocks")
    plain, _ = mark(with_outline=False)
    write("mark-reverse.svg", plain, "Chiseled Blocks")
    write("mark-mono.svg", mono(plain, faces), "Chiseled Blocks")
    write("mark-small.svg", small(), "Chiseled Blocks")
    write("wordmark.svg", wordmark(), "Chiseled Blocks")
