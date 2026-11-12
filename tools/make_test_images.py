#!/usr/bin/env python3
"""Make test photos for the import check: exif_rot90.jpg (EXIF orientation 6) and big_8000x6000.jpg.

Usage: python3 -I tools/make_test_images.py <output-dir>
Uses Pillow when installed, otherwise ImageMagick (`magick`).
"""
import subprocess
import sys
from pathlib import Path


def with_pillow(out: Path) -> None:
    from PIL import Image, ImageDraw

    def gradient(w, h):
        img = Image.new("RGB", (w, h))
        px = img.load()
        step = max(1, w // 400)
        for x in range(0, w, step):
            for y in range(0, h, step):
                px[x, y] = (x * 255 // w, y * 255 // h, 128)
        return img.resize((w, h)) if step == 1 else _fill(img, w, h, step)

    def _fill(img, w, h, step):
        d = ImageDraw.Draw(img)
        for x in range(0, w, step):
            for y in range(0, h, step):
                d.rectangle([x, y, x + step, y + step], fill=(x * 255 // w, y * 255 // h, 128))
        return img

    # Stored landscape 1600x1200, red block in the stored top-left; orientation 6 means "rotate 90 CW to view",
    # so the upright image is portrait 1200x1600 with the red block at the top-right.
    img = gradient(1600, 1200)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 300, 300], fill=(255, 0, 0))
    exif = Image.Exif()
    exif[0x0112] = 6
    img.save(out / "exif_rot90.jpg", quality=92, exif=exif)

    big = gradient(8000, 6000)
    d = ImageDraw.Draw(big)
    d.rectangle([0, 0, 800, 800], fill=(255, 0, 0))
    big.save(out / "big_8000x6000.jpg", quality=85)


def with_magick(out: Path) -> None:
    def make(name, w, h, extra):
        subprocess.run(
            ["magick", "-size", f"{w}x{h}", "gradient:red-blue", "-fill", "red", "-draw", "rectangle 0,0 300,300",
             *extra, str(out / name)],
            check=True,
        )

    make("exif_rot90.jpg", 1600, 1200, ["-orient", "RightTop", "-quality", "92"])
    make("big_8000x6000.jpg", 8000, 6000, ["-quality", "85"])


def main() -> None:
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("build/test-images")
    out.mkdir(parents=True, exist_ok=True)
    try:
        import PIL  # noqa: F401
        with_pillow(out)
    except ImportError:
        with_magick(out)
    print(f"wrote {out}/exif_rot90.jpg and {out}/big_8000x6000.jpg")


if __name__ == "__main__":
    main()
