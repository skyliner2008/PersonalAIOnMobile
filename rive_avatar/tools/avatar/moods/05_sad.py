# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 5. SAD -> CRYING -----------------------------------------------------------
# Starts on the normal eye, then acts it out (enter -> act -> exit):
#   0-30    normal, the gaze sinks
#   30      eyes reshape into the sad droop (jelly, right eye a beat later)
#   46-218  sad: the eyes tremble unevenly, the lower lids quiver, two sniffs,
#           tears pool underneath
#   220     the eyes melt into crying
#   238-358 crying: stronger shaking, three sobs (the eyes squeeze, the head
#           hitches), tear streams run down, drops fall
#   360     back to sad, sniffing, the trembling fades out
_SAD_SHAPES = [(0, "round"), (30, "droop"), (220, "melt"), (360, "droop"), (MD, "droop")]
_SAD_WINDOWS = [(48, 218, 1.8), (238, 358, 3.4), (378, 414, 1.0)]
_sad_amp = envelope((40, 0), (80, 1.3), (218, 1.6), (240, 2.8), (356, 2.8), (385, 1.0), (412, 0))
_sad_y = [(0, 0), (30, 6), (220, 10), (360, 12), (MD, 10)]
_sad_y_l = piecewise(_sad_y)
_sad_y_r = piecewise(lagged(_sad_y, 4))
_sobs = (250, 287, 322)
_sniff_l, _sniff_r = sniffs((120, 176) + _sobs + (384, 402))
_sob_face = [(0, 0)]
for _s in _sobs:
    _sob_face += [(_s, 0), (_s + 5, -5), (_s + 12, 3), (_s + 20, 0)]
_sob_face.append((MD, 0))


def _drop_cycle(starts, fall=38, dist=190):
    ys, ops = [(0, 0)], [(0, 0)]
    for s0 in starts:
        ys += [(s0, 0), (s0 + fall, dist), (s0 + fall + 1, 0)]
        ops += [(s0, 0), (s0 + 2, 1), (s0 + fall - 6, 1), (s0 + fall, 0)]
    return ys + [(MD, 0)], ops + [(MD, 0)]


_drop_l = _drop_cycle((244, 292, 334))
_drop_r = _drop_cycle((256, 302, 348))

MOOD_STORIES["MoodSad"] = (MD, [
    (0, "Neutral", MOOD_SAD, G_NONE, F_NONE),
    (30, "Sad", MOOD_SAD, G_NONE, F_NONE),
    (220, "Crying", MOOD_SAD, G_NONE, F_NONE),
    (360, "Sad", MOOD_SAD, G_NONE, F_NONE),
    (MD, "Sad", MOOD_SAD, G_NONE, F_NONE),
],
    # each eye shakes on its own (different seeds), the right one follows the sinking late
    eye_pair(14, tremble(0, MD, _sad_amp, 11, base=_sad_y_l),
             tremble(0, MD, _sad_amp, 23, base=_sad_y_r)) +
    eye_pair(13, tremble(0, MD, lambda f: _sad_amp(f) * 0.7, 31),
             tremble(0, MD, lambda f: _sad_amp(f) * 0.7, 47)) +
    eye_pair(17, _sniff_l, _sniff_r) +
    # the outline itself quivers: lower lid (3, 4, 5) and, softer, the upper lid
    vertex_quiver("L", (3, 4, 5), _SAD_SHAPES, _SAD_WINDOWS, 5) +
    vertex_quiver("R", (3, 4, 5), _SAD_SHAPES, _SAD_WINDOWS, 9) +
    vertex_quiver("L", (7, 0, 1), _SAD_SHAPES, [(a, b, amp * 0.45) for a, b, amp in _SAD_WINDOWS], 15) +
    vertex_quiver("R", (7, 0, 1), _SAD_SHAPES, [(a, b, amp * 0.45) for a, b, amp in _SAD_WINDOWS], 21) + [
    ("face", 14, _sob_face),
    ("face", 15, [(0, 0), (40, 0), (120, .035), (220, .02), (360, .045), (MD, .03)]),
    ("mouth", 13, tremble(220, 360, 1.6, 61)),
    ("mouth", 14, tremble(220, 360, 1.2, 67)),
] + [
    # the old water pool sits across the middle of the reshaped eyes: kept hidden
    (_mp("MoodSad", "PoolL"), 18, [(0, 0), (MD, 0)]),
    (_mp("MoodSad", "PoolR"), 18, [(0, 0), (MD, 0)]),
] + [
    (_mp("MoodSad", "StreamL"), 17, [(0, 0), (238, 0), (272, 1), (352, 1.05), (372, 0), (MD, 0)]),
    (_mp("MoodSad", "StreamR"), 17, [(0, 0), (246, 0), (282, 1), (356, 1.05), (376, 0), (MD, 0)]),
    (_mp("MoodSad", "StreamL"), 18, [(0, 0), (236, 0), (240, 1), (370, 1), (376, 0), (MD, 0)]),
    (_mp("MoodSad", "StreamR"), 18, [(0, 0), (244, 0), (248, 1), (374, 1), (380, 0), (MD, 0)]),
    (_mp("MoodSad", "StreamL"), 13, tremble(240, 372, 1.5, 81)),
    (_mp("MoodSad", "StreamR"), 13, tremble(248, 376, 1.5, 83)),
    (_mp("MoodSad", "DropL"), 14, _drop_l[0], "easeIn"),
    (_mp("MoodSad", "DropL"), 18, _drop_l[1]),
    (_mp("MoodSad", "DropR"), 14, _drop_r[0], "easeIn"),
    (_mp("MoodSad", "DropR"), 18, _drop_r[1]),
])
