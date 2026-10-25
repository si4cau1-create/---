#!/usr/bin/env python3
"""生成卡点 APK 启动图标。
- 传统图标：由现有 icon-512.png 缩放出各密度（API 24-25 使用）
- 自适应前景层：蓝底白勾（透明背景白图形），API 26+ 使用
"""
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_512 = os.path.join(ROOT, '..', 'kadian', 'assets', 'icons', 'icon-512.png')
RES = os.path.join(ROOT, 'app', 'src', 'main', 'res')

# (mipmap 目录, 传统图标尺寸, 前景层尺寸) —— 前景层 108dp 对应各密度
DENSITIES = [
    ('mdpi', 48, 108),
    ('hdpi', 72, 162),
    ('xhdpi', 96, 216),
    ('xxhdpi', 144, 324),
    ('xxxhdpi', 192, 432),
]

BLUE = (0x37, 0x8A, 0xDD, 255)
WHITE = (255, 255, 255, 255)


def legacy_icons(src: Image.Image) -> None:
    for name, size, _ in DENSITIES:
        d = os.path.join(RES, 'mipmap-' + name)
        os.makedirs(d, exist_ok=True)
        src.resize((size, size), Image.LANCZOS).save(os.path.join(d, 'ic_launcher.png'))


def foreground_icon(size: int) -> Image.Image:
    """透明背景 + 白色「卡点」勾形标记。图形占画布约 60%，留在 72dp 安全区内。"""
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    s = size / 100.0  # 以 100 为坐标系
    lw = max(2, int(5 * s))          # 线宽
    r = 4.6 * s                      # 圆点半径

    # 勾形：一段弧 + 短竖（与 favicon 的 path 对应）
    dr.line([(20 * s, 40 * s), (20 * s, 52 * s)], fill=WHITE, width=lw)
    dr.line([(20 * s, 52 * s), (30 * s, 52 * s)], fill=WHITE, width=lw)
    dr.arc([20 * s, 26 * s, 48 * s, 54 * s], start=290, end=80, fill=WHITE, width=lw)
    # 圆点
    cx, cy = 42 * s, 42 * s
    dr.ellipse([cx - r, cy - r, cx + r, cy + r], fill=WHITE)
    return img


def main() -> None:
    src = Image.open(SRC_512).convert('RGBA')
    legacy_icons(src)
    for name, _, fg in DENSITIES:
        d = os.path.join(RES, 'mipmap-' + name)
        os.makedirs(d, exist_ok=True)
        foreground_icon(fg).save(os.path.join(d, 'ic_launcher_foreground.png'))
    print('icons generated:', ', '.join(n for n, _, _ in DENSITIES))


if __name__ == '__main__':
    main()
