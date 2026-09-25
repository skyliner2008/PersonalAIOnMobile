# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

import sys

# --------------------------------------------------------------- SAFETY GUARDS
# 1) the two eyes must never touch, however far a story slides them together
MIN_EYE_GAP = 22.0        # px between the inner edges

_EYE_HALF_W_AUTHORED = {  # half of each variant's width, as authored (radius 54)
    V_ROUND: EYE_R * EYE_OX, V_WIDE: (EYE_R + 9) * EYE_OX, V_TINY: 22 * EYE_OX, V_DOME: EYE_R,
    # a cross is two thin strokes, not a solid block -- its bounding box
    # overstates how close the pair actually reads
    V_BAR: 46, V_BARTILT: 47, V_CROSS: 60, V_CHEV: 46, V_HEART: 53,
    V_ARC: 61, V_ARCDOWN: 61, V_HALFLID: EYE_R, V_SPIRAL: 55,
    V_DOLLAR: 33, V_STAREYE: 59, V_EMPTY: 0, V_RING: 51,
    # the morph eye: measured from the shapes below (the Eye Lab can draw any width)
    V_MORPH: EYE_R * EYE_OX,
    # slabs are rotated, so the bounding half-width grows
    V_WEDGE: (2 * EYE_R * math.cos(.30) + 42 * math.sin(.30)) / 2,
    V_ANGRY: ((2 * EYE_R + 8) * math.cos(.34) + 72 * math.sin(.34)) / 2,
    V_EVIL: ((2 * EYE_R + 4) * math.cos(.20) + 68 * math.sin(.20)) / 2,
    V_DROOP: (2 * EYE_R * math.cos(.30) + 62 * math.sin(.30)) / 2,
}
# The morph eye's inner half-width (toward the nose) is the widest of its shapes: +x of a
# left-eye outline, -x of a separately drawn right eye. Only the inner side can touch the
# other eye; the outer side is checked against the screen by eye_fit_warnings() below.
_morph_inner = [max(p[0] for p in pts) for pts, _s in MORPH_SHAPES.values()]
_morph_inner += [max(-p[0] for p in pts) for pts, _s in MORPH_SHAPES_R.values()]
_EYE_HALF_W_AUTHORED[V_MORPH] = max([EYE_R * EYE_OX] + _morph_inner)
# ...and as rendered, after the variant node's EYE_SCALE
EYE_HALF_W = {v: hw * EYE_SCALE for v, hw in _EYE_HALF_W_AUTHORED.items()}


# 1b) where an eye lands on the phone. The artboard is 500 x 500; in portrait the app
#     makes it 1.10 x the screen width (PetModeScreen.kt RIVE_PORTRAIT_SCALE), so only
#     x 23..477 is on screen whatever the phone's height.
ART_W = ART_H = 500.0
PORTRAIT_X = (ART_W / 2 - ART_W / 2 / 1.10, ART_W / 2 + ART_W / 2 / 1.10)


def eye_fit_warnings(boxes):
    """boxes: {"L" | "R": (x0, x1, y0, y1)} in artboard units -> problems, in Thai (the Eye
    Lab and ship.py show them to the user)."""
    out_ = []
    (lx0, lx1, ly0, ly1), (rx0, rx1, ry0, ry1) = boxes["L"], boxes["R"]
    gap = rx0 - lx1
    if gap < MIN_EYE_GAP:
        out_.append("ตา 2 ข้างชิดกันเกินไป (ห่าง %.0f, ควร >= %.0f)" % (gap, MIN_EYE_GAP))
    x0, x1, y0, y1 = min(lx0, rx0), max(lx1, rx1), min(ly0, ry0), max(ly1, ry1)
    if x0 < 0 or x1 > ART_W or y0 < 0 or y1 > ART_H:
        out_.append("ตาเกินขอบภาพ Rive (ถูกตัด) %.0f px" % max(-x0, x1 - ART_W, -y0, y1 - ART_H))
    elif x0 < PORTRAIT_X[0] or x1 > PORTRAIT_X[1]:
        out_.append("ตาล้นจอแนวตั้ง %.0f px" % max(PORTRAIT_X[0] - x0, x1 - PORTRAIT_X[1]))
    return out_


def shape_boxes(left, right, scale=None, dx=None):
    """Library shape at rest (no pose): left-eye points + right-eye points -> boxes."""
    scale, dx = scale or EYE_SCALE, dx or EYE_DX
    box = lambda pts, cx: (cx + min(p[0] for p in pts) * scale, cx + max(p[0] for p in pts) * scale,
                           CY + EYE_Y + min(p[1] for p in pts) * scale, CY + EYE_Y + max(p[1] for p in pts) * scale)
    return {"L": box(left, CX - dx), "R": box(right, CX + dx)}


def check_morph_shapes():
    """Warn (not fail) about library shapes that collide or leave the screen at rest."""
    for name, (pts, smooth) in sorted(MORPH_SHAPES.items()):
        right = MORPH_SHAPES_R[name][0] if name in MORPH_SHAPES_R else _mirror(pts, smooth)[0]
        for msg in eye_fit_warnings(shape_boxes(pts, right)):
            sys.stderr.write("eye shape %s: %s\n" % (name, msg))


def max_eye_scale(eyeL, eyeR):
    """How far a pair of eyes may be blown up before they close the gap.
    Widening the eyes is the other way to make them collide -- a 'ตาโต' beat
    is a convergence too, just a symmetrical one."""
    half = EYE_HALF_W[eyeL] + EYE_HALF_W[eyeR]
    if half <= 0:
        return 99.0
    return (2 * EYE_DX - MIN_EYE_GAP) / half


def max_converge(eyeL, eyeR, scale=1.0):
    """How far each eye may slide inward before the pair reads as one blob."""
    gap = 2 * EYE_DX - (EYE_HALF_W[eyeL] + EYE_HALF_W[eyeR]) * scale
    return max(0.0, (gap - MIN_EYE_GAP) / 2.0)


# 2) brows must not read as a second pair of eyes: if the brow is the same
#    visual family as the eye, drop it.
EYE_FAMILY = {
    V_ARC: "arc", V_ARCDOWN: "arc",
    V_BAR: "bar", V_BARTILT: "bar",
    V_HALFLID: "slab", V_DROOP: "slab", V_ANGRY: "slab", V_EVIL: "slab",
    V_WEDGE: "slab",
}
BROW_FAMILY = {
    B_RAISED: ("arc",), B_WORRIED: ("arc",),
    B_ANGRY: ("bar",), B_SAD: ("bar",), B_FLAT: ("bar",), B_SHARP: ("bar",),
    B_ONEUP: ("bar", "arc"),
}


MOUTH_FAMILY = {M_FLAT: "bar"}


def brow_clashes(eyeL, eyeR, brow, mouth=M_NONE):
    if brow == B_NONE:
        return False
    fams = BROW_FAMILY.get(brow, ())
    for e in (eyeL, eyeR):
        efam = EYE_FAMILY.get(e, "blob")
        # same family as the eye -> reads as a second pair of eyes
        if efam in fams:
            return True
        # three stacked horizontal bars (brow / slab eye / flat mouth) is
        # just as unreadable, even though the families differ
        if efam == "slab" and "bar" in fams and MOUTH_FAMILY.get(mouth) == "bar":
            return True
        # a bar brow over a slab eye with NOTHING else on the face is four bars
        # and no face at all -- it needs a mouth to anchor the reading
        if efam == "slab" and "bar" in fams and mouth == M_NONE:
            return True
    return False


def check_faces():
    bad = [f[0] for f in FACES if brow_clashes(f[1], f[2], f[3], f[4])]
    if bad:
        raise SystemExit("brow reads as a second pair of eyes on: " + ", ".join(bad))


check_faces()
check_morph_shapes()
