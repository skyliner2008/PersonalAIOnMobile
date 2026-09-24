# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 17. VR MODE --------------------------------------------------------------
def _card(start_x, t0, t1, t2, t3):
    """Two passes of an isometric card from a bottom corner to the top centre."""
    x = [(0, start_x), (t0, start_x), (t1, 0), (t1 + 1, start_x), (t2, start_x), (t3, 0), (540, 0)]
    y = [(0, 150), (t0, 150), (t1, -210), (t1 + 1, 150), (t2, 150), (t3, -210), (540, -210)]
    s = [(0, 1.1), (t0, 1.1), (t1, .15), (t1 + 1, 1.1), (t2, 1.1), (t3, .15), (540, .15)]
    o = [(0, 0), (t0, 0), (t0 + 8, 1), (t1 - 10, 1), (t1, 0), (t2, 0), (t2 + 8, 1), (t3 - 10, 1), (t3, 0), (540, 0)]
    return x, y, s, o


_vr_extras = [
    ("blinkL", 14, [(0, 0), (48, 0), (62, 24), (84, 24), (156, -6), (170, 0), (540, 0)]),
    ("blinkR", 14, [(0, 0), (48, 0), (62, 24), (84, 24), (156, -6), (170, 0), (540, 0)]),
    ("blink", 17, [(0, 1), (150, 1), (158, .1), (176, .1), (190, 1), (540, 1)]),
    ("face", 15, [(0, 0), (227, 0)] + osc(228, 492, 66, -.07, .07) + [(504, 0), (540, 0)]),
    ("face", 14, [(0, 0), (160, 0), (170, 10), (182, 0), (228, 0), (300, -12), (372, 6), (444, -10), (504, 0),
                  (516, -10), (528, 0), (540, 0)]),
    (_sp("SceneVR", "Visor"), 14, [(0, 430), (48, 430), (60, 320), (84, 230), (110, 160), (140, 40), (160, -8), (170, 0), (540, 0)]),
    (_sp("SceneVR", "Visor"), 15, [(0, .2), (84, .18), (110, -.12), (140, .08), (168, 0), (540, 0)]),
    (_sp("SceneVR", "Visor"), 16, [(0, .8), (84, .8), (168, 1), (174, 1.06), (182, 1), (540, 1)]),
    (_sp("SceneVR", "Visor"), 17, [(0, .8), (84, .8), (168, 1), (174, 1.06), (182, 1), (540, 1)]),
    (_sp("SceneVR", "Lens"), 18, [(0, 0), (186, 0), (194, 1), (206, .4), (214, 1), (540, 1)]),
    (_sp("SceneVR", "Popcorn"), 14, [(0, 180), (300, 180), (330, 0), (540, 0)]),
    (_sp("SceneVR", "Sparkles"), 18, [(0, 0), (500, 0), (508, 1), (540, 1)]),
]
for _label, (_sx, _a, _b, _c, _d) in (("CardA", (-210, 228, 348, 372, 492)),
                                     ("CardB", (210, 276, 396, 420, 536)),
                                     ("CardC", (-210, 324, 444, 460, 539))):
    _x, _y, _s, _o = _card(_sx, _a, _b, _c, _d)
    _vr_extras += [(_sp("SceneVR", _label), 13, _x, "soft"), (_sp("SceneVR", _label), 14, _y, "soft"),
                   (_sp("SceneVR", _label), 16, _s, "soft"), (_sp("SceneVR", _label), 17, _s, "soft"),
                   (_sp("SceneVR", _label), 18, _o)]
SCENE_STORIES["SceneVR"] = (540, [
    (0, "Neutral", VR, G_NONE, F_NONE),
    (150, "Sleepy", VR, G_NONE, F_NONE),
    (172, "Blank", VR, G_NONE, F_NONE),
    (540, "Blank", VR, G_NONE, F_NONE),
], _vr_extras)
