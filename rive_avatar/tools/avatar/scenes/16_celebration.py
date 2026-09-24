# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 16. CELEBRATION ----------------------------------------------------------
SCENE_STORIES["SceneCelebrate"] = (510, [
    (0, "Neutral", PARTY, G_NONE, F_NONE),
    (96, "Excited", PARTY, G_NONE, F_NONE),
    (144, "Laughing", PARTY, G_BOKEH, F_CONFETTI),
    (180, "Starry", PARTY, G_BOKEH, F_CONFETTI),
    (408, "Happy", PARTY, G_BOKEH, F_CONFETTI),
    (510, "Happy", PARTY, G_BOKEH, F_CONFETTI),
], [
    ("blinkL", 14, [(0, 0), (30, 0), (40, -20), (66, -20), (72, 0), (96, 20), (140, 20), (148, 0), (510, 0)]),
    ("blinkR", 14, [(0, 0), (30, 0), (40, -20), (66, -20), (72, 0), (96, 20), (140, 20), (148, 0), (510, 0)]),
    ("blinkL", 13, [(0, 0), (96, 0), (106, -18), (140, -18), (148, 0), (510, 0)]),
    ("blinkR", 13, [(0, 0), (96, 0), (106, -18), (140, -18), (148, 0), (510, 0)]),
    ("face", 14, [(0, 0), (179, 0)] + osc(180, 384, 30, 0, -22) + [(400, 0), (510, 0)]),
    (_sp("SceneParty", "Hat"), 14, [(0, -260), (36, -260), (64, 0), (70, 8), (76, 0), (510, 0)]),
    (_sp("SceneParty", "Hat"), 15, [(0, .6), (36, .6), (64, -.12), (510, -.12)]),
    (_sp("SceneParty", "Popper"), 14, [(0, 170), (90, 170), (120, 0), (144, 0), (150, 14), (160, 0), (510, 0)]),
] + _scale("SceneParty", "Burst", [(0, 0), (140, 0), (150, 1.3), (176, 1.7), (510, 1.7)]) + [
    (_sp("SceneParty", "Burst"), 18, [(0, 0), (140, 0), (144, 1), (182, 0), (510, 0)]),
    (_sp("SceneParty", "Balloons"), 14, [(0, 120), (180, 120), (420, -300), (500, -600), (510, -600)], "linear"),
])
