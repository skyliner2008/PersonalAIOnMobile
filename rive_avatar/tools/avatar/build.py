#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Entry point of the avatar generator: builds scene.rml (+ presets.json) for rive_avatar.

    python tools/avatar/build.py [out.rml]        (from rive_avatar/)

The generator is split into small files -- one per mood story, one per scene story,
plus the shared rig -- listed in ORDER.txt. They are executed in that order into ONE
shared namespace, exactly like the original single-file script, so every piece can use
what earlier pieces defined (palette, nid(), w(), FACES, ...). Order matters: a mood
file must come after moods/_common.py and before stories/assemble.py.

To add a mood story: create moods/NN_name.py (register it in MOOD_STORIES like the
others), add it to ORDER.txt before stories/assemble.py, and add its preset row in
stories/assemble.py (MOOD_PRESETS).
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))


def main():
    order = [ln.strip() for ln in open(os.path.join(HERE, "ORDER.txt"), encoding="utf-8")
             if ln.strip() and not ln.startswith("#")]
    # the Eye Lab file (data/eye_lab.json) is loaded first: its "rig" sets the eye size,
    # spacing, layer offset and glow that the layout and the eye parts read
    lab_path = os.path.join(HERE, "data", "eye_lab.json")
    lab = json.load(open(lab_path, encoding="utf-8")) if os.path.exists(lab_path) else {}
    ns = {"__name__": "avatar_build", "__builtins__": __builtins__, "AVATAR_DIR": HERE,
          "EYE_LAB": lab, "EYE_RIG": lab.get("rig", {})}
    for rel in order:
        path = os.path.join(HERE, rel)
        code = compile(open(path, encoding="utf-8").read(), path, "exec")
        exec(code, ns)


if __name__ == "__main__":
    sys.exit(main())
