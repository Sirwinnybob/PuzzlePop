#!/usr/bin/env python3
"""Render every app/src/main/res/drawable/ic_*.xml onto one contact-sheet PNG.

Converts the Android vector drawables to SVG (resolving @color/... from
res/values/icon_colors.xml, and res/values-night/icon_colors.xml for the dark
tiles), rasterises them with ImageMagick and lays them out in a grid.
Run through tools/icon_sheet.sh (python3 -I).
"""
import os
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
RES = os.path.join(ROOT, "app/src/main/res")
SCALE = 3  # 48 dp at xxhdpi-ish density


def load_colors(*files):
    colors = {}
    for f in files:
        if os.path.exists(f):
            for el in ET.parse(f).getroot().iter("color"):
                colors[el.get("name")] = el.text.strip()
    return colors


def resolve(value, colors):
    if value is None:
        return None, 1.0
    if value.startswith("@color/"):
        value = colors[value[7:]]
    if value.startswith("#"):
        h = value[1:]
        if len(h) == 8:  # AARRGGBB
            a = int(h[:2], 16) / 255.0
            return "#" + h[2:], a
        if len(h) == 6:
            return value, 1.0
        if len(h) == 4:  # ARGB
            a = int(h[0], 16) / 15.0
            return "#" + h[1:], a
        if len(h) == 3:
            return value, 1.0
    raise ValueError("unsupported colour " + value)


def f(el, name, default=None):
    v = el.get(ANDROID + name)
    return default if v is None else v


def vector_to_svg(path, colors, px):
    root = ET.parse(path).getroot()
    vw = float(f(root, "viewportWidth"))
    vh = float(f(root, "viewportHeight"))
    defs, body = [], []
    counter = [0]

    def walk(el, out):
        for ch in el:
            tag = ch.tag
            if tag == "group":
                for t in ("rotation", "scaleX", "scaleY", "translateX", "translateY"):
                    if f(ch, t) is not None:
                        raise ValueError("group transforms unsupported: " + path)
                inner = []
                clip = None
                for c in ch:
                    if c.tag == "clip-path":
                        counter[0] += 1
                        cid = "c%d" % counter[0]
                        defs.append('<clipPath id="%s"><path d="%s"/></clipPath>' % (cid, f(c, "pathData")))
                        clip = cid
                # clip applies to the siblings that follow it inside the group
                rest = [c for c in ch if c.tag != "clip-path"]
                tmp = ET.Element("group")
                tmp.extend(rest)
                walk(tmp, inner)
                out.append('<g%s>%s</g>' % (' clip-path="url(#%s)"' % clip if clip else "", "".join(inner)))
            elif tag == "path":
                fill, fa = resolve(f(ch, "fillColor"), colors)
                stroke, sa = resolve(f(ch, "strokeColor"), colors)
                fa *= float(f(ch, "fillAlpha", 1))
                sa *= float(f(ch, "strokeAlpha", 1))
                attrs = ['d="%s"' % f(ch, "pathData")]
                attrs.append('fill="%s" fill-opacity="%g"' % (fill, fa) if fill else 'fill="none"')
                if f(ch, "fillType") == "evenOdd":
                    attrs.append('fill-rule="evenodd"')
                if stroke:
                    attrs.append('stroke="%s" stroke-opacity="%g" stroke-width="%s"' % (stroke, sa, f(ch, "strokeWidth", "1")))
                    attrs.append('stroke-linecap="%s" stroke-linejoin="%s"' % (f(ch, "strokeLineCap", "butt"), f(ch, "strokeLineJoin", "miter")))
                out.append("<path %s/>" % " ".join(attrs))

    walk(root, body)
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %g %g">'
        "<defs>%s</defs>%s</svg>" % (px, px, vw, vh, "".join(defs), "".join(body))
    )


def magick(*args):
    subprocess.run(["magick", *args], check=True)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "icon-sheet.png"
    day = load_colors(os.path.join(RES, "values/icon_colors.xml"))
    night = dict(day)
    night.update(load_colors(os.path.join(RES, "values-night/icon_colors.xml")))
    drawables = sorted(
        n for n in os.listdir(os.path.join(RES, "drawable")) if n.startswith("ic_") and n.endswith(".xml") and not n.startswith("ic_launcher")
    )
    tmp = tempfile.mkdtemp(prefix="icon_sheet_")
    tile = 48 * SCALE
    pad = 14
    cells = []
    for n in drawables:
        name = n[:-4]
        src = os.path.join(RES, "drawable", n)
        pair = []
        for mode, cols, bg in (("day", day, "#FFFBFE"), ("night", night, "#1C1B1F")):
            svg = os.path.join(tmp, "%s_%s.svg" % (name, mode))
            png = os.path.join(tmp, "%s_%s.png" % (name, mode))
            with open(svg, "w") as fh:
                fh.write(vector_to_svg(src, cols, tile))
            magick("-background", bg, "-density", "96", "svg:" + svg, "-gravity", "center",
                   "-extent", "%dx%d" % (tile + 2 * pad, tile + 2 * pad), png)
            pair.append(png)
        # a true-size (48 px) preview on the light colour, next to the pair
        svg = os.path.join(tmp, name + "_small.svg")
        small = os.path.join(tmp, name + "_small.png")
        with open(svg, "w") as fh:
            fh.write(vector_to_svg(src, day, 48))
        magick("-background", "#FFFBFE", "-density", "96", "svg:" + svg, "-gravity", "center",
               "-extent", "%dx%d" % (48 + 8, tile + 2 * pad), small)
        cell = os.path.join(tmp, name + "_cell.png")
        magick(pair[0], pair[1], small, "+append", "-gravity", "south", "-background", "#FFFBFE",
               "-splice", "0x30", "-gravity", "south", "-fill", "#333333", "-pointsize", "18",
               "-annotate", "+0+6", name, cell)
        cells.append(cell)
    cols_n = 3
    rows = []
    for i in range(0, len(cells), cols_n):
        row = os.path.join(tmp, "row%d.png" % i)
        magick(*cells[i:i + cols_n], "-background", "#FFFBFE", "-gravity", "north", "+append", row)
        rows.append(row)
    magick(*rows, "-background", "#FFFBFE", "-gravity", "west", "-append", out)
    print("wrote", out)


if __name__ == "__main__":
    main()
