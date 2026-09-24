# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 14. AWESOME --------------------------------------------------------------
_shades_y = [(0, -320), (20, -320), (60, 0), (66, 8), (72, 0)]
MOOD_STORIES["MoodAwesome"] = (MD, [
    (0, "Neutral", MOOD_COOL, G_NONE, F_NONE),
    (60, "Smug", MOOD_COOL, G_NONE, F_NONE),
    (MD, "Smug", MOOD_COOL, G_NONE, F_NONE),
], _eyes(14, [(0, 0), (20, -14), (56, -14), (60, 0), (MD, 0)]) +
   _eyes(17, [(0, 1), (303, 1)] + osc(304, MD, 20, 1.08, .92)) + [
    ("face", 15, [(0, 0), (180, 0), (214, .12), (300, .12), (310, 0), (MD, 0)]),
    ("face", 14, [(0, 0), (299, 0)] + osc(300, MD, 20, 0, 12)),
    (_mp("MoodCool", "Shades"), 14, _shades_y + [(299, 0)] + osc(300, MD, 20, 0, 12)),
    (_mp("MoodCool", "Shades"), 15, [(0, 0), (180, 0), (214, .12), (300, .12), (310, 0), (MD, 0)]),
    (_mp("MoodCool", "Glint"), 15, [(0, 0), (84, 0), (104, 1.5), (150, 1.5), (170, 3), (MD, 3)]),
    (_mp("MoodCool", "Thumb"), 14, [(0, 260), (180, 260), (210, -14), (220, 6), (228, 0), (MD, 0)]),
    (_mp("MoodCool", "Thumb"), 15, [(0, 0), (229, 0)] + osc(230, 300, 20, -.1, .1) + [(310, 0), (MD, 0)]),
] + _sc("MoodCool", "Glint", [(0, 0), (84, 0), (92, 1.4), (104, 0), (150, 0), (158, 1.2), (170, 0), (MD, 0)]) +
    _sc("MoodCool", "Thumb", [(0, 1), (210, 1), (220, 1.15), (230, 1), (MD, 1)]))
