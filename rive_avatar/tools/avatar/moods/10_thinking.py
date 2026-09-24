# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 10. THINKING -------------------------------------------------------------
_cx, _cy = _circle(180, 290, 80, 16)
MOOD_STORIES["MoodThinking"] = (MD, [
    (0, "Neutral", MOOD_THINK, G_NONE, F_NONE),
    (20, "Thinking", MOOD_THINK, G_NONE, F_NONE),
    (300, "Neutral", MOOD_THINK, G_NONE, F_NONE),
    (MD, "Neutral", MOOD_THINK, G_NONE, F_NONE),
], [
    ("blinkL", 15, [(0, 0), (30, .2), (290, .2), (300, 0), (MD, 0)]),
    ("blinkR", 15, [(0, 0), (30, -.2), (290, -.2), (300, 0), (MD, 0)]),
] + _eyes(17, [(0, 1), (30, .82), (180, .82)] + osc(190, 290, 20, .95, .8) + [(300, .82), (306, 1.35), (316, .9), (326, 1.05), (336, 1), (MD, 1)]) +
    _eyes(16, [(0, 1), (300, 1), (306, .85), (316, 1.06), (326, 1), (MD, 1)]) +
    _eyes(13, [(0, 0), (170, 0)] + _cx + [(300, 0), (MD, 0)]) +
    _eyes(14, [(0, 0), (170, 0)] + _cy + [(300, 0), (MD, 0)]) + [
    ("face", 15, [(0, 0), (30, .05), (290, .05), (300, 0), (MD, 0)]),
    ("face", 14, [(0, 0), (300, 0), (306, -14), (320, 0), (MD, 0)]),
    (_mp("MoodThink", "Gear"), 15, [(0, 0), (290, 12), (MD, 12)], "linear"),
    (_mp("MoodThink", "Gear2"), 15, [(0, 0), (290, -16), (MD, -16)], "linear"),
    (_mp("MoodThink", "Bulb"), 18, [(0, 0), (304, 0), (308, 1), (MD, 1)]),
] + _sc("MoodThink", "Bubble", [(0, 0), (30, 0), (50, 1.1), (60, 1), (290, 1), (300, 0), (MD, 0)]) +
    _sc("MoodThink", "Bulb", [(0, 0), (304, 0), (316, 1.3), (326, 1), (MD, 1)]))
