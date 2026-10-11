"""Contact sheet: every texture art.py painted, upscaled with nearest-neighbour, plus GUI renders of the 3D guns."""

import os

from PIL import Image, ImageDraw

import artlib as A

BG = (34, 32, 38, 255)
SLOT = (58, 55, 64, 255)
INK = (220, 214, 200, 255)


def contact_sheet(textures, path, cell=96, cols=8):
    import guns

    entries = []
    for gun, img in guns.PREVIEWS.items():
        entries.append((f"{gun} (3D)", img))
    for p in sorted(textures):
        img = textures[p]
        name = os.path.splitext(os.path.basename(p))[0]
        if "/textures/item/" in p and name in guns.GUNS:
            name += " (atlas)"
        w, h = img.size
        if h > w and h % w == 0:  # animation strip: show frame 0 and the middle frame side by side
            f0 = img.crop((0, 0, w, w))
            fm = img.crop((0, (h // w // 2) * w, w, (h // w // 2 + 1) * w))
            both = Image.new("RGBA", (w * 2 + 1, w), A.TRANSPARENT)
            both.paste(f0, (0, 0))
            both.paste(fm, (w + 1, 0))
            img, name = both, name + " (anim)"
        entries.append((name, img))
    rows = (len(entries) + cols - 1) // cols
    label = 14
    sheet = Image.new("RGBA", (cols * cell, rows * (cell + label)), BG)
    dr = ImageDraw.Draw(sheet)
    for i, (name, img) in enumerate(entries):
        x, y = (i % cols) * cell, (i // cols) * (cell + label)
        dr.rectangle([x + 2, y + 2, x + cell - 3, y + cell - 3], fill=SLOT)
        w, h = img.size
        k = max(1, min((cell - 8) // w, (cell - 8) // h)) if max(w, h) <= cell else 0
        big = img.resize((w * k, h * k), Image.NEAREST) if k else img.resize((cell - 8, cell - 8), Image.LANCZOS)
        if big.width > cell - 8 or big.height > cell - 8:
            big.thumbnail((cell - 8, cell - 8), Image.NEAREST)
        sheet.alpha_composite(big, (x + (cell - big.width) // 2, y + (cell - big.height) // 2))
        dr.text((x + 3, y + cell), name[:16], fill=INK)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    sheet.save(path, optimize=True)
    # Guns at a readable size, GUI pose (what the inventory shows).
    big = Image.new("RGBA", (256 * len(guns.PREVIEWS), 256 + label), BG)
    bd = ImageDraw.Draw(big)
    for i, (gun, img) in enumerate(guns.PREVIEWS.items()):
        big.alpha_composite(img, (256 * i, 0))
        bd.text((256 * i + 6, 256), gun, fill=INK)
    big.save(os.path.splitext(path)[0] + "-guns.png", optimize=True)
