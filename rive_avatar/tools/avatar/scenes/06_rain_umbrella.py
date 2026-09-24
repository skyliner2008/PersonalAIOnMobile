# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 6. RAIN / UMBRELLA -------------------------------------------------------
SCENE_STORIES["SceneRain"] = (480, [
    (0, "Neutral", RAIN, G_DEEPBLUE, F_NONE),
    (84, "Sleepy", RAIN, G_DEEPBLUE, F_NONE),
    (144, "Sad", RAIN, G_DEEPBLUE, F_NONE),
    (204, "Shock", RAIN, G_DEEPBLUE, F_NONE),
    (252, "Happy", RAIN, G_DEEPBLUE, F_NONE),
    (480, "Happy", RAIN, G_NONE, F_NONE),
], [
    ("blinkL", 14, [(0, 0), (24, 0), (40, -20), (84, -20), (96, 0), (480, 0)]),
    ("blinkR", 14, [(0, 0), (24, 0), (40, -20), (84, -20), (96, 0), (480, 0)]),
    ("face", 14, [(0, 0), (84, 0), (92, 14), (110, 6), (204, 6), (212, -8), (230, 0), (480, 0)]),
    ("face", 15, [(0, 0), (251, 0)] + osc(252, 408, 26, -.05, .05) + [(420, 0), (480, 0)]),
    (_sp("SceneRain", "Cloud"), 13, [(0, -380), (24, -380), (70, 0), (420, 0), (470, 400), (480, 400)]),
    (_sp("SceneRain", "Drops"), 18, [(0, 0), (80, 0), (86, 1), (410, 1), (420, 0), (480, 0)]),
    (_sp("SceneRain", "HeadDrop"), 18, [(0, 0), (140, 0), (146, 1), (200, 1), (210, 0), (480, 0)]),
    (_sp("SceneRain", "HeadDrop"), 14, [(0, 0), (140, 0), (200, 30), (480, 30)]),
    (_sp("SceneRain", "Umbrella"), 14, [(0, 640), (200, 640), (232, 0), (480, 0)]),
    (_sp("SceneRain", "Canopy"), 16, [(0, .15), (232, .15), (250, 1.1), (260, 1), (480, 1)]),
    (_sp("SceneRain", "Rainbow"), 18, [(0, 0), (420, 0), (446, .8), (480, .8)]),
])
