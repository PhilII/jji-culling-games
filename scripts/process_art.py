"""Turns the ChatGPT art (flat #FF00FF magenta background) into game textures.

Usage: python3 scripts/process_art.py ~/culling-games-assets/originals
Needs Pillow and numpy. Writes into src/main/resources. Originals stay outside the repo.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

SRC = Path(sys.argv[1]).expanduser()
RES = Path(__file__).resolve().parent.parent / "src/main/resources"
GUI = RES / "assets/cullinggames/textures/gui"
MAGENTA = np.array([255.0, 0.0, 255.0])


def keyed(name, despill=False):
    """Loads an image and turns the magenta background transparent, unmixing soft edges.

    despill also removes the purple fringe where dark ink was blended into magenta. Only for art
    without real purples (it would eat them)."""
    rgb = np.asarray(Image.open(SRC / f"{name}.webp").convert("RGB")).astype(float)
    dist = np.sqrt(((rgb - MAGENTA) ** 2).sum(-1))
    alpha = np.clip((dist - 45) / (110 - 45), 0, 1)
    a = np.maximum(alpha, 1e-3)[..., None]
    color = np.clip((rgb - (1 - a) * MAGENTA) / a, 0, 255)
    if despill:
        spill = np.clip(np.minimum(color[..., 0], color[..., 2]) - color[..., 1], 0, 255)
        color[..., 0] -= spill
        color[..., 2] -= spill
        alpha = alpha * (1 - spill / 255)
    color[alpha == 0] = 0
    out = np.dstack([color, alpha * 255]).astype(np.uint8)
    return Image.fromarray(out)


def fit(img, w, h=None):
    """Resizes to width w (keeping aspect) or to exactly w x h."""
    if h is None:
        h = round(img.height * w / img.width)
    return img.resize((w, h), Image.LANCZOS)


def square(img):
    """Pads to a square, centred."""
    s = max(img.size)
    out = Image.new("RGBA", (s, s))
    out.paste(img, ((s - img.width) // 2, (s - img.height) // 2))
    return out


def save(img, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, optimize=True)
    print(f"{path.relative_to(RES)}  {img.width}x{img.height}")


def main():
    # HUD icons: points, timer, colony, lives, sealed. Cut lines found by measuring the gaps.
    icons = keyed("hud-icons")
    cuts = [0, 408, 704, 1160, 1572, icons.width]
    strip = Image.new("RGBA", (48 * 5, 48))
    for i in range(5):
        part = icons.crop((cuts[i], 0, cuts[i + 1], icons.height))
        part = part.crop(part.getbbox())
        strip.paste(fit(square(part), 48, 48), (48 * i, 0))
    save(strip, GUI / "hud_icons.png")

    # Kogane: neutral, announcing, annoyed. Same crop box per frame so they line up.
    kog = keyed("kogane-48", despill=True)
    sheet = Image.new("RGBA", (128 * 3, 128))
    for i, x0 in enumerate([19, 686, 1352]):
        frame = square(kog.crop((x0, 60, x0 + 629, 671)))
        sheet.paste(fit(frame, 128, 128), (128 * i, 0))
    save(sheet, GUI / "kogane.png")

    # Shop: Kogane's head on top, a 9-slice screen frame, the neck and tail below.
    shop = keyed("shop-kogane", despill=True)
    head = shop.crop((26, 44, 1229, 372))
    px = head.load()
    for y in range(345 - 44, head.height):  # keep only the jaw below the rim line
        for x in range(head.width):
            r, g, b, _ = px[x, y]
            if not 560 - 26 <= x <= 700 - 26 or (g > r + 25 and g > b + 25):  # drop the rim and the green
                px[x, y] = (0, 0, 0, 0)
    save(fit(head, 602), GUI / "shop_head.png")
    # Frame: from the top of the rim (row 368) so no wing or tooth pixels are left in its corners.
    save(fit(shop.crop((415, 368, 840, 966)), 212, 299), GUI / "shop_frame.png")
    # Tail: starts 120 rows above the frame bottom so the heart-shaped tip (beside the frame) is kept;
    # everything of the frame itself in those rows is cleared.
    tail = shop.crop((454, 846, 1080, 1226))
    tp = tail.load()
    for y in range(966 - 846):
        for x in range(900 - 454):
            tp[x, y] = (0, 0, 0, 0)
    save(fit(tail, 313, 190), GUI / "shop_tail.png")

    title = keyed("title-card", despill=True)
    save(fit(title.crop(title.getbbox()), 1024), GUI / "title_card.png")

    banner = keyed("rule-banner", despill=True)
    save(fit(banner.crop(banner.getbbox()), 768), GUI / "rule_banner.png")

    barrier = keyed("barrier-icon")
    save(fit(square(barrier.crop(barrier.getbbox())), 64, 64), GUI / "barrier.png")

    logo = Image.open(SRC / "logo.webp").convert("RGB")
    save(fit(logo, 256, 256), RES / "logo.png")


if __name__ == "__main__":
    main()
