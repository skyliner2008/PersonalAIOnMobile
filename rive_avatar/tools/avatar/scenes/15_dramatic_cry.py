# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 15. DRAMATIC CRY ---------------------------------------------------------
SCENE_STORIES["SceneCry"] = (510, [
    (0, "Sad", CRY, G_DEEPBLUE, F_NONE),
    (108, "Shock", CRY, G_DEEPBLUE, F_NONE),
    (156, "Sad", CRY, G_DEEPBLUE, F_NONE),
    (240, "Crying", CRY, G_DEEPBLUE, F_NONE),
    (432, "Sad", CRY, G_DEEPBLUE, F_NONE),
    (510, "Sad", CRY, G_DEEPBLUE, F_NONE),
], [
    ("blink", 17, [(0, 1), (48, 1), (52, .85), (56, 1), (60, .85), (64, 1), (156, 1), (164, .4), (176, 1), (190, .4),
                   (202, 1), (510, 1)]),
    ("face", 14, [(0, 0), (156, 0), (164, 10), (176, 0), (190, 10), (202, 0), (510, 0)]),
    ("face", 13, [(0, 0), (239, 0)] + osc(240, 420, 20, -4, 4) + [(432, 0), (510, 0)]),
    (_sp("SceneCry", "HalfL"), 13, [(0, -360), (40, -360), (80, 0), (108, 0), (118, -26), (510, -26)]),
    (_sp("SceneCry", "HalfR"), 13, [(0, -360), (40, -360), (80, 0), (108, 0), (118, 26), (510, 26)]),
    (_sp("SceneCry", "HalfL"), 15, [(0, 0), (108, 0), (118, -.4), (200, -.9), (510, -.9)]),
    (_sp("SceneCry", "HalfR"), 15, [(0, 0), (108, 0), (118, .4), (200, .9), (510, .9)]),
    (_sp("SceneCry", "HalfL"), 14, [(0, 0), (118, 0), (210, 420), (510, 420)], "easeIn"),
    (_sp("SceneCry", "HalfR"), 14, [(0, 0), (118, 0), (210, 420), (510, 420)], "easeIn"),
    (_sp("SceneCry", "HalfL"), 18, [(0, 1), (196, 1), (212, 0), (510, 0)]),
    (_sp("SceneCry", "HalfR"), 18, [(0, 1), (196, 1), (212, 0), (510, 0)]),
    (_sp("SceneCry", "Crack"), 18, [(0, 0), (96, 0), (100, 1), (110, 1), (114, 0), (510, 0)]),
    (_sp("SceneCry", "Cloud"), 14, [(0, -160), (160, -160), (196, 0), (510, 0)]),
    (_sp("SceneCry", "Drops"), 18, [(0, 0), (240, 0), (250, 1), (420, 1), (432, 0), (510, 0)]),
    (_sp("SceneCry", "Tissue"), 13, [(0, 160), (430, 160), (470, 0), (510, 0)]),
    (_sp("SceneCry", "Tissue"), 15, [(0, 0), (470, 0), (480, .15), (490, -.15), (500, .1), (510, 0)]),
])
