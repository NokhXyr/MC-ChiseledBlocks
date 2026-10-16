"""Builds the animated banners of the Chiseled Blocks page (images/*.gif) from the footage recorded
in game with Just Replay (images/tools/footage/: the set, the shot plans).

    python images/tools/make_banners.py [names...]

Frames are taken from the exported videos at 1600 x 900 and 20 frames a second
(run-capture/screenshots/<take>_<number>.png). A crop is given in pixels of those frames, a box (the
area that keeps moving) in pixels of the banner; outside the boxes a still plate is shown, which
keeps the files small.

Author: NokhXyr
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, HERE)

import fx  # noqa: E402
import kit  # noqa: E402
import pixelfont as font  # noqa: E402
from theme import Theme  # noqa: E402

SRC = os.path.join(ROOT, "run-capture", "screenshots")
OUT = os.path.dirname(HERE)
THEME = Theme.load(os.path.join(HERE, "theme.json"))
BRAND_LOGO = os.path.join(ROOT, "Branding", "logo", "png", "chiseled-blocks-horizontal-reverse-1024.png")


def src(name):
    return os.path.join(SRC, name)


def out(name):
    return os.path.join(OUT, name)


kit.LOOK = fx.look(THEME, vibrance=14, vignette=22, particles=6, particle_type="cube")


def hero():
    """The colonnade seen from its front down to the chiseled block and its blue pixel, with the logo
    of the brand kit on a dark veil at the left and the tagline under it."""
    from PIL import Image
    logo = Image.open(BRAND_LOGO).convert("RGBA")
    logo = logo.resize((400, round(logo.height * 400 / logo.width)), Image.LANCZOS)
    tagline = kit.text_image("CHISEL ANY BLOCK, PIXEL BY PIXEL", 2, THEME.paper, outline=THEME.ink)
    # A dark veil on the left only, fading out before the middle, so the logo reads and the colonnade
    # keeps its light.
    veil = Image.new("RGBA", (800, 300), (14, 16, 22, 0))
    alpha = Image.new("L", (800, 300), 0)
    alpha.putdata([int(200 * max(0.0, 1.0 - x / 470.0) ** 1.2) for _ in range(300) for x in range(800)])
    veil.putalpha(alpha)

    def overlay(canvas, i, n):
        canvas.alpha_composite(veil)
        y = (300 - logo.height - tagline.height - 6) // 2
        canvas.alpha_composite(logo, (26, y))
        canvas.alpha_composite(tagline, (26 + (400 - tagline.width) // 2, y + logo.height + 6))

    kit.still_scene(out("hero.gif"), THEME, src("dolly_0060.png"), crop=(0, 225, 1600, 825), size=(800, 300), length=60,
                    overlay=overlay, floaters=False)


def carve():
    """A calcite block carved into a stair, stroke by stroke, then the blue pixel placed above it."""
    kit.clip_scene(out("carve.gif"), THEME, kit.clip(SRC, "carve", 1, 110), crop=(60, 180, 1527, 840), size=(800, 360),
                   plate=src("carve_0001.png"), boxes=[(165, 70, 300, 250), (285, 70, 520, 370)], threshold=8, tail=6)


def speed():
    """The colonnade still, and what 16,000 chiseled blocks cost, the numbers counting up (the stress
    run of the mod alone, docs/benchmarks/2026-10-16-stress.md)."""
    stats = [("PLAYABLE AFTER", 1.0, "{:.1f} S"), ("FLYING OVER", 617.0, "{:.0f} FPS"), ("WORLD SAVE", 67.0, "{:.0f} MS")]

    def overlay(canvas, i, n):
        card = kit.panel(372, 150, THEME)
        font.draw(card, "16,000 CHISELED BLOCKS", 16, 12, 2, THEME.accent)
        # The loop opens on the final numbers (the still a visitor sees first), then counts up again.
        ratio = min(1.0, (((i / n) + 0.5) % 1.0) / 0.5)
        ease = 1.0 - (1.0 - ratio) ** 3
        for k, (name, value, fmt) in enumerate(stats):
            y = 44 + k * 34
            font.draw(card, name, 16, y, 2, THEME.ink)
            label = fmt.format(value * ease)
            font.draw(card, label, 356 - font.width(label, 2), y, 2, THEME.ink)
        canvas.alpha_composite(card, (412, 160))

    kit.still_scene(out("speed.gif"), THEME, src("wide_0035.png"), crop=(0, 120, 1600, 760), size=(800, 320), length=100,
                    overlay=overlay, floaters=False)


def create():
    """The Create addon: a stone block cut into slices, rods and pixels on its way along the belt."""
    kit.clip_scene(out("create.gif"), THEME, kit.clip(SRC, "create", 20, 150), crop=(40, 150, 1560, 758), size=(800, 320),
                   plate=src("create_0001.png"), boxes=[(85, 110, 735, 305)], threshold=8, tail=6)


BUILDERS = {f.__name__: f for f in (hero, carve, speed, create)}


def main():
    for name in sys.argv[1:] or list(BUILDERS):
        BUILDERS[name]()


if __name__ == "__main__":
    main()
