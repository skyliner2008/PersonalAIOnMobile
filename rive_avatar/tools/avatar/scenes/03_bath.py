# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 3. BATH ------------------------------------------------------------------
SCENE_STORIES["SceneBath"] = (480, [
    (0, "Neutral", BATH, G_NONE, F_NONE),
    (72, "Sleepy", BATH, G_NONE, F_NONE),
    (120, "Laughing", BATH, G_NONE, F_NONE),
    (300, "Excited", BATH, G_NONE, F_NONE),
    (360, "Happy", BATH, G_NONE, F_NONE),
    (480, "Happy", BATH, G_NONE, F_NONE),
], [
    ("blinkL", 14, [(0, 0), (24, 0), (36, -20), (66, -20), (72, 0), (480, 0)]),
    ("blinkR", 14, [(0, 0), (24, 0), (36, -20), (66, -20), (72, 0), (480, 0)]),
    ("face", 13, [(0, 0), (119, 0)] + osc(120, 270, 15, -10, 10) + [(290, 0), (359, 0)] + osc(360, 468, 12, -14, 14) + [(480, 0)]),
    ("face", 14, [(0, 0), (72, 0), (78, 12), (96, 0), (480, 0)]),
    (_sp("SceneBath", "Shower"), 14, [(0, -150), (24, -150), (56, 0), (420, 0), (470, -160), (480, -160)]),
    (_sp("SceneBath", "Water"), 18, [(0, 0), (66, 0), (72, 1), (290, 1), (300, 0), (480, 0)]),
    (_sp("SceneBath", "Water"), 14, [(0, 0), (72, 0)] + osc(72, 290, 10, 0, 16) + [(300, 0)]),
] + _scale("SceneBath", "Bubbles", [(0, 0), (118, 0), (150, 1.1), (160, 1), (290, 1), (300, 1.3), (306, 0), (480, 0)]) + [
    (_sp("SceneBath", "Sponge"), 13, [(0, 0), (119, 0)] + osc(120, 280, 20, -80, 80) + [(300, 0)]),
    (_sp("SceneBath", "Sponge"), 18, [(0, 0), (110, 0), (120, 1), (282, 1), (296, 0), (480, 0)]),
    (_sp("SceneBath", "Clean"), 18, [(0, 0), (296, 0), (304, 1), (360, 0), (480, 0)]),
    (_sp("SceneBath", "Duck"), 13, [(0, -420), (360, -420), (470, 120), (480, 120)], "linear"),
    (_sp("SceneBath", "Duck"), 18, [(0, 0), (356, 0), (362, 1), (480, 1)]),
])
