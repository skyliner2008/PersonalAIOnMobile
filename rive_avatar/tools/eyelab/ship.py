#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Move finished Eye Lab designs into the Android app.

The Eye Lab is a design table: the user sketches on the page, Claude refines in code,
both look at the result on the page. Nothing there reaches the app until Claude ships it:

    workspace (the page + Claude)            app (the generator reads only these)
    tools/eyelab/data/eye_lab.json     ->    tools/avatar/data/eye_app.json
    tools/eyelab/data/stories/<n>.json ->    tools/avatar/data/stories/<n>.json (+ "target")
    key "sound" fields                 ->    the story's cues in RiveMoodStories.kt

    python rive_avatar/tools/eyelab/ship.py status               what differs from the app
    python rive_avatar/tools/eyelab/ship.py check [--shots]      build the WORKSPACE into a temp
                                     folder (stories use their "intent"), rive --verify it, print
                                     every warning; --shots renders each story at its keys
    python rive_avatar/tools/eyelab/ship.py eye [shapes faces motion rig]   (default: all four)
    python rive_avatar/tools/eyelab/ship.py story <name> <MoodTarget> [--mute-cues | --keep-cues]
    python rive_avatar/tools/eyelab/ship.py unship <name>        take a story out of the app again
    python rive_avatar/tools/eyelab/ship.py build                generator + rive --once + copy .riv

A story's sounds: keys with "sound" replace that mood's cues in RiveMoodStories.kt (the old
ones are kept in the shipped file and come back on unship). A story without sounds must say
what to do with the old cues, which were timed for the old story: --mute-cues or --keep-cues.
"""
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
RIVE_DIR = os.path.abspath(os.path.join(HERE, "..", ".."))
REPO = os.path.abspath(os.path.join(RIVE_DIR, ".."))
LAB = os.path.join(HERE, "data", "eye_lab.json")
LAB_STORIES = os.path.join(HERE, "data", "stories")
APP = os.path.join(RIVE_DIR, "tools", "avatar", "data", "eye_app.json")
APP_STORIES = os.path.join(RIVE_DIR, "tools", "avatar", "data", "stories")
APP_RIV = os.path.join(REPO, "composeApp", "src", "androidMain", "res", "raw", "avatar.riv")
KT_DIR = os.path.join(REPO, "composeApp", "src", "commonMain", "kotlin", "com", "skyliner2008", "jarvis")
MOODS_KT = os.path.join(KT_DIR, "ui", "component", "avatar", "RiveMoodStories.kt")
SOUNDS_KT = os.path.join(KT_DIR, "sound", "RobotSoundPlayer.kt")
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
    """A story without the fields that only matter on the page or in the app copy."""
    return {k: v for k, v in story.items() if k not in ("target", "intent", "shippedAt", "oldCues", "mtime")}


# ---------------------------------------------------------------- the app's side (Kotlin)
def _read(path):
    """Text as it is on disk -- line endings untouched (the Kotlin sources use CRLF)."""
    try:
        with open(path, encoding="utf-8", newline="") as f:
            return f.read()
    except OSError:
        return ""


def robot_sounds():
    """RobotSound enum names (the sounds a story cue can play)."""
    src = _read(SOUNDS_KT)
    m = re.search(r"enum class RobotSound\s*\{(.*?)\}", src, re.S)
    if not m:
        return []
    body = re.sub(r"//[^\n]*|/\*.*?\*/", "", m.group(1), flags=re.S)
    return [n for n in re.findall(r"\b([A-Z][A-Z0-9_]*)\b", body)]


def _cues_block(src, name):
    return re.search(r'(RiveMoodStory\("%s",\s*\d+,\s*cues\()(.*?)(\),\s*\n\s*nameTh)' % re.escape(name), src, re.S)


def app_moods():
    """The app's mood stories: name, seq, Thai name, the emotions that play it, its cues."""
    src = _read(MOODS_KT).replace("\r\n", "\n")        # parsing only; set_cues() keeps the file's CRLF
    by_val, out = {}, []
    for m in re.finditer(r'val (\w+) = RiveMoodStory\("(\w+)",\s*(\d+),\s*cues\((.*?)\),\s*\n\s*nameTh = "([^"]*)"', src, re.S):
        cues = [{"ms": int(ms), "sound": s} for ms, s in re.findall(r"(\d+)L to (\w+)", m.group(4))]
        item = {"name": m.group(2), "seq": int(m.group(3)), "nameTh": m.group(5), "cues": cues, "emotions": []}
        by_val[m.group(1)] = item
        out.append(item)
    groups = {"IDLE_VARIANTS": []}
    g = re.search(r"val IDLE_VARIANTS = listOf\(([^)]*)\)", src)
    if g:
        groups["IDLE_VARIANTS"] = [v.strip() for v in g.group(1).split(",")]
    fn = re.search(r"fun variantsFor\(.*?\n\s*\}\n", src, re.S)
    for line in (fn.group(0).splitlines() if fn else []):
        m = re.match(r"\s*((?:AvatarEmotion\.\w+\s*,?\s*)+)->\s*(.+)$", line)
        if not m:
            continue
        emotions = re.findall(r"AvatarEmotion\.(\w+)", m.group(1))
        rhs = m.group(2).strip()
        vals = groups.get(rhs) or [v.strip() for v in re.sub(r"^listOf\(|\)$", "", rhs).split(",")]
        for v in vals:
            if v in by_val:
                by_val[v]["emotions"] += emotions
    return out


def _format_cues(cues, nl="\n"):
    if not cues:
        return ""
    parts = ["%dL to %s" % (c["ms"], c["sound"]) for c in cues]
    lines, cur = [], ""
    for p in parts:                         # wrap like the hand-written entries
        cur = p if not cur else cur + ", " + p
        if len(cur) > 90:
            lines.append(cur + ",")
            cur = ""
    if cur:
        lines.append(cur)
    elif lines:
        lines[-1] = lines[-1].rstrip(",")
    return nl + "        " + (nl + "        ").join(lines)


def set_cues(target, cues):
    """Replace a mood story's cues in RiveMoodStories.kt -> the old cues (for unship)."""
    src = _read(MOODS_KT)
    m = _cues_block(src, target)
    if not m:
        sys.exit("cannot find the cues of %s in %s" % (target, MOODS_KT))
    old = [{"ms": int(ms), "sound": s} for ms, s in re.findall(r"(\d+)L to (\w+)", m.group(2))]
    nl = "\r\n" if "\r\n" in src else "\n"          # keep the file's own line endings
    new_src = src[:m.start(2)] + _format_cues(cues, nl) + src[m.end(2):]
    with open(MOODS_KT, "w", encoding="utf-8", newline="") as f:
        f.write(new_src)
    return old


def story_cues(story):
    """Keys with a sound -> cues [{ms, sound}] in time order (checked against RobotSound)."""
    known = set(robot_sounds())
    cues = []
    for k in sorted(story.get("keys", []), key=lambda q: q.get("t", 0)):
        s = (k.get("sound") or "").strip()
        if not s:
            continue
        if known and s not in known:
            sys.exit("unknown sound %s at %s ms (RobotSound: %s)" % (s, k.get("t"), ", ".join(sorted(known))))
        cues.append({"ms": int(k.get("t", 0)), "sound": s})
    return cues


# ---------------------------------------------------------------- status / check
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


def check(shots=False):
    """Build the workspace as if everything were shipped -- into a temp project, so the app
    and scene.rml stay untouched -- and report every warning."""
    tmp = tempfile.mkdtemp(prefix="eyelab_check_")
    shutil.copy(os.path.join(RIVE_DIR, "rive.yaml"), tmp)
    env = dict(os.environ, AVATAR_EYE_FILE=LAB, AVATAR_STORY_DIR=LAB_STORIES, AVATAR_STORY_KEY="intent",
               PYTHONIOENCODING="utf-8")
    p = subprocess.run([sys.executable, os.path.join(RIVE_DIR, "tools", "avatar", "build.py"),
                        os.path.join(tmp, "scene.rml")], cwd=RIVE_DIR, env=env, capture_output=True,
                       text=True, encoding="utf-8", errors="replace")
    lines = (p.stdout + p.stderr).strip().splitlines()
    warn = [ln for ln in lines if not ln.startswith("wrote ")]
    print("== generator (%s)" % ("ok" if p.returncode == 0 else "FAILED"))
    for ln in warn:
        print("  " + ln)
    if p.returncode:
        return 1
    v = subprocess.run(["rive", tmp, "--verify"], capture_output=True, text=True, encoding="utf-8",
                       errors="replace", shell=(os.name == "nt"))
    print("== rive --verify: " + ((v.stdout + v.stderr).strip().splitlines() or ["?"])[-1])
    if shots:
        seqs = [q["name"] for q in _load(os.path.join(tmp, "presets.json"), {}).get("seqs", [])]
        out_dir = os.path.join(tmp, "shots")
        os.makedirs(out_dir, exist_ok=True)
        for f in sorted(os.listdir(LAB_STORIES)):
            st = _load(os.path.join(LAB_STORIES, f), {})
            if not f.endswith(".json") or st.get("intent") not in seqs:
                continue
            for k in st.get("keys", []):
                fr = int(round(k.get("t", 0) * 60 / 1000.0)) + 20       # a moment after the key lands
                shot = os.path.join(out_dir, "%s_%05d.png" % (f[:-5], k.get("t", 0)))
                subprocess.run(["rive", tmp, "--screenshot=" + shot, "--data=face=0",
                                "--data=seq=%d" % seqs.index(st["intent"]), "--advance=%d" % fr],
                               capture_output=True, shell=(os.name == "nt"))
        print("== shots: " + out_dir)
    print("== temp project: " + tmp)
    return 0


# ---------------------------------------------------------------- ship
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


def ship_story(name, target, cue_mode=None):
    st = _load(os.path.join(LAB_STORIES, name + ".json"))
    if st is None:
        sys.exit("no Eye Lab story " + name)
    moods = {m["name"]: m for m in app_moods()}
    presets = _load(os.path.join(RIVE_DIR, "presets.json"), {})
    durations = {q["name"]: q.get("durationMs") for q in presets.get("seqs", [])}
    if target not in durations:
        sys.exit("unknown story target %s (a Mood... name from presets.json)" % target)
    cues = story_cues(st)
    if not cues and target in moods and moods[target]["cues"] and cue_mode not in ("mute", "keep"):
        sys.exit("%s has sounds timed for the old story (%s).\n"
                 "Add sounds to the keys, or ship again with --mute-cues (silence) or --keep-cues."
                 % (target, ", ".join("%.2fs %s" % (c["ms"] / 1000.0, c["sound"]) for c in moods[target]["cues"])))
    # the look of a story also depends on the rig / head / motion: warn when the Lab's differ
    changed = [s for s in status()["eye"] if s in ("rig", "motion")]
    if changed:
        print("warning: the Lab's %s differ from the app's -- the story was designed with the Lab's;"
              " ship them too (ship.py eye %s) or it will look different" % (" + ".join(changed), " ".join(changed)))
    if durations.get(target) and abs(int(st.get("durationMs", 7000)) - durations[target]) > 20:
        print("warning: the story is %s ms, %s plays for %s ms" % (st.get("durationMs"), target, durations[target]))
    os.makedirs(APP_STORIES, exist_ok=True)
    old_cues = None
    for f in os.listdir(APP_STORIES):          # one story per target
        other = _load(os.path.join(APP_STORIES, f), {})
        if other.get("target") == target:
            old_cues = other.get("oldCues", old_cues)
            if f != name + ".json":
                os.remove(os.path.join(APP_STORIES, f))
                print("replaced", f[:-5])
    out = _strip(st)
    out["target"] = target
    if cues or cue_mode == "mute":
        replaced = set_cues(target, cues)
        out["oldCues"] = old_cues if old_cues is not None else replaced
        print("cues of %s: %s" % (target, ", ".join("%.2fs %s" % (c["ms"] / 1000.0, c["sound"]) for c in cues) or "(silent)"))
    elif old_cues is not None:
        out["oldCues"] = old_cues
    _dump(os.path.join(APP_STORIES, name + ".json"), out, indent=1)
    print("shipped story", name, "->", target)


def unship(name):
    p = os.path.join(APP_STORIES, name + ".json")
    if not os.path.exists(p):
        print("not in the app:", name)
        return
    st = _load(p, {})
    if st.get("oldCues") is not None and st.get("target"):
        set_cues(st["target"], st["oldCues"])
        print("restored the old cues of", st["target"])
    os.remove(p)
    print("removed", name, "from the app")


def build():
    for cmd in ([sys.executable, os.path.join("tools", "avatar", "build.py"), "scene.rml"], ["rive", ".", "--once"]):
        p = subprocess.run(cmd, cwd=RIVE_DIR, shell=(os.name == "nt" and cmd[0] == "rive"))
        if p.returncode:
            sys.exit(p.returncode)
    shutil.copyfile(os.path.join(RIVE_DIR, "build", "rive_avatar.riv"), APP_RIV)
    print("copied the .riv into the app:", APP_RIV)


def main(argv):
    flags = {a for a in argv if a.startswith("--")}
    args = [a for a in argv if not a.startswith("--")]
    if not args or args[0] == "status":
        print(json.dumps(status(), ensure_ascii=False, indent=1))
    elif args[0] == "check":
        return check(shots="--shots" in flags)
    elif args[0] == "eye":
        ship_eye(args[1:] or list(SECTIONS))
    elif args[0] == "story" and len(args) == 3:
        ship_story(args[1], args[2], "mute" if "--mute-cues" in flags else "keep" if "--keep-cues" in flags else None)
    elif args[0] == "unship" and len(args) == 2:
        unship(args[1])
    elif args[0] == "build":
        build()
    else:
        sys.exit(__doc__)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
