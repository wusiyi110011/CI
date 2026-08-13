"""从本地 AI 人物的明亮开启态生成整套 Android 启动图标资源。

原稿是透明 PNG。这里统一按可见主体缩进自适应图标安全圆，再切各密度资源。

跑法：/opt/miniconda3/envs/Codex/bin/python icon/generate.py
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).parent
RES = HERE.parent / "app/src/main/res"
SRC = HERE / "ai_local_light_on.png"

PAPER = "#F7F3EA"          # 与 Color.kt 的 PaperLight 一致
CANVAS = 1024
VISIBLE_RATIO = 72 / 108   # 自适应图标 108dp 画布中一定可见的中心区域
TARGET_RADIUS = 318        # 主体外接圆目标半径，比安全半径 341 再留余量

FOREGROUND_DP = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
LEGACY_PX = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def _rgb(hex_color: str) -> np.ndarray:
    return np.array([int(hex_color[i:i + 2], 16) for i in (1, 3, 5)], dtype=np.float32)


def build_foreground() -> Image.Image:
    """整备成 1024×1024 的 RGBA 前景层：主体居中缩进安全圆，其余透明。"""
    arr = np.asarray(Image.open(SRC).convert("RGBA"))
    solid = arr[..., 3] > 12
    ys, xs = np.nonzero(solid)
    box = (int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1)
    cx, cy = (box[0] + box[2]) / 2, (box[1] + box[3]) / 2
    max_radius = float(np.hypot(xs - cx, ys - cy).max())

    subject = Image.fromarray(arr, mode="RGBA").crop(box)

    scale = TARGET_RADIUS / max_radius
    sized = subject.resize(
        (round(subject.width * scale), round(subject.height * scale)), Image.LANCZOS
    )
    out = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    out.paste(sized, ((CANVAS - sized.width) // 2, (CANVAS - sized.height) // 2))
    print(f"前景层：bbox {box}，最大半径 {max_radius:.1f}px，缩放 {scale:.3f}")
    return out


def build_monochrome(foreground: Image.Image) -> Image.Image:
    """主题图标层：只取深色笔画做剪影，纯色填充由系统着色。

    不能直接拿整个主体当剪影——书堆被压成一坨实心块就没形了；只留描边才有结构。
    """
    arr = np.asarray(foreground).astype(np.float32)
    opaque = arr[..., 3] > 128
    ink = (arr[..., :3].mean(axis=2) < 175) & opaque
    alpha = (ink * 255).astype(np.uint8)
    mono = np.zeros((*alpha.shape, 4), dtype=np.uint8)
    mono[..., 3] = alpha
    return Image.fromarray(mono, mode="RGBA")


def flatten(foreground: Image.Image, size: int, round_mask: bool) -> Image.Image:
    """传统位图图标：取可视区、铺纸白底，round 版再套圆形遮罩。"""
    visible = round(CANVAS * VISIBLE_RATIO)
    inset = (CANVAS - visible) // 2
    crop = foreground.crop((inset, inset, inset + visible, inset + visible)).resize(
        (size, size), Image.LANCZOS
    )
    out = Image.new("RGBA", (size, size), PAPER)
    out.alpha_composite(crop)
    if not round_mask:
        return out.convert("RGB")
    mask = Image.new("L", (size * 4, size * 4), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
    circled = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    circled.paste(out, (0, 0), mask.resize((size, size), Image.LANCZOS))
    return circled


def write_xml(path: Path, body: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(body, encoding="utf-8")


def main() -> None:
    foreground = build_foreground()
    monochrome = build_monochrome(foreground)

    for bucket, px in FOREGROUND_DP.items():
        out_dir = RES / f"mipmap-{bucket}"
        out_dir.mkdir(parents=True, exist_ok=True)
        foreground.resize((px, px), Image.LANCZOS).save(out_dir / "ic_launcher_foreground.png")
        monochrome.resize((px, px), Image.LANCZOS).save(out_dir / "ic_launcher_monochrome.png")

    for bucket, px in LEGACY_PX.items():
        out_dir = RES / f"mipmap-{bucket}"
        flatten(foreground, px, round_mask=False).save(out_dir / "ic_launcher.png")
        flatten(foreground, px, round_mask=True).save(out_dir / "ic_launcher_round.png")

    adaptive = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/ic_launcher_background" />\n'
        '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
        '    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />\n'
        "</adaptive-icon>\n"
    )
    for name in ("ic_launcher", "ic_launcher_round"):
        write_xml(RES / "mipmap-anydpi-v26" / f"{name}.xml", adaptive)

    write_xml(
        RES / "values" / "ic_launcher_background.xml",
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<resources>\n"
        f'    <color name="ic_launcher_background">{PAPER}</color>\n'
        "</resources>\n",
    )

    flatten(foreground, 512, round_mask=False).save(HERE / "play_store_icon.png")
    print(f"资源已写入 {RES}")


if __name__ == "__main__":
    main()
