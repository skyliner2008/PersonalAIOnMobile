# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 9. ROYAL -----------------------------------------------------------------
SCENE_STORIES["SceneRoyal"] = (480, [
    (0, "Neutral", ROYAL, G_NONE, F_NONE),
    (36, "Listening", ROYAL, G_SPOTLIGHT, F_NONE),
    (84, "Starry", ROYAL, G_SPOTLIGHT, F_NONE),
    (176, "Happy", ROYAL, G_SPOTLIGHT, F_NONE),
    (216, "Smug", ROYAL, G_SPOTLIGHT, F_NONE),
    (480, "Smug", ROYAL, G_SPOTLIGHT, F_NONE),
], [
    ("blinkL", 14, [(0, 0), (30, 0), (44, -20), (170, -20), (180, 0), (480, 0)]),
    ("blinkR", 14, [(0, 0), (30, 0), (44, -20), (170, -20), (180, 0), (480, 0)]),
    ("face", 14, [(0, 0), (170, 0), (186, 14), (206, 0), (276, -8), (480, -8)]),
    ("face", 15, [(0, 0), (275, 0)] + osc(276, 470, 40, -.03, .03) + [(480, 0)]),
    (_sp("SceneRoyal", "Crown"), 14, [(0, -260), (84, -260), (176, 0), (186, 8), (196, 0), (480, 0)], "soft"),
    (_sp("SceneRoyal", "Crown"), 15, [(0, -.6), (84, -.6), (176, 0), (480, 0)]),
    (_sp("SceneRoyal", "Cape"), 16, [(0, 0), (210, 0), (236, 1.1), (246, 1), (480, 1)]),
    (_sp("SceneRoyal", "Petals"), 18, [(0, 0), (270, 0), (290, 1), (480, 1)]),
    (_sp("SceneRoyal", "Sparkles"), 18, [(0, 0), (210, 0), (220, 1), (480, 1)]),
])
