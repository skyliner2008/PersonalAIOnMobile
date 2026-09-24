# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 18. MUSIC ----------------------------------------------------------------
SCENE_STORIES["SceneMusic"] = (510, [
    (0, "Neutral", MUSIC, G_NONE, F_NONE),
    (96, "Happy", MUSIC, G_NONE, F_NONE),
    (510, "Happy", MUSIC, G_NONE, F_NONE),
], [
    ("blinkL", 14, [(0, 0), (30, 0), (44, -20), (90, -20), (96, 0), (510, 0)]),
    ("blinkR", 14, [(0, 0), (30, 0), (44, -20), (90, -20), (96, 0), (510, 0)]),
    ("face", 15, [(0, 0), (155, 0)] + osc(156, 456, 30, -.08, .08) + [(468, 0), (510, 0)]),
    ("face", 13, [(0, 0), (155, 0)] + osc(156, 456, 30, -14, 14) + [(468, 0), (510, 0)]),
    ("blink", 17, [(0, 1), (155, 1)] + osc(156, 456, 15, 1, .45) + [(468, 1), (510, 1)]),
    (_sp("SceneMusic", "Phones"), 14, [(0, -320), (36, -320), (90, 0), (96, 8), (104, 0), (510, 0)]),
    (_sp("SceneMusic", "EQ"), 18, [(0, 0), (128, 0), (136, 1), (468, 1), (480, 0), (510, 0)]),
    (_sp("SceneMusic", "Notes"), 18, [(0, 0), (150, 0), (166, 1), (456, 1), (480, 0), (510, 0)]),
])

HAND_STORIES.update(SCENE_STORIES)

(MOOD_BALL, MOOD_YOYO, MOOD_STARS, MOOD_SHOCK, MOOD_SAD, MOOD_CURIOUS, MOOD_MISSILE, MOOD_FUME,
 MOOD_GLITCH, MOOD_THINK, MOOD_HAPPY, MOOD_LOVE, MOOD_GLAD, MOOD_COOL, MOOD_SHY, MOOD_EMBARRASSED,
 MOOD_SHOWOFF, MOOD_LISTEN, MOOD_ARROGANT) = (P_NAMES.index(n) for n in MOOD_NAMES)
