# -*- coding: utf-8 -*-
"""羊毛毡风图标落进 Android 资源（按 generate-app-icons 技能第 5 步做细节调整）：
   - 去右下角水印（镜像补丁 + 羽化）
   - 整图圆角
   - 自适应图标：前景=整幅画面缩进安全区并羽化边缘，背景=左右条带插值出的同色渐变
运行：python design/make_icon_felt.py
"""
import os
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DESIGN = os.path.join(ROOT, "design")
SRC = os.path.join(DESIGN, "v4", "Wool_felt_style_app_icon__1_1__2026-11-26T22-39-45.png")
S = 1024

im = Image.open(SRC).convert("RGB")

# ---- 1. 去水印：把左上方同尺寸区域镜像贴到右下角，边缘羽化 ----
box = (880, 910, S, S)
w, h = box[2] - box[0], box[3] - box[1]
patch = im.crop((box[0] - w - 12, box[1] - h - 12, box[0] - 12, box[1] - 12))
patch = patch.transpose(Image.ROTATE_180)
feather = Image.new("L", (w, h), 0)
ImageDraw.Draw(feather).rectangle([22, 22, w - 1, h - 1], fill=255)
feather = feather.filter(ImageFilter.GaussianBlur(18))
im.paste(patch, (box[0], box[1]), feather)

# ---- 2. 背景：左右条带逐行插值（水印行用干净行兜底） ----
def strip(x0, x1):
    col = im.crop((x0, 0, x1, S)).resize((1, S), Image.BOX)
    px = col.load()
    safe = px[0, 880]
    for y in range(880, S):
        col.putpixel((0, y), safe)
    return col.resize((S, S), Image.NEAREST)

left_col = strip(0, 70)
right_col = strip(954, S)
hgrad = Image.linear_gradient("L").rotate(-90, expand=True).resize((S, S), Image.BILINEAR)
bg = Image.composite(right_col, left_col, hgrad).filter(ImageFilter.GaussianBlur(3))

# ---- 3. 整图：原画 + 圆角 ----
icon = im.convert("RGBA")
mask_r = Image.new("L", (S, S), 0)
ImageDraw.Draw(mask_r).rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.225), fill=255)
icon.putalpha(mask_r)
icon.save(os.path.join(DESIGN, "icon_1024.png"))

# ---- 4. 自适应前景：整幅画面缩到 78% 居中，边缘羽化融入背景 ----
scale = 0.78
small = im.resize((int(S * scale), int(S * scale)), Image.LANCZOS)
fg = Image.new("RGBA", (S, S), (0, 0, 0, 0))
px_fg = small.convert("RGBA")
edge = Image.new("L", small.size, 255)
de = ImageDraw.Draw(edge)
inset = 26
de.rectangle([inset, inset, small.size[0] - inset, small.size[1] - inset], fill=255)
de.rectangle([0, 0, small.size[0], small.size[1]], outline=0, width=0)
edge = edge.filter(ImageFilter.GaussianBlur(inset / 1.6))
px_fg.putalpha(edge)
fg.alpha_composite(px_fg, ((S - small.size[0]) // 2, (S - small.size[1]) // 2))
fg.save(os.path.join(DESIGN, "ic_launcher_fg_1024.png"))
bg.convert("RGBA").save(os.path.join(DESIGN, "ic_launcher_bg_1024.png"))

for pxx in (48, 96, 144, 192):
    icon.resize((pxx, pxx), Image.LANCZOS).save(os.path.join(DESIGN, f"preview_{pxx}.png"))

circle = icon.copy()
cm = Image.new("L", (S, S), 0)
ImageDraw.Draw(cm).ellipse([0, 0, S - 1, S - 1], fill=255)
circle.putalpha(cm)
circle.resize((256, 256), Image.LANCZOS).save(os.path.join(DESIGN, "preview_adaptive_circle.png"))
print("done")
