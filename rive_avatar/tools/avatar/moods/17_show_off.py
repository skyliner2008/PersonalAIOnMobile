# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 17. SHOW-OFF -------------------------------------------------------------
_flick = [(0, 0), (179, 0), (190, -40), (200, -40), (214, 40), (224, 40), (238, -40), (248, -40), (262, 40), (272, 40), (290, 0), (MD, 0)]
MOOD_STORIES["MoodShowOff"] = (MD, [
    (0, "Smug", MOOD_SHOWOFF, G_NONE, F_NONE),
    (300, "Smug", MOOD_SHOWOFF, G_SPOTLIGHT, F_NONE),
    (MD, "Smug", MOOD_SHOWOFF, G_SPOTLIGHT, F_NONE),
], _eyes(17, [(0, 1), (330, 1), (350, .75), (MD, .75)]) +
   _eyes(14, [(0, 0), (330, 0), (350, 6), (MD, 6)]) +
   _eyes(13, [(f, v * -.3) for f, v in _flick]) + [
    ("face", 13, _flick),
    ("face", 15, [(f, v * -.0035) for f, v in _flick]),
    ("face", 17, [(0, 1), (60, 1), (170, 1.14), (180, 1.14), (190, 1), (309, 1)] + osc(310, MD, 20, 1.03, .97)),
    ("face", 14, [(0, 0), (60, 0), (170, -20), (190, 0), (MD, 0)]),
    (_mp("MoodShowOff", "Shades"), 14, _shades_y + [(170, -20), (190, 0), (300, 0), (330, -360), (MD, -360)]),
    (_mp("MoodShowOff", "Shades"), 13, _flick),
    (_mp("MoodShowOff", "Shades"), 15, [(f, v * -.0035) for f, v in _flick]),
    (_mp("MoodShowOff", "Stars"), 18, [(0, 0), (186, 0), (192, 1), (206, 0), (212, 1), (226, 0), (236, 1), (250, 0),
                                       (260, 1), (274, 0), (300, 0), (310, 1), (MD, 1)]),
] + _sc("MoodShowOff", "Stars", [(0, 1), (179, 1)] + osc(180, MD, 12, .8, 1.2)))
