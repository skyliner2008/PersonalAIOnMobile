#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Move finished Eye Lab designs into the Android app.

The Eye Lab is a design table: the user sketches on the page, Claude refines in code,
both look at the result on the page. Nothing there reaches the app until Claude ships it:

    workspace (the page + Claude)            app (the generator reads only these)
    tools/eyelab/data/eye_lab.json     ->    tools/avatar/data/eye_app.json
    tools/eyelab/data/stories/<n>.json ->    tools/avatar/data/stories/<n>.json (+ "target")

    python rive_avatar/tools/eyelab/ship.py status               what differs from the app
    python rive_avatar/tools/eyelab/ship.py eye [shapes faces motion rig]   (default: all four)
    python rive_avatar/tools/eyelab/ship.py story <name> <MoodTarget>       replaces that mood story
    python rive_avatar/tools/eyelab/ship.py unship <name>        take a story out of the app again
    python rive_avatar/tools/eyelab/ship.py build                generator + rive --once + copy .riv
"""
import json
import os
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
RIVE_DIR = os.path.abspath(os.path.join(HERE, "..", ".."))
LAB = os.path.join(HERE, "data", "eye_lab.json")
LAB_STORIES = os.path.join(HERE, "data", "stories")
APP = os.path.join(RIVE_DIR, "tools", "avatar", "data", "eye_app.json")
APP_STORIES = os.path.join(RIVE_DIR, "tools", "avatar", "data", "stories")
APP_RIV = os.path.abspath(os.path.join(RIVE_DIR, "..", "composeApp", "src", "androidMain", "res", "raw", "avatar.riv"))
SECTIONS = ("shapes", "faces", "motion", "rig")


def _load(path, default=None):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return default


def _dump(path, data, indent=2):
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=indent)
        f.write("\n")
    os.replace(tmp, path)


def _strip(story):
    """A story without the fields that only matter on the page."""
    return {k: v for k, v in story.items() if k not in ("target", "intent", "shippedAt")}


def status():
    """-> {"eye": [changed section / shape names], "stories": [{name, by, intent, shipped, changed}]}"""
    lab, app = _load(LAB, {}), _load(APP, {})
    eye = []
    for sec in SECTIONS:
        a, b = lab.get(sec), app.get(sec)
        if sec == "shapes":
            a, b = a or {}, b or {}
            for key in sorted(set(a) | set(b)):
                if a.get(key) != b.get(key):
                    eye.append("shapes." + key)
        elif a != b:
            eye.append(sec)
    shipped = {}
    for f in os.listdir(APP_STORIES) if os.path.isdir(APP_STORIES) else []:
        if f.endswith(".json"):
            shipped[f[:-5]] = _load(os.path.join(APP_STORIES, f), {})
    stories = []
    for f in sorted(os.listdir(LAB_STORIES)) if os.path.isdir(LAB_STORIES) else []:
        if not f.endswith(".json"):
            continue
        name, st = f[:-5], _load(os.path.join(LAB_STORIES, f), {})
        app_st = shipped.get(name)
        stories.append({"name": name, "by": st.get("by", "user"), "intent": st.get("intent"),
                        "shipped": app_st.get("target") if app_st else None,
                        "changed": bool(app_st) and _strip(app_st) != _strip(st)})
    return {"eye": eye, "stories": stories, "orphans": sorted(set(shipped) - {s["name"] for s in stories})}


def ship_eye(sections):
    lab, app = _load(LAB, {}), _load(APP, {})
    for sec in sections:
        if sec not in SECTIONS:
            sys.exit("unknown section %s (use %s)" % (sec, ", ".join(SECTIONS)))
        if sec in lab:
            app[sec] = lab[sec]
    app["version"] = int(app.get("version", 0)) + 1
    _dump(APP, app)
    print("shipped", ", ".join(sections), "->", APP)


def ship_story(name, target):
    st = _load(os.path.join(LAB_STORIES, name + ".json"))
    if st is None:
        sys.exit("no Eye Lab story " + name)
    presets = _load(os.path.join(RIVE_DIR, "presets.json"), {})
    if target not in [q["name"] for q in presets.get("seqs", [])]:
        sys.exit("unknown story target %s (a Mood... name from presets.json)" % target)
    os.makedirs(APP_STORIES, exist_ok=True)
    for f in os.listdir(APP_STORIES):          # one story per target
        other = _load(os.path.join(APP_STORIES, f), {})
        if f != name + ".json" and other.get("target") == target:
            os.remove(os.path.join(APP_STORIES, f))
            print("replaced", f[:-5])
    out = _strip(st)
    out["target"] = target
    _dump(os.path.join(APP_STORIES, name + ".json"), out, indent=1)
    print("shipped story", name, "->", target)


def unship(name):
    p = os.path.join(APP_STORIES, name + ".json")
    if os.path.exists(p):
        os.remove(p)
        print("removed", name, "from the app")
    else:
        print("not in the app:", name)


def build():
    for cmd in ([sys.executable, os.path.join("tools", "avatar", "build.py"), "scene.rml"], ["rive", ".", "--once"]):
        p = subprocess.run(cmd, cwd=RIVE_DIR, shell=(os.name == "nt" and cmd[0] == "rive"))
        if p.returncode:
            sys.exit(p.returncode)
    shutil.copyfile(os.path.join(RIVE_DIR, "build", "rive_avatar.riv"), APP_RIV)
    print("copied the .riv into the app:", APP_RIV)


def main(argv):
    if not argv or argv[0] == "status":
        print(json.dumps(status(), ensure_ascii=False, indent=1))
    elif argv[0] == "eye":
        ship_eye(argv[1:] or list(SECTIONS))
    elif argv[0] == "story" and len(argv) == 3:
        ship_story(argv[1], argv[2])
    elif argv[0] == "unship" and len(argv) == 2:
        unship(argv[1])
    elif argv[0] == "build":
        build()
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main(sys.argv[1:])
