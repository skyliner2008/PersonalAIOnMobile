# -*- coding: utf-8 -*-
"""
Helpers for Claude to refine Eye Lab stories in code (the page shows the result live).

    import sys; sys.path.insert(0, "rive_avatar/tools/eyelab"); import labstory as ls
    st = ls.fork("sad_sketch", "sad_v1")          # Claude's version; the sketch stays as it is
    k = ls.add_key(st, 1200, like=ls.key_at(st, 1000))
    k["tremble"] = 2.5; k["claudeNote"] = "ตาสั่นแรงขึ้นก่อนร้อง"
    st["claudeNote"] = "เพิ่มจังหวะสะอื้น 3 ครั้ง ตาขวาช้ากว่าซ้าย"
    ls.save("sad_v1", st)

Story format (one file per story in data/stories/): {"by", "basedOn", "intent", "durationMs",
"note", "claudeNote", "keys": [key]}; a key = {"t" (ms), "ease", "pop", "tremble", "quiver",
"eyes": {"L"|"R": {"pts": [[x, y, smooth] * 8], "color"}}, "mirror", "pose": {"yaw", "pitch",
"roll"} (-1..1), "eyeRot": {"L"|"R": {"x", "y", "z"} degrees}, "rotLink", "prop": null |
{"index", "dx", "dy", "scale", "rot", "opacity"}, "note", "claudeNote"}.
Points are the LEFT eye's order for "L" and the mirrored order for "R" (see mirror()).
"""
import copy
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
STORIES = os.path.join(HERE, "data", "stories")
LAB = os.path.join(HERE, "data", "eye_lab.json")


def load(name):
    with open(os.path.join(STORIES, name + ".json"), encoding="utf-8") as f:
        return json.load(f)


def save(name, story):
    """Atomic write; keys are kept sorted by time. The open page reloads it by itself."""
    story["keys"].sort(key=lambda k: k["t"])
    os.makedirs(STORIES, exist_ok=True)
    path = os.path.join(STORIES, name + ".json")
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8", newline="\n") as f:
        json.dump({k: v for k, v in story.items() if k != "mtime"}, f, ensure_ascii=False, indent=1)
        f.write("\n")
    os.replace(tmp, path)
    return path


def fork(src, dst):
    """A copy of the user's story to refine: marked as Claude's, remembers where it came from."""
    st = copy.deepcopy(load(src))
    st.update(by="claude", basedOn=src, claudeNote=st.get("claudeNote", ""))
    return st


def key_at(story, t):
    """The last key at or before t (ms)."""
    ks = [k for k in sorted(story["keys"], key=lambda k: k["t"]) if k["t"] <= t]
    return ks[-1] if ks else story["keys"][0]


def add_key(story, t, like=None, **fields):
    """A new key at t, copied from `like` (default: the key before t), then fields applied."""
    k = copy.deepcopy(like or key_at(story, t))
    k.update(t=int(t), note="", claudeNote="")
    k.update(fields)
    story["keys"].append(k)
    story["keys"].sort(key=lambda q: q["t"])
    return k


def mirror(pts):
    """Left-eye points -> the right eye (x mirrored, re-numbered clockwise from the top)."""
    n = len(pts)
    return [[-pts[(n - i) % n][0], pts[(n - i) % n][1], pts[(n - i) % n][2]] for i in range(n)]


def shape(name):
    """A library shape's left-eye points from the lab's eye_lab.json."""
    with open(LAB, encoding="utf-8") as f:
        return copy.deepcopy(json.load(f)["shapes"][name]["left"])


def set_eyes(key, left, right=None, color=None):
    key["eyes"]["L"]["pts"] = copy.deepcopy(left)
    key["eyes"]["R"]["pts"] = copy.deepcopy(right) if right is not None else mirror(left)
    key["mirror"] = right is None
    if color:
        key["eyes"]["L"]["color"] = key["eyes"]["R"]["color"] = color
    return key
