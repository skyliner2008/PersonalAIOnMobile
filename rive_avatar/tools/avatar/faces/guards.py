# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

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
    # slabs are rotated, so the bounding half-width grows
    V_WEDGE: (2 * EYE_R * math.cos(.30) + 42 * math.sin(.30)) / 2,
    V_ANGRY: ((2 * EYE_R + 8) * math.cos(.34) + 72 * math.sin(.34)) / 2,
    V_EVIL: ((2 * EYE_R + 4) * math.cos(.20) + 68 * math.sin(.20)) / 2,
    V_DROOP: (2 * EYE_R * math.cos(.30) + 62 * math.sin(.30)) / 2,
}
# ...and as rendered, after the variant node's EYE_SCALE
EYE_HALF_W = {v: hw * EYE_SCALE for v, hw in _EYE_HALF_W_AUTHORED.items()}


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
