"""Draws the Chiseled Blocks: Create marks and wordmark, derived from the Chiseled Blocks ones.

    python build_marks.py      (writes mark*.svg and wordmark.svg next to this file)

The mark is the carved stone block of Chiseled Blocks with its blue pixel, set in front of a brass
cogwheel, the material of Create's machines: the block is now cut by machines. The small mark is the
front view of the block on the same cog. The wordmark is the Chiseled Blocks title with "Create"
under it in brass.

Author: NokhXyr
"""
import importlib.util
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MAIN = os.path.join(HERE, "..", "..", "..", "Branding", "source", "build_marks.py")
# The Chiseled Blocks marks, loaded under their own name (this file has the same one).
_spec = importlib.util.spec_from_file_location("chiseled_blocks_marks", MAIN)
base = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(base)
from pixel import cells_svg, ramp, title_cells  # noqa: E402

BRASS = ramp("#d19a3a", 6)
INK = base.INK


def iso_cog(cx, cy, radius, teeth=10, tooth=3.0, depth=3):
    """A cogwheel lying flat, seen in the same 2:1 isometric view as the block: its top face in
    light brass, its rim in dark brass, as a turntable the block rides on."""
    def top(x, y):
        dx, dy = (x + 0.5 - cx) / 2.0, y + 0.5 - cy
        r = math.hypot(dx, dy)
        angle = (math.atan2(dy, dx) + math.pi / teeth / 2) % (2 * math.pi / teeth)
        return r <= radius or (angle < math.pi / teeth and r <= radius + tooth)

    span = int(2 * (radius + tooth)) + 2
    faces = {(x, y) for y in range(int(cy) - span, int(cy) + span) for x in range(int(cx) - 2 * span, int(cx) + 2 * span)
             if top(x, y)}
    cells = {}
    for (x, y) in faces:
        for k in range(1, depth + 1):
            if (x, y + k) not in faces:
                cells[(x, y + k)] = BRASS[1] if k < depth else BRASS[0]
    cells = base.bevel(cells, BRASS, set()) or cells
    for (x, y) in faces:
        edge = any((x + dx, y + dy) not in faces for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        cells[(x, y)] = BRASS[4] if edge else BRASS[3]
    return base.outline(cells)


# The block rides on a cog lying under it, centred on its footprint; the stair and the blue pixel
# stay clear of it.
COG = (15.0, 29.0, 10.5, 10, 2.5, 3)


def mark(with_outline=True):
    block, faces = base.mark(with_outline)
    cells = iso_cog(*COG)
    cells.update(block)
    return cells, faces


def mono(color=INK):
    """One color: the block drawn as in the Chiseled Blocks mono mark, the cog under it with a gap
    of one pixel, so the two shapes stay apart."""
    plain, faces = base.mark(with_outline=False)
    block = base.mono(plain, faces, color)
    near = {(x + dx, y + dy) for (x, y) in plain for dx in (-1, 0, 1) for dy in (-1, 0, 1)}
    cells = {p: color for p, c in iso_cog(*COG).items() if c != INK and p not in near}
    cells.update(block)
    return cells


def small():
    """The front view of the block for 32 px and below, standing on the cog seen edge on: a brass
    band under it, toothed on its lower side."""
    block = base.small()
    bottom = max(y for _, y in block)
    mask = {(x, y) for x in range(-1, 17) for y in (bottom, bottom + 1)}
    mask |= {(x, bottom + 2) for x in range(-1, 17) if (x + 1) % 4 < 2}
    cells = base.outline(base.bevel({}, BRASS, mask))
    cells.update(block)
    return cells


def wordmark():
    top = base.wordmark()
    width = max(x for x, _ in top) + 1
    height = max(y for _, y in top) + 1
    word = title_cells("Create", "#d19a3a", depth=3, sub=3, texture=0.0, spacing=1, outline=INK, seed=7)
    w2 = max(x for x, _ in word) + 1
    dx, dy = width - w2, height + 3
    cells = dict(top)
    for (x, y), c in word.items():
        cells[(x + dx, y + dy)] = c
    return cells


def write(name, cells, title):
    with open(os.path.join(HERE, name), "w", encoding="utf-8", newline="\n") as f:
        f.write(cells_svg(cells, title=title))
    print("wrote", name)


if __name__ == "__main__":
    title = "Chiseled Blocks: Create"
    color, _ = mark()
    write("mark.svg", color, title)
    plain, _ = mark(with_outline=False)
    write("mark-reverse.svg", plain, title)
    write("mark-mono.svg", mono(), title)
    write("mark-small.svg", small(), title)
    write("wordmark.svg", wordmark(), title)
