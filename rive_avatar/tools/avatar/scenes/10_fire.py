# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 10. FIRE -----------------------------------------------------------------
_run = osc(140, 310, 14, -60, 60)
SCENE_STORIES["SceneFire"] = (450, [
    (0, "Neutral", FIRE, G_NONE, F_NONE),
    (48, "Confused", FIRE, G_NONE, F_NONE),
    (96, "Shock", FIRE, G_NONE, F_NONE),
    (132, "Dead", FIRE, G_REDALERT, F_NONE),
    (324, "Happy", FIRE, G_NONE, F_NONE),
    (372, "Dizzy", FIRE, G_NONE, F_SMOKE),
    (450, "Dizzy", FIRE, G_NONE, F_SMOKE),
], [
    ("blinkL", 14, [(0, 0), (40, 0), (56, 22), (96, 22), (106, 0), (450, 0)]),
    ("blinkR", 14, [(0, 0), (40, 0), (56, 22), (96, 22), (106, 0), (450, 0)]),
    ("face", 13, [(0, 0), (139, 0)] + _run + [(324, 0), (450, 0)]),
    ("face", 14, [(0, 0), (139, 0)] + osc(140, 310, 7, 0, -10) + [(324, 0), (450, 0)]),
    (_sp("SceneFire", "Flames"), 13, [(0, 0), (139, 0)] + _run + [(324, 0), (450, 0)]),
] + _scale("SceneFire", "Flames", [(0, 0), (44, 0), (50, .2), (60, .5), (96, .5), (110, 1.2), (310, 1.2), (330, 0), (450, 0)]) + [
    (_sp("SceneFire", "Sweat"), 18, [(0, 0), (130, 0), (136, 1), (316, 1), (324, 0), (450, 0)]),
    (_sp("SceneFire", "Bucket"), 14, [(0, -160), (300, -160), (320, 0), (372, 0), (400, -180), (450, -180)]),
    (_sp("SceneFire", "Bucket"), 15, [(0, 0), (300, 0), (318, 1.9), (372, 1.9), (400, 0), (450, 0)]),
    (_sp("SceneFire", "Splash"), 18, [(0, 0), (318, 0), (324, 1), (360, 0), (450, 0)]),
    (_sp("SceneFire", "Splash"), 14, [(0, 0), (318, 0), (360, 80), (450, 80)]),
    (_sp("SceneFire", "Soot"), 18, [(0, 0), (368, 0), (380, 1), (450, 1)]),
])
