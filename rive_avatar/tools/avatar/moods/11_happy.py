# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 11. HAPPY ----------------------------------------------------------------
MOOD_STORIES["MoodHappy"] = (MD, [
    (0, "Happy", MOOD_HAPPY, G_NONE, F_NONE),
    (330, "Beam", MOOD_HAPPY, G_NONE, F_NONE),
    (MD, "Beam", MOOD_HAPPY, G_NONE, F_NONE),
], [
    ("blinkL", 13, [(0, 0), (300, 0), (330, 90), (345, 90), (362, -14), (374, 4), (384, 0), (MD, 0)]),
    ("blinkR", 13, [(0, 0), (300, 0), (330, -90), (345, -90), (362, 14), (374, -4), (384, 0), (MD, 0)]),
] + _eyes(17, osc(0, 176, 8, 1, .8) + [(180, 1)] + osc(190, 290, 20, 1.3, .75) +
          [(300, 1), (330, 1.1), (345, 1.1), (362, .9), (374, 1), (MD, 1)]) +
    _eyes(16, [(0, 1), (300, 1), (330, .8), (345, .8), (362, 1.08), (374, 1), (MD, 1)]) + [
    ("face", 14, osc(0, 176, 8, 0, -14) + [(180, 0)] + osc(190, 290, 20, -20, 8) + [(300, 0), (MD, 0)]),
    (_mp("MoodHappy", "Twinkle"), 18, osc(0, MD, 12, .3, 1)),
    (_mp("MoodHappy", "Burst"), 18, [(0, 0), (180, 0), (184, 1), (270, 1), (290, 0), (MD, 0)]),
] + _sc("MoodHappy", "Twinkle", osc(0, MD, 15, .7, 1.1)) +
    _sc("MoodHappy", "Burst", [(0, .3), (180, .3), (290, 1.7), (MD, 1.7)], "easeOut"))
