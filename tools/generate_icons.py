#!/usr/bin/env python3
"""生成启动图标（柔光拟态风格）。

为什么用脚本而不是直接放一张图：
- 自适应图标要 108dp 的**前景**（透明底，只画浮雕元素）与**背景**两层，
  前景需要 5 个密度档，手改一张图很容易漏档、尺寸对不上；
- 配色与浮雕参数（光源方向、阴影强度、倒角半径）都在这里，改一个数就能整体重出。

设计约束（来自需求）：
- 单一左上光源，体积全部由光塑造，不描边、不分割线；
- 低饱和近单色，只有指针用一点陶土色；
- 缩到 48px 仍靠轮廓可辨：表盘 + 顶部表冠 + 一根指针。

用法：python3 tools/generate_icons.py
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"

# 画布按 108dp 设计；K 为超采样倍率，最后缩到各密度。
DP = 108.0
K = 8
S = int(DP * K)

# ---- 配色（「暖沙」浅色系：暖灰底材 + 陶土点缀） ----
TILE_LIGHT = (230, 221, 207)
TILE_DARK = (208, 195, 174)
DIAL_LIGHT = (251, 246, 237)
DIAL_DARK = (228, 215, 195)
FACE = (236, 227, 212)
SHADOW_DARK = (191, 174, 149)
SHADOW_LIGHT = (255, 253, 248)
ACCENT = (150, 69, 31)

# ---- 几何（dp） ----
DIAL_CX, DIAL_CY, DIAL_R = 54.0, 56.5, 25.0
CROWN_BOX = (49.2, 25.6, 58.8, 34.2)
CROWN_R = 3.6
# 指针带一小截尾巴，比"一根短棍"更像仪表的针。
HAND_TAIL = (52.5, 59.5)
HAND_TO = (62.0, 44.4)
HAND_W = 3.2
PIVOT_R = 2.3


def px(value: float) -> float:
    return value * K


def circle_mask(cx: float, cy: float, r: float) -> Image.Image:
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).ellipse(
        [px(cx - r), px(cy - r), px(cx + r), px(cy + r)],
        fill=255,
    )
    return mask


def round_rect_mask(box: tuple[float, float, float, float], radius: float) -> Image.Image:
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [px(box[0]), px(box[1]), px(box[2]), px(box[3])],
        radius=px(radius),
        fill=255,
    )
    return mask


def hand_mask() -> Image.Image:
    mask = Image.new("L", (S, S), 0)
    draw = ImageDraw.Draw(mask)
    draw.line(
        [px(HAND_TAIL[0]), px(HAND_TAIL[1]), px(HAND_TO[0]), px(HAND_TO[1])],
        fill=255,
        width=int(px(HAND_W)),
    )
    # 线端是平的，用两个圆把端点补成圆角。
    for x, y in (HAND_TAIL, HAND_TO):
        r = px(HAND_W) / 2
        draw.ellipse([px(x) - r, px(y) - r, px(x) + r, px(y) + r], fill=255)
    return mask


def offset_mask(mask: Image.Image, dx: float, dy: float) -> Image.Image:
    """平移蒙版；不用 ImageChops.offset，因为它会从另一侧卷回来。"""
    out = Image.new("L", mask.size, 0)
    out.paste(mask, (int(round(dx)), int(round(dy))))
    return out


def colorize(mask: Image.Image, color: tuple[int, int, int], alpha: float, blur: float) -> Image.Image:
    layer = mask
    if blur > 0:
        layer = layer.filter(ImageFilter.GaussianBlur(blur))
    if alpha < 1.0:
        layer = layer.point(lambda v: int(v * alpha))
    out = Image.new("RGBA", mask.size, color)
    out.putalpha(layer)
    return out


def radial_fill(
    cx: float,
    cy: float,
    falloff: float,
    inner,
    outer,
    cover: float,
) -> Image.Image:
    """以 (cx,cy) 为亮点、向外的柔和渐变。

    [cover] 必须大于「亮点到形状最远角」的距离，否则渐变方块会小于形状，
    贴上去之后形状的边会被切成直角（这个坑踩过一次）。
    [falloff] 是明暗过渡走完的半径，与 [cover] 分开，避免为了覆盖而把渐变压平。
    """
    size = int(round(px(cover) * 2))
    ramp = Image.radial_gradient("L").resize((size, size), Image.LANCZOS)
    gain = cover / falloff
    ramp = ramp.point(lambda v: min(255, int(v * gain)))
    blended = Image.composite(
        Image.new("RGBA", (size, size), outer),
        Image.new("RGBA", (size, size), inner),
        ramp,
    )
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(blended, (int(round(px(cx) - size / 2)), int(round(px(cy) - size / 2))))
    return out


def tile_gradient() -> Image.Image:
    """整块底材：左上亮、右下暗。

    用「横向斜坡 + 纵向斜坡取平均」得到对角渐变——直接旋转一张方形渐变图
    会在角上带进黑色填充，出现一道斜向硬边。
    """
    vertical = Image.linear_gradient("L").resize((S, S), Image.LANCZOS)
    horizontal = vertical.transpose(Image.ROTATE_90)
    diagonal = ImageChops.add(vertical, horizontal, scale=2.0)
    return Image.composite(
        Image.new("RGBA", (S, S), TILE_DARK),
        Image.new("RGBA", (S, S), TILE_LIGHT),
        diagonal,
    ).convert("RGBA")


def inner_rim(mask: Image.Image, dx: float, dy: float, blur: float) -> Image.Image:
    """形状内侧的窄边（倒角）：只留边缘一圈，再糊开。"""
    rim = ImageChops.subtract(mask, offset_mask(mask, px(dx), px(dy)))
    rim = rim.filter(ImageFilter.GaussianBlur(px(blur)))
    return ImageChops.multiply(rim, mask)


def raised(mask: Image.Image, fill: Image.Image) -> Image.Image:
    """浮雕：右下投影 + 面色 + 左上亮边 + 右下暗边（内倒角，不往外发光）。"""
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    layer = Image.alpha_composite(
        layer,
        colorize(offset_mask(mask, px(0.9), px(2.4)), SHADOW_DARK, 0.42, px(3.4)),
    )

    body = fill.copy()
    body.putalpha(ImageChops.multiply(body.getchannel("A"), mask))
    layer = Image.alpha_composite(layer, body)

    layer = Image.alpha_composite(
        layer,
        colorize(inner_rim(mask, 1.5, 1.9, 1.5), SHADOW_LIGHT, 0.75, 0),
    )
    layer = Image.alpha_composite(
        layer,
        colorize(inner_rim(mask, -1.5, -1.9, 1.7), SHADOW_DARK, 0.28, 0),
    )
    return layer


def recessed(mask: Image.Image, fill_color) -> Image.Image:
    """凹陷：面略暗，左上内阴影 + 右下内高光（光源仍在左上）。"""
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    body = Image.new("RGBA", (S, S), fill_color)
    body.putalpha(mask)
    layer = Image.alpha_composite(layer, body)

    layer = Image.alpha_composite(
        layer,
        colorize(inner_rim(mask, 1.7, 2.1, 2.0), SHADOW_DARK, 0.55, 0),
    )
    layer = Image.alpha_composite(
        layer,
        colorize(inner_rim(mask, -1.7, -2.1, 2.2), SHADOW_LIGHT, 0.55, 0),
    )
    return layer


def render_foreground() -> Image.Image:
    """前景：透明底，只画浮雕元素；元素都落在自适应图标的安全区内。"""
    canvas = Image.new("RGBA", (S, S), (0, 0, 0, 0))

    # 表冠先画，表盘压住它的下沿，看起来是同一块材料上压出来的凸起。
    crown = round_rect_mask(CROWN_BOX, CROWN_R)
    canvas = Image.alpha_composite(
        canvas,
        raised(crown, radial_fill(52.0, 27.0, 9.0, (247, 241, 231), (229, 218, 200), cover=13.0)),
    )

    dial = circle_mask(DIAL_CX, DIAL_CY, DIAL_R)
    canvas = Image.alpha_composite(
        canvas,
        raised(dial, radial_fill(45.5, 46.0, 30.0, DIAL_LIGHT, DIAL_DARK, cover=41.0)),
    )

    # 表冠压在表盘上：补一层接触阴影，强调"同一块材料压出来"。
    contact = ImageChops.multiply(
        offset_mask(crown, 0.0, px(1.8)).filter(ImageFilter.GaussianBlur(px(2.0))),
        dial,
    )
    canvas = Image.alpha_composite(canvas, colorize(contact, SHADOW_DARK, 0.45, 0))

    hand = hand_mask()
    canvas = Image.alpha_composite(
        canvas,
        colorize(offset_mask(hand, px(0.4), px(1.0)), SHADOW_DARK, 0.28, px(0.9)),
    )
    canvas = Image.alpha_composite(canvas, colorize(hand, ACCENT, 0.93, px(0.4)))

    # 中心轴帽：压住针的根部，让针看起来是穿在轴上而不是贴上去的。
    pivot = circle_mask(DIAL_CX, DIAL_CY, PIVOT_R)
    canvas = Image.alpha_composite(canvas, raised(pivot, radial_fill(DIAL_CX - 0.8, DIAL_CY - 1.0, 3.2, DIAL_LIGHT, DIAL_DARK, cover=5.0)))
    return canvas


def render_legacy(circular: bool = False) -> Image.Image:
    """非自适应场景：带底材的完整图标。

    [circular] 用于 `ic_launcher_round`：部分启动器会直接显示这张图，
    lint 的 IconLauncherShape 也会检查它必须是圆的。
    """
    mask = (
        circle_mask(DP / 2, DP / 2, DP / 2)
        if circular
        else round_rect_mask((0.0, 0.0, DP, DP), 24.0)
    )
    canvas = tile_gradient()
    canvas.putalpha(mask)
    canvas = Image.alpha_composite(canvas, render_foreground())
    return canvas


def render_brand() -> Image.Image:
    """关于页的品牌位：只取浮雕主体（不含底材），裁到内容边界后留一点边距。

    不带底材是有意的：它要贴在应用内的卡片上，再套一层底材会变成"框里套框"；
    顺带也避免了与启动图标内容完全相同（lint 的 IconDuplicates 会拦）。
    """
    foreground = render_foreground()
    bbox = foreground.getbbox()
    if bbox is None:
        return foreground
    cropped = foreground.crop(bbox)
    side = int(max(cropped.size) * 1.08)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(
        cropped,
        ((side - cropped.size[0]) // 2, (side - cropped.size[1]) // 2),
        cropped,
    )
    return canvas


def save(image: Image.Image, path: Path, size: int) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.resize((size, size), Image.LANCZOS).save(path, "PNG", optimize=True)


def main() -> None:
    foreground = render_foreground()
    legacy = render_legacy()
    legacy_round = render_legacy(circular=True)

    for bucket, size in (
        ("mdpi", 108),
        ("hdpi", 162),
        ("xhdpi", 216),
        ("xxhdpi", 324),
        ("xxxhdpi", 432),
    ):
        save(foreground, RES / f"drawable-{bucket}/ic_launcher_foreground.png", size)

    for bucket, size in (
        ("mdpi", 48),
        ("hdpi", 72),
        ("xhdpi", 96),
        ("xxhdpi", 144),
        ("xxxhdpi", 192),
    ):
        save(legacy, RES / f"mipmap-{bucket}/ic_launcher.png", size)
        save(legacy_round, RES / f"mipmap-{bucket}/ic_launcher_round.png", size)

    # 关于页的品牌位（自适应图标不能直接当位图加载，单独出一张）。
    # 放 nodpi：界面里按 64dp 固定尺寸绘制，不需要按密度各存一份；
    # 放在 drawable-xxxhdpi 会被 lint 的 IconDensities / IconDuplicates 拦下。
    save(render_brand(), RES / "drawable-nodpi/ic_brand.png", 192)

    # 预览大图（不参与打包，便于人工检查）
    save(legacy, ROOT / "build/icon-preview/icon-512.png", 512)
    save(legacy, ROOT / "build/icon-preview/icon-96.png", 96)
    save(foreground, ROOT / "build/icon-preview/foreground-432.png", 432)
    print("icons written")


if __name__ == "__main__":
    main()
