# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# ================================================================= EYES
V_NAMES = ["Round", "Arc", "Dome", "Bar", "BarTilt", "Cross", "Chevron", "Wedge",
           "Angry", "Evil", "Empty", "Heart", "ArcDown", "HalfLid", "Tiny",
           "Spiral", "Droop", "Dollar", "Wide", "StarEye", "Ring"]
(V_ROUND, V_ARC, V_DOME, V_BAR, V_BARTILT, V_CROSS, V_CHEV, V_WEDGE, V_ANGRY,
 V_EVIL, V_EMPTY, V_HEART, V_ARCDOWN, V_HALFLID, V_TINY, V_SPIRAL, V_DROOP,
 V_DOLLAR, V_WIDE, V_STAREYE, V_RING) = range(len(V_NAMES))

V_STROKED = (V_ARC, V_ARCDOWN, V_SPIRAL, V_DOLLAR, V_RING)
V_HIGHLIGHT = (V_ROUND, V_WIDE, V_DOME, V_HEART, V_TINY)


# LOOI eyes read as soft vertical ovals, not coins
EYE_OX, EYE_OY = 0.90, 1.06


def eye_geo(d, v, s):
    if v == V_ROUND:
        g_ellipse(d, 2 * EYE_R * EYE_OX, 2 * EYE_R * EYE_OY)
    elif v == V_WIDE:
        g_ellipse(d, (2 * EYE_R + 18) * EYE_OX, (2 * EYE_R + 18) * EYE_OY)
    elif v == V_TINY:
        g_ellipse(d, 44 * EYE_OX, 44 * EYE_OY)
    elif v == V_DOME:
        k = EYE_R * 0.5523
        w(d, '<PointsPath isClosed="true" name="P">')
        w(d + 1, '<StraightVertex x="-%g" y="34" radius="7"/>' % EYE_R)
        w(d + 1, '<CubicMirroredVertex x="-%g" y="0" rotation="-1.5708" distance="%g"/>' % (EYE_R, k))
        w(d + 1, '<CubicMirroredVertex x="0" y="-%g" rotation="0" distance="%g"/>' % (EYE_R, k))
        w(d + 1, '<CubicMirroredVertex x="%g" y="0" rotation="1.5708" distance="%g"/>' % (EYE_R, k))
        w(d + 1, '<StraightVertex x="%g" y="34" radius="7"/>' % EYE_R)
        w(d, '</PointsPath>')
    elif v == V_BAR:
        g_rect(d, 92, 18, r=9)
    elif v == V_BARTILT:
        g_rect(d, 92, 18, r=9, rot=-0.22 * s)
    elif v == V_CROSS:
        g_rect(d, 108, 20, r=10, rot=0.7854)
        g_rect(d, 108, 20, r=10, rot=-0.7854)
    elif v == V_CHEV:
        g_rect(d, 66, 20, x=-16 * s, y=-17, r=10, rot=0.62 * s)
        g_rect(d, 66, 20, x=-16 * s, y=17, r=10, rot=-0.62 * s)
    elif v == V_WEDGE:
        g_rect(d, 2 * EYE_R, 42, rot=0.30 * s, rtl=5, rtr=5, rbl=21, rbr=21)
    elif v == V_ANGRY:
        g_rect(d, 2 * EYE_R + 8, 72, rot=0.34 * s, rtl=6, rtr=6, rbl=38, rbr=38)
    elif v == V_EVIL:
        g_rect(d, 2 * EYE_R + 4, 68, rot=0.20 * s, rtl=16, rtr=16, rbl=36, rbr=36)
    elif v == V_HALFLID:
        g_rect(d, 2 * EYE_R, 56, y=12, rtl=6, rtr=6, rbl=28, rbr=28)
    elif v == V_DROOP:
        g_rect(d, 2 * EYE_R, 62, rot=-0.30 * s, rtl=6, rtr=6, rbl=30, rbr=30)
    elif v == V_HEART:
        g_heart(d, 0.98)
    elif v == V_STAREYE:
        g_star(d, 118, points=5, inner=0.44, corner=6)
    elif v == V_ARC:
        g_arcup(d)
    elif v == V_ARCDOWN:
        g_arcdown(d)
    elif v == V_SPIRAL:
        g_ellipse(d, 96, 96)
        g_ellipse(d, 50, 50)
    elif v == V_RING:
        g_ellipse(d, 84, 84)
    elif v == V_DOLLAR:
        # S-curve plus the vertical bar
        w(d, '<PointsPath name="P">')
        w(d + 1, '<StraightVertex x="26" y="-34"/>')
        w(d + 1, '<CubicMirroredVertex x="-8" y="-40" rotation="3.1416" distance="18"/>')
        w(d + 1, '<CubicMirroredVertex x="-8" y="-4" rotation="0" distance="18"/>')
        w(d + 1, '<CubicMirroredVertex x="8" y="6" rotation="0" distance="14"/>')
        w(d + 1, '<CubicMirroredVertex x="8" y="40" rotation="3.1416" distance="18"/>')
        w(d + 1, '<StraightVertex x="-26" y="34"/>')
        w(d, '</PointsPath>')


def eye_color(v):
    if v in (V_ANGRY, V_EVIL):
        return RED, RED_DEEP
    if v == V_HEART:
        return HEART, PINK_DEEP
    if v in (V_DOLLAR, V_STAREYE):
        return GOLD, GOLD_DEEP
    return CYAN, BLUE


# Each eye is two layers kept in step: the bright FRONT shape rides the iris
# nodes (it shifts toward where the eye looks), the darker BACK shape stays put
# a little below centre -- so the crescent of back that shows moves opposite to
# the gaze instead of always sitting on one side.
BACK_DY = 5.0
EYE_BACK = {}        # front variant id -> back variant id


def emit_eye_variant(d, v, s, tag, part="front"):
    vid = nid()
    name = "%s_%s%s" % (tag, V_NAMES[v], "" if part == "front" else "_Back")
    if v == V_EMPTY:
        w(d, '<Node name="%s" id="%s"/>' % (name, vid))
        return vid
    main, back = eye_color(v)
    w(d, '<Node scaleX="%g" scaleY="%g" name="%s" id="%s">' % (EYE_SCALE, EYE_SCALE, name, vid))
    if part == "front":
        w(d + 1, '<Shape name="Crisp">')
        eye_geo(d + 2, v, s)
        if v in V_STROKED:
            th = {V_SPIRAL: 13, V_DOLLAR: 13, V_RING: 17}.get(v, 22)
            p_stroke(d + 2, main, th + 4, feather=GLOW)
            p_stroke(d + 2, main, th)
        else:
            p_glowfill(d + 2, main)
        w(d + 1, '</Shape>')
        if v == V_DOLLAR:
            solid(d + 1, "Bar", lambda k: g_rect(k, 11, 116, r=5), main)
    else:
        w(d + 1, '<Shape x="0" y="%g" name="Shadow">' % (BACK_DY / EYE_SCALE))
        eye_geo(d + 2, v, s)
        if v in V_STROKED:
            p_stroke(d + 2, back, {V_SPIRAL: 13, V_DOLLAR: 13, V_RING: 17}.get(v, 22))
        else:
            p_fill(d + 2, back)
        w(d + 1, '</Shape>')
    w(d, '</Node>')
    return vid
