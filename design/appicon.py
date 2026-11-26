# -*- coding: utf-8 -*-
"""
言岁AI / Yansui —— App 图标生成脚本
意象：对话气泡（言）+ 内部嵌套年轮（岁）
纯 PIL 程序绘制，无外部依赖、不依赖字体，任意尺寸清晰
输出：
  icon_1024.png            整图图标（圆角方形，四角透明）
  ic_launcher_fg_1024.png  自适应图标前景层（透明底）
  ic_launcher_bg_1024.png  自适应图标背景层（渐变）
  preview_*                各密度预览
"""
import os
from PIL import Image, ImageDraw, ImageFilter

OUT = os.path.dirname(os.path.abspath(__file__))
S = 1024

TOP = (40, 48, 74)      # 墨蓝
BOT = (13, 17, 26)      # 近黑
GLOW = (216, 132, 74)   # 暮色琥珀
HILITE = (98, 112, 150)
RING = (19, 24, 36)     # 年轮墨色
ACCENT = (212, 126, 68)  # 年轮芯


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def gradient_layer():
    small = Image.new("RGB", (32, 32))
    d = ImageDraw.Draw(small)
    for y in range(32):
        d.line([(0, y), (32, y)], fill=lerp(TOP, BOT, (y / 31) ** 0.9))
    return small.resize((S, S), Image.BICUBIC)


def radial_layer(color, center, radius, strength, squash=1.0):
    n = 96
    m = Image.new("L", (n, n), 0)
    cx, cy = center[0] / S * n, center[1] / S * n
    rr = radius / S * n
    for i in range(n):
        for j in range(n):
            dx = (j - cx) / max(rr, 1e-6)
            dy = (i - cy) / max(rr * squash, 1e-6)
            dist = (dx * dx + dy * dy) ** 0.5
            if dist < 1:
                m.putpixel((j, i), int(strength * (1 - dist) ** 1.7))
    m = m.resize((S, S), Image.BICUBIC).filter(ImageFilter.GaussianBlur(14))
    return Image.new("RGB", (S, S), color), m


def background():
    bg = gradient_layer()
    for color, center, radius, strength, squash in [
        (GLOW, (512, 975), 720, 54, 0.80),
        (HILITE, (290, 70), 620, 30, 1.0),
    ]:
        layer, mask = radial_layer(color, center, radius, strength, squash)
        bg = Image.composite(layer, bg, mask)
    return bg


def foreground():
    """对话气泡 + 嵌套年轮，透明底"""
    f = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(f)

    L, T, R, B = 214, 250, 810, 660
    rad = 150
    d.rounded_rectangle([L, T, R, B], radius=rad, fill=(255, 255, 255, 255))
    d.polygon([(352, 606), (300, 762), (474, 646)], fill=(255, 255, 255, 255))

    rings = [(RING, 255), (RING, 214), (ACCENT, 255)]
    step, sw = 52, 13
    for i, (col, a) in enumerate(rings, start=1):
        k = step * i
        d.rounded_rectangle([L + k, T + k, R - k, B - k],
                            radius=max(rad - k, 34),
                            outline=col + (a,), width=sw)
    return f


def rounded_mask(radius=224):
    m = Image.new("L", (S, S), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, S - 1, S - 1], radius=radius, fill=255)
    return m


def main():
    bg, fg = background(), foreground()

    icon = bg.convert("RGBA")
    icon.alpha_composite(fg)
    icon.putalpha(rounded_mask())
    icon.save(os.path.join(OUT, "icon_1024.png"))

    bg.save(os.path.join(OUT, "ic_launcher_bg_1024.png"))
    fg.save(os.path.join(OUT, "ic_launcher_fg_1024.png"))

    for name, px in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96),
                     ("xxhdpi", 144), ("xxxhdpi", 192)]:
        icon.resize((px, px), Image.LANCZOS).save(
            os.path.join(OUT, f"preview_{name}_{px}.png"))
    print("done")


if __name__ == "__main__":
    main()
