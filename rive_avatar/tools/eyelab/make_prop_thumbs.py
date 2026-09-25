#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Render every library prop from the real .riv so the Eye Lab can place it exactly as the
app draws it:  python rive_avatar/tools/eyelab/make_prop_thumbs.py   (a few minutes)

For each prop index: `rive . --screenshot --data=face=<Blank> --data=prop=<i>` on the
500x500 artboard, key out the headless background, crop to what was drawn and write
props/<i>.png plus props/props.json [{index, name, box: [x, y, w, h]}] in artboard units.
"""
import json
import os
import subprocess
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RIVE_DIR = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(HERE, "props")
BG = (29, 29, 29)          # the headless screenshot's clear colour


def key_out(im):
    """Background -> transparent; glow edges become partly transparent."""
    im = im.convert("RGBA")
    px = im.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            r, g, b, _a = px[x, y]
            d = max(abs(r - BG[0]), abs(g - BG[1]), abs(b - BG[2]))
            if d <= 3:
                px[x, y] = (0, 0, 0, 0)
            elif d < 60:
                a = d / 60.0
                # un-mix the background so the colour is not dulled by it
                px[x, y] = (max(0, min(255, int((r - BG[0] * (1 - a)) / a))),
                            max(0, min(255, int((g - BG[1] * (1 - a)) / a))),
                            max(0, min(255, int((b - BG[2] * (1 - a)) / a))), int(a * 255))
    return im


def main():
    presets = json.load(open(os.path.join(RIVE_DIR, "presets.json"), encoding="utf-8"))
    names, blank = presets["props"], presets["faces"].index("Blank")
    os.makedirs(OUT, exist_ok=True)
    only = set(int(a) for a in sys.argv[1:]) or None
    meta_path = os.path.join(OUT, "props.json")
    meta = {m["index"]: m for m in json.load(open(meta_path, encoding="utf-8"))} if os.path.exists(meta_path) else {}
    for i, name in enumerate(names):
        if i == 0 or (only and i not in only):
            continue
        shot = os.path.join(OUT, "_shot.png")
        subprocess.run(["rive", ".", "--screenshot=" + shot, "--data=face=%d" % blank, "--data=prop=%d" % i,
                        "--advance=90"], cwd=RIVE_DIR, capture_output=True, shell=(os.name == "nt"))
        if not os.path.exists(shot):
            print("  skip", i, name, "(no screenshot)")
            continue
        im = key_out(Image.open(shot))
        os.remove(shot)
        box = im.getbbox()
        if not box:
            print("  empty", i, name)
            continue
        x0, y0, x1, y1 = box
        im.crop(box).save(os.path.join(OUT, "%d.png" % i))
        meta[i] = {"index": i, "name": name, "box": [x0, y0, x1 - x0, y1 - y0]}
        print("  %2d %-14s %s" % (i, name, meta[i]["box"]), flush=True)
    json.dump([meta[k] for k in sorted(meta)], open(meta_path, "w", encoding="utf-8", newline="\n"),
              ensure_ascii=False, indent=1)
    print("wrote", meta_path, len(meta), "props")


if __name__ == "__main__":
    main()
