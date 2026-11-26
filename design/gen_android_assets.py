# -*- coding: utf-8 -*-
"""把 design/icon_1024.png 与两层自适应图标资源铺到 app-android/src/main/res"""
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DESIGN = os.path.join(ROOT, "design")
RES = os.path.join(ROOT, "app-android", "src", "main", "res")

LEGACY = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
ADAPTIVE = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}

icon = Image.open(os.path.join(DESIGN, "icon_1024.png")).convert("RGBA")
fg = Image.open(os.path.join(DESIGN, "ic_launcher_fg_1024.png")).convert("RGBA")
bg = Image.open(os.path.join(DESIGN, "ic_launcher_bg_1024.png")).convert("RGBA")


def circle(im):
    m = Image.new("L", im.size, 0)
    ImageDraw.Draw(m).ellipse([0, 0, im.size[0] - 1, im.size[1] - 1], fill=255)
    out = im.copy()
    out.putalpha(m)
    return out


for dpi, px in LEGACY.items():
    d = os.path.join(RES, f"mipmap-{dpi}")
    os.makedirs(d, exist_ok=True)
    small = icon.resize((px, px), Image.LANCZOS)
    small.save(os.path.join(d, "ic_launcher.png"))
    circle(small).save(os.path.join(d, "ic_launcher_round.png"))

for dpi, px in ADAPTIVE.items():
    d = os.path.join(RES, f"mipmap-{dpi}")
    os.makedirs(d, exist_ok=True)
    fg.resize((px, px), Image.LANCZOS).save(os.path.join(d, "ic_launcher_fg.png"))
    bg.resize((px, px), Image.LANCZOS).save(os.path.join(d, "ic_launcher_bg.png"))

anydpi = os.path.join(RES, "mipmap-anydpi-v26")
os.makedirs(anydpi, exist_ok=True)
xml = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_bg" />
    <foreground android:drawable="@mipmap/ic_launcher_fg" />
    <monochrome android:drawable="@mipmap/ic_launcher_fg" />
</adaptive-icon>
'''
for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
    with open(os.path.join(anydpi, name), "w", encoding="utf-8") as f:
        f.write(xml)

vals = os.path.join(RES, "values")
os.makedirs(vals, exist_ok=True)
with open(os.path.join(vals, "strings.xml"), "w", encoding="utf-8") as f:
    f.write('''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">言岁AI</string>
</resources>
''')

print("assets written to", RES)
