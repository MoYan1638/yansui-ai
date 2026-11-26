# -*- coding: utf-8 -*-
"""把 AI 生图的章鱼图标转成 Android 资源三层（纯 PIL，无 numpy 依赖）：
   1) icon_1024.png            整图 = 去水印后的原图（保留原生的光晕质感）+ 圆角遮罩
   2) ic_launcher_fg_1024.png  前景层（白色章鱼，透明底，缩进安全区）
   3) ic_launcher_bg_1024.png  背景层（取章鱼两侧的干净渐变条带，插值重建）
运行：python design/make_icon_v2.py
"""
import os
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DESIGN = os.path.join(ROOT, "design")
SRC = os.path.join(DESIGN, "v2", "Minimalist_Android_app_icon__r_2026-11-26T22-26-33.png")
S = 1024
SAFE_TOP, SAFE_BOTTOM = 25, 1015   # 避开圆角白边的采样行范围

im = Image.open(SRC).convert("RGB")

# ---- 1. 去掉右下角 AI 水印：镜像补丁 + 羽化边缘，避免出现补丁接缝 ----
box = (860, 900, S, S)
w = box[2] - box[0]
patch = im.crop((box[0] - w - 10, box[1], box[0] - 10, box[3])).transpose(Image.FLIP_LEFT_RIGHT)
feather = Image.new("L", (w, S - box[1]), 0)
ImageDraw.Draw(feather).rectangle([18, 18, w - 1, S - box[1] - 1], fill=255)
feather = feather.filter(ImageFilter.GaussianBlur(16))
im.paste(patch, (box[0], box[1]), feather)

# ---- 2. 抠出白色章鱼（只取中间区域的亮像素，阈值取高避免带上阴影）----
gray = im.convert("L")
mask_img = gray.point(lambda v: 255 if v > 210 else 0)
keep = Image.new("L", (S, S), 0)
ImageDraw.Draw(keep).rectangle([170, 140, S - 170, S - 140], fill=255)
mask_img = Image.composite(mask_img, Image.new("L", (S, S), 0), keep)
mask_img = mask_img.filter(ImageFilter.MinFilter(5)).filter(ImageFilter.MaxFilter(5))  # 内缩再补回，去掉抗锯齿黑边
bbox = mask_img.getbbox()
print("章鱼 bbox", bbox)

# 前景用纯白实心 + 外发光，避免把原图的暗边带进来
octo_mask = mask_img.crop(bbox)
glow = octo_mask.filter(ImageFilter.GaussianBlur(22)).point(lambda v: int(v * 0.45))
octo = Image.new("RGBA", octo_mask.size, (0, 0, 0, 0))
glow_layer = Image.new("RGBA", octo_mask.size, (255, 255, 255, 0))
glow_layer.putalpha(glow)
octo.alpha_composite(glow_layer)
body = Image.new("RGBA", octo_mask.size, (253, 253, 255, 255))
body.putalpha(octo_mask)
octo.alpha_composite(body)

target_h = int(S * 0.64)
scale = target_h / octo.height
octo = octo.resize((max(1, int(octo.width * scale)), target_h), Image.LANCZOS)
fg = Image.new("RGBA", (S, S), (0, 0, 0, 0))
fg.alpha_composite(octo, ((S - octo.width) // 2, (S - octo.height) // 2 - int(S * 0.02)))

# ---- 3. 背景：章鱼左右两侧的干净条带，逐行取均值后水平插值 ----
def strip(x0, x1):
    col = im.crop((x0, 0, x1, S)).resize((1, S), Image.BOX)
    px = col.load()
    top = px[0, SAFE_TOP]
    bottom = px[0, SAFE_BOTTOM]
    for y in range(S):
        if y < SAFE_TOP:
            col.putpixel((0, y), top)
        elif y > SAFE_BOTTOM:
            col.putpixel((0, y), bottom)
    return col.resize((S, S), Image.NEAREST)

left_col = strip(150, 226)
right_col = strip(798, 874)
hgrad = Image.linear_gradient("L").rotate(-90, expand=True).resize((S, S), Image.BILINEAR)
bg_img = Image.composite(right_col, left_col, hgrad).filter(ImageFilter.GaussianBlur(2))

# ---- 4. 整图：原始画作（含水印已清）+ 圆角遮罩，保留光晕质感 ----
mask_r = Image.new("L", (S, S), 0)
ImageDraw.Draw(mask_r).rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.225), fill=255)
icon = im.convert("RGBA")
icon.putalpha(mask_r)

icon.save(os.path.join(DESIGN, "icon_1024.png"))
fg.save(os.path.join(DESIGN, "ic_launcher_fg_1024.png"))
bg_img.convert("RGBA").save(os.path.join(DESIGN, "ic_launcher_bg_1024.png"))
for px in (48, 96, 144, 192):
    icon.resize((px, px), Image.LANCZOS).save(os.path.join(DESIGN, f"preview_{px}.png"))
# 自适应图标预览：前景缩到 66%（系统会把前景按 108dp 裁成各种形状）
adapt = bg_img.convert("RGBA")
adapt.alpha_composite(fg, (0, 0))
m = Image.new("L", (S, S), 0)
ImageDraw.Draw(m).ellipse([0, 0, S - 1, S - 1], fill=255)
adapt.putalpha(m)
adapt.resize((256, 256), Image.LANCZOS).save(os.path.join(DESIGN, "preview_adaptive_circle.png"))
print("done")
