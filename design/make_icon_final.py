# -*- coding: utf-8 -*-
# 言岁AI 最终图标：深墨底 + 白气泡挖空一颗四角星（「一句话里带着 AI」）
# 输出：icon_1024 / ic_launcher_fg_1024（挖空透明）/ ic_launcher_bg_1024（纯色）
import os, math
from PIL import Image, ImageDraw, ImageChops

S = 1024
SS = 2048
DESIGN = os.path.dirname(os.path.abspath(__file__))
INK = (24, 25, 36)


def star_pts(cx, cy, a, c=0.19, rot=0.0):
    pts = []
    for k in range(4):
        ang_tip = math.radians(rot + 90 * k)
        ang_waist = math.radians(rot + 90 * k + 45)
        tip = (cx + a * math.cos(ang_tip), cy + a * math.sin(ang_tip))
        waist = (cx + a * c * math.cos(ang_waist), cy + a * c * math.sin(ang_waist))
        n = 24
        for i in range(n):
            t = i / n
            pts.append((tip[0] + (waist[0] - tip[0]) * t, tip[1] + (waist[1] - tip[1]) * t))
    return pts


def bubble_layer(star_scale=0.115):
    """白色气泡，中央星形挖空为透明（自适应前景用）；star_scale=0 时为实心白"""
    fg = Image.new("RGBA", (SS, SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(fg)
    L, T, R, B = SS * 0.175, SS * 0.235, SS * 0.825, SS * 0.655
    d.rounded_rectangle([L, T, R, B], radius=SS * 0.165, fill=(255, 255, 255, 255))
    d.polygon([(SS * 0.335, SS * 0.615), (SS * 0.345, SS * 0.725), (SS * 0.465, SS * 0.635)],
              fill=(255, 255, 255, 255))
    if star_scale > 0:
        cx, cy = SS / 2, (T + B) / 2 + SS * 0.004
        m = Image.new("L", (SS, SS), 0)
        ImageDraw.Draw(m).polygon(star_pts(cx, cy, SS * star_scale), fill=255)
        fg.putalpha(ImageChops.subtract(fg.getchannel("A"), m))
    return fg


def round_mask(img, circle=False):
    m = Image.new("L", img.size, 0)
    d = ImageDraw.Draw(m)
    if circle:
        d.ellipse([0, 0, img.size[0] - 1, img.size[1] - 1], fill=255)
    else:
        d.rounded_rectangle([0, 0, img.size[0] - 1, img.size[1] - 1],
                            radius=int(img.size[0] * 0.225), fill=255)
    img.putalpha(m)
    return img


bg = Image.new("RGBA", (SS, SS), INK + (255,))

# 整图（圆角预览图）
icon = bg.copy()
icon.alpha_composite(bubble_layer(star_scale=0.115))
icon = icon.resize((S, S), Image.LANCZOS)
round_mask(icon).save(os.path.join(DESIGN, "icon_1024.png"))

# 自适应两层：前景 = 白气泡（星挖空透明），背景 = 纯色
fg_out = bubble_layer(star_scale=0.115).resize((S, S), Image.LANCZOS)
fg_out.save(os.path.join(DESIGN, "ic_launcher_fg_1024.png"))
bg.resize((S, S), Image.LANCZOS).save(os.path.join(DESIGN, "ic_launcher_bg_1024.png"))

# 预览
for px in (48, 96, 144, 192):
    icon.resize((px, px), Image.LANCZOS).save(os.path.join(DESIGN, f"preview_{px}.png"))
circle = bg.copy()
circle.alpha_composite(bubble_layer(star_scale=0.115))
circle = circle.resize((S, S), Image.LANCZOS)
round_mask(circle, circle=True).resize((256, 256), Image.LANCZOS).save(
    os.path.join(DESIGN, "preview_adaptive_circle.png"))
print("done")
