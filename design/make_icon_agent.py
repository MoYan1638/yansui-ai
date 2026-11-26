# -*- coding: utf-8 -*-
# 言岁AI 极简图标（Agent 版）：纯色底 + 单一主形状，全图两色
# A: 深墨底 + 白色气泡（内三点打字中）
# B: 深墨底 + 白色四角星（AI sparkle）
# C: 深墨底 + 白色圆点光斑（极简到极致）
import os, math
from PIL import Image, ImageDraw

S = 1024
SS = 2048
DESIGN = os.path.dirname(os.path.abspath(__file__))


def solid(color):
    return Image.new("RGBA", (SS, SS), color + (255,))


def fg_bubble(dots=(24, 25, 36)):
    """圆角气泡 + 三点（对话/打字中），尾巴平滑短小"""
    fg = Image.new("RGBA", (SS, SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(fg)
    L, T, R, B = SS * 0.175, SS * 0.235, SS * 0.825, SS * 0.655
    rad = SS * 0.165
    d.rounded_rectangle([L, T, R, B], radius=rad, fill=(255, 255, 255, 255))
    # 尾巴：短三角 + 尖端小圆，视觉平滑
    d.polygon([(SS * 0.335, SS * 0.615), (SS * 0.345, SS * 0.725), (SS * 0.465, SS * 0.635)],
              fill=(255, 255, 255, 255))
    # 三点
    cy = (T + B) / 2 + SS * 0.004
    r = SS * 0.039
    for x in (0.378, 0.5, 0.622):
        cx = SS * x
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=dots + (255,))
    return fg


def star(cx, cy, a, c=0.19, rot=0.0, curve=0.0):
    """四角星：尖点在 0/90/180/270，腰部内凹；curve>0 时边向内微凹成弧"""
    pts = []
    for k in range(4):
        ang_tip = math.radians(rot + 90 * k)          # 尖点
        ang_waist = math.radians(rot + 90 * k + 45)   # 腰部
        tip = (cx + a * math.cos(ang_tip), cy + a * math.sin(ang_tip))
        waist = (cx + a * c * math.cos(ang_waist), cy + a * c * math.sin(ang_waist))
        # 尖点 → 腰部：直边，可选内凹弧（控制点朝外拉）
        n = 24
        for i in range(n):
            t = i / n
            if curve > 0:
                # 二次贝塞尔：P0=tip, P2=waist, P1 = 中点向外偏移
                mx = (tip[0] + waist[0]) / 2
                my = (tip[1] + waist[1]) / 2
                dx, dy = mx - cx, my - cy
                L = math.hypot(dx, dy) or 1
                ctrl = (mx + dx / L * a * curve, my + dy / L * a * curve)
                x = (1 - t) ** 2 * tip[0] + 2 * (1 - t) * t * ctrl[0] + t ** 2 * waist[0]
                y = (1 - t) ** 2 * tip[1] + 2 * (1 - t) * t * ctrl[1] + t ** 2 * waist[1]
            else:
                x = tip[0] + (waist[0] - tip[0]) * t
                y = tip[1] + (waist[1] - tip[1]) * t
            pts.append((x, y))
    return pts


def fg_sparkle():
    """四角星（AI sparkle）+ 一颗小星"""
    fg = Image.new("RGBA", (SS, SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(fg)
    cx, cy = SS / 2, SS * 0.475
    d.polygon(star(cx, cy, SS * 0.335, c=0.19), fill=(255, 255, 255, 255))
    d.polygon(star(cx + SS * 0.245, cy + SS * 0.235, SS * 0.098, c=0.19),
              fill=(255, 255, 255, 255))
    return fg


def fg_dot():
    """单个大圆点：极简到极致，中心发光点"""
    fg = Image.new("RGBA", (SS, SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(fg)
    r = SS * 0.30
    cx = cy = SS / 2
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 255, 255, 255))
    return fg


def fg_bubble_star():
    """气泡 + 中心小四角星：一句话里藏着 AI"""
    fg = Image.new("RGBA", (SS, SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(fg)
    L, T, R, B = SS * 0.175, SS * 0.235, SS * 0.825, SS * 0.655
    rad = SS * 0.165
    d.rounded_rectangle([L, T, R, B], radius=rad, fill=(255, 255, 255, 255))
    d.polygon([(SS * 0.335, SS * 0.615), (SS * 0.345, SS * 0.725), (SS * 0.465, SS * 0.635)],
              fill=(255, 255, 255, 255))
    cx, cy = SS / 2, (T + B) / 2 + SS * 0.004
    # 小星挖空在气泡内（深色）
    d.polygon(star(cx, cy, SS * 0.098, c=0.19), fill=(24, 25, 36, 255))
    return fg


VARIANTS = {
    "A_bubble":  (solid((24, 25, 36)), fg_bubble()),
    "B_sparkle": (solid((24, 25, 36)), fg_sparkle()),
    "C_bubble_star": (solid((24, 25, 36)), fg_bubble_star()),
}

os.makedirs(os.path.join(DESIGN, "v6"), exist_ok=True)
for name, (bg, fg) in VARIANTS.items():
    canvas = bg.copy()
    canvas.alpha_composite(fg)
    icon = canvas.resize((S, S), Image.LANCZOS)
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.225), fill=255)
    icon.putalpha(mask)
    icon.save(os.path.join(DESIGN, "v6", f"{name}.png"))
    icon.resize((96, 96), Image.LANCZOS).save(os.path.join(DESIGN, "v6", f"{name}_96.png"))
    icon.resize((48, 48), Image.LANCZOS).save(os.path.join(DESIGN, "v6", f"{name}_48.png"))
print("ok")
