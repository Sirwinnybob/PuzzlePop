#!/usr/bin/env python3
"""Builds the Google Play listing art from raw device screenshots.

Usage: python3 -I tools/store_art.py <raw-dir> <out-dir>

<raw-dir> holds PNG screenshots straight from `adb exec-out screencap -p`, named as in CAPTIONS below
(phone_*.png from a phone held sideways, tablet_*.png from a tablet in landscape). Missing ones are skipped.
Writes to <out-dir>:
  feature-graphic.png   1024x500, the banner at the top of the listing
  phone-N.png           1920x1080 framed phone screenshots with a caption
  tablet-N.png          2560x1440 framed tablet screenshots with a caption
The icon (icon-512.png) is rendered separately from tools/launcher-icon/preview.svg; see play-store/README.md.
Needs Pillow and the Open Sans fonts (Fedora: open-sans-fonts).
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

FONT_BOLD = "/usr/share/fonts/open-sans/OpenSans-ExtraBold.ttf"
FONT_SEMI = "/usr/share/fonts/open-sans/OpenSans-Semibold.ttf"
SKY_TOP, SKY_BOTTOM = (203, 234, 255), (124, 200, 255)  # the launcher icon's background
INK = (46, 31, 102)  # deep grape, readable on the sky

# (raw file, caption) in listing order.
CAPTIONS = {
    "phone": [
        ("phone_play.png", "Turn your own photos into jigsaw puzzles"),
        ("phone_library.png", "Pick up right where you left off"),
        ("phone_finish.png", "A big celebration for every finished puzzle"),
        ("phone_kid_home.png", "Kid mode: big pictures and nothing else to tap"),
        ("phone_kid_sizes.png", "Three easy sizes for little hands"),
    ],
    "tablet": [
        ("tablet_play.png", "Turn your own photos into jigsaw puzzles"),
        ("tablet_sheet.png", "10 to 1000 pieces, eight piece shapes"),
        ("tablet_library.png", "Pick up right where you left off"),
        ("tablet_finish.png", "A big celebration for every finished puzzle"),
        ("tablet_kid_home.png", "Kid mode: big pictures and nothing else to tap"),
        ("tablet_settings.png", "Play-time limits, behind a grown-up PIN"),
    ],
}
CANVAS = {"phone": (1920, 1080), "tablet": (2560, 1440)}  # Play wants 16:9 for tablet slots


def sky(size):
    """A vertical gradient in the launcher icon's colours."""
    w, h = size
    img = Image.new("RGB", size)
    px = img.load()
    for y in range(h):
        t = y / max(1, h - 1)
        c = tuple(round(a + (b - a) * t) for a, b in zip(SKY_TOP, SKY_BOTTOM))
        for x in range(w):
            px[x, y] = c
    return img


def rounded(img, radius):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, img.width - 1, img.height - 1), radius, fill=255)
    out = img.convert("RGBA")
    out.putalpha(mask)
    return out


def paste_with_shadow(canvas, shot, xy, radius):
    """Pastes [shot] with rounded corners and a soft drop shadow."""
    pad = 100  # room for the blur, so the shadow fades out instead of ending in a hard edge
    shadow = Image.new("RGBA", (shot.width + 2 * pad, shot.height + 2 * pad), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((pad, pad + 10, pad + shot.width, pad + 10 + shot.height), radius, fill=(20, 30, 60, 110))
    shadow = shadow.filter(ImageFilter.GaussianBlur(22))
    canvas.alpha_composite(shadow, (xy[0] - pad, xy[1] - pad))
    canvas.alpha_composite(rounded(shot, radius), xy)


def status_bar_height(img):
    """The system status bar at the top of a landscape screenshot; it is cut off."""
    return round(img.height * 0.085) if is_phone(img) else round(img.height * 0.045)


def is_phone(img):
    return img.height < img.width * 0.5


def hide_samsung_overlays(img):
    """Inpaints the floating S Pen button (left edge) and the edge-panel handle (right edge) that Samsung
    tablets draw over every app. Positions are for a 2800x1752 Tab S7+/S8+ screenshot, scaled to others.
    Needs OpenCV (python3-opencv); without it the overlays are left as they are."""
    try:
        import cv2
        import numpy as np
    except ImportError:
        return img
    w, h = img.size
    sx, sy = w / 2800, h / 1752
    mask = np.zeros((h, w), np.uint8)
    cv2.circle(mask, (round(30 * sx), round(355 * sy)), round(46 * sx), 255, -1)
    cv2.rectangle(mask, (round(2780 * sx), round(236 * sy)), (w - 1, round(422 * sy)), 255, -1)
    fixed = cv2.inpaint(np.array(img)[:, :, ::-1], mask, 7, cv2.INPAINT_TELEA)
    return Image.fromarray(fixed[:, :, ::-1])


def bottom_cut(img):
    """Samsung tablets show a taskbar of app icons along the bottom; that strip is cut off too."""
    return 0 if is_phone(img) else round(img.height * 0.07)


def frame(raw_path, caption, size):
    w, h = size
    canvas = sky(size).convert("RGBA")
    shot = Image.open(raw_path).convert("RGB")
    if not is_phone(shot):
        shot = hide_samsung_overlays(shot)
    shot = shot.crop((0, status_bar_height(shot), shot.width, shot.height - bottom_cut(shot)))
    caption_h = round(h * 0.2)
    margin = round(w * 0.045)
    max_w, max_h = w - 2 * margin, h - caption_h - margin
    scale = min(max_w / shot.width, max_h / shot.height)
    shot = shot.resize((round(shot.width * scale), round(shot.height * scale)), Image.LANCZOS)
    paste_with_shadow(canvas, shot, ((w - shot.width) // 2, caption_h), round(h * 0.03))
    draw = ImageDraw.Draw(canvas)
    size_px = round(h * 0.07)
    font = ImageFont.truetype(FONT_BOLD, size_px)
    while draw.textlength(caption, font=font) > w * 0.92:  # long captions shrink to fit
        size_px -= 4
        font = ImageFont.truetype(FONT_BOLD, size_px)
    tw = draw.textlength(caption, font=font)
    draw.text(((w - tw) / 2, caption_h * 0.5), caption, font=font, fill=INK, anchor="lm")
    return canvas.convert("RGB")


def feature_graphic(icon_path, play_shot, out_path):
    w, h = 1024, 500
    canvas = sky((w, h)).convert("RGBA")
    if play_shot and os.path.exists(play_shot):
        shot = Image.open(play_shot).convert("RGB")
        shot = shot.crop((0, status_bar_height(shot), shot.width, shot.height))
        scale = 300 / shot.height
        shot = shot.resize((round(shot.width * scale), 300), Image.LANCZOS)
        # Bleeds off the right edge, tilted a little, like a photo on a table.
        tilted = rounded(shot, 22).rotate(-6, expand=True, resample=Image.BICUBIC)
        canvas.alpha_composite(tilted, (560, 70))
    icon = Image.open(icon_path).convert("RGBA").resize((150, 150), Image.LANCZOS)
    canvas.alpha_composite(rounded(icon, 34), (60, 70))
    draw = ImageDraw.Draw(canvas)
    draw.text((60, 250), "PuzzlePop", font=ImageFont.truetype(FONT_BOLD, 84), fill=INK, anchor="lm")
    tag = ImageFont.truetype(FONT_SEMI, 32)
    draw.text((62, 330), "Jigsaw puzzles from", font=tag, fill=INK, anchor="lm")
    draw.text((62, 372), "your own photos", font=tag, fill=INK, anchor="lm")
    canvas.convert("RGB").save(out_path)


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    raw, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    for kind, items in CAPTIONS.items():
        n = 0
        for name, caption in items:
            path = os.path.join(raw, name)
            if not os.path.exists(path):
                continue
            n += 1
            frame(path, caption, CANVAS[kind]).save(os.path.join(out, f"{kind}-{n}.png"))
            print(f"{kind}-{n}.png  <- {name}")
    icon = os.path.join(out, "icon-512.png")
    if os.path.exists(icon):
        feature_graphic(icon, os.path.join(raw, "phone_play.png"), os.path.join(out, "feature-graphic.png"))
        print("feature-graphic.png")


if __name__ == "__main__":
    main()
