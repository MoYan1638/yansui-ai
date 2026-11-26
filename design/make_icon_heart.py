# -*- coding: utf-8 -*-
"""用 love-companion 的爱心图标做「言岁AI」App 图标：
   - 从灰底抠出爱心（含粉色投影、眼睛、嘴形），按 alpha 合成到品牌深色渐变上
   - 输出整图 + 自适应两层 + 多尺寸预览
运行：python design/make_icon_heart.py
"""
import os
from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DESIGN = os.path.join(ROOT, "design")
SRC = os.path.join(DESIGN, "v2", "love_icon.png")
S = 1024

src = Image.open(SRC).convert("RGB")
bgc = src.getpixel((5, 5))

# ---- 1. 用「与底色差异」做 alpha：爱心内部纯白也会和灰底有差异，边缘得到柔和过渡 ----
diff = ImageChops.difference(src, Image.new("RGB", src.size, bgc)).convert("L")
alpha = diff.point(lambda v: min(255, v * 18))

heart = src.convert("RGBA")
heart.putalpha(alpha)
bbox = alpha.point(lambda v: 255 if v > 30 else 0).getbbox()
heart = heart.crop(bbox)
print("爱心 bbox", bbox)

# ---- 2. 品牌底色：明快粉渐变（上浅下深）+ 爱心后方一层柔和亮光 ----
top, bottom = (247, 128, 150), (222, 74, 110)
grad = Image.new("RGB", (1, S))
gp = grad.load()
for y in range(S):
    t = y / (S - 1)
    gp[0, y] = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
bg = grad.resize((S, S), Image.BILINEAR)

glow_mask = Image.radial_gradient("L").resize((S, S), Image.BILINEAR)  # 中心黑 → 边缘白
glow_mask = glow_mask.point(lambda v: int((255 - v) * 0.30))
glow = Image.new("RGBA", (S, S), (255, 226, 232, 0))
glow.putalpha(glow_mask.filter(ImageFilter.GaussianBlur(70)))
bg = Image.alpha_composite(bg.convert("RGBA"), glow)

# ---- 3. 爱心缩到 62% 居中（自适应图标安全区） ----
target_h = int(S * 0.62)
scale = target_h / heart.height
h = heart.resize((max(1, int(heart.width * scale)), target_h), Image.LANCZOS)
fg = Image.new("RGBA", (S, S), (0, 0, 0, 0))
fg.alpha_composite(h, ((S - h.width) // 2, (S - h.height) // 2))

# ---- 4. 整图 + 圆角遮罩 ----
icon = bg.copy()
icon.alpha_composite(fg)
mask = Image.new("L", (S, S), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.225), fill=255)
icon.putalpha(mask)

icon.save(os.path.join(DESIGN, "icon_1024.png"))
fg.save(os.path.join(DESIGN, "ic_launcher_fg_1024.png"))
bg.save(os.path.join(DESIGN, "ic_launcher_bg_1024.png"))

for px in (48, 96, 144, 192):
    icon.resize((px, px), Image.LANCZOS).save(os.path.join(DESIGN, f"preview_{px}.png"))

circle = bg.copy()
circle.alpha_composite(fg)
cm = Image.new("L", (S, S), 0)
ImageDraw.Draw(cm).ellipse([0, 0, S - 1, S - 1], fill=255)
circle.putalpha(cm)
circle.resize((256, 256), Image.LANCZOS).save(os.path.join(DESIGN, "preview_adaptive_circle.png"))
print("done")
