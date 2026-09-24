# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 1. IDLE: BALL ------------------------------------------------------------
_c = [160, 196, 232, 268, 300]
_bx = [-96, 96, -96, 96, -96]
_ball_y = [(0, -260), (118, -260), (160, 23)]
for _i in range(1, len(_c)):
    _ball_y += [((_c[_i - 1] + _c[_i]) // 2, -80), (_c[_i], 23)]
_ball_y += [(336, -420), (MD, -420)]


def _hit_eye(side):
    """Gaze toward the ball + a squash-and-stretch every time this eye heads it."""
    hits = [c for c, x in zip(_c, _bx) if (x < 0) == (side == "L")]
    y = {0: 0, 110: 0, 125: -14, 290: -14}
    sy = {0: 1, 18: 1, 22: .08, 28: .08, 34: 1, 150: 1}
    sx = {0: 1, 150: 1}
    for c in hits:
        y.update({c - 8: -14, c - 2: -24, c + 4: -6, c + 12: -16, c + 20: -14})
        sy.update({c - 8: 1, c - 2: 1.22, c + 4: .78, c + 12: 1.08, c + 20: 1})
        sx.update({c - 8: 1, c - 2: .9, c + 4: 1.1, c + 12: .96, c + 20: 1})
    y.update({320: -30, 345: 14, MD: 14})
    sy.update({322: 1, 340: 1, 352: .25, 362: 1, 380: 1, 392: .3, 404: 1, MD: 1})
    sx.update({322: 1, MD: 1})
    return sorted(y.items()), sorted(sy.items()), sorted(sx.items())


_yl, _syl, _sxl = _hit_eye("L")
_yr, _syr, _sxr = _hit_eye("R")
_gx = [(0, 0), (40, 0), (70, -28), (80, -28), (115, 28), (125, 0)] + \
      [(c, x * .22) for c, x in zip(_c, _bx)] + [(336, 30), (360, 0), (MD, 0)]
MOOD_STORIES["MoodIdleBall"] = (MD, [
    (0, "Neutral", MOOD_BALL, G_NONE, F_NONE),
    (128, "Happy", MOOD_BALL, G_NONE, F_NONE),
    (340, "Sleepy", MOOD_BALL, G_NONE, F_NONE),
    (360, "Sleepy", P_ZZZ, G_NONE, F_NONE),
    (MD, "Sleepy", P_ZZZ, G_NONE, F_NONE),
], [
    ("blinkL", 14, _yl), ("blinkR", 14, _yr), ("blinkL", 17, _syl), ("blinkR", 17, _syr),
    ("blinkL", 16, _sxl), ("blinkR", 16, _sxr),
    ("blinkL", 13, _gx), ("blinkR", 13, _gx),
    ("face", 14, [(0, 0), (340, 0), (356, 10), (MD, 10)]),
    (_mp("MoodBall", "Ball"), 13, [(0, -96), (160, -96)] + [(c, x) for c, x in zip(_c[1:], _bx[1:])] + [(336, 330), (MD, 330)], "linear"),
    (_mp("MoodBall", "Ball"), 14, _ball_y),
    (_mp("MoodBall", "Ball"), 15, [(0, 0), (160, 0), (336, 9)], "linear"),
    (_mp("MoodBall", "Ball"), 18, [(0, 0), (116, 0), (122, 1), (330, 1), (336, 0), (MD, 0)]),
])
