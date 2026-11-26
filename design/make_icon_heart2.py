# -*- coding: utf-8 -*-
"""「言岁AI」自研爱心图标（v2）：
   借鉴 love-companion 图标的表达思路（极简白形 + 错位投影 + 有表情的脸），
   形状、比例、表情全部程序自绘：参数方程爱心按包围盒精确放置、纵向略压扁更圆润、
   眼睛+微笑+腮红、微歪头、品牌粉渐变底。2048 超采样 → 1024 输出。
运行：python design/make_icon_heart2.py
"""
import math
import os
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DESIGN = os.path.join(ROOT, "design")
SS = 2048
S = 1024
TILT = -3.5                       # 微歪头（负=向左）

INK = (32, 22, 28)
SHADOW_PINK = (203, 58, 94)
BLUSH = (250, 158, 176)


def raw_heart():
    """参数方程爱心，返回归一化坐标点（x∈[-1,1]，y 上正下负）"""
    pts = []
    for i in range(720):
        t = i * math.pi / 360
        x = 16 * math.sin(t) ** 3
        y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        pts.append((x / 16.0, y / 16.0))
    return pts


BASE = raw_heart()
XS = [p[0] for p in BASE]
YS = [p[1] for p in BASE]
BW, BH = max(XS) - min(XS), max(YS) - min(YS)


def heart_polygon(w, h, cx, cy, squash=0.92):
    """把归一化爱心映射到目标宽高并居中（squash<1 更圆润饱满）"""
    out = []
    for x, y in BASE:
        nx = (x - (max(XS) + min(XS)) / 2) / BW * w
        ny = (y - (max(YS) + min(YS)) / 2) / BH * h * squash
        out.append((cx + nx, cy - ny))
    return out


# ---- 版面：爱心占画布宽 74%，垂直略偏上（投影在右下，视觉重量更平衡） ----
W = SS * 0.72
H = W * 0.90
CX, CY = SS / 2, SS * 0.470

POLY = heart_polygon(W, H, CX, CY)

face = Image.new("RGBA", (SS, SS), (0, 0, 0, 0))
d = ImageDraw.Draw(face)
d.polygon(POLY, fill=(255, 255, 255, 255))

# 表情按爱心包围盒比例摆放
eye_gap = W * 0.115      # 眼睛到中轴的横向距离
eye_y = CY + H * 0.02
eye_r = W * 0.030
for s in (-1, 1):
    d.ellipse([CX + s * eye_gap - eye_r, eye_y - eye_r * 1.10,
               CX + s * eye_gap + eye_r, eye_y + eye_r * 1.10], fill=INK + (255,))

# 微笑：下弧
smile_r = W * 0.042
smile_cy = eye_y + W * 0.062
d.arc([CX - smile_r, smile_cy - smile_r, CX + smile_r, smile_cy + smile_r],
      start=25, end=155, fill=INK + (255,), width=int(W * 0.013))

# 腮红：眼睛斜下方，靠近脸颊内侧
blush_r = W * 0.030
blush_layer = Image.new("RGBA", (SS, SS), (0, 0, 0, 0))
db = ImageDraw.Draw(blush_layer)
for s in (-1, 1):
    bx = CX + s * (eye_gap + W * 0.062)
    by = eye_y + W * 0.038
    db.ellipse([bx - blush_r, by - blush_r * 0.66, bx + blush_r, by + blush_r * 0.66],
               fill=BLUSH + (155,))
blush_layer = blush_layer.filter(ImageFilter.GaussianBlur(3))
face = Image.alpha_composite(face, blush_layer)

# 整体微歪头
face = face.rotate(TILT, resample=Image.BICUBIC, center=(CX, CY))

# ---- 错位投影：同形状深粉，右下方 ----
shadow = Image.new("RGBA", (SS, SS), (0, 0, 0, 0))
ImageDraw.Draw(shadow).polygon(POLY, fill=SHADOW_PINK + (255,))
shadow = shadow.rotate(TILT, resample=Image.BICUBIC, center=(CX, CY))
sx_off, sy_off = int(SS * 0.024), int(SS * 0.028)

# ---- 品牌底：粉渐变 + 中心柔光 ----
top, bottom = (247, 128, 150), (221, 71, 107)
g = Image.new("RGB", (1, SS))
gp = g.load()
for y in range(SS):
    t = y / (SS - 1)
    gp[0, y] = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
bg = g.resize((SS, SS), Image.BILINEAR).convert("RGBA")
glow_mask = Image.radial_gradient("L").resize((SS, SS), Image.BILINEAR).point(
    lambda v: int((255 - v) * 0.26))
glow = Image.new("RGBA", (SS, SS), (255, 228, 234, 0))
glow.putalpha(glow_mask.filter(ImageFilter.GaussianBlur(140)))
bg = Image.alpha_composite(bg, glow)

canvas = bg.copy()
canvas.alpha_composite(shadow, (sx_off, sy_off))
canvas.alpha_composite(face)

icon = canvas.resize((S, S), Image.LANCZOS)
mask = Image.new("L", (S, S), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.225), fill=255)
icon.putalpha(mask)
icon.save(os.path.join(DESIGN, "icon_1024.png"))

fg = Image.new("RGBA", (SS, SS), (0, 0, 0, 0))
fg.alpha_composite(shadow, (sx_off, sy_off))
fg.alpha_composite(face)
fg.resize((S, S), Image.LANCZOS).save(os.path.join(DESIGN, "ic_launcher_fg_1024.png"))
bg.resize((S, S), Image.LANCZOS).save(os.path.join(DESIGN, "ic_launcher_bg_1024.png"))

for px in (48, 96, 144, 192):
    icon.resize((px, px), Image.LANCZOS).save(os.path.join(DESIGN, f"preview_{px}.png"))

circle = canvas.resize((S, S), Image.LANCZOS)
cm = Image.new("L", (S, S), 0)
ImageDraw.Draw(cm).ellipse([0, 0, S - 1, S - 1], fill=255)
circle.putalpha(cm)
circle.resize((256, 256), Image.LANCZOS).save(os.path.join(DESIGN, "preview_adaptive_circle.png"))
print("done")
