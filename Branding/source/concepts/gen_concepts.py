"""Draws the Chiseled Blocks logo concepts as pixel art and writes them as crisp SVGs.

Isometric concepts are built from voxels: each voxel is projected 2:1 (a top diamond 2a wide and a
high, sides a high) and painted far to near, top face light, left face mid, right face dark (light
from the top left). Flat concepts are drawn on a 16 x 16 grid by hand.

    python gen_concepts.py            (writes concept-<n>.svg next to this file)

Author: NokhXyr
"""
import os
import random
import sys

SKILL = os.path.join(os.path.expanduser("~"), ".claude", "skills", "brand-logo", "scripts")
sys.path.insert(0, SKILL)
from pixel import cells_svg, ramp  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))

# The mod's own colors: the stone of the reference blocks, the blue of its tools and panel.
STONE = ramp("#8a8f96", 6)
BLUE = ramp("#3a8fe0", 6)
INK = "#14161c"


def outline(cells, color=INK):
    """A one pixel dark outline round every shape (4 neighbours), corners left open."""
    add = {}
    for (x, y) in list(cells):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = (x + dx, y + dy)
            if p not in cells:
                add[p] = color
    cells.update(add)
    return cells


def fill_polygon(cells, points, color):
    """Fills a convex polygon of integer points, pixel centres inside or on the edge."""
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    for y in range(int(min(ys)), int(max(ys))):
        for x in range(int(min(xs)), int(max(xs))):
            cx, cy = x + 0.5, y + 0.5
            inside = True
            n = len(points)
            sign = 0
            for i in range(n):
                ax, ay = points[i]
                bx, by = points[(i + 1) % n]
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


def iso(voxels, a=4, origin=(17, 17), seed=3):
    """voxels: {(x, y, z): ramp} with y up. Returns pixel cells, painted far to near."""
    rnd = random.Random(seed)
    ox, oy = origin

    def p(x, y, z):
        return ((x - z) * a + ox, (x + z) * a / 2 - y * a + oy)

    cells = {}
    for (x, y, z) in sorted(voxels, key=lambda v: (v[0] + v[2], v[1])):
        shades = voxels[(x, y, z)]

        def tone(i):
            # A light stone speckle: now and then one step up or down the ramp.
            def pick(px, py):
                r = rnd.random()
                return shades[max(0, min(len(shades) - 1, i + (1 if r < 0.03 else -1 if r < 0.06 else 0)))]
            return pick

        top = [p(x, y + 1, z), p(x + 1, y + 1, z), p(x + 1, y + 1, z + 1), p(x, y + 1, z + 1)]
        left = [p(x, y + 1, z + 1), p(x + 1, y + 1, z + 1), p(x + 1, y, z + 1), p(x, y, z + 1)]
        right = [p(x + 1, y + 1, z), p(x + 1, y + 1, z + 1), p(x + 1, y, z + 1), p(x + 1, y, z)]
        if (x, y + 1, z) not in voxels:
            fill_polygon(cells, top, tone(4))
        if (x, y, z + 1) not in voxels:
            fill_polygon(cells, left, tone(3))
        if (x + 1, y, z) not in voxels:
            fill_polygon(cells, right, tone(1))
    return cells


def write(n, cells, title):
    path = os.path.join(HERE, f"concept-{n}.svg")
    with open(path, "w", encoding="utf-8") as f:
        f.write(cells_svg(cells, title=title))
    print("wrote", path)


def concept_bite():
    """A stone block with a stair cut into its top right edge; the cut pixel floats out, blue."""
    n = 4
    vox = {(x, y, z): STONE for x in range(n) for y in range(n) for z in range(n) if (x - z) + y < 5}
    cells = iso(vox, a=4, origin=(15, 21))
    cells.update(iso({(4.25, 4.75, -1.25): BLUE}, a=4, origin=(15, 21), seed=9))
    return outline(cells)


def concept_pulled():
    """The bite seen from the other side: the stair on the top left edge, the pixel out to the left."""
    n = 4
    vox = {(x, y, z): STONE for x in range(n) for y in range(n) for z in range(n) if (z - x) + y < 5}
    cells = iso(vox, a=4, origin=(19, 21))
    cells.update(iso({(-1.25, 4.75, 4.25): BLUE}, a=4, origin=(19, 21), seed=9))
    return outline(cells)


def bevel(cells, shades, mask):
    """Front view: light on top and left edges of the mask, dark on bottom and right, mid inside."""
    for (x, y) in mask:
        up, left = (x, y - 1) not in mask, (x - 1, y) not in mask
        down, right = (x, y + 1) not in mask, (x + 1, y) not in mask
        if up or left:
            cells[(x, y)] = shades[4]
        elif down or right:
            cells[(x, y)] = shades[1]
        else:
            cells[(x, y)] = shades[3] if (x * 7 + y * 13) % 11 else shades[2]
    return cells


def concept_sixteenths():
    """The block seen from the front: a three step stair cut into its top right corner, the cut
    pixel leaving it, blue."""
    size, step = 12, 3
    mask = set()
    for y in range(size):
        for x in range(size):
            k = size - 1 - x
            # Steps three pixels wide and high: the corner loses a staircase of three steps.
            if k // step + y // step < 2:
                continue
            mask.add((x + 1, y + 4))
    cells = bevel({}, STONE, mask)
    cells = bevel(cells, BLUE, {(x, y) for x in range(12, 15) for y in range(0, 3)})
    return outline(cells)


def concept_c():
    """A blocky C whose opening holds the blue pixel."""
    rows = [
        "..XXXXXXXXXX..",
        ".XXXXXXXXXXXX.",
        "XXXXXXXXXXXXXX",
        "XXXX......XXXX",
        "XXXX..........",
        "XXXX..........",
        "XXXX..........",
        "XXXX..........",
        "XXXX..........",
        "XXXX..........",
        "XXXX......XXXX",
        "XXXXXXXXXXXXXX",
        ".XXXXXXXXXXXX.",
        "..XXXXXXXXXX..",
    ]
    mask = {(x + 1, y + 1) for y, r in enumerate(rows) for x, c in enumerate(r) if c == "X"}
    cells = bevel({}, STONE, mask)
    cells = bevel(cells, BLUE, {(x, y) for x in range(9, 13) for y in range(6, 10)})
    return outline(cells)


if __name__ == "__main__":
    write(1, concept_bite(), "The bite")
    write(2, concept_pulled(), "The pulled pixel")
    write(3, concept_sixteenths(), "The sixteenths")
    write(4, concept_c(), "The C")
