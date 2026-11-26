import math
from PIL import Image, ImageDraw

SIZE = 1024
img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
draw = ImageDraw.Draw(img)

# 背景：圆角矩形 + 紫色渐变
radius = int(SIZE * 0.22)
grad = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
gd = ImageDraw.Draw(grad)
c1 = (108, 92, 231)    # #6c5ce7
c2 = (162, 155, 254)   # #a29bfe
for y in range(SIZE):
    t = y / SIZE
    r = int(c1[0] + (c2[0] - c1[0]) * t)
    g = int(c1[1] + (c2[1] - c1[1]) * t)
    b = int(c1[2] + (c2[2] - c1[2]) * t)
    gd.line([(0, y), (SIZE, y)], fill=(r, g, b, 255))

mask = Image.new("L", (SIZE, SIZE), 0)
md = ImageDraw.Draw(mask)
md.rounded_rectangle([0, 0, SIZE - 1, SIZE - 1], radius=radius, fill=255)
img.paste(grad, (0, 0), mask)

# 声波：5 根圆头竖条
draw = ImageDraw.Draw(img)
bar_color = (255, 255, 255, 240)
heights = [0.22, 0.44, 0.62, 0.40, 0.24]
bar_w = int(SIZE * 0.072)
gap = int(SIZE * 0.048)
total_w = len(heights) * bar_w + (len(heights) - 1) * gap
start_x = (SIZE - total_w) // 2
cy = SIZE // 2

for i, h in enumerate(heights):
    bh = int(SIZE * h)
    x0 = start_x + i * (bar_w + gap)
    y0 = cy - bh // 2
    draw.rounded_rectangle([x0, y0, x0 + bar_w, y0 + bh], radius=bar_w // 2, fill=bar_color)

img.save("icon-base.png")

# 生成各尺寸
sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}
import os
base = "apk/platforms/android/app/src/main/res"
for folder, size in sizes.items():
    d = os.path.join(base, folder)
    os.makedirs(d, exist_ok=True)
    resized = img.resize((size, size), Image.LANCZOS)
    resized.save(os.path.join(d, "ic_launcher.png"))
    # 圆形图标
    circle = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    cm = Image.new("L", (size, size), 0)
    cd = ImageDraw.Draw(cm)
    cd.ellipse([0, 0, size - 1, size - 1], fill=255)
    circle.paste(resized, (0, 0), cm)
    circle.save(os.path.join(d, "ic_launcher_round.png"))
    # 前景（自适应图标用，留安全边距）
    fg_size = int(size * 1.0)
    fg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    inner = img.resize((int(size * 0.62), int(size * 0.62)), Image.LANCZOS)
    off = (size - inner.width) // 2
    fg.paste(inner, (off, off), inner)
    fg.save(os.path.join(d, "ic_launcher_foreground.png"))

print("icons generated")
