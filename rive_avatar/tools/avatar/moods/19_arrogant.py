# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 19. ARROGANT -------------------------------------------------------------
MOOD_STORIES["MoodArrogant"] = (MD, [
    (0, "Haughty", MOOD_ARROGANT, G_NONE, F_NONE),
    (350, "Sleepy", MOOD_ARROGANT, G_NONE, F_NONE),
    (MD, "Sleepy", MOOD_ARROGANT, G_NONE, F_NONE),
], _eyes(14, [(0, 0), (40, -16), (180, -16), (220, -28), (300, -28), (330, -8), (MD, -8)]) +
   _eyes(13, [(0, 0), (180, 0), (290, -18), (MD, -18)]) +
   _eyes(17, [(0, 1), (300, 1), (340, .5), (MD, .5)]) + [
    ("face", 14, [(0, 0), (40, -40), (MD, -40)]),
    ("face", 17, [(0, 1), (40, 1.1), (300, 1.1), (330, 1), (MD, 1)]),
    ("face", 13, [(0, 0), (200, 0), (290, -34), (330, -34), (380, -70), (MD, -70)]),
    ("face", 15, [(0, 0), (200, 0), (290, -.1), (MD, -.1)]),
    ("face", 16, [(0, 1), (330, 1), (380, .86), (MD, .86)]),
    (_mp("MoodArrogant", "Crown"), 14, [(0, -260), (180, -260), (210, -40), (218, -48), (226, -40), (MD, -40)]),
    (_mp("MoodArrogant", "Crown"), 13, [(0, 0), (200, 0), (290, -34), (330, -34), (380, -70), (MD, -70)]),
    (_mp("MoodArrogant", "Crown"), 15, [(0, 0), (200, 0), (290, -.1), (MD, -.1)]),
    (_mp("MoodArrogant", "Fan"), 13, [(0, 200), (190, 200), (220, 0), (MD, 0)]),
    (_mp("MoodArrogant", "Fan"), 15, [(0, 0), (219, 0)] + osc(220, MD, 12, -.25, .25)),
    (_mp("MoodArrogant", "Puff"), 18, [(0, 0), (310, 0), (314, 1), (360, 0), (MD, 0)]),
    (_mp("MoodArrogant", "Puff"), 13, [(0, 0), (310, 0), (360, 50), (MD, 50)]),
] + _sc("MoodArrogant", "Puff", [(0, .4), (310, .4), (360, 1.6), (MD, 1.6)]))
