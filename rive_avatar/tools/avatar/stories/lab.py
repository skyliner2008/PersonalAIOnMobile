# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# ================================================================= EYE LAB STORIES
# Timelines designed in the Eye Lab (tools/eyelab/, "ไทม์ไลน์") are a workspace; when one is
# finished Claude ships it here (tools/eyelab/ship.py story <name> <Mood>): data/stories/<name>.json
# with a "target" replaces that mood story (e.g. MoodSad). Each key is a whole
# frame of the lab: both eyes' 8 points + colour, the head pose, the per-eye rotation and
# one library prop, plus how it moves on to the next key (ease) and its effects
# (pop on arrival, trembling / lower-lid quiver through the segment).
#
# Mapping onto the rig (the same maths as the lab's eyeXform, machine/poses.py):
#   eye outline       -> vtxL:i / vtxR:i          (front + back morph vertices)
#   head yaw / pitch  -> blinkL/R x,y             (each eye's offset from the face centre)
#                        sblinkL/R scaleX,Y       (foreshortening)
#                        face x, y, rotation      (the centre's shift + neck roll)
#                        irisL/R x, y             (the front shape leads the look)
#   eye rotation      -> tiltL/R scaleX=cos y, scaleY=cos x, rotation=z
#   pop               -> popL/R (right eye POP_LAG frames later, softer)
#   prop              -> the beat's prop + PropsSolo x/y/rotation/scale/opacity
#                        about the prop's own centre (tools/eyelab/props/props.json)

import glob as _glob
import json as _json
import os as _os
import sys as _sys

_LAB_DIR = STORY_DIR          # data/stories (shipped); ship.py check points it at the Lab's workspace
_PROP_META = _os.path.join(AVATAR_DIR, "..", "eyelab", "props", "props.json")
_LAB_EASE = {"jelly": "jelly", "smooth": "cubic", "soft": "soft", "linear": "linear", "hold": "hold"}
_BEZ = {"jelly": (0.30, 0.0, 0.32, 1.28), "cubic": (0.42, 0.0, 0.58, 1.0), "soft": (0.25, 0.1, 0.25, 1.0)}
LAB_COLOR_KEYS = {}      # story name -> [(frame, {"L": colour, "R": colour})]; animations.py paints them
LAB_STORIES = []         # (file name, target) -- for the build log


def _ease_fn(name):
    if name == "linear":
        return lambda x: x
    if name == "hold":
        return lambda x: 0.0
    x1, y1, x2, y2 = _BEZ.get(name, _BEZ["cubic"])
    cx, cy = 3 * x1, 3 * y1
    bx, by = 3 * (x2 - x1) - cx, 3 * (y2 - y1) - cy
    ax, ay = 1 - cx - bx, 1 - cy - by

    def f(x):
        t = x
        for _ in range(8):
            d = (3 * ax * t + 2 * bx) * t + cx
            if abs(d) < 1e-6:
                break
            t -= (((ax * t + bx) * t + cx) * t - x) / d
        t = min(1.0, max(0.0, t))
        return ((ay * t + by) * t + cy) * t
    return f


def _eased(keys):
    """[(frame, value, ease)] -> value(frame) following each segment's own ease."""
    def f(fr):
        if fr <= keys[0][0]:
            return keys[0][1]
        for (f0, v0, e0), (f1, v1, _e1) in zip(keys, keys[1:]):
            if fr <= f1:
                return v0 + (v1 - v0) * _ease_fn(e0)((fr - f0) / float(f1 - f0 or 1))
        return keys[-1][1]
    return f


def _shaken(keys, windows, seed):
    """Keys of one track + irregular shaking inside windows [(f0, f1, amp)]. Inside a
    window the track is sampled densely (so it still follows the segment's ease) and the
    window's first key turns linear; the keys at the window edges are kept."""
    if not any(a > 0 for _f0, _f1, a in windows):
        return keys
    base = _eased(keys)
    out_ = {k[0]: k for k in keys}
    for n, (f0, f1, amp) in enumerate(windows):
        if amp <= 0 or f1 - f0 < 8:
            continue
        if f0 in out_:
            out_[f0] = (f0, out_[f0][1], "linear")
        for k in tremble(f0, f1, amp, seed * 101 + n * 7, base=base, step=(3, 5), interp="linear")[1:-1]:
            out_[k[0]] = k
    return [out_[f] for f in sorted(out_)]


def _varies(keys, rest, eps=1e-4):
    return any(abs(k[1] - rest) > eps for k in keys)


def _rot(x, y, a):
    return x * math.cos(a) - y * math.sin(a), x * math.sin(a) + y * math.cos(a)


def _prop_centres():
    try:
        with open(_PROP_META, encoding="utf-8") as fh:
            return {m["index"]: (m["box"][0] + m["box"][2] / 2.0, m["box"][1] + m["box"][3] / 2.0)
                    for m in _json.load(fh)}
    except (OSError, ValueError):
        return {}


def key_boxes(k):
    """Where the two eyes of a key land on the artboard (anchor points, with the pose,
    per-eye rotation, pupil lead and trembling -- the Eye Lab's eyeXform)
    -> {"L"|"R": (x0, x1, y0, y1)}."""
    h = EYE_HEAD
    pose = k.get("pose", {})
    yaw, pitch, roll = float(pose.get("yaw", 0)), float(pose.get("pitch", 0)), float(pose.get("roll", 0))
    yw, pt = yaw * h["maxYaw"] * math.pi / 180, pitch * h["maxPitch"] * math.pi / 180
    r = (roll * float(h.get("maxRoll", 10)) - yaw * h["yawRoll"]) * math.pi / 180
    shake = float(k.get("tremble") or 0)
    out_ = {}
    for tag, bx in (("L", -EYE_DX), ("R", EYE_DX)):
        dx, dy, sx, sy = head_project(bx, EYE_Y, yw, pt)
        x, y = bx + dx + h["travelX"] * yaw, EYE_Y + dy + h["travelY"] * pitch
        cx, cy = CX + x * math.cos(r) - y * math.sin(r), CY + x * math.sin(r) + y * math.cos(r)
        er = k.get("eyeRot", {}).get(tag, {})
        ssx = EYE_SCALE * sx * math.cos(math.radians(float(er.get("y", 0))))
        ssy = EYE_SCALE * sy * math.cos(math.radians(float(er.get("x", 0))))
        rot = r + math.radians(float(er.get("z", 0)))
        ix, iy = h["pupil"] * yaw / EYE_SCALE, h["pupil"] * pitch / EYE_SCALE
        xs, ys = [], []
        for q in k["eyes"][tag]["pts"]:
            u, v = (q[0] + ix) * ssx, (q[1] + iy) * ssy
            xs.append(cx + u * math.cos(rot) - v * math.sin(rot))
            ys.append(cy + u * math.sin(rot) + v * math.cos(rot))
        out_[tag] = (min(xs) - shake, max(xs) + shake, min(ys) - shake, max(ys) + shake)
    return out_


def lab_story_warnings(target, st):
    """Problems worth fixing before the story goes into the app (printed, not fatal)."""
    out_ = []
    for n, k in enumerate(sorted(st.get("keys", []), key=lambda q: q.get("t", 0))):
        for msg in eye_fit_warnings(key_boxes(k)):
            out_.append("คีย์ %d (%.2f s): %s" % (n + 1, float(k.get("t", 0)) / 1000.0, msg))
    target_ms = int(round(MOOD_STORIES[target][0] * 1000 / 60.0))
    if abs(int(st.get("durationMs", 7000)) - target_ms) > 20:
        out_.append("ยาว %d ms แต่เรื่องเดิมยาว %d ms (แอปเล่นเรื่องนี้ %d ms เสมอ)"
                    % (int(st.get("durationMs", 7000)), target_ms, target_ms))
    return out_


def lab_story(name, st):
    """A lab story file -> (dur, beats, extra) in the MOOD_STORIES format."""
    dur = max(2, int(round(float(st.get("durationMs", 7000)) * 60 / 1000.0)))
    raw = sorted(st.get("keys", []), key=lambda k: k.get("t", 0))
    keys = {}
    for k in raw:                         # two keys on one frame: the later one wins
        keys[min(dur, max(0, int(round(float(k.get("t", 0)) * 60 / 1000.0))))] = k
    frames = sorted(keys)
    if not frames:
        raise SystemExit("Eye Lab story %s has no keys" % name)
    ks = [keys[f] for f in frames]
    eases = [_LAB_EASE.get(k.get("ease", "jelly"), "jelly") for k in ks]
    ends = frames[1:] + [dur]              # each key's segment runs to the next key
    h = dict(EYE_HEAD)
    max_roll = float(h.get("maxRoll", 10))
    extra = []

    def track(nkey, prop, values, rest, windows=None, seed=0, always=False):
        kk = [(f, v, e) for f, v, e in zip(frames, values, eases)]
        if windows:
            kk = _shaken(kk, windows, seed)
        if always or _varies(kk, rest):
            extra.append((nkey, prop, kk))
            return True
        return False

    # ---- head pose + per-eye rotation --------------------------------------
    face_x, face_y, face_r = [], [], []
    eye_pose = {"L": {"x": [], "y": [], "sx": [], "sy": [], "ix": [], "iy": []},
                "R": {"x": [], "y": [], "sx": [], "sy": [], "ix": [], "iy": []}}
    tilt = {"L": {"sx": [], "sy": [], "r": []}, "R": {"sx": [], "sy": [], "r": []}}
    for k in ks:
        pose = k.get("pose", {})
        yaw, pitch, roll = float(pose.get("yaw", 0)), float(pose.get("pitch", 0)), float(pose.get("roll", 0))
        yw, pt = yaw * h["maxYaw"] * math.pi / 180, pitch * h["maxPitch"] * math.pi / 180
        r = (roll * max_roll - yaw * h["yawRoll"]) * math.pi / 180
        cdx, cdy, _sx, _sy = head_project(0.0, 0.0, yw, pt)
        # the lab turns the whole face about its centre: shift first, then roll
        fx, fy = _rot(cdx + h["travelX"] * yaw, cdy + h["travelY"] * pitch, r)
        face_x.append(fx)
        face_y.append(fy)
        face_r.append(r)
        for tag, bx in (("L", -EYE_DX), ("R", EYE_DX)):
            dx, dy, sx, sy = head_project(bx, EYE_Y, yw, pt)
            p = eye_pose[tag]
            p["x"].append(dx - cdx)
            p["y"].append(dy - cdy)
            p["sx"].append(sx)
            p["sy"].append(sy)
            p["ix"].append(h["pupil"] * yaw)
            p["iy"].append(h["pupil"] * pitch)
            er = k.get("eyeRot", {}).get(tag, {})
            tilt[tag]["sx"].append(math.cos(math.radians(float(er.get("y", 0)))))
            tilt[tag]["sy"].append(math.cos(math.radians(float(er.get("x", 0)))))
            tilt[tag]["r"].append(math.radians(float(er.get("z", 0))))

    track("face", 13, face_x, 0.0)
    track("face", 14, face_y, 0.0)
    track("face", 15, face_r, 0.0)
    shake = [(f0, f1, float(k.get("tremble") or 0)) for f0, f1, k in zip(frames, ends, ks)]
    for n, tag in enumerate(("L", "R")):
        p = eye_pose[tag]
        moved = track("blink" + tag, 13, p["x"], 0.0, shake, 11 + n * 12)
        moved = track("blink" + tag, 14, p["y"], 0.0, shake, 17 + n * 12) or moved
        track("sblink" + tag, 16, p["sx"], 1.0)
        track("sblink" + tag, 17, p["sy"], 1.0)
        # after the blink keys, so the lab's lead replaces the automatic iris follow
        track("iris" + tag, 13, p["ix"], 0.0, always=moved)
        track("iris" + tag, 14, p["iy"], 0.0, always=moved)
        rot0 = eye_rotation(tag)
        track("tilt" + tag, 16, tilt[tag]["sx"], math.cos(math.radians(rot0["y"])))
        track("tilt" + tag, 17, tilt[tag]["sy"], math.cos(math.radians(rot0["x"])))
        track("tilt" + tag, 15, tilt[tag]["r"], math.radians(rot0["z"]))

    # ---- the eye outlines (+ lower-lid quiver) -------------------------------
    quiver = [(f0, f1, float(k.get("quiver") or 0)) for f0, f1, k in zip(frames, ends, ks)]
    for n, tag in enumerate(("L", "R")):
        verts = [outline([(q[0], q[1]) for q in k["eyes"][tag]["pts"]], [q[2] for q in k["eyes"][tag]["pts"]])
                 for k in ks]
        for i in range(MORPH_N):
            for key in MORPH_KEYS:
                vals = [v[i][key] for v in verts]
                wins = quiver if key == VY and i in (3, 4, 5) else None
                # always keyed: otherwise the live face's own morph shape would show through
                track("vtx%s:%d" % (tag, i), key, vals, 0.0, wins, 31 + n * 50 + i * 3, always=True)
    LAB_COLOR_KEYS[name] = [(f, {t: k["eyes"][t].get("color", "cyan") for t in ("L", "R")})
                            for f, k in zip(frames, ks)]

    # ---- pops: every key marked "pop" squashes and settles, right eye later ----
    pops = [f for f, k in zip(frames, ks) if k.get("pop", True)]
    for tag, lag, gain in (("L", 0, 1.0), ("R", POP_LAG, MOTION["popGainR"])):
        for key, col in ((16, 1), (17, 2)):
            kk = {}
            for j, p0 in enumerate(pops):
                stop = pops[j + 1] if j + 1 < len(pops) else dur + 1
                rows = ([(p0, 1.0, "hold")] if lag else []) + \
                       [(p0 + lag + row[0], 1 + (row[col] - 1) * gain, "cubic") for row in POP_KEYS]
                for r_ in rows:
                    if r_[0] < stop and r_[0] <= dur:
                        kk[r_[0]] = r_
            if kk:
                extra.append(("pop" + tag, key, [kk[f] for f in sorted(kk)]))

    # ---- the prop: which one (beats) and where (PropsSolo transform) --------
    centres = _prop_centres()
    beats, last = [], None
    for f, k in zip(frames, ks):
        pr = k.get("prop") or None
        idx = int(pr["index"]) if pr else P_NONE
        if idx != last:
            beats.append((f, "LabMorph", idx, G_NONE, F_NONE))
            last = idx
    if beats[0][0] != 0:
        beats.insert(0, (0, "LabMorph", P_NONE, G_NONE, F_NONE))
    if any(k.get("prop") for k in ks):
        cols = {c: [] for c in (13, 14, 15, 16, 17, 18)}
        p_eases = []
        for j, k in enumerate(ks):
            pr = k.get("prop")
            nxt = ks[j + 1].get("prop") if j + 1 < len(ks) else None
            if not pr:
                vals = (0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
            else:
                s, a = float(pr.get("scale", 1)), math.radians(float(pr.get("rot", 0)))
                cx, cy = centres.get(int(pr["index"]), (CX, CY))
                rx, ry = _rot(cx * s, cy * s, a)
                # scale / rotate about the prop's own centre, then move it by (dx, dy)
                vals = (cx + float(pr.get("dx", 0)) - rx, cy + float(pr.get("dy", 0)) - ry, a, s, s,
                        float(pr.get("opacity", 1)))
            for c, v in zip((13, 14, 15, 16, 17, 18), vals):
                cols[c].append(v)
            same = pr and nxt and int(nxt["index"]) == int(pr["index"])
            p_eases.append(eases[j] if same else "hold")
        for c, rest in ((13, 0.0), (14, 0.0), (15, 0.0), (16, 1.0), (17, 1.0), (18, 1.0)):
            kk = list(zip(frames, cols[c], p_eases))
            if _varies(kk, rest):
                extra.append(("props", c, kk))
    return dur, beats, extra


for _path in sorted(_glob.glob(_os.path.join(_LAB_DIR, "*.json"))):
    _name = _os.path.splitext(_os.path.basename(_path))[0]
    with open(_path, encoding="utf-8") as _fh:
        _st = _json.load(_fh)
    _target = _st.get(STORY_TARGET_KEY)
    if not _target:
        continue                               # still a draft: not used in the app yet
    if _target not in MOOD_STORIES:
        _sys.stderr.write("Eye Lab story %s: ไม่รู้จักเรื่อง %s (ต้องเป็นชื่อ Mood... ใน presets.json)\n"
                          % (_name, _target))
        continue
    for _msg in lab_story_warnings(_target, _st):
        _sys.stderr.write("Eye Lab story %s: %s\n" % (_name, _msg))
    MOOD_STORIES[_target] = lab_story(_target, _st)
    LAB_STORIES.append((_name, _target))
if LAB_STORIES:
    _sys.stderr.write("Eye Lab stories: %s\n" % ", ".join("%s -> %s" % s for s in LAB_STORIES))
