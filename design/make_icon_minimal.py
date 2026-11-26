# -*- coding: utf-8 -*-
# 言岁AI 极简图标：单一主形状、三色以内、剪影测试通过
# A: 深墨底 + 纯白爱心   B: 粉渐变底 + 纯白爱心   C: 奶白底 + 单色粉爱心
import os, math
from PIL import Image, ImageDraw, ImageFilter

S = 1024          # 输出尺寸
SS = 2048         # 绘制尺寸（4x 超采样）
DESIGN = os.path.dirname(os.path.abspath(__file__))


def heart_path(cx, cy, w, h):
    """参数方程心形，返回多边形点集"""
    pts = []
    for i in range(360):
        t = math.radians(i)
        x = 16 * math.sin(t) ** 3
        y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        pts.append((cx + x * w / 32.0, cy - y * h / 32.0))
    return pts


def gradient(top, bottom):
    g = Image.new("RGB", (1, SS))
    p = g.load()
    for y in range(SS):
        t = y / (SS - 1)
        p[0, y] = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
    return g.resize((SS, SS), Image.BILINEAR).convert("RGBA")


def compose(bg, heart_color, heart_alpha=255, heart_scale=0.66, cy=0.465):
    fg = Image.new("RGBA", (SS, SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(fg)
    w = SS * heart_scale
    pts = heart_path(SS / 2, SS * cy, w, w * 0.94)
    d.polygon(pts, fill=heart_color + (heart_alpha,))
    return fg


def solid(color):
    return Image.new("RGBA", (SS, SS), color + (255,))

VARIANTS = {
    # 极简：底 / 主形，两色到位
    "A_dark":  (solid((24, 25, 36)), (250, 248, 246), 0.66),
    "B_pink":  (solid((242, 137, 166)), (253, 244, 247), 0.66),
    "C_cream": (solid((252, 238, 242)), (236, 106, 142), 0.66),
}

os.makedirs(os.path.join(DESIGN, "v5"), exist_ok=True)
for name, (bg, color, scale) in VARIANTS.items():
    canvas = bg.copy()
    canvas.alpha_composite(compose(bg, color, heart_scale=scale))
    icon = canvas.resize((S, S), Image.LANCZOS)
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.225), fill=255)
    icon.putalpha(mask)
    icon.save(os.path.join(DESIGN, "v5", f"{name}.png"))
    icon.resize((96, 96), Image.LANCZOS).save(os.path.join(DESIGN, "v5", f"{name}_96.png"))
    icon.resize((48, 48), Image.LANCZOS).save(os.path.join(DESIGN, "v5", f"{name}_48.png"))
print("ok")
