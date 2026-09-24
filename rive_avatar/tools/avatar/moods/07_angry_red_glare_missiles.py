# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 7. ANGRY: RED GLARE & MISSILES -------------------------------------------
_launch = (190, 215, 240, 265)
_m_extra = []
for _i, _t in enumerate(_launch):
    _dx = 150 if _i < 2 else -150
    _nm = "M%d" % _i
    _m_extra += [
        (_mp("MoodMissile", _nm), 18, [(0, 0), (_t - 2, 0), (_t, 1), (_t + 34, 1), (_t + 40, 0), (MD, 0)]),
        (_mp("MoodMissile", _nm), 13, [(0, 0), (_t, 0), (_t + 40, _dx), (MD, _dx)], "easeIn"),
        (_mp("MoodMissile", _nm), 14, [(0, 0), (_t, 0), (_t + 40, -60), (MD, -60)], "easeIn"),
    ] + _sc("MoodMissile", _nm, [(0, .6), (_t, .6), (_t + 40, 2.8), (MD, 2.8)], "easeIn")
MOOD_STORIES["MoodAngryMissile"] = (MD, [
    (0, "Angry", MOOD_MISSILE, G_NONE, F_NONE),
    (60, "Rage", MOOD_MISSILE, G_REDALERT, F_NONE),
    (300, "Rage", MOOD_MISSILE, G_REDALERT, F_EXPLOSION),
    (336, "Evil", MOOD_MISSILE, G_REDALERT, F_EXPLOSION),
    (MD, "Evil", MOOD_MISSILE, G_REDALERT, F_EXPLOSION),
], [
    ("blinkL", 13, [(0, 0), (60, 0), (100, ANGRY_CONV), (MD, ANGRY_CONV)]),
    ("blinkR", 13, [(0, 0), (60, 0), (100, -ANGRY_CONV), (MD, -ANGRY_CONV)]),
] + _eyes(16, [(0, 1), (60, 1), (100, ANGRY_SX), (MD, ANGRY_SX)]) +
    _eyes(17, osc(0, 120, 5, .94, 1.06) + [(140, 1), (170, 1.2), (300, 1.2), (330, 1)] + osc(336, MD, 8, .8, 1.15)) + [
    ("face", 16, [(0, 1), (140, 1), (170, 1.12), (300, 1.12), (312, 1), (MD, 1)]),
    ("face", 17, [(0, 1), (140, 1), (170, 1.12), (300, 1.12), (312, 1), (MD, 1)]),
    ("face", 13, [(0, 0), (299, 0)] + osc(300, MD, 4, -9, 9)),
    ("face", 15, [(0, 0), (335, 0)] + osc(336, MD, 16, -.1, .1)),
    (_mp("MoodMissile", "PortL"), 17, [(0, 0), (130, 0), (150, 1.1), (158, 1), (MD, 1)]),
    (_mp("MoodMissile", "PortR"), 17, [(0, 0), (130, 0), (150, 1.1), (158, 1), (MD, 1)]),
    (_mp("MoodMissile", "Flash"), 18, [(0, 0), (298, 0), (300, .8), (318, 0), (MD, 0)]),
] + _m_extra)
