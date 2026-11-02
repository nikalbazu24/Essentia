"""Generates Essentia's "essence flask" icon: adaptive vector drawables, legacy PNGs and TV banner."""
import os
import sys
from PIL import Image, ImageDraw, ImageFont

RES = sys.argv[1]  # path to app/src/main/res

BG = "#1e2240"
# Pixel-art flask on a 150x150 canvas (10 px cells), as (x, y, w, h, color)
RECTS = [
    (120, 50, 30, 20, "#c9a27a"),   # cork
    (120, 50, 30, 6, "#e3c39f"),    # cork highlight
    (120, 70, 30, 20, "#cfd8ea"),   # neck
    (100, 90, 70, 10, "#cfd8ea"),   # shoulders
    (90, 100, 90, 50, "#cfd8ea"),   # body
    (100, 150, 70, 10, "#cfd8ea"),  # base
    (100, 110, 70, 10, "#b48cff"),  # liquid top
    (100, 120, 70, 30, "#8a5cf6"),  # liquid
    (110, 150, 50, 4, "#6b3fe0"),   # liquid bottom
    (100, 100, 10, 10, "#ffffff"),  # glass shine
    (150, 130, 10, 10, "#d9c7ff"),  # bubble
    (130, 105, 6, 6, "#d9c7ff"),    # bubble
    (170, 60, 8, 8, "#ffe066"),     # sparkle
    (182, 48, 6, 6, "#ffe066"),     # sparkle
]
# Canvas origin of the art in the concept (the rounded square started at 60,30)
OX, OY = 64, 30  # centers the art (bbox 90..188 x 50..160)


def vector(path, viewport, scale, offset, mono=False):
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<!-- Essentia: frasco de esencia (generado por make_icon.py) -->',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{viewport}dp" android:height="{viewport}dp"',
        f'    android:viewportWidth="{viewport}" android:viewportHeight="{viewport}">',
    ]
    for x, y, w, h, c in RECTS:
        x0 = offset + (x - OX) * scale
        y0 = offset + (y - OY) * scale
        x1, y1 = x0 + w * scale, y0 + h * scale
        color = "#FF000000" if mono else c
        lines.append(
            f'    <path android:fillColor="{color}" '
            f'android:pathData="M{x0:.2f},{y0:.2f}H{x1:.2f}V{y1:.2f}H{x0:.2f}Z"/>'
        )
    lines.append("</vector>")
    with open(path, "w") as f:
        f.write("\n".join(lines) + "\n")


def draw_art(img, scale, ox, oy):
    d = ImageDraw.Draw(img)
    for x, y, w, h, c in RECTS:
        x0 = ox + (x - OX) * scale
        y0 = oy + (y - OY) * scale
        d.rectangle([round(x0), round(y0), round(x0 + w * scale) - 1, round(y0 + h * scale) - 1], fill=c)


def legacy_png(size, path, round_icon=False):
    ss = 4  # supersample for smooth corners
    big = size * ss
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if round_icon:
        d.ellipse([0, 0, big - 1, big - 1], fill=BG)
    else:
        d.rounded_rectangle([0, 0, big - 1, big - 1], radius=int(big * 0.22), fill=BG)
    # art spans 150 units; fit into ~70% of the icon
    scale = big * 0.84 / 150
    off = (big - 150 * scale) / 2
    draw_art(img, scale, off, off)
    img = img.resize((size, size), Image.LANCZOS)
    img.save(path)


# --- adaptive icon (vector, API 26+) ---
os.makedirs(f"{RES}/drawable", exist_ok=True)
# 108dp canvas, art 150 units -> 60dp centered (inside the 66dp safe zone)
vector(f"{RES}/drawable/ic_launcher_foreground.xml", 108, 0.44, 21)
vector(f"{RES}/drawable/ic_launcher_monochrome.xml", 108, 0.44, 21, mono=True)
with open(f"{RES}/values/ic_launcher_colors.xml", "w") as f:
    f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
            f'    <color name="ic_launcher_background">{BG}</color>\n</resources>\n')

adaptive = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
"""
with open(f"{RES}/mipmap-anydpi-v26/ic_launcher.xml", "w") as f:
    f.write(adaptive)

# --- legacy PNGs (Android 5-7) ---
for density, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    folder = f"{RES}/mipmap-{density}"
    for old in ("ic_launcher_background.png", "ic_launcher_foreground.png", "ic_launcher_monochrome.png"):
        p = f"{folder}/{old}"
        if os.path.exists(p):
            os.remove(p)
    legacy_png(size, f"{folder}/ic_launcher.png")

# --- Android TV banner (320x180, xhdpi) ---
W, H = 320, 180
ss = 3
banner = Image.new("RGBA", (W * ss, H * ss), BG)
scale = (H * ss * 0.62) / 150
draw_art(banner, scale, 34 * ss, (H * ss - 150 * scale) / 2)
d = ImageDraw.Draw(banner)
font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 34 * ss)
d.text((150 * ss, H * ss / 2), "Essentia", font=font, fill="#f3eefe", anchor="lm")
banner.resize((W, H), Image.LANCZOS).save(f"{RES}/mipmap-xhdpi/ic_banner.png")

# preview for review
prev = Image.new("RGBA", (192 * 3 + 40, 192), (255, 255, 255, 0))
for i, (sz) in enumerate([192]):
    pass
legacy_png(512, sys.argv[2] if len(sys.argv) > 2 else "/tmp/icon_preview.png")
print("ok")
