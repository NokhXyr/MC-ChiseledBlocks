"""Builds the animated banners of the Chiseled Blocks: Create page (chiseledblocks-create/images/*.gif)
from the footage recorded in game with Just Replay (images/tools/footage/create-line.json, the shot
plan of the main mod's tools).

    python chiseledblocks-create/images/tools/make_banners.py [names...]

The drawing library is the one of the main page (images/tools/ at the repository root). Frames come
from the exported video at 1600 x 900 and 20 frames a second
(chiseledblocks-create/run-capture/screenshots/line_<number>.png): shot 1 (frames 1 to 160) is the
wide view, shots 2 to 4 (161 to 580) follow the slicer, the cutter and the crusher, shot 5 (581 to
800) faces the whole line. A crop is given in pixels of those frames, a box in pixels of the banner.

Author: NokhXyr
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ADDON = os.path.dirname(os.path.dirname(HERE))
ROOT = os.path.dirname(ADDON)
sys.path.insert(0, os.path.join(ROOT, "images", "tools"))

import fx  # noqa: E402
import kit  # noqa: E402
from theme import Theme  # noqa: E402

SRC = os.path.join(ADDON, "run-capture", "screenshots")
OUT = os.path.dirname(HERE)
THEME = Theme.load(os.path.join(HERE, "theme.json"))
BRAND_LOGO = os.path.join(ADDON, "Branding", "logo", "png", "chiseled-blocks-create-stacked-reverse-1024.png")


def src(name):
    return os.path.join(SRC, name)


def out(name):
    return os.path.join(OUT, name)


kit.LOOK = fx.look(THEME, vibrance=14, vignette=22, particles=6, particle_type="cube")


def hero():
    """The pixel line seen from its corner, with the addon's stacked logo on a dark veil in the empty
    grass at the left (the tagline is in the page's introduction: the line fills the rest)."""
    from PIL import Image
    logo = Image.open(BRAND_LOGO).convert("RGBA")
    logo.thumbnail((210, 250), Image.LANCZOS)
    veil = Image.new("RGBA", (800, 300), (14, 16, 22, 0))
    alpha = Image.new("L", (800, 300), 0)
    alpha.putdata([int(200 * max(0.0, 1.0 - x / 300.0) ** 1.3) for _ in range(300) for x in range(800)])
    veil.putalpha(alpha)

    def overlay(canvas, i, n):
        canvas.alpha_composite(veil)
        canvas.alpha_composite(logo, (18, (300 - logo.height) // 2))

    kit.still_scene(out("hero.gif"), THEME, src("line_0060.png"), crop=(0, 150, 1600, 750), size=(800, 300), length=60,
                    overlay=overlay, floaters=False)


def line():
    """The whole line from the front: stone blocks go in on the left, slices, rods and pixels come out."""
    kit.clip_scene(out("line.gif"), THEME, kit.clip(SRC, "line", 600, 740), crop=(0, 120, 1600, 760), size=(800, 320),
                   plate=src("line_0600.png"), boxes=[(0, 150, 800, 320)], threshold=8, tail=6)


BUILDERS = {f.__name__: f for f in (hero, line)}


def main():
    for name in sys.argv[1:] or list(BUILDERS):
        BUILDERS[name]()


if __name__ == "__main__":
    main()
