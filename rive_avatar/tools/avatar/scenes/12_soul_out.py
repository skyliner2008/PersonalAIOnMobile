# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 12. SOUL OUT -------------------------------------------------------------
SCENE_STORIES["SceneSoulOut"] = (480, [
    (0, "Tired", SOUL, G_NONE, F_NONE),
    (60, "Bored", SOUL, G_NONE, F_NONE),
    (120, "Dead", SOUL, G_NONE, F_NONE),
    (324, "Shock", SOUL, G_NONE, F_NONE),
    (372, "Dizzy", SOUL, G_NONE, F_NONE),
    (480, "Dizzy", SOUL, G_NONE, F_NONE),
], [
    ("face", 14, [(0, 0), (60, 0), (110, 20), (324, 20), (330, -12), (344, 0), (480, 0)]),
    ("face", 15, [(0, 0), (60, 0), (110, .08), (324, .08), (334, 0), (480, 0)]),
    ("blink", 17, [(0, 1), (60, 1), (90, .6), (120, 1), (480, 1)]),
    (_sp("SceneSoul", "Sweat"), 18, [(0, 0), (56, 0), (62, 1), (120, 1), (130, 0), (480, 0)]),
    (_sp("SceneSoul", "Sweat"), 14, [(0, 0), (56, 0), (120, 34), (480, 34)]),
    (_sp("SceneSoul", "Soul"), 14, [(0, 40), (120, 40), (300, -90), (324, -90), (340, 40), (480, 40)]),
    (_sp("SceneSoul", "Soul"), 18, [(0, 0), (118, 0), (132, .95), (330, .95), (344, 0), (480, 0)]),
    (_sp("SceneSoul", "Soul"), 13, [(0, 0), (129, 0)] + osc(130, 320, 32, -22, 22) + [(330, 0), (480, 0)]),
] + _scale("SceneSoul", "Soul", [(0, 1), (324, 1), (344, .2), (480, .2)]) + [
    (_sp("SceneSoul", "Stars"), 18, [(0, 0), (370, 0), (380, 1), (480, 1)]),
])
