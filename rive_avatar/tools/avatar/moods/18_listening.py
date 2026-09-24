# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 18. LISTENING ------------------------------------------------------------
_rx, _ry = _circle(300, MD, 120, 14)
_wave_o = [(0, 0), (99, 0), (100, 1), (129, 1), (130, 0), (159, 0), (160, 1), (179, 1), (180, 0), (MD, 0)]
MOOD_STORIES["MoodListening"] = (MD, [
    (0, "Neutral", MOOD_LISTEN, G_NONE, F_NONE),
    (100, "Blank", MOOD_LISTEN, G_NONE, F_NONE),
    (130, "Neutral", MOOD_LISTEN, G_NONE, F_NONE),
    (160, "Blank", MOOD_LISTEN, G_NONE, F_NONE),
    (180, "Neutral", MOOD_LISTEN, G_NONE, F_NONE),
    (300, "Happy", MOOD_LISTEN, G_NONE, F_NONE),
    (MD, "Happy", MOOD_LISTEN, G_NONE, F_NONE),
], _eyes(13, [(0, 0), (179, 0)] + osc(180, 290, 15, -12, 12) + _rx) +
   _eyes(14, [(0, 0), (20, -14), (60, -14), (70, 0), (290, 0)] + _ry) +
   _eyes(17, [(0, 1), (179, 1)] + osc(180, 290, 15, .85, 1.1) + [(300, 1), (MD, 1)]) + [
    ("face", 14, [(0, 0), (179, 0)] + osc(180, 290, 15, 4, -4) + [(300, 0), (MD, 0)]),
    ("face", 15, [(0, 0), (299, 0)] + osc(300, MD, 30, -.08, .08)),
    (_mp("MoodListen", "Phones"), 14, [(0, -340), (20, -340), (64, 0), (70, 8), (76, 0), (MD, 0)]),
    (_mp("MoodListen", "WaveL"), 18, _wave_o),
    (_mp("MoodListen", "WaveR"), 18, _wave_o),
    (_mp("MoodListen", "WaveL"), 17, [(0, 1), (99, 1)] + osc(100, 180, 5, .5, 1.3)),
    (_mp("MoodListen", "WaveR"), 17, [(0, 1), (99, 1)] + osc(100, 180, 5, 1.3, .5)),
] + _sc("MoodListen", "Phones", [(0, 1), (64, 1), (72, 1.05), (80, 1), (179, 1)] + osc(180, MD, 15, 1.03, 1)))
