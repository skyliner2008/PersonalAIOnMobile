#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build_scene.py v6 -- generates scene.rml for the LOOI-style neon robot avatar.

Rig
---
    FgSolo                              foreground (water, explosion, chyron)
    PropSolo                            face-level props
    FaceYaw            <- GazeH layer   (x / scaleX / rotation only)
      FacePitch        <- GazeV layer   (y / scaleY only)
        LEyeYaw > LEyePitch > LBlink > LEyeSolo
        REyeYaw > REyePitch > RBlink > REyeSolo
        BrowYaw > BrowPitch > BrowSolo
        MouthYaw> MouthPitch> MouthScale > MouthSolo
    BgSolo                              background (grids, charts, glows)

The yaw/pitch split is what makes two blend-state layers composable: a layer
only ever writes properties no other layer writes, so "the later layer wins"
never bites. Together they read as an invisible head turning in 2.5D.

Every mood is one row in MOODS -- seven Solo choices plus flags. Ids,
animations, states and transitions are all derived from it.
"""

import math

# ---------------------------------------------------------------- palette
CYAN       = "FF3FE7F5"
CYAN_SOFT  = "FF8FF4FF"
CYAN_DIM   = "FF1FB6C9"
BLUE       = "FF1B34D8"
BLUE_DEEP  = "FF0B1C8A"
RED        = "FFFF4D5E"
RED_DEEP   = "FF8E0F26"
ORANGE     = "FFFF8A3D"
PURPLE     = "FF9B5CF6"
PINK       = "FFFF7AA2"
PINK_DEEP  = "FFB0184A"
HEART      = "FFFF5C8A"
WHITE      = "FFFFFFFF"
AMBER      = "FFF5B841"
GOLD       = "FFFFC53D"
GOLD_DEEP  = "FF9A6A05"
CREAM      = "FFF7E3B5"
BROWN      = "FF8A5A2B"
GREEN      = "FF4CD98A"
GREEN_DEEP = "FF0F6B3C"
TEAR       = "FF6FC9FF"
DARK       = "FF0B1020"
GREY       = "FF3A4256"
SILVER     = "FFB8C4D8"
# transparent: in the app the Compose background layer shows through behind the
# face (Rive bg scenes still paint their own ground)
BG         = "0004060B"

SHADOW_DX, SHADOW_DY = 4, 9
GLOW = 16

# ---------------------------------------------------------------- ids
_next = [1000]


def nid():
    _next[0] += 1
    return "0:%d" % _next[0]


ART_ID, STYLE_ID, SM_ID = "0:2", "0:5", "0:7"
VM_ID, VMI_ID = "0:3", "0:4"
VP_FACE, VP_PROP, VP_BG, VP_FG = "0:301", "0:306", "0:307", "0:308"
VP_GAZEX, VP_GAZEY, VP_SPEAKING, VP_MOUTH = "0:302", "0:303", "0:304", "0:305"
VP_EYEACT, VP_SEQ = "0:309", "0:310"
VP_STATE, VP_TILTX, VP_TILTY, VP_AUDIO = "0:312", "0:313", "0:314", "0:315"
CONV_TILTX, CONV_TILTY, CONV_TILTR = "0:316", "0:317", "0:318"
CONV_AUDIO = "0:319"
VP_REACT = "0:320"
CONV_AUDIO_BAR, CONV_AUDIO_X = "0:321", "0:322"
# StandbyClock: the host writes the four digits it wants shown (HH:MM)
VP_CLOCK = ["0:323", "0:324", "0:325", "0:326"]
# scene item: which food / drink / device a scene uses (0 = the first one)
VP_ITEM = "0:327"
CONV_GAZE = "0:311"          # -1..1  ->  0..100 for the blend axis

out = []


def w(d, s):
    out.append("    " * d + s)


# A `cubic` keyframe with no nested interpolator does NOT ease -- the runtime
# falls back to a straight blend (rive docs gotchas). Every eased keyframe goes
# through kf() so the curve is always attached. Names other than the Rive enum
# are eases this generator understands; they all emit interpolationType="cubic".
EASES = {
    "cubic":   (0.42, 0.0, 0.58, 1.0),     # ease-in-out, the default
    "easeOut": (0.0, 0.0, 0.58, 1.0),
    "easeIn":  (0.42, 0.0, 1.0, 1.0),
    "soft":    (0.25, 0.1, 0.25, 1.0),
    # eyes / head: arrives a touch past the target and settles back (squishy, not stiff)
    "jelly":   (0.30, 0.0, 0.32, 1.28),
}


def kf(d, value, frame=None, interp="cubic"):
    fr = '' if frame is None else ' frame="%d"' % frame
    if interp in EASES:
        x1, y1, x2, y2 = EASES[interp]
        w(d, '<KeyFrameDouble value="%g"%s interpolationType="cubic">' % (value, fr))
        w(d + 1, '<CubicEaseInterpolator x1="%g" y1="%g" x2="%g" y2="%g"/>' % (x1, y1, x2, y2))
        w(d, '</KeyFrameDouble>')
    else:
        w(d, '<KeyFrameDouble value="%g"%s interpolationType="%s"/>' % (value, fr, interp))


# ---------------------------------------------------------------- paint
def p_glowfill(d, color, feather=GLOW, thick=5):
    w(d, '<Stroke thickness="%g" cap="round" join="round" name="Glow">' % thick)
    w(d + 1, '<SolidColor colorValue="%s" name="C"/>' % color)
    w(d + 1, '<Feather strength="%g" name="F"/>' % feather)
    w(d, '</Stroke>')
    w(d, '<Fill name="Fill">')
    w(d + 1, '<SolidColor colorValue="%s" name="C"/>' % color)
    w(d, '</Fill>')


def p_fill(d, color):
    w(d, '<Fill name="Fill">')
    w(d + 1, '<SolidColor colorValue="%s" name="C"/>' % color)
    w(d, '</Fill>')


def p_grad(d, c0, c1, x0=0, y0=-50, x1=0, y1=50):
    w(d, '<Fill name="Fill">')
    w(d + 1, '<LinearGradient startX="%g" startY="%g" endX="%g" endY="%g" name="G">'
      % (x0, y0, x1, y1))
    w(d + 2, '<GradientStop colorValue="%s" position="0"/>' % c0)
    w(d + 2, '<GradientStop colorValue="%s" position="1"/>' % c1)
    w(d + 1, '</LinearGradient>')
    w(d, '</Fill>')


def p_radial(d, c0, c1, r=100):
    w(d, '<Fill name="Fill">')
    w(d + 1, '<RadialGradient startX="0" startY="0" endX="%g" endY="0" name="G">' % r)
    w(d + 2, '<GradientStop colorValue="%s" position="0"/>' % c0)
    w(d + 2, '<GradientStop colorValue="%s" position="1"/>' % c1)
    w(d + 1, '</RadialGradient>')
    w(d, '</Fill>')


def p_stroke(d, color, thick, feather=None, cap="round"):
    w(d, '<Stroke thickness="%g" cap="%s" join="round" name="S">' % (thick, cap))
    w(d + 1, '<SolidColor colorValue="%s" name="C"/>' % color)
    if feather:
        w(d + 1, '<Feather strength="%g" name="F"/>' % feather)
    w(d, '</Stroke>')


# ---------------------------------------------------------------- geometry
def g_ellipse(d, ww, hh, x=0, y=0, rot=0.0):
    w(d, '<Ellipse width="%g" height="%g" x="%g" y="%g" rotation="%.4f"'
         ' originX="0.5" originY="0.5" name="P"/>' % (ww, hh, x, y, rot))


def g_rect(d, ww, hh, x=0, y=0, rot=0.0, r=0, rtl=None, rtr=None, rbl=None, rbr=None):
    if rtl is not None or rtr is not None or rbl is not None or rbr is not None:
        rtl = 0 if rtl is None else rtl
        rtr = 0 if rtr is None else rtr
        rbl = 0 if rbl is None else rbl
        rbr = 0 if rbr is None else rbr
    if rtl is None:
        w(d, '<Rectangle width="%g" height="%g" x="%g" y="%g" rotation="%.4f"'
             ' cornerRadiusTL="%g" originX="0.5" originY="0.5" name="P"/>'
          % (ww, hh, x, y, rot, r))
    else:
        w(d, '<Rectangle width="%g" height="%g" x="%g" y="%g" rotation="%.4f"'
             ' linkCornerRadius="false" cornerRadiusTL="%g" cornerRadiusTR="%g"'
             ' cornerRadiusBL="%g" cornerRadiusBR="%g" originX="0.5" originY="0.5"'
             ' name="P"/>' % (ww, hh, x, y, rot, rtl, rtr, rbl, rbr))


def g_star(d, size, x=0, y=0, points=4, inner=0.28, corner=1.5, rot=0.0):
    w(d, '<Star width="%g" height="%g" x="%g" y="%g" rotation="%.4f" points="%d"'
         ' innerRadius="%g" cornerRadius="%g" originX="0.5" originY="0.5" name="P"/>'
      % (size, size, x, y, rot, points, inner, corner))


def g_poly(d, size, x=0, y=0, points=3, corner=2, rot=0.0, hh=None):
    w(d, '<Polygon width="%g" height="%g" x="%g" y="%g" rotation="%.4f" points="%d"'
         ' cornerRadius="%g" originX="0.5" originY="0.5" name="P"/>'
      % (size, hh if hh else size, x, y, rot, points, corner))


def g_tri(d, ww, hh, x=0, y=0, rot=0.0):
    w(d, '<Triangle width="%g" height="%g" x="%g" y="%g" rotation="%.4f"'
         ' originX="0.5" originY="0.5" name="P"/>' % (ww, hh, x, y, rot))


def g_heart(d, k=1.0, x=0, y=0, rot=0.0):
    w(d, '<PointsPath isClosed="true" x="%g" y="%g" rotation="%.4f" name="P">' % (x, y, rot))
    w(d + 1, '<StraightVertex x="0" y="%g" radius="%g"/>' % (46 * k, 5 * k))
    w(d + 1, '<CubicMirroredVertex x="%g" y="%g" rotation="-1.5708" distance="%g"/>' % (-52 * k, -8 * k, 34 * k))
    w(d + 1, '<CubicMirroredVertex x="%g" y="%g" rotation="0" distance="%g"/>' % (-26 * k, -42 * k, 22 * k))
    w(d + 1, '<StraightVertex x="0" y="%g" radius="%g"/>' % (-12 * k, 5 * k))
    w(d + 1, '<CubicMirroredVertex x="%g" y="%g" rotation="0" distance="%g"/>' % (26 * k, -42 * k, 22 * k))
    w(d + 1, '<CubicMirroredVertex x="%g" y="%g" rotation="1.5708" distance="%g"/>' % (52 * k, -8 * k, 34 * k))
    w(d, '</PointsPath>')


def g_drop(d, k=1.0, x=0, y=0, rot=0.0):
    w(d, '<PointsPath isClosed="true" x="%g" y="%g" rotation="%.4f" name="P">' % (x, y, rot))
    w(d + 1, '<StraightVertex x="0" y="%g" radius="%g"/>' % (-22 * k, 3 * k))
    w(d + 1, '<CubicMirroredVertex x="%g" y="%g" rotation="1.5708" distance="%g"/>' % (13 * k, 8 * k, 10 * k))
    w(d + 1, '<CubicMirroredVertex x="0" y="%g" rotation="3.1416" distance="%g"/>' % (22 * k, 10 * k))
    w(d + 1, '<CubicMirroredVertex x="%g" y="%g" rotation="-1.5708" distance="%g"/>' % (-13 * k, 8 * k, 10 * k))
    w(d, '</PointsPath>')


def g_arcup(d, hw=50, dep=30, base=12):
    w(d, '<PointsPath name="P">')
    w(d + 1, '<StraightVertex x="%g" y="%g"/>' % (-hw, base))
    w(d + 1, '<CubicMirroredVertex x="0" y="%g" rotation="0" distance="%g"/>' % (-dep, hw * 0.64))
    w(d + 1, '<StraightVertex x="%g" y="%g"/>' % (hw, base))
    w(d, '</PointsPath>')


def g_arcdown(d, hw=50, dep=28, base=-14):
    w(d, '<PointsPath name="P">')
    w(d + 1, '<StraightVertex x="%g" y="%g"/>' % (-hw, base))
    w(d + 1, '<CubicMirroredVertex x="0" y="%g" rotation="0" distance="%g"/>' % (dep, hw * 0.64))
    w(d + 1, '<StraightVertex x="%g" y="%g"/>' % (hw, base))
    w(d, '</PointsPath>')


def g_wave(d, hw=44, amp=10):
    w(d, '<PointsPath name="P">')
    w(d + 1, '<StraightVertex x="%g" y="0"/>' % -hw)
    w(d + 1, '<CubicMirroredVertex x="%g" y="%g" rotation="0" distance="%g"/>' % (-hw / 3.0, -amp, hw / 4.0))
    w(d + 1, '<CubicMirroredVertex x="%g" y="%g" rotation="0" distance="%g"/>' % (hw / 3.0, amp, hw / 4.0))
    w(d + 1, '<StraightVertex x="%g" y="0"/>' % hw)
    w(d, '</PointsPath>')


# ---------------------------------------------------------------- shape sugar
CX_WASH = CY_WASH = 250.0


def shape(d, name, body, x=0, y=0, rot=0.0):
    w(d, '<Shape x="%g" y="%g" rotation="%.4f" name="%s">' % (x, y, rot, name))
    body(d + 1)
    w(d, '</Shape>')


def solid(d, name, geo, color, x=0, y=0, rot=0.0):
    shape(d, name, lambda k: (geo(k), p_fill(k, color)), x, y, rot)


def neon(d, name, geo, color, x=0, y=0, rot=0.0, feather=12, thick=4):
    shape(d, name, lambda k: (geo(k), p_glowfill(k, color, feather, thick)), x, y, rot)


def line(d, name, geo, color, thick, x=0, y=0, rot=0.0, feather=None, cap="round"):
    def body(k):
        geo(k)
        if feather:
            p_stroke(k, color, thick + 3, feather=feather)
        p_stroke(k, color, thick, cap=cap)
    shape(d, name, body, x, y, rot)


def grad(d, name, geo, c0, c1, x=0, y=0, gy=50):
    shape(d, name, lambda k: (geo(k), p_grad(k, c0, c1, 0, -gy, 0, gy)), x, y)


def wash(d, name, color, x=CX_WASH, y=CY_WASH, r=250.0, hold=0.45):
    """A full-screen tint that is NOT a rectangle: solid to `hold` of the radius,
    then fading to transparent before the 500 px artboard edge -- so on a phone,
    where the artboard is a square inside a taller view, it blends into the page
    instead of showing a box."""
    clear = "00" + color[2:]

    def body(k):
        g_ellipse(k, r * 2, r * 2)
        w(k, '<Fill name="Fill">')
        w(k + 1, '<RadialGradient startX="0" startY="0" endX="%g" endY="0" name="G">' % r)
        w(k + 2, '<GradientStop colorValue="%s" position="0"/>' % color)
        w(k + 2, '<GradientStop colorValue="%s" position="%g"/>' % (color, hold))
        w(k + 2, '<GradientStop colorValue="%s" position="1"/>' % clear)
        w(k + 1, '</RadialGradient>')
        w(k, '</Fill>')
    shape(d, name, body, x, y)


def glowdisc(d, name, r, c0, c1, x=0, y=0):
    """A soft radial halo -- the Feather-in-a-Fill substitute."""
    shape(d, name, lambda k: (g_ellipse(k, r * 2, r * 2), p_radial(k, c0, c1, r)), x, y)


def reverse_blocks(lines, indent):
    """Group top-level blocks at `indent` and reverse them so the natural
    reading order (last = foreground) matches Rive's first-declared-on-top."""
    pad = "    " * indent
    blocks, cur = [], []
    for ln in lines:
        head = ln.startswith(pad) and not ln.startswith(pad + " ") \
            and ln.lstrip().startswith("<") and not ln.lstrip().startswith("</")
        if head:
            if cur:
                blocks.append(cur)
            cur = [ln]
        else:
            cur.append(ln)
    if cur:
        blocks.append(cur)
    res = []
    for b in reversed(blocks):
        res.extend(b)
    return res


# ================================================================= LAYOUT
CX, CY = 250.0, 250.0
# LOOI reads through the eyes alone, so they are big and sit close to the
# middle. Eye geometry is authored at radius 54 and the variant node scales it
# up by EYE_SCALE, which keeps every shape, glow and shadow in proportion.
EYE_SCALE = 1.32
EYE_DX = 100.0         # eye offset from face centre
EYE_Y = -20.0          # eye row, relative to face centre
BROW_Y = -120.0
MOUTH_Y = 100.0
EYE_R = 54.0           # authored radius; rendered radius = EYE_R * EYE_SCALE (67)
EYE_RR = EYE_R * EYE_SCALE
LEFT_X, RIGHT_X = CX - EYE_DX, CX + EYE_DX
EYE_ABS_Y = CY + EYE_Y          # 230
# free corners for small status icons, clear of the (big) eyes
CORNER_R = (428.0, 100.0)
CORNER_L = (72.0, 100.0)

# animation tracks registered while emitting: (objId, key, [(frame, value)])
TRACKS = []


# A mover created with an anchor does not sit at 0,0 any more, and keyframing
# x / y REPLACES the node's value rather than adding to it -- so every
# translation keyframe aimed at an anchored mover has to be rebased onto that
# anchor or the artwork jumps to the top-left corner. One place, so both the
# prop loops and the story/state/react timelines get it right.
ANCHOR_OF = {}
MOVER_BASE = {}      # mover id -> its authored (x, y): the value a rest pose returns to

# Entrance tracks: one-shot motion that has to start when the prop / bg / fg
# is SHOWN (sunglasses dropping, a chyron sliding in). They cannot live in the
# shared PropLoops clock, which runs whether the prop is visible or not. They
# are baked into the channel animation that shows the item and into every
# story beat that switches to it. kind -> index -> [(obj, key, kfs, interp)]
INTROS = {"prop": {}, "bg": {}, "fg": {}}
_cur_intro = [None]


def intro(obj, key, kfs, interp="cubic"):
    _cur_intro[0].append((obj, key, rebase(obj, key, kfs), interp))


def rebase(oid, key, kfs):
    a = ANCHOR_OF.get(oid)
    if a is None or key not in (13, 14):
        return kfs
    off = a[0] if key == 13 else a[1]
    return [(fr, vv + off) for fr, vv in kfs]


def track(obj, key, kfs, interp="cubic"):
    TRACKS.append((obj, key, rebase(obj, key, kfs), interp))


PROP_NODES = {}      # prop index -> {mover name: id}
_cur_reg = [None]


def mover(d, name, body, x=0, y=0, anchor=None):
    """A Node with an id, so an animation can move it.

    Rotation and scale on a Node happen about that node's OWN origin. A mover
    parked at 0,0 whose artwork sits at absolute coordinates would therefore
    fling that artwork across the artboard the moment it is scaled or spun.
    Pass `anchor` = the artwork's own centre and the mover parks there, with an
    inner node undoing the offset: the shapes keep their absolute coordinates
    and now scale and rotate about themselves."""
    mid = nid()
    if _cur_reg[0] is not None:
        _cur_reg[0][name] = mid

    def emit_body(bd):
        # same convention as props: author back-to-front, last = foreground
        mark = len(out)
        body(bd)
        chunk = out[mark:]
        del out[mark:]
        out.extend(reverse_blocks(chunk, bd))

    if anchor is None:
        MOVER_BASE[mid] = (x, y)
        w(d, '<Node x="%g" y="%g" name="%s" id="%s">' % (x, y, name, mid))
        emit_body(d + 1)
        w(d, '</Node>')
        return mid
    ax, ay = anchor
    ANCHOR_OF[mid] = (x + ax, y + ay)
    MOVER_BASE[mid] = (x + ax, y + ay)
    w(d, '<Node x="%g" y="%g" name="%s" id="%s">' % (x + ax, y + ay, name, mid))
    w(d + 1, '<Node x="%g" y="%g" name="%sAt">' % (-ax, -ay, name))
    emit_body(d + 2)
    w(d + 1, '</Node>')
    w(d, '</Node>')
    return mid


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


# ================================================================= BROWS
B_NAMES = ["None", "Angry", "Sad", "Raised", "OneUp", "Flat", "Worried", "Sharp", "AngryRed"]
(B_NONE, B_ANGRY, B_SAD, B_RAISED, B_ONEUP, B_FLAT, B_WORRIED, B_SHARP,
 B_ANGRYRED) = range(len(B_NAMES))

BROW_DX = 96.0


def brow_bar(d, name, x, y, rot, ww=64, hh=11, color=CYAN):
    line(d, name, lambda k: g_rect(k, ww, hh, r=hh / 2.0), color, hh, x=x, y=y, rot=rot,
         feather=12)


def emit_brow_variant(d, b):
    bid = nid()
    w(d, '<Node name="Brow_%s" id="%s"' % (B_NAMES[b], bid) + ('/>' if b == B_NONE else '>'))
    if b == B_NONE:
        return bid
    # one colour per face: red brows only ever sit over red eyes
    c = RED if b == B_ANGRYRED else CYAN
    if b in (B_ANGRY, B_ANGRYRED):
        brow_bar(d + 1, "L", -BROW_DX, 0, 0.42, color=c)
        brow_bar(d + 1, "R", BROW_DX, 0, -0.42, color=c)
    elif b == B_SAD:
        brow_bar(d + 1, "L", -BROW_DX, 0, -0.40)
        brow_bar(d + 1, "R", BROW_DX, 0, 0.40)
    elif b == B_RAISED:
        line(d + 1, "L", lambda k: g_arcup(k, 27, 11, 3), CYAN, 9, x=-BROW_DX, y=-6, feather=10)
        line(d + 1, "R", lambda k: g_arcup(k, 27, 11, 3), CYAN, 9, x=BROW_DX, y=-6, feather=10)
    elif b == B_ONEUP:
        brow_bar(d + 1, "L", -BROW_DX, 8, 0.0)
        line(d + 1, "R", lambda k: g_arcup(k, 27, 13, 3), CYAN, 9, x=BROW_DX, y=-12, feather=10)
    elif b == B_FLAT:
        brow_bar(d + 1, "L", -BROW_DX, 0, 0.0)
        brow_bar(d + 1, "R", BROW_DX, 0, 0.0)
    elif b == B_WORRIED:
        line(d + 1, "L", lambda k: g_arcup(k, 26, 10, 3), CYAN, 9, x=-BROW_DX, y=-4, rot=-0.28, feather=10)
        line(d + 1, "R", lambda k: g_arcup(k, 26, 10, 3), CYAN, 9, x=BROW_DX, y=-4, rot=0.28, feather=10)
    elif b == B_SHARP:
        brow_bar(d + 1, "L", -BROW_DX, 0, 0.30, ww=72, hh=10)
        brow_bar(d + 1, "R", BROW_DX, 0, -0.30, ww=72, hh=10)
    w(d, '</Node>')
    return bid


# ================================================================= MOUTHS
M_NAMES = ["None", "Smile", "SmileOpen", "Flat", "Frown", "OpenO", "Wavy",
           "Grin", "Zigzag", "Smirk", "Cat", "Tongue", "Fang", "ZigzagRed"]
(M_NONE, M_SMILE, M_SMILEOPEN, M_FLAT, M_FROWN, M_OPENO, M_WAVY, M_GRIN,
 M_ZIGZAG, M_SMIRK, M_CAT, M_TONGUE, M_FANG, M_ZIGZAGRED) = range(len(M_NAMES))

VOICE_BAR = [None]   # id of the talk bar that lives in the mouth-less variant


def g_zigzag(k, hw=48, amp=11, teeth=4):
    """One continuous polyline -- separate slanted rects never line up."""
    w(k, '<PointsPath name="P">')
    n = teeth * 2
    for i in range(n + 1):
        x = -hw + 2.0 * hw * i / n
        y = -amp if i % 2 else amp
        w(k + 1, '<StraightVertex x="%g" y="%g"/>' % (x, y))
    w(k, '</PointsPath>')


def emit_mouth_variant(d, m):
    mid = nid()
    w(d, '<Node name="Mouth_%s" id="%s">' % (M_NAMES[m], mid))
    if m == M_NONE:
        # LOOI faces have no mouth. When the robot talks, a short neon bar
        # fades in here instead (Speech layer keys its opacity, the mic level
        # stretches it), so speech is visible on every mouth-less face. A face
        # WITH a mouth hides this variant through the Solo and talks with it.
        bar = nid()
        VOICE_BAR[0] = bar
        w(d + 1, '<Node opacity="0" name="VoiceBar" id="%s">' % bar)
        w(d + 2, '<DataBindContext sourcePathIds="%s-%s" propertyKey="17" converterId="%s"/>'
          % (VM_ID, VP_AUDIO, CONV_AUDIO_BAR))
        neon(d + 2, "Bar", lambda k: g_rect(k, 80, 16, r=8), CYAN, feather=10)
        w(d + 1, '</Node>')
        w(d, '</Node>')
        return mid
    if m == M_FANG:
        solid(d + 1, "Fang", lambda k: g_tri(k, 26, 32, rot=3.1416), WHITE, 0, -54)
    elif m == M_ZIGZAGRED:
        line(d + 1, "M", g_zigzag, RED, 11, feather=12)
    elif m == M_SMILE:
        line(d + 1, "M", lambda k: g_arcdown(k, 44, 26, -10), CYAN, 14, feather=12)
    elif m == M_SMILEOPEN:
        solid(d + 1, "Tongue", lambda k: g_ellipse(k, 46, 26), PINK, 0, 20)
        shape(d + 1, "M", lambda k: (g_rect(k, 96, 62, rtl=10, rtr=10, rbl=46, rbr=46),
                                     p_glowfill(k, CYAN, 12, 4)))
    elif m == M_FLAT:
        line(d + 1, "M", lambda k: g_rect(k, 70, 11, r=6), CYAN, 12, feather=10)
    elif m == M_FROWN:
        line(d + 1, "M", lambda k: g_arcup(k, 42, 24, 8), CYAN, 14, feather=12)
    elif m == M_OPENO:
        shape(d + 1, "M", lambda k: (g_ellipse(k, 62, 74), p_glowfill(k, CYAN, 12, 4)))
    elif m == M_WAVY:
        line(d + 1, "M", lambda k: g_wave(k, 52, 14), CYAN, 13, feather=12)
    elif m == M_GRIN:
        solid(d + 1, "Teeth", lambda k: g_rect(k, 102, 15, rtl=9, rtr=9, rbl=4, rbr=4), WHITE, 0, -18)
        shape(d + 1, "M", lambda k: (g_rect(k, 112, 56, rtl=12, rtr=12, rbl=50, rbr=50),
                                     p_glowfill(k, CYAN, 12, 4)))
    elif m == M_ZIGZAG:
        line(d + 1, "M", g_zigzag, CYAN, 11, feather=12)
    elif m == M_SMIRK:
        line(d + 1, "M", lambda k: g_arcdown(k, 40, 20, -8), CYAN, 13, x=14, rot=-0.22, feather=12)
    elif m == M_CAT:
        line(d + 1, "L", lambda k: g_arcdown(k, 24, 16, -6), CYAN, 12, x=-24, feather=10)
        line(d + 1, "R", lambda k: g_arcdown(k, 24, 16, -6), CYAN, 12, x=24, feather=10)
    elif m == M_TONGUE:
        solid(d + 1, "T", lambda k: g_rect(k, 30, 26, rtl=4, rtr=4, rbl=14, rbr=14), PINK, 8, 18)
        line(d + 1, "M", lambda k: g_arcdown(k, 42, 24, -10), CYAN, 14, feather=12)
    w(d, '</Node>')
    return mid


# ================================================================= PROPS
P_NAMES = ["None", "Zzz", "Question", "Burger", "Beer", "Sparkle", "Headphones",
           "VR", "Snorkel", "Devil", "Sparkles", "Blush", "Exclaim", "Trash",
           "Camera", "AngryMark", "Hearts", "Tears", "ThinkDots", "SoundWave",
           "Questions", "Steam", "DizzyStars", "Sweat", "Shiver", "Medal",
           "BatteryLow", "Mic", "Sunglasses", "Missiles", "SLTag",
           "Turrets", "Snore", "HoloPat", "HoloPokeL", "HoloPokeR", "HoloChin",
           # LOOI status / AI-feature set
           "Bulb", "Bolt", "Barcode", "Gears", "UpdateArrows", "Magnifier",
           "SignalBars", "PointHand", "FaceBrackets", "Sun", "RainCloud",
           "AlarmClock", "Calendar", "Clock", "Pencil", "Warning", "Curtains",
           "BigBattery", "Cracked"]
(P_NONE, P_ZZZ, P_QUESTION, P_BURGER, P_BEER, P_SPARKLE, P_HEADPHONES, P_VR,
 P_SNORKEL, P_DEVIL, P_SPARKLES, P_BLUSH, P_EXCLAIM, P_TRASH, P_CAMERA,
 P_ANGRYMARK, P_HEARTS, P_TEARS, P_THINKDOTS, P_SOUNDWAVE, P_QUESTIONS,
 P_STEAM, P_DIZZYSTARS, P_SWEAT, P_SHIVER, P_MEDAL, P_BATTERYLOW, P_MIC,
 P_SUNGLASSES, P_MISSILES, P_SLTAG, P_TURRETS, P_SNORE,
 P_HOLOPAT, P_HOLOPOKEL, P_HOLOPOKER, P_HOLOCHIN,
 P_BULB, P_BOLT, P_BARCODE, P_GEARS, P_UPDATE, P_MAGNIFIER, P_SIGNAL,
 P_POINTHAND, P_BRACKETS, P_SUN, P_RAIN, P_ALARM, P_CALENDAR, P_CLOCK,
 P_PENCIL, P_WARNING, P_CURTAINS, P_BIGBATTERY, P_CRACKED) = range(len(P_NAMES))

# The touch props are a hologram of a hand, so they read as something reaching
# into the scene rather than something the robot is wearing: a low-alpha cyan
# body under a bright edge, no shadow plate.
HOLO_FILL = "552FD8FF"
HOLO_EDGE = "EE8FF4FF"

TURRET_BASE_Y = 402.0


def holo_hand(k, s=1.0, fdir=1):
    """Palm plus four separated fingers and a thumb. fdir=+1 fingers point up
    (scratching a chin from below), fdir=-1 fingers point down (patting a head).
    The fingers need real gaps between them or the whole thing reads as a
    mitten rather than a hand."""
    g_rect(k, 78 * s, 56 * s, y=8 * s * fdir, r=22 * s)                      # palm
    for fx, fr in ((-30, -.17), (-10, -.06), (10, .06), (30, .17)):
        g_rect(k, 14 * s, 48 * s, x=fx * s, y=-32 * s * fdir, r=7 * s, rot=fr * fdir)
    g_rect(k, 16 * s, 36 * s, x=-47 * s, y=6 * s * fdir, r=8 * s, rot=-.62 * fdir)  # thumb


def holo_finger(k, dirx=1):
    """A fist with one finger extended, tip at local 0,0, the rest of the hand
    trailing off toward the screen edge behind it."""
    g_rect(k, 100, 26, x=-50 * dirx, r=13)                                   # index
    g_rect(k, 14, 18, x=-12 * dirx, y=-2, r=6)                               # nail
    for j, fy in enumerate((-22, 0, 22)):                                    # curled
        g_rect(k, 32, 19, x=(-108 - j % 2 * 4) * dirx, y=fy, r=9)
    g_rect(k, 70, 80, x=-140 * dirx, r=26)                                   # fist


def holo_point(k):
    """A hand with the index finger pointing straight up (gesture input)."""
    g_rect(k, 80, 76, y=40, r=26)                                            # fist
    g_rect(k, 24, 92, x=-16, y=-36, r=12)                                    # index
    for fx in (6, 24):
        g_rect(k, 20, 30, x=fx, y=6, r=9)                                    # curled
    g_rect(k, 18, 44, x=-44, y=34, r=9, rot=-.5)                             # thumb


def g_bolt(k, s=1.0):
    w(k, '<PointsPath isClosed="true" name="P">')
    for x, y in ((8, -46), (-22, 6), (-2, 6), (-10, 46), (22, -8), (2, -8)):
        w(k + 1, '<StraightVertex x="%g" y="%g" radius="2"/>' % (x * s, y * s))
    w(k, '</PointsPath>')


def g_poly_path(k, pts, closed=False):
    w(k, '<PointsPath%s name="P">' % (' isClosed="true"' if closed else ''))
    for x, y in pts:
        w(k + 1, '<StraightVertex x="%g" y="%g"/>' % (x, y))
    w(k, '</PointsPath>')


def g_gear(k, r, teeth):
    g_star(k, r * 2, points=teeth, inner=.80, corner=3)


def g_anger(k, r=21, hw=12, dep=9):
    """The manga anger vein (💢): four arcs bulging toward a common centre."""
    for i in range(4):
        th = math.pi / 4 + i * math.pi / 2
        w(k, '<PointsPath x="%g" y="%g" rotation="%.4f" name="P">'
          % (math.sin(th) * r, -math.cos(th) * r, th))
        w(k + 1, '<StraightVertex x="%g" y="-4"/>' % -hw)
        w(k + 1, '<CubicMirroredVertex x="0" y="%g" rotation="0" distance="%g"/>' % (dep - 4, hw * .64))
        w(k + 1, '<StraightVertex x="%g" y="-4"/>' % hw)
        w(k, '</PointsPath>')


# 7-segment digits for the standby clock: a Solo per digit, one child per value
SEG = {  # name: (x, y, w, h) in a 56x100 cell
    "a": (0, -44, 40, 12), "b": (22, -22, 12, 40), "c": (22, 22, 12, 40),
    "d": (0, 44, 40, 12), "e": (-22, 22, 12, 40), "f": (-22, -22, 12, 40),
    "g": (0, 0, 40, 12)}
DIGIT_SEGS = ["abcdef", "bc", "abged", "abgcd", "fgbc", "afgcd", "afgedc", "abc",
              "abcdefg", "abcdfg"]
CLOCK_SOLOS = []      # solo id per digit slot
CLOCK_DIGITS = []     # [slot][value] -> node id

CX_ = CX  # alias used in lambdas below
EY = EYE_ABS_Y
CRX, CRY = CORNER_R
CLX, CLY = CORNER_L


# ================================================================= SCENE PROPS
# One composite prop per scene storyboard (.obsidian-wiki/02_Components/Pet_Scene_Scripts.md).
# Every moving part is a named mover; the scene's story keys them by
# "prop:<index>:<mover>". Rest pose of every mover is where it sits at the
# climax of the scene, so story keyframes are OFFSETS from that pose.
#
# Swappable items (food, drink, laptop / book) are Solos driven by the `item`
# channel, so one EATING story serves burger, pizza, cake, ice cream, popcorn.
ITEM_SOLOS = []      # (solo id, [child ids])


def fit(k, cx, cy, sc, draw, sy=None):
    """Draw at absolute coordinates, scaled about (cx, cy). Children keep the
    back-to-front authoring order used everywhere else."""
    w(k, '<Node x="%g" y="%g" scaleX="%g" scaleY="%g" name="Fit">' % (cx, cy, sc, sc if sy is None else sy))
    w(k + 1, '<Node x="%g" y="%g" name="FitAt">' % (-cx, -cy))
    mark = len(out)
    draw(k + 2)
    chunk = out[mark:]
    del out[mark:]
    out.extend(reverse_blocks(chunk, k + 2))
    w(k + 1, '</Node>')
    w(k, '</Node>')


# Readability pass (phone screens): small status icons grow about their own
# centre. (pivot x, pivot y, scale) -- every result stays inside the artboard
# and head-top icons stay above the eye row (eye top = EYE_ABS_Y - EYE_RR = 163).
PROP_FIT = {
    "Question": (CRX, CRY + 60, 1.15), "Burger": (CX, 382, 1.5), "Beer": (CX, 388, 1.45),
    "Sparkle": (CRX - 10, CRY + 20, 1.35), "Blush": (CX, EYE_ABS_Y + 90, 1.3), "Exclaim": (CRX, CRY + 10, 1.3),
    "Trash": (430, 418, 1.3), "Camera": (CRX, CRY + 10, 1.35), "AngryMark": (CRX, CRY + 6, 1.5),
    "ThinkDots": (410, 84, 1.4), "SoundWave": (CX, 420, 1.25), "Questions": (CX, 100, 1.2),
    "Sweat": (CRX, CRY + 30, 1.5), "Medal": (CRX, CRY, 1.35), "BatteryLow": (CRX - 4, CRY, 1.5),
    "HoloPat": (CX, 70, 1.3), "HoloChin": (CX, 440, 1.3), "Bolt": (CRX, CRY, 1.5),
    "Calendar": (CRX - 4, CRY + 6, 1.5),
    "HoloPokeL": (LEFT_X - EYE_RR - 2, EYE_ABS_Y + 40, 1.45), "HoloPokeR": (RIGHT_X + EYE_RR + 2, EYE_ABS_Y + 40, 1.45), "Pencil": (CRX, CRY, 1.35), "Warning": (CRX, CRY, 1.6),
}


def item_solo(k, name, drawers):
    sid = nid()
    ids = []
    w(k, '<Solo activeComponentId="0:0" name="%s" id="%s">' % (name, sid))
    for label, fn in drawers:
        cid = nid()
        ids.append(cid)
        w(k + 1, '<Node name="%s" id="%s">' % (label, cid))
        mark = len(out)
        fn(k + 2)
        chunk = out[mark:]
        del out[mark:]
        out.extend(reverse_blocks(chunk, k + 2))
        w(k + 1, '</Node>')
    w(k, '</Solo>')
    ITEM_SOLOS.append((sid, ids))
    return sid


# ---- item drawings, centred on (x, y) -----------------------------------
def draw_burger(k, x, y, s=1.0):
    solid(k, "BunBot", lambda q: g_rect(q, 92 * s, 30 * s, rtl=4, rtr=4, rbl=14, rbr=14), CREAM, x, y + 42 * s)
    solid(k, "Salad", lambda q: g_rect(q, 104 * s, 12 * s, r=6), GREEN, x, y + 19 * s)
    solid(k, "Patty", lambda q: g_rect(q, 100 * s, 20 * s, r=8), BROWN, x, y + 2 * s)
    solid(k, "BunTop", lambda q: g_rect(q, 92 * s, 40 * s, rtl=20, rtr=20, rbl=4, rbr=4), CREAM, x, y - 28 * s)
    solid(k, "Seed", lambda q: (g_ellipse(q, 8, 5, x=-18), g_ellipse(q, 8, 5, x=14, y=-6)), "FFE8C880", x, y - 34 * s)


def draw_pizza(k, x, y):
    w(k, '<Shape x="%g" y="%g" name="Slice">' % (x, y))
    w(k + 1, '<PointsPath isClosed="true" name="P">')
    for px, py in ((-46, -40), (46, -40), (0, 58)):
        w(k + 2, '<StraightVertex x="%g" y="%g" radius="6"/>' % (px, py))
    w(k + 1, '</PointsPath>')
    p_fill(k + 1, "FFFFC857")
    w(k, '</Shape>')
    solid(k, "Crust", lambda q: g_rect(q, 100, 18, r=9), "FFD98A3D", x, y - 42)
    solid(k, "Pep", lambda q: (g_ellipse(q, 16, 16, x=-16, y=-18), g_ellipse(q, 14, 14, x=14, y=-10),
                               g_ellipse(q, 12, 12, x=0, y=16)), RED, x, y)


def draw_cake(k, x, y):
    solid(k, "Base", lambda q: g_rect(q, 100, 40, r=8), "FFF7C6D9", x, y + 20)
    solid(k, "Top", lambda q: g_rect(q, 80, 32, r=8), "FFFFE3EE", x, y - 14)
    solid(k, "Icing", lambda q: g_rect(q, 104, 10, r=5), WHITE, x, y + 2)
    solid(k, "Candle", lambda q: g_rect(q, 10, 30, r=4), "FF7FD4FF", x, y - 44)
    neon(k, "Flame", lambda q: g_drop(q, .55, rot=3.1416), "FFFFB020", x, y - 66, feather=12)


def draw_icecream(k, x, y):
    w(k, '<Shape x="%g" y="%g" name="Cone">' % (x, y + 28))
    w(k + 1, '<PointsPath isClosed="true" name="P">')
    for px, py in ((-34, -20), (34, -20), (0, 56)):
        w(k + 2, '<StraightVertex x="%g" y="%g" radius="4"/>' % (px, py))
    w(k + 1, '</PointsPath>')
    p_fill(k + 1, "FFE0A458")
    w(k, '</Shape>')
    solid(k, "Scoop1", lambda q: g_ellipse(q, 70, 58), "FFFF9EC7", x, y - 6)
    solid(k, "Scoop2", lambda q: g_ellipse(q, 58, 50), "FFBDF2D0", x, y - 44)
    solid(k, "Cherry", lambda q: g_ellipse(q, 18, 18), RED, x, y - 74)


def draw_popcorn(k, x, y):
    solid(k, "Bucket", lambda q: g_rect(q, 70, 76, rtl=2, rtr=2, rbl=16, rbr=16), WHITE, x, y + 20)
    for j in (-22, 0, 22):
        solid(k, "Stripe%d" % j, lambda q: g_rect(q, 11, 76, r=2), RED, x + j, y + 20)
    for j, (ox, oy, rr) in enumerate(((-24, -24, 26), (2, -34, 28), (24, -22, 24), (-8, -14, 22))):
        solid(k, "Pop%d" % j, lambda q, rr=rr: g_ellipse(q, rr, rr), CREAM, x + ox, y + oy)


def draw_mug(k, x, y):
    line(k, "Handle", lambda q: g_ellipse(q, 30, 36), "FFE8EEF6", 8, x=x + 42, y=y)
    solid(k, "Body", lambda q: g_rect(q, 76, 84, rtl=6, rtr=6, rbl=18, rbr=18), "FFE8EEF6", x, y)
    solid(k, "Coffee", lambda q: g_ellipse(q, 62, 14), "FF6B3E1E", x, y - 36)
    solid(k, "Heart", lambda q: g_heart(q, .22), "FFF3D6B5", x, y + 6)


def draw_boba(k, x, y):
    solid(k, "Straw", lambda q: g_rect(q, 12, 70, r=5, rot=.18), PINK, x + 8, y - 64)
    solid(k, "Cup", lambda q: g_rect(q, 70, 96, rtl=4, rtr=4, rbl=20, rbr=20), "FFF1D3B0", x, y)
    solid(k, "Lid", lambda q: g_rect(q, 80, 12, r=6), "EEFFFFFF", x, y - 48)
    solid(k, "Pearls", lambda q: (g_ellipse(q, 12, 12, x=-18, y=30), g_ellipse(q, 12, 12, x=0, y=34),
                                  g_ellipse(q, 12, 12, x=18, y=30), g_ellipse(q, 12, 12, x=-8, y=18),
                                  g_ellipse(q, 12, 12, x=10, y=20)), "FF3B2415", x, y)


def draw_tea(k, x, y):
    solid(k, "Saucer", lambda q: g_ellipse(q, 110, 22), "FFE6F2EA", x, y + 34)
    line(k, "Handle", lambda q: g_ellipse(q, 24, 26), "FFD8F0E0", 7, x=x + 40, y=y)
    solid(k, "Cup", lambda q: g_rect(q, 80, 58, rtl=4, rtr=4, rbl=28, rbr=28), "FFD8F0E0", x, y + 2)
    solid(k, "Tea", lambda q: g_ellipse(q, 66, 12), "FF7BBF6A", x, y - 24)


def draw_beer(k, x, y):
    line(k, "Handle", lambda q: g_ellipse(q, 34, 44), CREAM, 8, x=x + 44, y=y + 6)
    grad(k, "Glass", lambda q: g_rect(q, 70, 90, rtl=6, rtr=6, rbl=12, rbr=12), "FFFFD86B", "FFD98A16", x, y + 6, gy=45)
    solid(k, "Foam", lambda q: (g_ellipse(q, 36, 28, x=-18, y=-4), g_ellipse(q, 32, 26, x=14, y=-8),
                                g_rect(q, 72, 20, r=10, y=4)), WHITE, x, y - 40)


def draw_laptop(k, x, y):
    solid(k, "Base", lambda q: g_rect(q, 190, 16, rbl=8, rbr=8), SILVER, x, y + 48)
    solid(k, "Lid", lambda q: g_rect(q, 170, 104, r=10), "FF2A3246", x, y - 12)
    solid(k, "Screen", lambda q: g_rect(q, 152, 86, r=6), "FF0E2A3A", x, y - 12)


def draw_book(k, x, y):
    solid(k, "Cover", lambda q: g_rect(q, 190, 110, r=8), "FF8E3B2E", x, y)
    solid(k, "PageL", lambda q: g_rect(q, 86, 96, rtl=4, rbl=4), "FFF7F1E3", x - 44, y - 2)
    solid(k, "PageR", lambda q: g_rect(q, 86, 96, rtr=4, rbr=4), "FFFFFBF0", x + 44, y - 2)
    solid(k, "Spine", lambda q: g_rect(q, 4, 96), "33000000", x, y - 2)


def iso_card(k, name, fill, deco):
    """An isometric 'VR window' card: a slanted parallelogram with a tiny scene."""
    w(k, '<Shape name="%s">' % name)
    w(k + 1, '<PointsPath isClosed="true" name="P">')
    for px, py in ((-110, -28), (46, -90), (110, 28), (-46, 90)):
        w(k + 2, '<StraightVertex x="%g" y="%g" radius="10"/>' % (px, py))
    w(k + 1, '</PointsPath>')
    p_fill(k + 1, fill)
    w(k + 1, '<Stroke thickness="6" join="round" name="S">')
    w(k + 2, '<SolidColor colorValue="FF8FF4FF" name="C"/>')
    w(k + 2, '<Feather strength="10" name="F"/>')
    w(k + 1, '</Stroke>')
    w(k, '</Shape>')
    deco(k)


SCENE_NAMES = ["SceneFood", "SceneDrink", "SceneBath", "SceneGame", "SceneStudy", "SceneRain",
               "SceneThug", "SceneRich", "SceneRoyal", "SceneFire", "SceneThunder", "SceneSoul",
               "SceneLove", "SceneCry", "SceneParty", "SceneVR", "SceneMusic"]


def emit_scene_prop(dd, p):
    name = P_NAMES[p]
    BY = 420.0      # bottom stage line

    if name == "SceneFood":
        def plate(k):
            solid(k, "Plate", lambda q: g_ellipse(q, 170, 34), "FFE8EEF6", CX, BY + 24)
            solid(k, "PlateRim", lambda q: g_ellipse(q, 130, 18), "FFC9D4E2", CX, BY + 22)
            mover(k, "Food", lambda q: fit(q, CX, BY - 30, 1.25, lambda z0: item_solo(z0, "FoodItem", [
                ("Burger", lambda z: draw_burger(z, CX, BY - 30, .9)),
                ("Pizza", lambda z: draw_pizza(z, CX, BY - 30)),
                ("Cake", lambda z: draw_cake(z, CX, BY - 26)),
                ("IceCream", lambda z: draw_icecream(z, CX, BY - 40)),
                ("Popcorn", lambda z: draw_popcorn(z, CX, BY - 30))])), anchor=(CX, BY - 30))
        mover(dd, "Plate", plate, anchor=(CX, BY))
        mover(dd, "Crumbs", lambda k: solid(k, "C", lambda q: (
            g_rect(q, 9, 7, x=-40, r=2), g_rect(q, 7, 6, x=-10, y=-8, r=2), g_rect(q, 8, 7, x=26, y=4, r=2),
            g_rect(q, 6, 6, x=46, y=-6, r=2)), "FFE0A458", CX, 330), anchor=(CX, 330))
        mover(dd, "Hearts", lambda k: (neon(k, "H1", lambda q: g_heart(q, .3), HEART, 96, 170, feather=12),
                                       neon(k, "H2", lambda q: g_heart(q, .22, rot=.3), HEART, 408, 150, feather=12)),
              anchor=(CX, 160))
        mover(dd, "Sparkles", lambda k: [neon(k, "S%d" % i, lambda q, s=s: g_star(q, s), GOLD, sx, sy)
                                         for i, (sx, sy, s) in enumerate(((150, 360, 30), (350, 350, 38), (250, 300, 24)))],
              anchor=(CX, 340))

    elif name == "SceneDrink":
        cx, cy = 360.0, 380.0
        def cup(k):
            mover(k, "Liquid", lambda q: item_solo(q, "DrinkItem", [
                ("Coffee", lambda z: draw_mug(z, cx, cy)),
                ("Boba", lambda z: draw_boba(z, cx, cy)),
                ("Tea", lambda z: draw_tea(z, cx, cy)),
                ("Beer", lambda z: draw_beer(z, cx, cy))]), anchor=(cx, cy + 40))
            m = mover(k, "Steam", lambda q: [line(q, "S%d" % i, lambda z: g_wave(z, 16, 6), "99E8EEF6", 5,
                                                   x=cx - 16 + i * 16, y=cy - 70, rot=1.5708)
                                              for i in range(3)], anchor=(cx, cy - 70))
            track(m, 14, [(0, 0), (40, -10), (80, 0)])
            track(m, 18, [(0, .3), (40, .9), (80, .3)])
        mover(dd, "Cup", cup, anchor=(cx, cy))
        mover(dd, "Sparkles", lambda k: [neon(k, "S%d" % i, lambda q, s=s: g_star(q, s), GOLD, sx, sy)
                                         for i, (sx, sy, s) in enumerate(((430, 120, 40), (80, 140, 30)))],
              anchor=(CX, 130))

    elif name == "SceneBath":
        def shower(k):
            solid(k, "Pipe", lambda q: g_rect(q, 16, 80, r=6), SILVER, CX + 70, 20)
            solid(k, "Head", lambda q: g_rect(q, 120, 30, rtl=6, rtr=6, rbl=26, rbr=26), SILVER, CX, 62)
            mover(k, "Water", lambda q: [solid(q, "D%d" % i, lambda z: g_rect(z, 5, 26, r=3), "AA9FE0FF",
                                               CX - 45 + i * 18, 100 + (i % 2) * 20) for i in range(6)],
                  anchor=(CX, 110))
        mover(dd, "Shower", shower, anchor=(CX, 60))
        mover(dd, "Bubbles", lambda k: [line(k, "B%d" % i, lambda q, s=s: g_ellipse(q, s, s), "CCE8F8FF", 4, x=bx, y=by)
                                        for i, (bx, by, s) in enumerate(((70, 170, 44), (430, 160, 52), (96, 330, 36),
                                                                          (410, 320, 40), (250, 120, 30), (170, 380, 26)))],
              anchor=(CX, 250))
        mover(dd, "Sponge", lambda k: (solid(k, "Body", lambda q: g_rect(q, 90, 54, r=18), "FFFFD86B", CX, 110),
                                       solid(k, "Holes", lambda q: (g_ellipse(q, 10, 8, x=-20), g_ellipse(q, 8, 8, x=10, y=-10),
                                                                    g_ellipse(q, 9, 7, x=24, y=10)), "FFE0B040", CX, 110)),
              anchor=(CX, 110))
        mover(dd, "Clean", lambda k: [neon(k, "S%d" % i, lambda q, s=s: g_star(q, s), WHITE, sx, sy)
                                      for i, (sx, sy, s) in enumerate(((80, 150, 40), (420, 140, 46), (250, 90, 30)))],
              anchor=(CX, 130))
        mover(dd, "Duck", lambda k: (solid(k, "Body", lambda q: g_ellipse(q, 70, 46), "FFFFD23F", CX, 440),
                                     solid(k, "Head", lambda q: g_ellipse(q, 40, 40), "FFFFD23F", CX + 24, 410),
                                     solid(k, "Beak", lambda q: g_tri(q, 18, 14, rot=1.5708), ORANGE, CX + 48, 412),
                                     solid(k, "Eye", lambda q: g_ellipse(q, 6, 6), DARK, CX + 30, 404)),
              anchor=(CX, 430))

    elif name == "SceneGame":
        def pad(k):
            solid(k, "Body", lambda q: g_rect(q, 200, 84, r=40), "FF39415A", CX, BY + 10)
            solid(k, "DPad", lambda q: (g_rect(q, 42, 12, r=4), g_rect(q, 12, 42, r=4)), "FF15181F", CX - 58, BY + 10)
            solid(k, "BtnA", lambda q: g_ellipse(q, 18, 18), RED, CX + 50, BY + 2)
            solid(k, "BtnB", lambda q: g_ellipse(q, 18, 18), GREEN, CX + 74, BY + 20)
        mover(dd, "Controller", pad, anchor=(CX, BY + 10))

        def screen(k):
            solid(k, "Frame", lambda q: g_rect(q, 230, 110, r=12), "FF1A2030", CX, 90)
            solid(k, "Sky", lambda q: g_rect(q, 214, 94, r=6), "FF12345A", CX, 90)
            solid(k, "Ground", lambda q: g_rect(q, 214, 14), GREEN, CX, 130)
            m = mover(k, "Hero", lambda q: (solid(q, "B", lambda z: g_rect(z, 18, 22, r=3), GOLD, CX, 112),
                                            solid(q, "E", lambda z: g_rect(z, 4, 4), DARK, CX + 4, 108)),
                      anchor=(CX, 112))
            track(m, 13, [(0, -80), (120, 80), (240, -80)], interp="linear")
            track(m, 14, [(0, 0), (15, -22), (30, 0), (60, 0), (75, -22), (90, 0), (120, 0)])
            mover(k, "Enemy", lambda q: solid(q, "B", lambda z: g_rect(z, 18, 18, r=4), RED, CX + 40, 115),
                  anchor=(CX + 40, 115))
        mover(dd, "Screen", screen, anchor=(CX, 90))
        mover(dd, "Win", lambda k: [neon(k, "S%d" % i, lambda q, s=s: g_star(q, s, points=5, inner=.45), GOLD, sx, 40)
                                    for i, (sx, s) in enumerate(((190, 34), (250, 46), (310, 34)))],
              anchor=(CX, 40))

    elif name == "SceneStudy":
        def desk(k):
            mover(k, "Device", lambda q: item_solo(q, "StudyItem", [
                ("Laptop", lambda z: draw_laptop(z, CX, BY)),
                ("Book", lambda z: draw_book(z, CX, BY))]), anchor=(CX, BY))
            mover(k, "Lines", lambda q: [solid(q, "L%d" % i, lambda z, ww=ww: g_rect(z, ww, 6, r=3), "CC7FE8FF",
                                                CX - 60 + ww / 2.0, BY - 40 + i * 14) for i, ww in enumerate((96, 120, 70, 110, 84))],
                  anchor=(CX, BY - 12))
        mover(dd, "Desk", desk, anchor=(CX, BY))
        mover(dd, "Dots", lambda k: [neon(k, "D%d" % i, lambda q, r=r: g_ellipse(q, r, r), CYAN, 380 + i * 30, 90)
                                     for i, r in enumerate((12, 16, 22))], anchor=(410, 90))
        def bulb(k):
            glowdisc(k, "Glow", 60, "66FFE07A", "00FFE07A", 90, 100)
            shape(k, "Glass", lambda q: (g_ellipse(q, 50, 56), p_glowfill(q, "FFFFE9A0", 14, 4)), 90, 96)
            solid(k, "Base", lambda q: g_rect(q, 24, 16, r=4), SILVER, 90, 130)
        mover(dd, "Bulb", lambda k: fit(k, 90, 100, 1.5, bulb), anchor=(90, 100))
        mover(dd, "Check", lambda k: line(k, "C", lambda q: g_poly_path(q, ((-33, 0), (-9, 27), (36, -30))), GREEN, 16,
                                          x=420, y=110, feather=12), anchor=(420, 110))

    elif name == "SceneRain":
        def cloud(k):
            solid(k, "C", lambda q: (g_ellipse(q, 110, 80, x=-60, y=10), g_ellipse(q, 130, 110, x=10, y=-14),
                                     g_ellipse(q, 100, 76, x=74, y=12), g_rect(q, 240, 50, y=30, r=25)), "FF8D9AB3", CX, 60)
            m = mover(k, "Drops", lambda q: [neon(q, "D%d" % i, lambda z: g_drop(z, .5), TEAR, CX - 110 + i * 44, 120,
                                                  feather=8) for i in range(6)], anchor=(CX, 120))
            track(m, 14, [(0, 0), (30, 120)], interp="linear")
            track(m, 18, [(0, 1), (24, 1), (30, 0)])
        mover(dd, "Cloud", cloud, anchor=(CX, 60))
        mover(dd, "HeadDrop", lambda k: neon(k, "D", lambda q: g_drop(q, .9), TEAR, 330, 140, feather=10),
              anchor=(330, 140))
        def umbrella(k):
            mover(k, "Canopy", lambda q: (shape(q, "Top", lambda z: (g_arcup(z, 170, 110, 0),
                                                                     p_fill(z, "FFFF5C8A")), CX, 90),
                                          solid(q, "Stripe", lambda z: g_rect(z, 8, 100, r=4), "FFFFB3C8", CX, 60)),
                  anchor=(CX, 90))
            solid(k, "Shaft", lambda q: g_rect(q, 8, 150, r=4), SILVER, CX, 160)
            line(k, "Hook", lambda q: g_arcdown(q, 16, 18, 0), SILVER, 7, x=CX - 16, y=236)
        mover(dd, "Umbrella", umbrella, anchor=(CX, 150))
        mover(dd, "Rainbow", lambda k: [line(k, "R%d" % i, lambda q, i=i: g_arcup(q, 200 - i * 14, 120 - i * 10, 0), col, 10,
                                             x=CX, y=150) for i, col in enumerate(("AAFF5C8A", "AAFFC53D", "AA4CD98A", "AA3FE7F5"))],
              anchor=(CX, 120))

    elif name == "SceneThug":
        mover(dd, "Shades", lambda k: (
            solid(k, "Bridge", lambda q: g_rect(q, 44, 30, r=2), "FF101216", CX, EY - 50),
            solid(k, "L", lambda q: (g_rect(q, 176, 40, y=-58), g_rect(q, 176, 40, y=-20), g_rect(q, 176, 40, y=18),
                                     g_rect(q, 156, 36, x=-8, y=54), g_rect(q, 116, 30, x=-22, y=86)), "FF101216", LEFT_X - 2, EY),
            solid(k, "R", lambda q: (g_rect(q, 176, 40, y=-58), g_rect(q, 176, 40, y=-20), g_rect(q, 176, 40, y=18),
                                     g_rect(q, 156, 36, x=8, y=54), g_rect(q, 116, 30, x=22, y=86)), "FF101216", RIGHT_X + 2, EY),
            solid(k, "ArmL", lambda q: g_rect(q, 44, 20), "FF101216", CX - 212, EY - 58),
            solid(k, "ArmR", lambda q: g_rect(q, 44, 20), "FF101216", CX + 212, EY - 58),
            solid(k, "Pix", lambda q: (g_rect(q, 26, 16, x=-160, y=-40), g_rect(q, 26, 16, x=-134, y=-22),
                                       g_rect(q, 26, 16, x=52, y=-40), g_rect(q, 26, 16, x=78, y=-22)), "FFFFFFFF", CX, EY - 18)),
              x=0, y=12, anchor=(CX, EY))
        mover(dd, "Chain", lambda k: (line(k, "C", lambda q: g_arcdown(q, 110, 60, 0), GOLD, 12, x=CX, y=370, feather=10),
                                      neon(k, "Pend", lambda q: g_rect(q, 40, 40, r=6, rot=.785), GOLD, CX, 440, feather=10)),
              anchor=(CX, 400))
        mover(dd, "Spot", lambda k: glowdisc(k, "S", 220, "30FFFFFF", "00FFFFFF", CX, CY - 30), anchor=(CX, CY))
        m = mover(dd, "Stars", lambda k: [neon(k, "S%d" % i, lambda q, s=s: g_star(q, s), GOLD, sx, sy)
                                          for i, (sx, sy, s) in enumerate(((70, 80, 30), (430, 90, 36), (440, 400, 26)))],
                  anchor=(CX, 200))
        track(m, 18, [(0, .3), (30, 1), (60, .3)])

    elif name == "SceneRich":
        for i, (cx_, cy_) in enumerate(((120, 440), (250, 450), (380, 440))):
            mover(dd, "Coin%d" % i, lambda k, cx_=cx_, cy_=cy_: (
                grad(k, "Disc", lambda q: g_ellipse(q, 60, 60), "FFFFE07A", "FFC98A18", cx_, cy_, gy=30),
                solid(k, "Mark", lambda q: g_rect(q, 10, 30, r=4), "FF9A6A05", cx_, cy_)), anchor=(cx_, cy_))
        mover(dd, "Gold", lambda k: [grad(k, "Bar%d" % i, lambda q: g_rect(q, 80, 30, rtl=10, rtr=10, rbl=4, rbr=4),
                                          "FFFFE07A", "FFC98A18", 190 + i * 60 - (30 if i == 3 else 0), 460 - (32 if i == 3 else 0), gy=15)
                                     for i in range(4)], anchor=(CX, 450))
        m = mover(dd, "Sparkles", lambda k: [neon(k, "S%d" % i, lambda q, s=s: g_star(q, s), WHITE, sx, sy)
                                             for i, (sx, sy, s) in enumerate(((80, 120, 34), (430, 110, 40), (400, 380, 26)))],
                  anchor=(CX, 200))
        track(m, 18, [(0, .2), (20, 1), (40, .2)])

    elif name == "SceneRoyal":
        mover(dd, "Cape", lambda k: (solid(k, "L", lambda q: g_rect(q, 80, 120, rtl=30, rbl=8), "FFB0184A", 50, 450),
                                     solid(k, "R", lambda q: g_rect(q, 80, 120, rtr=30, rbr=8), "FFB0184A", 450, 450),
                                     solid(k, "FurL", lambda q: g_rect(q, 90, 16, r=8), WHITE, 56, 392),
                                     solid(k, "FurR", lambda q: g_rect(q, 90, 16, r=8), WHITE, 444, 392)), anchor=(CX, 420))
        def crown(k):
            w(k, '<Shape x="%g" y="%g" name="Band">' % (CX, 92))
            w(k + 1, '<PointsPath isClosed="true" name="P">')
            for px, py in ((-80, 30), (-80, -20), (-45, 8), (0, -40), (45, 8), (80, -20), (80, 30)):
                w(k + 2, '<StraightVertex x="%g" y="%g" radius="4"/>' % (px, py))
            w(k + 1, '</PointsPath>')
            p_glowfill(k + 1, GOLD, 12, 4)
            w(k, '</Shape>')
            solid(k, "Gems", lambda q: (g_ellipse(q, 14, 14, x=-40), g_ellipse(q, 16, 16), g_ellipse(q, 14, 14, x=40)), RED, CX, 112)
        mover(dd, "Crown", crown, anchor=(CX, 92))
        m = mover(dd, "Petals", lambda k: [solid(k, "P%d" % i, lambda q: g_ellipse(q, 14, 20, rot=.6), PINK, px, 0)
                                           for i, px in enumerate((60, 150, 320, 440))], anchor=(CX, 0))
        track(m, 14, [(0, -20), (120, 520)], interp="linear")
        mover(dd, "Sparkles", lambda k: [neon(k, "S%d" % i, lambda q, s=s: g_star(q, s), WHITE, sx, sy)
                                         for i, (sx, sy, s) in enumerate(((140, 70, 26), (370, 60, 30)))], anchor=(CX, 70))

    elif name == "SceneFire":
        def flames(k):
            neon(k, "Outer", lambda q: (g_drop(q, 2.4, x=-40, rot=3.1416), g_drop(q, 3.0, rot=3.1416),
                                        g_drop(q, 2.2, x=44, rot=3.1416)), "FFFF6B2B", CX, 460, feather=20)
            solid(k, "Inner", lambda q: (g_drop(q, 1.4, x=-30, rot=3.1416), g_drop(q, 1.8, rot=3.1416),
                                         g_drop(q, 1.3, x=32, rot=3.1416)), "FFFFD23F", CX, 474)
        m = mover(dd, "Flames", flames, anchor=(CX, 470))
        mover(dd, "Sweat", lambda k: (neon(k, "D1", lambda q: g_drop(q, 1.1), TEAR, 440, 150, feather=10),
                                      neon(k, "D2", lambda q: g_drop(q, .8), TEAR, 60, 170, feather=10)), anchor=(CX, 160))
        mover(dd, "Bucket", lambda k: (solid(k, "B", lambda q: g_rect(q, 90, 80, rtl=4, rtr=4, rbl=16, rbr=16), "FF5A8FD8", CX, 40),
                                       solid(k, "Rim", lambda q: g_rect(q, 100, 12, r=6), "FF3E6FB8", CX, 2)), anchor=(CX, 40))
        mover(dd, "Splash", lambda k: [neon(k, "W%d" % i, lambda q: g_drop(q, .9), TEAR, CX - 90 + i * 45, 300, feather=10)
                                       for i in range(5)], anchor=(CX, 300))
        mover(dd, "Soot", lambda k: solid(k, "S", lambda q: (g_ellipse(q, 60, 40, x=-30), g_ellipse(q, 70, 50, x=20, y=-10)),
                                          "66707A94", CX, 110), anchor=(CX, 110))

    elif name == "SceneThunder":
        def cloud(k):
            solid(k, "C", lambda q: (g_ellipse(q, 120, 90, x=-60, y=10), g_ellipse(q, 140, 120, x=10, y=-16),
                                     g_ellipse(q, 110, 84, x=74, y=12), g_rect(q, 250, 54, y=32, r=27)), "FF3A4256", CX, 56)
            mover(k, "Glow", lambda q: glowdisc(q, "G", 140, "88FFF3A0", "00FFF3A0", CX, 56), anchor=(CX, 56))
        mover(dd, "Cloud", cloud, anchor=(CX, 56))
        mover(dd, "Bolt", lambda k: neon(k, "B", lambda q: g_bolt(q, 2.4), GOLD, CX + 10, 150, feather=22), anchor=(CX, 60))
        mover(dd, "Flash", lambda k: wash(k, "F", "EEFFFFFF", hold=.6), anchor=(CX, CY))
        m = mover(dd, "Sparks", lambda k: [line(k, "Z%d" % i, lambda q: g_poly_path(q, ((0, 0), (10, 12), (0, 20), (12, 34))),
                                                GOLD, 5, x=sx, y=sy, rot=rot, feather=8)
                                           for i, (sx, sy, rot) in enumerate(((70, 180, -.5), (430, 170, .5), (90, 300, -.8), (410, 300, .8)))],
                  anchor=(CX, 240))
        track(m, 18, [(0, 1), (4, .2), (8, 1), (12, .3), (16, 1)], interp="hold")
        mover(dd, "Soot", lambda k: solid(k, "S", lambda q: (g_ellipse(q, 70, 46, x=-34), g_ellipse(q, 80, 58, x=20, y=-12),
                                                             g_ellipse(q, 56, 40, x=56, y=4)), "AA2A2F3A", CX, 100), anchor=(CX, 100))

    elif name == "SceneSoul":
        mover(dd, "Sweat", lambda k: neon(k, "D", lambda q: g_drop(q, 1.2), TEAR, 420, 150, feather=10), anchor=(420, 150))
        def soul(k):
            neon(k, "Body", lambda q: (g_ellipse(q, 130, 140, y=-14), g_rect(q, 130, 80, y=40, rbl=14, rbr=14)),
                 "AA9FE8FF", CX, 170, feather=24)
            solid(k, "Eyes", lambda q: (g_ellipse(q, 18, 24, x=-26), g_ellipse(q, 18, 24, x=26)), "EE0B1020", CX, 160)
            solid(k, "Mouth", lambda q: g_ellipse(q, 22, 16), "EE0B1020", CX, 196)
        mover(dd, "Soul", soul, anchor=(CX, 170))
        m = mover(dd, "Stars", lambda k: [neon(k, "S%d" % i, lambda q: g_star(q, 26, points=5, inner=.45), GOLD, CX, 90)
                                          for i in range(1)], anchor=(CX, 90))
        track(m, 13, [(0, -90), (30, 90), (60, -90)])

    elif name == "SceneLove":
        m = mover(dd, "BigHeart", lambda k: glowdisc(k, "H", 1, "00000000", "00000000", CX, CY) or
                  neon(k, "H", lambda q: g_heart(q, 2.6), "55FF5C8A", CX, CY + 10, feather=30), anchor=(CX, CY))
        mover(dd, "Blush", lambda k: [neon(k, "B%d" % i, lambda q: g_ellipse(q, 60, 26), PINK, bx, EY + 92, feather=14)
                                      for i, bx in enumerate((LEFT_X - 20, RIGHT_X + 20))], anchor=(CX, EY + 92))
        mover(dd, "Arrow", lambda k: (solid(k, "Shaft", lambda q: g_rect(q, 150, 8, r=4, rot=-.3), "FFB07A3A", CX - 20, 96),
                                      solid(k, "Tip", lambda q: g_heart(q, .22, rot=1.2), HEART, CX + 52, 76),
                                      solid(k, "Feather", lambda q: g_rect(q, 30, 20, r=4, rot=-.3), WHITE, CX - 92, 118)),
              anchor=(CX, 96))
        m = mover(dd, "Small", lambda k: [neon(k, "H%d" % i, lambda q, s=s: g_heart(q, s), HEART, hx, 440, feather=12)
                                          for i, (hx, s) in enumerate(((70, .28), (170, .2), (330, .24), (430, .3)))],
                  anchor=(CX, 440))
        track(m, 14, [(0, 0), (90, -420)], interp="linear")
        mover(dd, "Burst", lambda k: [neon(k, "S%d" % i, lambda q, s=s: g_star(q, s), "FFFFB3C8", sx, sy)
                                      for i, (sx, sy, s) in enumerate(((150, 130, 40), (360, 120, 46), (120, 340, 32), (390, 350, 36)))],
              anchor=(CX, CY))

    elif name == "SceneCry":
        for side, sx in (("L", -1), ("R", 1)):
            mover(dd, "Half" + side, lambda k, sx=sx: w(k, '<Shape x="%g" y="%g" name="Half">' % (CX + sx * 3, 110)) or (
                w(k + 1, '<PointsPath isClosed="true" name="P">'),
                [w(k + 2, '<StraightVertex x="%g" y="%g"/>' % (px * sx, py)) for px, py in
                 ((0, 46), (-50, -2), (-44, -34), (-20, -44), (0, -26), (-8, -6), (6, 8), (0, 46))],
                w(k + 1, '</PointsPath>'), p_glowfill(k + 1, HEART, 12, 3), w(k, '</Shape>')),
                  anchor=(CX + sx * 20, 110))
        mover(dd, "Crack", lambda k: line(k, "C", lambda q: g_poly_path(q, ((0, -44), (-8, -20), (8, 0), (-6, 22), (0, 46))),
                                          WHITE, 4, x=CX, y=110), anchor=(CX, 110))
        def rain_cloud(k):
            solid(k, "C", lambda q: (g_ellipse(q, 90, 64, x=-40, y=6), g_ellipse(q, 100, 84, x=16, y=-10),
                                     g_ellipse(q, 76, 58, x=62, y=8), g_rect(q, 170, 40, y=24, r=20)), "FF6E7A92", CX, 60)
            m2 = mover(k, "Drops", lambda q: [neon(q, "D%d" % i, lambda z: g_drop(z, .45), TEAR, CX - 60 + i * 30, 110, feather=6)
                                              for i in range(5)], anchor=(CX, 110))
            track(m2, 14, [(0, 0), (24, 90)], interp="linear")
            track(m2, 18, [(0, 1), (20, 1), (24, 0)])
        mover(dd, "Cloud", rain_cloud, anchor=(CX, 60))
        mover(dd, "Tissue", lambda k: (solid(k, "Box", lambda q: g_rect(q, 90, 50, r=8), "FF7FD4FF", 430, 440),
                                       solid(k, "Sheet", lambda q: g_rect(q, 44, 56, r=10, rot=.2), WHITE, 426, 396)),
              anchor=(430, 420))

    elif name == "SceneParty":
        def hat(k):
            w(k, '<Shape x="%g" y="%g" name="Cone">' % (CX, 70))
            w(k + 1, '<PointsPath isClosed="true" name="P">')
            for px, py in ((-44, 44), (0, -60), (44, 44)):
                w(k + 2, '<StraightVertex x="%g" y="%g" radius="4"/>' % (px, py))
            w(k + 1, '</PointsPath>')
            p_fill(k + 1, PURPLE)
            w(k, '</Shape>')
            solid(k, "Stripes", lambda q: (g_rect(q, 60, 8, y=10, rot=-.2), g_rect(q, 40, 8, y=-18, rot=-.2)), GOLD, CX, 70)
            solid(k, "Pom", lambda q: g_ellipse(q, 22, 22), PINK, CX, 8)
        mover(dd, "Hat", hat, anchor=(CX, 80))
        mover(dd, "Popper", lambda k: (solid(k, "Tube", lambda q: g_rect(q, 40, 90, rtl=4, rtr=4, rbl=12, rbr=12, rot=.6),
                                             "FFFF7AA2", 90, 430),
                                       solid(k, "Band", lambda q: g_rect(q, 42, 10, rot=.6), GOLD, 104, 410)), anchor=(90, 430))
        mover(dd, "Burst", lambda k: [solid(k, "C%d" % i, lambda q, i=i: g_rect(q, 12, 20, r=3, rot=i * .7),
                                            (CYAN, PINK, GOLD, GREEN, PURPLE)[i % 5],
                                            140 + math.cos(i * .8) * (60 + i * 8), 340 - math.sin(i * .8 + .4) * (70 + i * 10))
                                      for i in range(10)], anchor=(140, 360))
        mover(dd, "Balloons", lambda k: [((line(k, "S%d" % i, lambda q: g_rect(q, 2, 80), "88FFFFFF", 2, x=bx, y=560),
                                           solid(k, "B%d" % i, lambda q: g_ellipse(q, 60, 72), col, bx, 500)))
                                         for i, (bx, col) in enumerate(((110, "FFFF5C8A"), (250, "FF3FE7F5"), (390, "FFFFC53D")))],
              anchor=(CX, 520))

    elif name == "SceneVR":
        mover(dd, "Visor", lambda k: (
            solid(k, "StrapL", lambda q: g_rect(q, 40, 30, r=8), GREY, CX - 212, EY),
            solid(k, "StrapR", lambda q: g_rect(q, 40, 30, r=8), GREY, CX + 212, EY),
            shape(k, "Shell", lambda q: (g_rect(q, 380, 168, r=70), p_grad(q, "FF3B2B7A", "FF120E2E", -190, -84, 190, 84)), CX, EY),
            line(k, "Rim", lambda q: g_rect(q, 380, 168, r=70), "FFE6ECF5", 9, x=CX, y=EY),
            solid(k, "Gleam", lambda q: g_rect(q, 150, 22, r=11, rot=-.3), "55FFFFFF", CX - 70, EY - 30),
            mover(k, "Lens", lambda q: (glowdisc(q, "LG", 120, "553FE7F5", "003FE7F5", CX, EY),
                                        line(q, "LL", lambda z: g_ellipse(z, 120, 90), "CC3FE7F5", 5, x=CX - 90, y=EY, feather=14),
                                        line(q, "LR", lambda z: g_ellipse(z, 120, 90), "CC3FE7F5", 5, x=CX + 90, y=EY, feather=14)),
                  anchor=(CX, EY))), anchor=(CX, EY))
        def card_mountain(k):
            iso_card(k, "Card", "FF1B5A8A", lambda z: (solid(z, "M", lambda q: g_tri(q, 110, 70), GREEN, -10, 14),
                                                        solid(z, "Sun", lambda q: g_ellipse(q, 30, 30), GOLD, 40, -30)))
        def card_stars(k):
            iso_card(k, "Card", "FF2A1B6A", lambda z: (neon(z, "S1", lambda q: g_star(q, 44), GOLD, -20, -8),
                                                        neon(z, "S2", lambda q: g_star(q, 28), WHITE, 30, 20)))
        def card_game(k):
            iso_card(k, "Card", "FF0E4A5A", lambda z: (solid(z, "H", lambda q: g_rect(q, 28, 34, r=4), GOLD, -24, 6),
                                                        solid(z, "E", lambda q: g_rect(q, 28, 28, r=6), RED, 30, 12),
                                                        solid(z, "G", lambda q: g_rect(q, 150, 10, rot=-.55), GREEN, 0, 36)))
        for label, fn in (("CardA", card_mountain), ("CardB", card_stars), ("CardC", card_game)):
            mover(dd, label, lambda k, fn=fn: w(k, '<Node x="%g" y="%g" name="At">' % (CX, 300)) or (fn(k + 1), w(k, '</Node>')),
                  anchor=(CX, 300))
        mover(dd, "Popcorn", lambda k: draw_popcorn(k, 430, 430), anchor=(430, 430))
        mover(dd, "Sparkles", lambda k: [neon(k, "S%d" % i, lambda q, s=s: g_star(q, s), CYAN, sx, sy)
                                         for i, (sx, sy, s) in enumerate(((60, 150, 30), (440, 140, 36)))], anchor=(CX, 145))

    elif name == "SceneMusic":
        mover(dd, "Phones", lambda k: (
            line(k, "Band", lambda q: g_arcup(q, 204, 96, 10), AMBER, 18, x=CX, y=EY - 70),
            solid(k, "PadL", lambda q: g_rect(q, 26, 80, r=13), DARK, LEFT_X - 88, EY + 6),
            solid(k, "PadR", lambda q: g_rect(q, 26, 80, r=13), DARK, RIGHT_X + 88, EY + 6),
            grad(k, "CupL", lambda q: g_rect(q, 56, 110, r=22), "FFFFD070", "FFC98A18", LEFT_X - 108, EY + 6, gy=55),
            grad(k, "CupR", lambda q: g_rect(q, 56, 110, r=22), "FFFFD070", "FFC98A18", RIGHT_X + 108, EY + 6, gy=55)),
              anchor=(CX, EY))
        def eq(k):
            for i, (dx, h) in enumerate(((-60, 30), (-30, 56), (0, 80), (30, 56), (60, 34))):
                m = mover(k, "Bar%d" % i, lambda q, dx=dx, h=h: neon(q, "B", lambda z: g_rect(z, 16, h, r=8), CYAN,
                                                                     CX + dx, 430, feather=10), anchor=(CX + dx, 430 + h / 2))
                track(m, 17, [(0, .3), (8 + i * 3, 1.2), (16 + i * 3, .4), (30, .3)])
        mover(dd, "EQ", eq, anchor=(CX, 430))
        def notes(k):
            for i, (nx, ny) in enumerate(((44, EY - 10), (456, EY + 20))):
                solid(k, "Head%d" % i, lambda q: g_ellipse(q, 24, 18, rot=-.4), GOLD, nx, ny + 22)
                solid(k, "Stem%d" % i, lambda q: g_rect(q, 5, 44, r=2), GOLD, nx + 10, ny)
                line(k, "Flag%d" % i, lambda q: g_arcdown(q, 10, 10, -4), GOLD, 5, x=nx + 18, y=ny - 20, rot=1.2)
        m = mover(dd, "Notes", notes, anchor=(CX, EY))
        track(m, 14, [(0, 0), (60, -120)], interp="linear")
        track(m, 18, [(0, 0), (10, 1), (50, 1), (60, 0)])


# ================================================================= MOOD PROPS
# Props for the jelly-eye mood stories (.obsidian-wiki/02_Components/Pet_Mood_Scripts.md).
# Same convention as the scene props: every moving part is a named mover whose
# rest pose is where it sits at the story's climax; the story keys offsets.
MOOD_NAMES = ["MoodBall", "MoodYoYo", "MoodStars", "MoodShock", "MoodSad", "MoodCurious",
              "MoodMissile", "MoodFume", "MoodGlitch", "MoodThink", "MoodHappy", "MoodLove",
              "MoodGlad", "MoodCool", "MoodShy", "MoodEmbarrassed", "MoodShowOff", "MoodListen",
              "MoodArrogant"]

MAGENTA = "FFFF5CD6"


def _q_mark(k, name, color, x, y, s=1.0):
    line(k, name, lambda q: g_poly_path(q, [(-14 * s, -12 * s), (-11 * s, -26 * s), (0, -33 * s), (13 * s, -26 * s),
                                           (14 * s, -12 * s), (0, 0), (0, 12 * s)]), color, 9 * s,
         x=x, y=y, feather=12)
    solid(k, name + "Dot", lambda q: g_ellipse(q, 12 * s, 12 * s), color, x, y + 30 * s)


def _bang(k, name, color, x, y, s=1.0):
    neon(k, name, lambda q: g_rect(q, 16 * s, 58 * s, r=8 * s), color, x, y - 14 * s, feather=14)
    neon(k, name + "Dot", lambda q: g_ellipse(q, 16 * s, 16 * s), color, x, y + 32 * s, feather=14)


def _bulb(k):
    glowdisc(k, "Glow", 70, "88FFC53D", "00FFC53D", CX, 60)
    neon(k, "Glass", lambda q: g_ellipse(q, 54, 58), GOLD, CX, 54, feather=18)
    solid(k, "Base", lambda q: g_rect(q, 26, 18, r=4), SILVER, CX, 90)
    for i in range(5):
        a = -2.6 + i * 0.52
        line(k, "Ray%d" % i, lambda q: g_rect(q, 14, 4, r=2), GOLD, 4,
             x=CX + math.cos(a) * 52, y=54 + math.sin(a) * 52, rot=a)


def _shades(k):
    for nm, ex in (("L", LEFT_X), ("R", RIGHT_X)):
        shape(k, "Lens" + nm, lambda q: (g_rect(q, 178, 154, rtl=20, rtr=20, rbl=66, rbr=66),
                                         p_grad(q, "FF2B2F3E", "FF06070C", 0, -77, 0, 77)), ex, EY + 4)
        line(k, "Rim" + nm, lambda q: g_rect(q, 178, 154, rtl=20, rtr=20, rbl=66, rbr=66), "FF10131C", 6, x=ex, y=EY + 4)
        solid(k, "Shine" + nm, lambda q: g_rect(q, 70, 14, r=7, rot=-.35), "44FFFFFF", ex - 34, EY - 40)
    solid(k, "Bridge", lambda q: g_rect(q, 44, 16, r=6), "FF10131C", CX, EY - 50)


def _pixel_hand(k, x, y, flip=1):
    col = "D98FF4FF"
    solid(k, "Palm", lambda q: g_rect(q, 120, 100, r=18), col, x, y + 40)
    for i, (dx, h) in enumerate(((-45, 70), (-15, 86), (15, 82), (45, 66))):
        solid(k, "F%d" % i, lambda q, h=h: g_rect(q, 24, h, r=12), col, x + dx * flip, y - h / 2 + 4)
    line(k, "Edge", lambda q: g_rect(q, 120, 100, r=18), CYAN_SOFT, 3, x=x, y=y + 40)


def emit_mood_prop(dd, p):
    name = P_NAMES[p]

    if name == "MoodBall":
        mover(dd, "Ball", lambda k: (glowdisc(k, "Glow", 84, "88FF5CD6", "00FF5CD6", CX, EY - 120),
                                     neon(k, "Core", lambda q: g_ellipse(q, 84, 84), MAGENTA, CX, EY - 120, feather=18),
                                     line(k, "Seam", lambda q: g_arcdown(q, 36, 16, -2), "CCFFFFFF", 5, x=CX, y=EY - 120),
                                     solid(k, "Gleam", lambda q: g_ellipse(q, 22, 16, rot=-.5), "AAFFFFFF", CX - 16, EY - 140)),
              anchor=(CX, EY - 120))

    elif name == "MoodYoYo":
        mover(dd, "String", lambda k: solid(k, "S", lambda q: g_rect(q, 4, 120, r=2), "CCE8EEF6", LEFT_X, EY + 110),
              anchor=(LEFT_X, EY + 50))
        mover(dd, "Yo", lambda k: (glowdisc(k, "Glow", 50, "663FE7F5", "003FE7F5", LEFT_X, EY + 170),
                                   neon(k, "Disc", lambda q: g_ellipse(q, 60, 60), PURPLE, LEFT_X, EY + 170, feather=14),
                                   line(k, "Ring", lambda q: g_ellipse(q, 40, 40), CYAN, 5, x=LEFT_X, y=EY + 170),
                                   solid(k, "Spoke", lambda q: (g_rect(q, 44, 6, r=3), g_rect(q, 6, 44, r=3)), GOLD,
                                         LEFT_X, EY + 170)),
              anchor=(LEFT_X, EY + 170))

    elif name == "MoodStars":
        def stars(k):
            for i, (sx, sy, s) in enumerate(((CX - 170, 70, 22), (CX - 80, 130, 14), (CX + 10, 60, 26),
                                             (CX + 100, 120, 16), (CX + 180, 80, 20))):
                neon(k, "S%d" % i, lambda q, s=s: g_star(q, s, points=4, inner=.3), GOLD if i % 2 else WHITE,
                     sx, sy, feather=10)
            for i, (px, py) in enumerate(((CX - 130, 40), (CX - 20, 150), (CX + 60, 30), (CX + 150, 160))):
                solid(k, "P%d" % i, lambda q: g_rect(q, 8, 8), CYAN_SOFT, px, py)
        mover(dd, "Stars", stars, anchor=(CX, 100))
        mover(dd, "HitStar", lambda k: neon(k, "S", lambda q: g_star(q, 34, points=5, inner=.45), GOLD, 490, 60, feather=14),
              anchor=(490, 60))
        mover(dd, "Puff", lambda k: [solid(k, "P%d" % i, lambda q: g_rect(q, 10, 10, r=2), "CC8FF4FF", CX + dx, EY + 110 + dy)
                                     for i, (dx, dy) in enumerate(((-30, 0), (-10, -14), (12, -4), (30, -16), (0, 10), (-22, -26)))],
              anchor=(CX, EY + 110))

    elif name == "MoodShock":
        mover(dd, "Flash", lambda k: wash(k, "F", "FFFFFFFF", hold=.6), anchor=(CX, CY))

        def spider(k):
            solid(k, "Thread", lambda q: g_rect(q, 3, 300), "AAE8EEF6", CX, -90)
            for i in range(4):
                for sgn in (-1, 1):
                    line(k, "Leg%d%s" % (i, "LR"[sgn > 0]), lambda q: g_arcdown(q, 22, 16, -2), SILVER, 5,
                         x=CX + sgn * 32, y=50 + i * 10, rot=sgn * (0.5 - i * 0.35))
            neon(k, "Body", lambda q: g_ellipse(q, 56, 50), "FF7A5CC8", CX, 62, feather=12)
            solid(k, "Head", lambda q: g_ellipse(q, 34, 30), "FF5A3FA0", CX, 36)
            solid(k, "Eyes", lambda q: (g_ellipse(q, 9, 9, x=-8), g_ellipse(q, 9, 9, x=8)), RED, CX, 34)
        mover(dd, "Spider", lambda k: fit(k, CX, 60, 1.5, spider), anchor=(CX, 60))
        mover(dd, "Bang", lambda k: _bang(k, "B", RED, 440, 110), anchor=(440, 110))

    elif name == "MoodSad":
        for nm, ex in (("L", LEFT_X), ("R", RIGHT_X)):
            mover(dd, "Pool" + nm, lambda k, ex=ex: (
                solid(k, "W", lambda q: g_rect(q, 112, 34, rtl=6, rtr=6, rbl=17, rbr=17), "996FC9FF", ex, EY + 23),
                line(k, "Wave", lambda q: g_wave(q, 48, 5), "DDB9E6FF", 4, x=ex, y=EY + 8)), anchor=(ex, EY + 40))
            mover(dd, "Drop" + nm, lambda k, ex=ex: [neon(k, "D%d" % i, lambda q, s=s: g_rect(q, s, s, r=2), TEAR,
                                                          ex - 22, EY + 50 + i * 16, feather=8)
                                                     for i, s in enumerate((12, 10, 8))], anchor=(ex - 22, EY + 60))

    elif name == "MoodCurious":
        mover(dd, "Q", lambda k: _q_mark(k, "Q", GOLD, 432, 70), anchor=(432, 80))

        def glass(k):
            solid(k, "Handle", lambda q: g_rect(q, 24, 96, r=10), BROWN, RIGHT_X + 92, EY + 92, rot=-0.785)
            solid(k, "Lens", lambda q: g_ellipse(q, 170, 170), "2248E0FF", RIGHT_X, EY)
            line(k, "Ring", lambda q: g_ellipse(q, 170, 170), SILVER, 13, x=RIGHT_X, y=EY, feather=10)
            solid(k, "Shine", lambda q: g_rect(q, 50, 12, r=6, rot=-.7), "55FFFFFF", RIGHT_X - 40, EY - 44)
        mover(dd, "Glass", glass, anchor=(RIGHT_X, EY))
        mover(dd, "Bulb", _bulb, anchor=(CX, 60))

    elif name == "MoodMissile":
        for nm, px in (("PortL", 36), ("PortR", 464)):
            mover(dd, nm, lambda k, px=px: (solid(k, "Box", lambda q: g_rect(q, 46, 96, r=8), GREY, px, EY),
                                            solid(k, "Stripe", lambda q: g_rect(q, 46, 10), RED, px, EY - 36),
                                            solid(k, "Hole", lambda q: (g_ellipse(q, 18, 18, y=-8), g_ellipse(q, 18, 18, y=24)),
                                                  DARK, px, EY)), anchor=(px, EY + 48))

        def missile(k, x, y):
            neon(k, "Flame", lambda q: g_drop(q, .5), ORANGE, x, y + 30, feather=14)
            solid(k, "Body", lambda q: g_rect(q, 18, 44, r=6), WHITE, x, y)
            solid(k, "Nose", lambda q: g_tri(q, 18, 18), RED, x, y - 30)
            solid(k, "Fins", lambda q: (g_tri(q, 12, 14, x=-11, y=18), g_tri(q, 12, 14, x=11, y=18)), RED, x, y)
        for i, (mx, my) in enumerate(((36, EY - 8), (36, EY + 24), (464, EY - 8), (464, EY + 24))):
            mover(dd, "M%d" % i, lambda k, mx=mx, my=my: missile(k, mx, my), anchor=(mx, my))
        mover(dd, "Flash", lambda k: wash(k, "F", "FFFFE2C0", hold=.6), anchor=(CX, CY))

    elif name == "MoodFume":
        mover(dd, "Heat", lambda k: wash(k, "H", "FFFF2A3A"), anchor=(CX, CY))
        for nm, px, sgn in (("JetL", 34, -1), ("JetR", 466, 1)):
            mover(dd, nm, lambda k, px=px, sgn=sgn: [neon(k, "P%d" % i, lambda q, r=r: g_ellipse(q, r, r), "BBFF7A6A",
                                                          px + sgn * i * 14, EY - i * 22, feather=16)
                                                     for i, r in enumerate((44, 36, 28, 20))], anchor=(px, EY))

    elif name == "MoodGlitch":
        for nm, col, dx in (("GhostR", "99FF2040", 18), ("GhostC", "7700F0FF", -18)):
            mover(dd, nm, lambda k, col=col, dx=dx: (
                solid(k, "L", lambda q: g_rect(q, 116, 72, rot=.34, rtl=6, rtr=6, rbl=38, rbr=38), col, LEFT_X + dx, EY),
                solid(k, "R", lambda q: g_rect(q, 116, 72, rot=-.34, rtl=6, rtr=6, rbl=38, rbr=38), col, RIGHT_X + dx, EY)),
                  anchor=(CX, EY))
        mover(dd, "Scan", lambda k: [solid(k, "B%d" % i, lambda q, h=h: g_rect(q, 500, h), "55FF4D5E", CX, y)
                                     for i, (y, h) in enumerate(((120, 6), (200, 14), (250, 4), (300, 10), (380, 6)))],
              anchor=(CX, CY))
        mover(dd, "Pixels", lambda k: [solid(k, "P%d" % i, lambda q: g_rect(q, 16, 16, r=2), RED,
                                             (LEFT_X if i % 2 else RIGHT_X) + math.cos(i * 1.3) * 60,
                                             EY + math.sin(i * 1.3) * 44) for i in range(16)], anchor=(CX, EY))
        mover(dd, "Bang", lambda k: (_bang(k, "B", RED, CX, 70, .9),
                                     line(k, "V1", lambda q: g_anger(q), RED, 7, x=CX - 90, y=70, feather=10),
                                     line(k, "V2", lambda q: g_anger(q, 16, 9, 7), RED, 6, x=CX + 92, y=56, feather=10)),
              anchor=(CX, 70))

    elif name == "MoodThink":
        def bubble(k):
            for i, (bx, by, r) in enumerate(((330, 170, 12), (352, 146, 18))):
                line(k, "Dot%d" % i, lambda q, r=r: g_ellipse(q, r, r), CYAN_SOFT, 4, x=bx, y=by)
            shape(k, "Cloud", lambda q: (g_ellipse(q, 90, 70, x=-40), g_ellipse(q, 90, 80, x=10, y=-14), g_ellipse(q, 80, 66, x=50),
                                         p_fill(q, "22FFFFFF")), 400, 88)
            line(k, "Edge", lambda q: g_rect(q, 190, 90, r=45), CYAN_SOFT, 4, x=402, y=86)
            m = mover(k, "Gear", lambda q: (line(q, "G", lambda z: g_gear(z, 22, 8), GOLD, 5, x=380, y=86),
                                            solid(q, "Hub", lambda z: g_ellipse(z, 10, 10), GOLD, 380, 86)), anchor=(380, 86))
            mover(k, "Gear2", lambda q: line(q, "G", lambda z: g_gear(z, 14, 6), CYAN, 4, x=424, y=76), anchor=(424, 76))
            solid(k, "Plus", lambda q: (g_rect(q, 18, 5, r=2), g_rect(q, 5, 18, r=2)), WHITE, 440, 104)
        mover(dd, "Bubble", bubble, anchor=(380, 110))
        mover(dd, "Bulb", _bulb, anchor=(CX, 60))

    elif name == "MoodHappy":
        mover(dd, "Twinkle", lambda k: [neon(k, "S%d" % i, lambda q, s=s: g_star(q, s), c, sx, sy, feather=12)
                                        for i, (sx, sy, s, c) in enumerate(((60, 110, 34, WHITE), (440, 100, 40, GOLD),
                                                                            (70, 380, 26, GOLD), (430, 390, 30, WHITE)))],
              anchor=(CX, 240))

        def burst(k):
            for i in range(8):
                a = i * math.pi / 4 + .3
                x, y = CX + math.cos(a) * 170, EY + math.sin(a) * 150
                if i % 2:
                    neon(k, "H%d" % i, lambda q: g_heart(q, .26), HEART, x, y, feather=10)
                else:
                    solid(k, "N%d" % i, lambda q: g_ellipse(q, 20, 15, rot=-.4), GOLD, x, y + 14)
                    solid(k, "St%d" % i, lambda q: g_rect(q, 4, 34, r=2), GOLD, x + 8, y - 4)
        mover(dd, "Burst", burst, anchor=(CX, EY))

    elif name == "MoodLove":
        mover(dd, "Blush", lambda k: [(solid(k, "B%s" % nm, lambda q: g_ellipse(q, 70, 30), "88FF5C8A", ex, EY + 100),
                                       line(k, "L%s" % nm, lambda q: (g_rect(q, 4, 22, rot=.5, x=-16), g_rect(q, 4, 22, rot=.5),
                                                                      g_rect(q, 4, 22, rot=.5, x=16)), HEART, 3, x=ex, y=EY + 100))
                                      for nm, ex in (("L", LEFT_X), ("R", RIGHT_X))], anchor=(CX, EY + 100))
        mover(dd, "Bubbles", lambda k: [(line(k, "C%d" % i, lambda q, r=r: g_ellipse(q, r, r), "CCFFB3CC", 3, x=bx, y=by),
                                         solid(k, "H%d" % i, lambda q, r=r: g_heart(q, r / 170.0), HEART, bx, by))
                                        for i, (bx, by, r) in enumerate(((70, 420, 50), (150, 480, 38), (250, 440, 56),
                                                                          (350, 500, 40), (430, 430, 46), (200, 540, 32)))],
              anchor=(CX, 460))

    elif name == "MoodGlad":
        mover(dd, "Gleam", lambda k: [neon(k, "G%s" % nm, lambda q: g_star(q, 30, points=4, inner=.25), WHITE, ex - 34, EY - 44, feather=10)
                                      for nm, ex in (("L", LEFT_X), ("R", RIGHT_X))], anchor=(CX, EY - 44))
        for nm, px, rot in (("PopL", 50, .7), ("PopR", 450, -.7)):
            mover(dd, nm, lambda k, px=px, rot=rot: (
                solid(k, "Tube", lambda q: g_rect(q, 44, 96, rtl=4, rtr=4, rbl=14, rbr=14, rot=rot), "FFFF7AA2", px, 440),
                solid(k, "Band", lambda q: g_rect(q, 46, 10, rot=rot), GOLD, px + (18 if rot > 0 else -18), 418)), anchor=(px, 440))
        mover(dd, "Burst", lambda k: [solid(k, "C%d" % i, lambda q, i=i: g_rect(q, 12, 20, r=3, rot=i * .7),
                                            (CYAN, PINK, GOLD, GREEN, PURPLE)[i % 5],
                                            CX + math.cos(i * .63) * (110 + i * 9), 230 + math.sin(i * .63) * (90 + i * 6))
                                      for i in range(14)], anchor=(CX, 230))

    elif name == "MoodCool":
        mover(dd, "Shades", _shades, anchor=(CX, EY))
        mover(dd, "Glint", lambda k: neon(k, "S", lambda q: g_star(q, 34, points=4, inner=.2), WHITE, RIGHT_X + 60, EY - 44, feather=12),
              anchor=(RIGHT_X + 60, EY - 44))

        def thumb(k):
            col = "FFFFD27A"
            solid(k, "Thumb", lambda q: g_rect(q, 30, 70, r=15), col, 404, 350)
            solid(k, "Fist", lambda q: g_rect(q, 96, 80, r=22), col, 430, 404)
            line(k, "Knuckles", lambda q: (g_rect(q, 40, 3, y=-10), g_rect(q, 40, 3, y=8)), "FFD9A441", 3, x=446, y=404)
            solid(k, "Cuff", lambda q: g_rect(q, 70, 20, r=6), CYAN, 440, 452)
        mover(dd, "Thumb", thumb, anchor=(430, 420))

    elif name == "MoodShy":
        mover(dd, "Blush", lambda k: [line(k, "B%s" % nm, lambda q: (g_rect(q, 5, 26, rot=.55, x=-18), g_rect(q, 5, 26, rot=.55),
                                                                      g_rect(q, 5, 26, rot=.55, x=18)), HEART, 4,
                                           x=ex, y=EY + 96, feather=8)
                                      for nm, ex in (("L", LEFT_X - 10), ("R", RIGHT_X + 10))], anchor=(CX, EY + 96))
        mover(dd, "HandL", lambda k: _pixel_hand(k, LEFT_X, EY + 10, -1), anchor=(LEFT_X, EY + 40))
        mover(dd, "HandR", lambda k: _pixel_hand(k, RIGHT_X, EY + 10, 1), anchor=(RIGHT_X, EY + 40))

    elif name == "MoodEmbarrassed":
        mover(dd, "Dim", lambda k: wash(k, "D", "FF03050C", hold=.55), anchor=(CX, CY))
        mover(dd, "Sweat", lambda k: [neon(k, "S%d" % i, lambda q: g_drop(q, .5), TEAR, sx, sy, feather=8)
                                      for i, (sx, sy) in enumerate(((60, 170), (440, 160), (96, 110), (404, 100)))],
              anchor=(CX, 140))

        def wall(k):
            solid(k, "Face", lambda q: g_rect(q, 470, 180, r=10), "FF2A3A66", CX, 350)
            for r_ in range(3):
                solid(k, "Row%d" % r_, lambda q: g_rect(q, 470, 4), "FF5B79B8", CX, 290 + r_ * 60)
                for c in range(5):
                    solid(k, "J%d%d" % (r_, c), lambda q: g_rect(q, 4, 56), "FF5B79B8",
                          CX - 200 + c * 100 + (50 if r_ % 2 else 0), 320 + r_ * 60)
            line(k, "Edge", lambda q: g_rect(q, 470, 180, r=10), CYAN_SOFT, 4, x=CX, y=350)
        mover(dd, "Wall", wall, anchor=(CX, 350))
        mover(dd, "Puddle", lambda k: (neon(k, "P", lambda q: g_ellipse(q, 260, 40), "CC6FC9FF", CX, 460, feather=16),
                                       [solid(k, "D%d" % i, lambda q: g_rect(q, 10, 10, r=2), TEAR, CX + dx, 432 + dy)
                                        for i, (dx, dy) in enumerate(((-90, 0), (-40, -14), (30, -8), (84, -2)))]),
              anchor=(CX, 460))

    elif name == "MoodShowOff":
        mover(dd, "Shades", _shades, anchor=(CX, EY))
        mover(dd, "Stars", lambda k: [neon(k, "S%d" % i, lambda q, s=s: g_star(q, s, points=5, inner=.45), GOLD, sx, sy, feather=12)
                                      for i, (sx, sy, s) in enumerate(((50, 120, 30), (450, 130, 34), (90, 360, 24),
                                                                        (420, 370, 28), (250, 60, 22)))], anchor=(CX, 240))

    elif name == "MoodListen":
        def phones(k):
            line(k, "Band", lambda q: g_arcup(q, 224, 110, 10), CYAN, 14, x=CX, y=EY - 80, feather=16)
            for nm, ex in (("L", LEFT_X - 112), ("R", RIGHT_X + 112)):
                neon(k, "Cup" + nm, lambda q: g_rect(q, 58, 120, r=26), MAGENTA, ex, EY + 6, feather=18)
                solid(k, "Pad" + nm, lambda q: g_rect(q, 30, 84, r=14), DARK, ex + (22 if nm == "L" else -22), EY + 6)
        mover(dd, "Phones", phones, anchor=(CX, EY))
        for nm, ex in (("WaveL", LEFT_X), ("WaveR", RIGHT_X)):
            pts = [(-60 + i * 10, (0, -18, 26, -34, 40, -40, 34, -26, 18, -10, 4, 0, 0)[i]) for i in range(13)]
            mover(dd, nm, lambda k, ex=ex, pts=pts: line(k, "W", lambda q: g_poly_path(q, pts), CYAN, 8, x=ex, y=EY, feather=14),
                  anchor=(ex, EY))

    elif name == "MoodArrogant":
        def crown(k):
            w(k, '<Shape x="%g" y="%g" name="Crown">' % (CX, 80))
            w(k + 1, '<PointsPath isClosed="true" name="P">')
            for px, py in ((-50, 26), (-50, -20), (-25, 4), (0, -34), (25, 4), (50, -20), (50, 26)):
                w(k + 2, '<StraightVertex x="%g" y="%g" radius="3"/>' % (px, py))
            w(k + 1, '</PointsPath>')
            p_glowfill(k + 1, GOLD, 10, 3)
            w(k, '</Shape>')
            solid(k, "Gems", lambda q: (g_ellipse(q, 12, 12, x=-26), g_ellipse(q, 14, 14), g_ellipse(q, 12, 12, x=26)), RED, CX, 94)
        mover(dd, "Crown", lambda k: fit(k, CX, 90, 1.4, crown), anchor=(CX, 90))

        def fan(k):
            for i in range(7):
                a = -0.9 + i * 0.3
                solid(k, "Blade%d" % i, lambda q, a=a: g_rect(q, 16, 90, r=8, rot=a, y=-40), "FFFF9EC7" if i % 2 else PINK,
                      440, 230)
            solid(k, "Handle", lambda q: g_rect(q, 12, 50, r=6), GOLD, 440, 254)
        mover(dd, "Fan", fan, anchor=(440, 230))
        mover(dd, "Puff", lambda k: [solid(k, "P%d" % i, lambda q, r=r: g_ellipse(q, r, r), "99D0D8E6", 430 + dx, EY + 70 + dy)
                                     for i, (dx, dy, r) in enumerate(((0, 0, 30), (22, -12, 24), (40, 4, 18)))],
              anchor=(440, EY + 70))


P_NAMES += SCENE_NAMES + MOOD_NAMES
P_SC_FIRST = P_NAMES.index("SceneFood")


def emit_prop(d, p):
    pid = nid()
    if p == P_NONE:
        w(d, '<Node name="Prop_None" id="%s"/>' % pid)
        return pid
    w(d, '<Node name="Prop_%s" id="%s">' % (P_NAMES[p], pid))
    dd = d + 1
    mark = len(out)
    reg = {}
    _cur_reg[0] = reg
    PROP_NODES[p] = reg
    _cur_intro[0] = INTROS["prop"].setdefault(p, [])

    def zed(k, h):
        g_rect(k, h, h * .20, y=-h * .42, r=h * .10)
        g_rect(k, h * 1.02, h * .20, r=h * .10, rot=.70)
        g_rect(k, h, h * .20, y=h * .42, r=h * .10)

    if P_NAMES[p] in MOOD_NAMES:
        emit_mood_prop(dd, p)

    elif p >= P_SC_FIRST:
        emit_scene_prop(dd, p)

    elif p in (P_ZZZ, P_SNORE):
        for i, (zx, zy, zs) in enumerate(((440, 70, 50), (398, 108, 36), (366, 138, 26))):
            m = mover(dd, "Z%d" % i, lambda k, zs=zs, zx=zx, zy=zy:
                      neon(k, "Z", lambda q, zs=zs: zed(q, zs), CYAN, zx, zy, feather=10, thick=3),
                      anchor=(zx, zy))
            track(m, 14, [(0, 0), (60 + i * 20, -14), (120 + i * 40, 0)])
            track(m, 18, [(0, .35), (40, 1), (120 + i * 40, .35)])
        if p == P_SNORE:
            # a proper snot bubble: soft filled glass that swells and shrinks
            bx, by = CX + 58, EY + 108
            mb = mover(dd, "Bubble", lambda k: (
                solid(k, "Glass", lambda q: g_ellipse(q, 62, 58), "3389E0FF", bx, by),
                line(k, "Rim", lambda q: g_ellipse(q, 62, 58), "CC9FE0FF", 4, x=bx, y=by, feather=10),
                solid(k, "Gleam", lambda q: g_ellipse(q, 14, 9, rot=-.6), "AAFFFFFF", bx - 14, by - 14)),
                anchor=(bx - 20, by + 20))
            track(mb, 16, [(0, .3), (90, 1.0), (180, .3)])
            track(mb, 17, [(0, .3), (90, 1.0), (180, .3)])

    elif p == P_QUESTION:
        def hook(k):
            w(k, '<PointsPath name="P">')
            w(k + 1, '<StraightVertex x="-22" y="-14"/>')
            w(k + 1, '<CubicMirroredVertex x="0" y="-30" rotation="0" distance="16"/>')
            w(k + 1, '<CubicMirroredVertex x="20" y="-6" rotation="1.5708" distance="16"/>')
            w(k + 1, '<CubicMirroredVertex x="0" y="14" rotation="3.1416" distance="12"/>')
            w(k + 1, '<StraightVertex x="0" y="28"/>')
            w(k, '</PointsPath>')
        m = mover(dd, "Q", lambda k: (line(k, "Hook", hook, CYAN, 12, CRX, CRY - 10, feather=14),
                                      neon(k, "Dot", lambda q: g_ellipse(q, 14, 14), CYAN, CRX, CRY + 40)),
                  anchor=(CRX, CRY + 12))
        track(m, 15, [(0, -.12), (45, .12), (90, -.12)])

    elif p == P_BURGER:
        # small, under and between the eyes -- never over them
        bx, by, s = CX, 382, .9
        solid(dd, "BunBot", lambda k: g_rect(k, 92 * s, 30 * s, rtl=4, rtr=4, rbl=14, rbr=14), CREAM, bx, by + 42 * s)
        solid(dd, "Salad", lambda k: g_rect(k, 104 * s, 12 * s, r=6), GREEN, bx, by + 19 * s)
        solid(dd, "Patty", lambda k: g_rect(k, 100 * s, 20 * s, r=8), BROWN, bx, by + 2 * s)
        solid(dd, "BunTop", lambda k: g_rect(k, 92 * s, 40 * s, rtl=20, rtr=20, rbl=4, rbr=4), CREAM, bx, by - 28 * s)
        solid(dd, "Seed1", lambda k: g_ellipse(k, 7, 5), "FFE8C880", bx - 15, by - 34 * s)
        solid(dd, "Seed2", lambda k: g_ellipse(k, 7, 5), "FFE8C880", bx + 11, by - 38 * s)

    elif p == P_BEER:
        bx, by, s = CX, 388, .9
        line(dd, "Handle", lambda k: g_ellipse(k, 36 * s, 46 * s), CREAM, 7, x=bx + 42 * s, y=by + 12 * s)
        grad(dd, "Glass", lambda k: g_rect(k, 66 * s, 84 * s, rtl=6, rtr=6, rbl=12, rbr=12),
             "FFFFD86B", "FFD98A16", bx, by + 12 * s, gy=30)
        solid(dd, "Foam3", lambda k: g_rect(k, 68 * s, 20 * s, r=8), WHITE, bx, by - 22 * s)
        solid(dd, "Foam1", lambda k: g_ellipse(k, 34 * s, 26 * s), WHITE, bx - 16 * s, by - 28 * s)
        solid(dd, "Foam2", lambda k: g_ellipse(k, 30 * s, 24 * s), WHITE, bx + 14 * s, by - 30 * s)

    elif p == P_SPARKLE:
        for i, (sx, sy, ss) in enumerate(((CRX + 8, CRY + 6, 48), (CRX - 30, CRY + 40, 24))):
            m = mover(dd, "S%d" % i, lambda k, sx=sx, sy=sy, ss=ss:
                      neon(k, "S", lambda q, ss=ss: g_star(q, ss), GOLD, sx, sy), anchor=(sx, sy))
            track(m, 16, [(0, .3), (24 + i * 10, 1.1), (60, .3)])
            track(m, 17, [(0, .3), (24 + i * 10, 1.1), (60, .3)])

    elif p == P_HEADPHONES:
        line(dd, "Band", lambda k: g_arcup(k, 204, 96, 10), AMBER, 18, x=CX, y=EY - 70)
        solid(dd, "PadL", lambda k: g_rect(k, 26, 80, r=13), DARK, LEFT_X - 88, EY + 6)
        solid(dd, "PadR", lambda k: g_rect(k, 26, 80, r=13), DARK, RIGHT_X + 88, EY + 6)
        grad(dd, "CupL", lambda k: g_rect(k, 56, 110, r=22), "FFFFD070", "FFC98A18", LEFT_X - 108, EY + 6, gy=55)
        grad(dd, "CupR", lambda k: g_rect(k, 56, 110, r=22), "FFFFD070", "FFC98A18", RIGHT_X + 108, EY + 6, gy=55)

    elif p == P_VR:
        solid(dd, "StrapL", lambda k: g_rect(k, 40, 30, r=8), GREY, CX - 212, EY)
        solid(dd, "StrapR", lambda k: g_rect(k, 40, 30, r=8), GREY, CX + 212, EY)
        w(dd, '<Shape x="%g" y="%g" name="Visor">' % (CX, EY))
        g_rect(dd + 1, 380, 168, rtl=70, rtr=70, rbl=70, rbr=70)
        w(dd + 1, '<Fill name="Fill">')
        w(dd + 2, '<LinearGradient startX="-190" startY="-84" endX="190" endY="84" name="G">')
        w(dd + 3, '<GradientStop colorValue="FF3B2B7A" position="0"/>')
        w(dd + 3, '<GradientStop colorValue="FF1B6FB8" position="0.55"/>')
        w(dd + 3, '<GradientStop colorValue="FF120E2E" position="1"/>')
        w(dd + 2, '</LinearGradient>')
        w(dd + 1, '</Fill>')
        w(dd + 1, '<Stroke thickness="9" name="Rim">')
        w(dd + 2, '<SolidColor colorValue="FFE6ECF5" name="C"/>')
        w(dd + 1, '</Stroke>')
        w(dd, '</Shape>')
        solid(dd, "Gleam", lambda k: g_rect(k, 150, 22, r=11, rot=-0.3), "55FFFFFF", CX - 70, EY - 30)
        # popcorn bucket, bottom right
        px, py = 432, 420
        solid(dd, "Bucket", lambda k: g_rect(k, 58, 64, rtl=2, rtr=2, rbl=16, rbr=16), WHITE, px, py)
        for j in (-18, 0, 18):
            solid(dd, "Stripe%d" % j, lambda k: g_rect(k, 10, 64, r=2), RED, px + j, py)
        for j, (ox, oy, rr) in enumerate(((-18, -38, 22), (2, -46, 24), (20, -36, 20), (-6, -30, 18))):
            solid(dd, "Pop%d" % j, lambda k, rr=rr: g_ellipse(k, rr, rr), CREAM, px + ox, py + oy)

    elif p == P_SNORKEL:
        solid(dd, "Tube", lambda k: g_rect(k, 22, 176, rtl=11, rtr=11, rbl=4, rbr=4), AMBER, CX + 214, EY - 40)
        solid(dd, "TubeTop", lambda k: g_rect(k, 56, 22, rtl=11, rtr=11, rbl=11, rbr=0), AMBER, CX + 194, EY - 118)
        solid(dd, "Glass", lambda k: g_rect(k, 370, 180, rtl=60, rtr=60, rbl=76, rbr=76), "3339C6E8", CX, EY)
        w(dd, '<Shape x="%g" y="%g" name="Mask">' % (CX, EY))
        g_rect(dd + 1, 370, 180, rtl=60, rtr=60, rbl=76, rbr=76)
        w(dd + 1, '<Stroke thickness="13" name="Rim">')
        w(dd + 2, '<SolidColor colorValue="%s" name="C"/>' % CREAM)
        w(dd + 1, '</Stroke>')
        w(dd, '</Shape>')
        solid(dd, "Gleam", lambda k: g_rect(k, 110, 16, r=8, rot=-.3), "44FFFFFF", CX - 90, EY - 50)
        for i, (bx, by, bs) in enumerate(((70, 120, 24), (48, 92, 16), (66, 64, 11))):
            m = mover(dd, "Bub%d" % i, lambda k, bx=bx, by=by, bs=bs:
                      line(k, "B", lambda q, bs=bs: g_ellipse(q, bs, bs), CYAN, 4, x=bx, y=by))
            track(m, 14, [(0, 30), (90 + i * 15, -40)], interp="linear")
            track(m, 18, [(0, 0), (20, 1), (70, 1), (90 + i * 15, 0)])

    elif p == P_DEVIL:
        # horns sit on the head: their base clears the eye top (163) by ~15 px
        neon(dd, "HornL", lambda k: g_tri(k, 56, 76, rot=-.3), RED, LEFT_X - 24, EY - 128, feather=10)
        neon(dd, "HornR", lambda k: g_tri(k, 56, 76, rot=.3), RED, RIGHT_X + 24, EY - 128, feather=10)
        solid(dd, "Imp", lambda k: g_ellipse(k, 64, 64), PURPLE, 446, 64)
        solid(dd, "ImpHornL", lambda k: g_tri(k, 20, 24, rot=-.3), PURPLE, 424, 28)
        solid(dd, "ImpHornR", lambda k: g_tri(k, 20, 24, rot=.3), PURPLE, 468, 28)
        solid(dd, "ImpEyes", lambda k: (g_ellipse(k, 10, 10, x=-12), g_ellipse(k, 10, 10, x=12)), WHITE, 446, 60)

    elif p == P_SPARKLES:
        for i, (sx, sy, ss) in enumerate(((60, 118, 60), (440, 124, 68), (436, 352, 44), (62, 356, 40))):
            m = mover(dd, "S%d" % i, lambda k, sx=sx, sy=sy, ss=ss:
                      neon(k, "S", lambda q, ss=ss: g_star(q, ss), GOLD, sx, sy), anchor=(sx, sy))
            ph = i * 15
            track(m, 16, [(0, .35), (ph + 20, 1.15), (80, .35)])
            track(m, 17, [(0, .35), (ph + 20, 1.15), (80, .35)])
            track(m, 15, [(0, 0), (80, 1.5708)], interp="linear")

    elif p == P_BLUSH:
        for sgn, bx in ((-1, LEFT_X - 34), (1, RIGHT_X + 34)):
            def cheek(k):
                for j in (-1, 0, 1):
                    g_rect(k, 9, 32, x=j * 15, r=5, rot=.42)
            neon(dd, "Blush%d" % (sgn > 0), cheek, PINK, bx, EY + 90, feather=14)

    elif p == P_EXCLAIM:
        m = mover(dd, "Bang", lambda k: (
            neon(k, "B", lambda q: g_rect(q, 18, 56, rtl=9, rtr=9, rbl=6, rbr=6), RED, CRX, CRY - 8),
            neon(k, "D", lambda q: g_ellipse(q, 18, 18), RED, CRX, CRY + 36)), anchor=(CRX, CRY + 10))
        track(m, 16, [(0, 1), (8, 1.35), (24, 1)])
        track(m, 17, [(0, 1), (8, 1.35), (24, 1)])

    elif p == P_TRASH:
        tx, ty = 430, 418
        solid(dd, "Bin", lambda k: g_rect(k, 76, 76, rtl=4, rtr=4, rbl=14, rbr=14), CYAN_DIM, tx, ty)
        for j in (-18, 0, 18):
            solid(dd, "Rib%d" % j, lambda k: g_rect(k, 6, 50, r=3), DARK, tx + j, ty + 2)
        solid(dd, "Lid", lambda k: g_rect(k, 92, 14, r=7), CYAN_DIM, tx, ty - 48)
        solid(dd, "Knob", lambda k: g_rect(k, 30, 10, r=5), CYAN_DIM, tx, ty - 60)

    elif p == P_CAMERA:
        solid(dd, "Body", lambda k: g_rect(k, 84, 60, r=12), AMBER, CRX, CRY + 12)
        solid(dd, "Hump", lambda k: g_rect(k, 34, 16, rtl=6, rtr=6), AMBER, CRX - 12, CRY - 22)
        solid(dd, "LensO", lambda k: g_ellipse(k, 40, 40), DARK, CRX, CRY + 14)
        neon(dd, "LensI", lambda k: g_ellipse(k, 18, 18), CYAN, CRX, CRY + 14)
        m = mover(dd, "Flash", lambda k: neon(k, "F", lambda q: g_ellipse(q, 10, 10), WHITE,
                                               CRX + 28, CRY - 6, feather=14))
        track(m, 18, [(0, .2), (6, 1), (20, .2), (90, .2)])

    elif p == P_ANGRYMARK:
        m = mover(dd, "Vein", lambda k: line(k, "V", g_anger, RED, 8, x=CRX, y=CRY + 6, feather=12),
                  anchor=(CRX, CRY + 6))
        track(m, 16, [(0, .85), (10, 1.15), (30, .85)])
        track(m, 17, [(0, .85), (10, 1.15), (30, .85)])

    elif p == P_HEARTS:
        for i, (hx, hy, hs, hr) in enumerate(((64, 150, .52, 0), (436, 142, .64, .28),
                                              (400, 72, .42, -.3), (104, 70, .38, .2))):
            m = mover(dd, "H%d" % i, lambda k, hx=hx, hy=hy, hs=hs, hr=hr:
                      neon(k, "H", lambda q, hs=hs, hr=hr: g_heart(q, hs, rot=hr), HEART,
                           hx, hy, feather=14))
            ph = i * 22
            track(m, 14, [(0, 20), (100 + ph, -60)], interp="linear")
            track(m, 18, [(0, 0), (25, 1), (75, 1), (100 + ph, 0)])
            track(m, 13, [(0, 0), (50, 10 if i % 2 else -10), (100 + ph, 0)])

    elif p == P_TEARS:
        # symmetric: each tear leaves the INNER lower corner of its eye
        for ex, sgn in ((LEFT_X, 1), (RIGHT_X, -1)):
            for i, (ty, ts) in enumerate(((58, 1.5), (116, 1.1), (160, .8))):
                m = mover(dd, "T", lambda k, ex=ex, sgn=sgn, ty=ty, ts=ts:
                          neon(k, "T", lambda q, ts=ts: g_drop(q, ts), TEAR,
                               ex + sgn * 34, EY + ty, feather=12))
                track(m, 14, [(0, -30), (48, 60)], interp="linear")
                track(m, 18, [(0, 0), (10, 1), (34, 1), (48, 0)])

    elif p == P_THINKDOTS:
        for i, (dx, r) in enumerate(((0, 9), (32, 13), (72, 19))):
            m = mover(dd, "Dot%d" % i, lambda k, dx=dx, r=r:
                      neon(k, "D", lambda q, r=r: g_ellipse(q, r * 2, r * 2), CYAN, 372 + dx, 84))
            track(m, 18, [(0, .2), (14 + i * 16, 1), (34 + i * 16, .2), (80, .2)])
            track(m, 14, [(0, 0), (14 + i * 16, -8), (80, 0)])

    elif p == P_SOUNDWAVE:
        for i, (dx, h) in enumerate(((-72, 26), (-40, 52), (-12, 80), (16, 52), (48, 34), (78, 18))):
            m = mover(dd, "Bar%d" % i, lambda k, dx=dx, h=h:
                      neon(k, "B", lambda q, h=h: g_rect(q, 14, h, r=7), CYAN, CX + dx, 420, feather=10),
                      anchor=(CX + dx, 420 + h / 2))
            ph = (i * 7) % 30
            track(m, 17, [(0, .35), (ph + 14, 1.25), (30 + ph, .35), (60, .35)])

    elif p == P_QUESTIONS:
        def hook2(k):
            w(k, '<PointsPath name="P">')
            w(k + 1, '<StraightVertex x="-20" y="-12"/>')
            w(k + 1, '<CubicMirroredVertex x="0" y="-28" rotation="0" distance="15"/>')
            w(k + 1, '<CubicMirroredVertex x="18" y="-5" rotation="1.5708" distance="15"/>')
            w(k + 1, '<CubicMirroredVertex x="0" y="13" rotation="3.1416" distance="11"/>')
            w(k + 1, '<StraightVertex x="0" y="26"/>')
            w(k, '</PointsPath>')
        m1 = mover(dd, "QBig", lambda k: (line(k, "H", hook2, CYAN, 12, CRX, CRY - 8, feather=14),
                                          neon(k, "D", lambda q: g_ellipse(q, 13, 13), CYAN, CRX, CRY + 38)),
                   anchor=(CRX, CRY + 15))
        track(m1, 15, [(0, -.14), (40, .14), (80, -.14)])
        m2 = mover(dd, "QSmall", lambda k: (line(k, "H", hook2, CYAN, 8, CLX, CLY, feather=10),
                                            neon(k, "D", lambda q: g_ellipse(q, 9, 9), CYAN, CLX, CLY + 32)),
                   anchor=(CLX, CLY + 16))
        track(m2, 15, [(0, .16), (40, -.16), (80, .16)])

    elif p == P_STEAM:
        for sgn, bx in ((-1, LEFT_X - 30), (1, RIGHT_X + 30)):
            neon(dd, "Cheek%d" % (sgn > 0), lambda k: g_ellipse(k, 50, 28), PINK, bx, EY + 92, feather=16)
        def puff(k):
            g_ellipse(k, 44, 34, x=-22, y=8)
            g_ellipse(k, 40, 40, x=6, y=-6)
            g_ellipse(k, 30, 26, x=32, y=10)
        for sgn, px in ((-1, 74), (1, 422)):
            m = mover(dd, "Puff%d" % (sgn > 0), lambda k, px=px:
                      neon(k, "P", puff, "FF9FB4C8", px, 96, feather=14))
            track(m, 14, [(0, 14), (60, -26), (61, 14), (120, 14)], interp="linear")
            track(m, 18, [(0, 0), (16, .85), (55, 0), (120, 0)])

    elif p == P_DIZZYSTARS:
        for i in range(5):
            a0 = i * (2 * math.pi / 5)
            m = mover(dd, "St%d" % i, lambda k, i=i:
                      neon(k, "S", lambda q, i=i: g_star(q, 48 - i * 4, points=5, inner=.42),
                           GOLD, CX, 88, feather=12))
            kf13, kf14 = [], []
            for f in range(0, 121, 20):
                a = a0 + (f / 120.0) * 2 * math.pi
                kf13.append((f, math.cos(a) * 118))
                kf14.append((f, math.sin(a) * 18))
            track(m, 13, kf13, interp="linear")
            track(m, 14, kf14, interp="linear")

    elif p == P_SWEAT:
        m = mover(dd, "Drop", lambda k: neon(k, "D", lambda q: g_drop(q, 1.6), TEAR,
                                             CRX, CRY + 30, feather=14))
        track(m, 14, [(0, -10), (70, 26), (140, -10)])

    elif p == P_SHIVER:
        for sgn, bx in ((-1, 34), (1, 466)):
            def lines(k):
                g_rect(k, 7, 30, x=-12, r=4)
                g_rect(k, 7, 46, x=0, r=4)
                g_rect(k, 7, 30, x=12, r=4)
            m = mover(dd, "Sh%d" % (sgn > 0), lambda k, bx=bx:
                      neon(k, "S", lines, CYAN_DIM, bx, EY, feather=10))
            track(m, 13, [(0, -4 * sgn), (8, 4 * sgn), (16, -4 * sgn)], interp="linear")
        neon(dd, "ScareDrop", lambda k: g_drop(k, 1.1), TEAR, CRX - 10, CRY + 20, feather=12)

    elif p == P_MEDAL:
        m = mover(dd, "Medal", lambda k: (
            solid(k, "RibL", lambda q: g_rect(q, 20, 46, r=3, rot=.32), RED, CRX - 13, CRY - 34),
            solid(k, "RibR", lambda q: g_rect(q, 20, 46, r=3, rot=-.32), RED, CRX + 13, CRY - 34),
            grad(k, "Disc", lambda q: g_ellipse(q, 56, 56), "FFFFE07A", "FFC98A18", CRX, CRY + 12, gy=28),
            solid(k, "Inner", lambda q: g_ellipse(q, 38, 38), "FFE0A017", CRX, CRY + 12),
            neon(k, "Star", lambda q: g_star(q, 26, points=5, inner=.42), WHITE, CRX, CRY + 12, feather=8)),
                  anchor=(CRX, CRY - 6))
        track(m, 15, [(0, -.08), (60, .08), (120, -.08)])

    elif p == P_BATTERYLOW:
        m = mover(dd, "Bat", lambda k: (
            line(k, "Shell", lambda q: g_rect(q, 72, 38, r=9), RED, 6, x=CRX - 4, y=CRY, feather=12),
            solid(k, "Nub", lambda q: g_rect(q, 8, 16, r=4), RED, CRX + 38, CRY),
            solid(k, "Charge", lambda q: g_rect(q, 14, 22, r=4), RED, CRX - 26, CRY)))
        track(m, 18, [(0, 1), (40, .25), (80, 1)])

    elif p == P_MIC:
        mx = 440
        solid(dd, "Handle", lambda k: g_rect(k, 32, 150, rtl=8, rtr=8, rbl=16, rbr=16), "FF23293A", mx, 440)
        solid(dd, "Flag", lambda k: g_rect(k, 70, 62, r=8), CYAN_DIM, mx, 414)
        solid(dd, "FlagLine1", lambda k: g_rect(k, 44, 8, r=4), DARK, mx, 402)
        solid(dd, "FlagLine2", lambda k: g_rect(k, 32, 8, r=4), DARK, mx, 420)
        solid(dd, "Neck", lambda k: g_rect(k, 22, 40, r=8), "FF39415A", mx, 350)
        grad(dd, "Ball", lambda k: g_ellipse(k, 72, 72), "FFCFD8E8", "FF6C778E", mx, 320, gy=36)
        for i in range(4):
            solid(dd, "Mesh%d" % i, lambda k, i=i: g_rect(k, 64, 4, r=2, y=-21 + i * 14),
                  "44121722", mx, 320)

    elif p == P_SUNGLASSES:
        m = mover(dd, "Shades", lambda k: (
            solid(k, "ArmL", lambda q: g_rect(q, 40, 12, r=6, rot=.12), "FF15181F", CX - 222, EY - 40),
            solid(k, "ArmR", lambda q: g_rect(q, 40, 12, r=6, rot=-.12), "FF15181F", CX + 222, EY - 40),
            solid(k, "Bridge", lambda q: g_rect(q, 48, 16, r=8), "FF15181F", CX, EY - 40),
            grad(k, "LensL", lambda q: g_rect(q, 184, 156, rtl=22, rtr=40, rbl=64, rbr=34),
                 "FF2B3350", "FF05070C", LEFT_X, EY + 4, gy=78),
            grad(k, "LensR", lambda q: g_rect(q, 184, 156, rtl=40, rtr=22, rbl=34, rbr=64),
                 "FF2B3350", "FF05070C", RIGHT_X, EY + 4, gy=78),
            line(k, "RimL", lambda q: g_rect(q, 184, 156, rtl=22, rtr=40, rbl=64, rbr=34),
                 CYAN, 4, x=LEFT_X, y=EY + 4, feather=12),
            line(k, "RimR", lambda q: g_rect(q, 184, 156, rtl=40, rtr=22, rbl=34, rbr=64),
                 CYAN, 4, x=RIGHT_X, y=EY + 4, feather=12),
            solid(k, "GleamL", lambda q: g_rect(q, 80, 14, r=7, rot=-.42), "AAFFFFFF", CX - 128, EY - 36),
            solid(k, "GleamR", lambda q: g_rect(q, 80, 14, r=7, rot=-.42), "AAFFFFFF", CX + 84, EY - 36)))
        # the drop is an ENTRANCE, so it plays when the glasses are shown
        intro(m, 14, [(0, -300), (30, 16), (40, -6), (50, 0)])

    elif p == P_MISSILES:
        for i, (mx, my, dl) in enumerate(((-150, -90, 0), (-30, 110, 14), (120, -130, 28))):
            m = mover(dd, "Msl%d" % i, lambda k, mx=mx, my=my:
                      (solid(k, "Flame", lambda q: g_tri(q, 22, 46, rot=-1.5708), ORANGE, CX + mx - 40, CY + my),
                       solid(k, "Body", lambda q: g_rect(q, 60, 22, r=10), SILVER, CX + mx, CY + my),
                       solid(k, "Fin", lambda q: g_rect(q, 16, 30, r=3), "FF7E8AA0", CX + mx - 22, CY + my),
                       solid(k, "Tip", lambda q: g_tri(q, 22, 30, rot=1.5708), RED, CX + mx + 38, CY + my)))
            track(m, 13, [(dl, -420), (46 + dl, 120)], interp="linear")
            track(m, 18, [(dl, 0), (8 + dl, 1), (38 + dl, 1), (46 + dl, 0)])

    elif p == P_TURRETS:
        for tag, bx in (("L", 64), ("R", 436)):
            def build(k, tag=tag):
                solid(k, "Mount", lambda q: g_rect(q, 96, 22, r=9), "FF39415A", 0, 36)
                mover(k, "Barrel" + tag, lambda q: (
                    grad(q, "Tube", lambda z: g_rect(z, 24, 112, r=8),
                         "FFC6D0E2", "FF5A6479", 0, -52, gy=56),
                    solid(q, "Ring1", lambda z: g_rect(z, 30, 8, r=4), "FF7F8AA2", 0, -72),
                    solid(q, "Ring2", lambda z: g_rect(z, 30, 8, r=4), "FF7F8AA2", 0, -50),
                    solid(q, "Muzzle", lambda z: g_rect(z, 38, 20, r=9), "FF8C97AD", 0, -100),
                    mover(q, "Flash" + tag, lambda z: (
                        neon(z, "F1", lambda y: g_star(y, 96, points=6, inner=.30),
                             "FFFFD86B", 0, -104, feather=22),
                        neon(z, "F2", lambda y: g_ellipse(y, 42, 42), WHITE, 0, -104, feather=14)))))
                grad(k, "Body", lambda q: g_rect(q, 78, 62, rtl=20, rtr=20, rbl=10, rbr=10),
                     "FF7A8599", "FF262C3A", 0, 0, gy=31)
                line(k, "Rim", lambda q: g_rect(q, 78, 62, rtl=20, rtr=20, rbl=10, rbr=10),
                     RED, 4, feather=12)
                solid(k, "Slot", lambda q: g_rect(q, 46, 10, r=5), "FF161B26", 0, 12)
            t = mover(dd, "Turret" + tag, build, x=bx, y=TURRET_BASE_Y)
            # left alone (no story driving them) the guns idle: a slow sweep and a
            # muzzle flash now and then
            sw = -1 if tag == "L" else 1
            track(reg["Barrel" + tag], 15, [(0, .10 * sw), (60, -.35 * sw), (120, .10 * sw)])
            track(reg["Flash" + tag], 18, [(0, 0), (58, 0), (60, 1), (66, 0), (120, 0)])
            intro(t, 14, [(0, TURRET_BASE_Y + 170), (38, TURRET_BASE_Y - 8),
                          (52, TURRET_BASE_Y + 6), (64, TURRET_BASE_Y)])

    elif p == P_HOLOPAT:
        # ลูบหัว -- hand strokes back and forth over the top of the head
        m = mover(dd, "Hand", lambda k: (
            solid(k, "Fill", lambda q: holo_hand(q, 1.14, -1), HOLO_FILL, CX, 70),
            line(k, "Edge", lambda q: holo_hand(q, 1.14, -1), HOLO_EDGE, 3,
                 x=CX, y=70, feather=14)),
            anchor=(CX, 70))
        track(m, 13, [(0, -62), (30, 62), (60, -62)])
        track(m, 14, [(0, 0), (15, 9), (30, 0), (45, 9), (60, 0)])
        track(m, 15, [(0, -.10), (30, .10), (60, -.10)])

    elif p in (P_HOLOPOKEL, P_HOLOPOKER):
        # จิ้มแก้ม -- a finger comes in from that edge and prods twice. It is
        # an entrance, so the prods line up with the head being pushed.
        dirx = 1 if p == P_HOLOPOKEL else -1
        px = LEFT_X - EYE_RR - 2 if p == P_HOLOPOKEL else RIGHT_X + EYE_RR + 2
        m = mover(dd, "Finger", lambda k, dirx=dirx, px=px: (
            solid(k, "Fill", lambda q: holo_finger(q, dirx), HOLO_FILL, px, EY + 40),
            line(k, "Edge", lambda q: holo_finger(q, dirx), HOLO_EDGE, 4,
                 x=px, y=EY + 40, feather=14)),
            anchor=(px, EY + 40))
        intro(m, 13, [(0, -150 * dirx), (14, 0), (28, -46 * dirx), (42, 0), (60, -40 * dirx),
                      (110, -40 * dirx), (138, -170 * dirx)])

    elif p == P_HOLOCHIN:
        # เกาคาง -- fingers wiggle up under the chin
        m = mover(dd, "Hand", lambda k: (
            solid(k, "Fill", lambda q: holo_hand(q, 1.04, 1), HOLO_FILL, CX, 440),
            line(k, "Edge", lambda q: holo_hand(q, 1.04, 1), HOLO_EDGE, 3,
                 x=CX, y=440, feather=14)),
            anchor=(CX, 440))
        track(m, 15, [(0, -.13), (10, .13), (20, -.13), (30, .13), (40, -.13)])
        track(m, 14, [(0, 0), (10, -7), (20, 0), (30, -7), (40, 0)])

    elif p == P_SLTAG:
        solid(dd, "Tag", lambda k: g_rect(k, 92, 40, r=8), RED, CRX - 6, CRY)
        solid(dd, "TagLine1", lambda k: g_rect(k, 24, 9, r=4), WHITE, CRX - 22, CRY)
        solid(dd, "TagLine2", lambda k: g_rect(k, 18, 9, r=4), WHITE, CRX + 10, CRY)
        m = mover(dd, "SLLine", lambda k: (
            line(k, "L", lambda q: g_rect(q, 470, 3), RED, 5, x=CX, y=326, feather=12)))
        track(m, 18, [(0, .35), (20, 1), (40, .35)])

    # ------------------------------------------------ LOOI status / AI set
    elif p == P_BULB:
        # ไอเดีย -- the right eye IS the bulb (pair with face OneEye)
        bx, by = RIGHT_X, EY - 10
        m = mover(dd, "Glow", lambda k: glowdisc(k, "H", 110, "66FFE07A", "00FFE07A", bx, by),
                  anchor=(bx, by))
        track(m, 18, [(0, .5), (40, 1), (80, .5)])
        track(m, 16, [(0, .92), (40, 1.08), (80, .92)])
        track(m, 17, [(0, .92), (40, 1.08), (80, .92)])
        shape(dd, "Glass", lambda k: (g_ellipse(k, 108, 116), p_glowfill(k, "FFFFE9A0", 18, 5)), bx, by)
        solid(dd, "Filament", lambda k: g_wave(k, 22, 8), "FFFFB020", bx, by + 8)
        line(dd, "FilamentS", lambda k: g_wave(k, 22, 8), "FFE08A00", 5, x=bx, y=by + 8)
        solid(dd, "Neck", lambda k: g_rect(k, 50, 22, r=6), SILVER, bx, by + 66)
        solid(dd, "Base", lambda k: g_rect(k, 40, 18, rbl=9, rbr=9), "FF8894AA", bx, by + 84)
        solid(dd, "Gleam", lambda k: g_ellipse(k, 26, 16, rot=-.6), "AAFFFFFF", bx - 22, by - 30)

    elif p == P_BOLT:
        m = mover(dd, "Bolt", lambda k: neon(k, "B", lambda q: g_bolt(q, 1.0), GOLD, CRX, CRY, feather=16),
                  anchor=(CRX, CRY))
        track(m, 18, [(0, 1), (10, .35), (20, 1), (70, 1), (90, 1)])
        track(m, 16, [(0, 1), (10, 1.18), (24, 1), (90, 1)])
        track(m, 17, [(0, 1), (10, 1.18), (24, 1), (90, 1)])

    elif p == P_BARCODE:
        widths = (10, 4, 6, 14, 4, 8, 4, 12, 6, 4, 10, 4, 14, 6, 4, 8, 12, 4, 6, 10)
        gaps = (6, 5, 7, 5, 6, 5, 8, 5, 6, 7, 5, 6, 5, 7, 5, 6, 5, 7, 6, 0)
        total = sum(widths) + sum(gaps)
        x = CX - total / 2.0
        for i, (bw, gp) in enumerate(zip(widths, gaps)):
            solid(dd, "B%d" % i, lambda k, bw=bw: g_rect(k, bw, 132, r=1), CYAN, x + bw / 2.0, EY)
            x += bw + gp
        m = mover(dd, "Scan", lambda k: neon(k, "L", lambda q: g_rect(q, total + 40, 4, r=2),
                                             RED, CX, EY, feather=14))
        track(m, 14, [(0, -74), (60, 74), (120, -74)])

    elif p == P_GEARS:
        for name, gx, gy, r, n, col, spin in (("Big", CX - 36, EY - 16, 78, 10, "FF7C8799", 1),
                                              ("Small", CX + 76, EY + 70, 48, 8, CYAN_DIM, -1)):
            m = mover(dd, name, lambda k, gx=gx, gy=gy, r=r, n=n, col=col: (
                solid(k, "G", lambda q: g_gear(q, r, n), col, gx, gy),
                solid(k, "Hub", lambda q: g_ellipse(q, r * .78, r * .78), "FF1A2030", gx, gy),
                solid(k, "Axle", lambda q: g_ellipse(q, r * .32, r * .32), col, gx, gy)),
                anchor=(gx, gy))
            track(m, 15, [(0, 0), (240 if r > 60 else 120, 6.2831855 * spin)], interp="linear")

    elif p == P_UPDATE:
        ux, uy = CX - 30, EY
        m = mover(dd, "Arrows", lambda k: (
            line(k, "A1", lambda q: g_arcup(q, 70, 58, 0), CYAN, 14, x=ux, y=uy - 10, feather=14),
            line(k, "A2", lambda q: g_arcdown(q, 70, 58, 0), CYAN, 14, x=ux, y=uy + 10, feather=14),
            solid(k, "H1", lambda q: g_tri(q, 34, 30, rot=3.1416), CYAN, ux + 70, uy + 4),
            solid(k, "H2", lambda q: g_tri(q, 34, 30), CYAN, ux - 70, uy - 4)),
            anchor=(ux, uy))
        track(m, 15, [(0, 0), (120, 6.2831855)], interp="linear")
        mm = mover(dd, "Lens", lambda k: (
            line(k, "Handle", lambda q: g_rect(q, 12, 40, r=6, rot=-.78), SILVER, 10, x=CRX + 16, y=CRY + 18),
            line(k, "Ring", lambda q: g_ellipse(q, 44, 44), SILVER, 8, x=CRX - 4, y=CRY - 4)),
            anchor=(CRX, CRY))
        track(mm, 13, [(0, -6), (60, 6), (120, -6)])
        track(mm, 14, [(0, 0), (30, -6), (60, 0), (90, -6), (120, 0)])

    elif p == P_MAGNIFIER:
        mx, my = CX - 24, EY - 20
        m = mover(dd, "Glass", lambda k: (
            solid(k, "Handle", lambda q: g_rect(q, 26, 110, r=13, rot=-.78), "FFB07A3A", mx + 96, my + 96),
            solid(k, "Lens", lambda q: g_ellipse(q, 150, 150), "3339C6E8", mx, my),
            line(k, "Ring", lambda q: g_ellipse(q, 150, 150), SILVER, 18, x=mx, y=my, feather=14),
            solid(k, "Gleam", lambda q: g_rect(q, 54, 14, r=7, rot=-.7), "88FFFFFF", mx - 28, my - 32)),
            anchor=(mx, my))
        track(m, 13, [(0, -30), (70, 24), (140, 14), (210, -30)])
        track(m, 14, [(0, 0), (70, -14), (140, 16), (210, 0)])
        mb = mover(dd, "Idea", lambda k: neon(k, "B", lambda q: g_ellipse(q, 30, 34), "FFFFE07A",
                                              CRX, CRY - 6, feather=16), anchor=(CRX, CRY - 6))
        track(mb, 18, [(0, .3), (40, 1), (80, .3)])

    elif p == P_SIGNAL:
        for i in range(6):
            h = 26 + i * 18
            bx = CX - 100 + i * 40
            m = mover(dd, "Bar%d" % i, lambda k, bx=bx, h=h: (
                neon(k, "B", lambda q, h=h: g_rect(q, 26, h, r=4), "FF3FB58F", bx, EY + 60 - h / 2.0,
                     feather=10)))
            track(m, 18, [(0, .25), (10 + i * 10, 1), (40 + i * 10, .25), (120, .25)])

    elif p == P_POINTHAND:
        hx, hy = RIGHT_X + 10, EY + 20
        m = mover(dd, "Hand", lambda k: (
            solid(k, "Fill", holo_point, HOLO_FILL, hx, hy),
            line(k, "Edge", holo_point, HOLO_EDGE, 4, x=hx, y=hy, feather=14)),
            anchor=(hx, hy))
        track(m, 14, [(0, 0), (20, -16), (34, 0), (90, 0)])
        track(m, 15, [(0, 0), (20, -.06), (34, 0), (90, 0)])
        mt = mover(dd, "Tap", lambda k: line(k, "R", lambda q: g_ellipse(q, 40, 40), CYAN, 4,
                                             x=hx - 16, y=hy - 96, feather=10),
                   anchor=(hx - 16, hy - 96))
        track(mt, 16, [(0, .4), (20, .4), (40, 1.6), (90, 1.6)])
        track(mt, 17, [(0, .4), (20, .4), (40, 1.6), (90, 1.6)])
        track(mt, 18, [(0, 0), (20, 1), (40, 0), (90, 0)])

    elif p == P_BRACKETS:
        m = mover(dd, "Frame", lambda k: [
            line(k, "C%d" % i, lambda q, i=i: g_poly_path(q, (
                (0, 46), (0, 0), (46, 0))), CYAN, 9,
                x=CX + (-1 if i in (0, 3) else 1) * 190, y=EY + (-1 if i < 2 else 1) * 118,
                rot=(0, 1.5708, 3.1416, 4.7124)[i], feather=12)
            for i in range(4)], anchor=(CX, EY))
        track(m, 16, [(0, 1), (30, .94), (60, 1)])
        track(m, 17, [(0, 1), (30, .94), (60, 1)])
        ms = mover(dd, "Spark", lambda k: neon(k, "S", lambda q: g_star(q, 36), GOLD, CRX + 10, 72),
                   anchor=(CRX + 10, 72))
        track(ms, 16, [(0, .4), (20, 1.1), (50, .4)])
        track(ms, 17, [(0, .4), (20, 1.1), (50, .4)])

    elif p == P_SUN:
        m = mover(dd, "Rays", lambda k: [
            solid(k, "R%d" % i, lambda q, i=i: g_rect(q, 12, 250, r=6, rot=i * (math.pi / 6)),
                  "55FFC53D", CX, EY) for i in range(6)], anchor=(CX, EY))
        track(m, 15, [(0, 0), (360, 6.2831855)], interp="linear")
        glowdisc(dd, "Halo", 150, "55FFC53D", "00FFC53D", CX, EY)
        grad(dd, "Disc", lambda k: g_ellipse(k, 170, 170), "FFFFE07A", "FFC98A18", CX, EY, gy=85)
        line(dd, "Rim", lambda k: g_ellipse(k, 150, 150), "FFFFF0B0", 6, x=CX, y=EY)
        solid(dd, "Gleam", lambda k: g_ellipse(k, 50, 26, rot=-.6), "66FFFFFF", CX - 40, EY - 44)

    elif p == P_RAIN:
        for i in range(6):
            rx = CX - 90 + i * 36
            m = mover(dd, "Drop%d" % i, lambda k, rx=rx: neon(k, "D", lambda q: g_drop(q, .9), TEAR,
                                                             rx, EY + 60, feather=10))
            track(m, 14, [(0, 0), (40 + (i % 3) * 8, 130)], interp="linear")
            track(m, 18, [(0, 0), (6, 1), (30 + (i % 3) * 8, 1), (40 + (i % 3) * 8, 0)])
        m = mover(dd, "Cloud", lambda k: solid(k, "C", lambda q: (
            g_ellipse(q, 120, 96, x=-60, y=10), g_ellipse(q, 140, 130, x=10, y=-20),
            g_ellipse(q, 110, 90, x=76, y=12), g_rect(q, 250, 60, y=34, r=30)), "FFE6EEF8", CX, EY - 30),
            anchor=(CX, EY - 30))
        track(m, 13, [(0, -6), (120, 6), (240, -6)])

    elif p == P_ALARM:
        for sgn in (-1, 1):
            m = mover(dd, "Waves%d" % (sgn > 0), lambda k, sgn=sgn: [
                line(k, "W%d" % j, lambda q, j=j: g_arcup(q, 40 + j * 16, 16 + j * 5, 0), CYAN, 8,
                     x=CX + sgn * (EYE_DX + EYE_RR + 16 + j * 20), y=EY,
                     rot=sgn * 1.5708, feather=10) for j in range(2)],
                anchor=(CX + sgn * (EYE_DX + EYE_RR + 26), EY))
            track(m, 18, [(0, .2), (12, 1), (30, .2)])
            track(m, 16, [(0, .9), (12, 1.12), (30, .9)])
        mc = mover(dd, "Clock", lambda k: fit(k, CRX, CRY, 1.45, lambda k: (
            solid(k, "BellL", lambda q: g_ellipse(q, 20, 20), RED, CRX - 22, CRY - 26),
            solid(k, "BellR", lambda q: g_ellipse(q, 20, 20), RED, CRX + 22, CRY - 26),
            solid(k, "Face", lambda q: g_ellipse(q, 56, 56), WHITE, CRX, CRY),
            line(k, "Rim", lambda q: g_ellipse(q, 56, 56), RED, 6, x=CRX, y=CRY),
            solid(k, "HandH", lambda q: g_rect(q, 5, 18, r=2, y=-7), DARK, CRX, CRY),
            solid(k, "HandM", lambda q: g_rect(q, 18, 5, r=2, x=7), DARK, CRX, CRY))),
            anchor=(CRX, CRY))
        track(mc, 15, osc(0, 24, 3, -.16, .16) + [(60, 0)], interp="linear")

    elif p == P_CALENDAR:
        cx0, cy0 = CRX - 4, CRY + 6
        solid(dd, "Page", lambda k: g_rect(k, 64, 60, r=8), WHITE, cx0, cy0)
        solid(dd, "Top", lambda k: g_rect(k, 64, 18, rtl=8, rtr=8), RED, cx0, cy0 - 21)
        for j, (ox, oy) in enumerate(((-18, 0), (0, 0), (18, 0), (-18, 16), (0, 16), (18, 16))):
            solid(dd, "Day%d" % j, lambda k: g_rect(k, 10, 8, r=2), "FF9AA6BC" if j != 4 else RED,
                  cx0 + ox, cy0 + oy)
        solid(dd, "RingL", lambda k: g_rect(k, 6, 16, r=3), GREY, cx0 - 16, cy0 - 32)
        solid(dd, "RingR", lambda k: g_rect(k, 6, 16, r=3), GREY, cx0 + 16, cy0 - 32)

    elif p == P_CLOCK:
        # HH:MM from the view model: clockD0..clockD3, one channel layer each
        for slot, dx in enumerate((-150, -62, 62, 150)):
            solo = nid()
            CLOCK_SOLOS.append(solo)
            ids = []
            w(dd, '<Solo activeComponentId="0:0" x="%g" y="%g" name="Digit%d" id="%s">'
              % (CX + dx, EY, slot, solo))
            for n in range(10):
                did = nid()
                ids.append(did)
                w(dd + 1, '<Node name="D%d" id="%s">' % (n, did))
                for s_ in DIGIT_SEGS[n]:
                    sx, sy, sw, sh = SEG[s_]
                    neon(dd + 2, "S" + s_, lambda k, sw=sw, sh=sh: g_rect(k, sw * 1.3, sh * 1.3, r=6),
                         CYAN, sx * 1.3, sy * 1.3, feather=12)
                w(dd + 1, '</Node>')
            w(dd, '</Solo>')
            CLOCK_DIGITS.append(ids)
        m = mover(dd, "Colon", lambda k: (
            neon(k, "U", lambda q: g_ellipse(q, 16, 16), CYAN, CX, EY - 28),
            neon(k, "L", lambda q: g_ellipse(q, 16, 16), CYAN, CX, EY + 28)))
        track(m, 18, [(0, 1), (29, 1), (30, .15), (59, .15), (60, 1)], interp="hold")

    elif p == P_PENCIL:
        m = mover(dd, "Pencil", lambda k: (
            solid(k, "Body", lambda q: g_rect(q, 18, 70, r=3), AMBER, CRX, CRY),
            solid(k, "Band", lambda q: g_rect(q, 18, 10, r=2), SILVER, CRX, CRY - 30),
            solid(k, "Eraser", lambda q: g_rect(q, 18, 14, rtl=6, rtr=6), PINK, CRX, CRY - 42),
            solid(k, "Wood", lambda q: g_tri(q, 18, 20, rot=3.1416), CREAM, CRX, CRY + 45),
            solid(k, "Lead", lambda q: g_tri(q, 7, 8, rot=3.1416), DARK, CRX, CRY + 51)),
            anchor=(CRX, CRY + 50))
        track(m, 15, [(0, .5), (10, .75), (20, .45), (30, .8), (40, .5)])
        track(m, 13, [(0, -6), (20, 6), (40, -6)])

    elif p == P_WARNING:
        m = mover(dd, "Sign", lambda k: (
            shape(k, "Tri", lambda q: (g_poly(q, 70, points=3, corner=8, hh=62),
                                       p_glowfill(q, AMBER, 12, 4)), CRX, CRY),
            solid(k, "Bar", lambda q: g_rect(q, 8, 22, r=4), DARK, CRX, CRY + 2),
            solid(k, "Dot", lambda q: g_ellipse(q, 8, 8), DARK, CRX, CRY + 20)),
            anchor=(CRX, CRY))
        track(m, 16, [(0, 1), (10, 1.15), (26, 1), (60, 1)])
        track(m, 17, [(0, 1), (10, 1.15), (26, 1), (60, 1)])

    elif p == P_CURTAINS:
        for tag, sgn in (("L", -1), ("R", 1)):
            def drape(k, sgn=sgn):
                grad(k, "Cloth", lambda q: g_rect(q, 250, 520), "FF1E4E5E", "FF0D2A36", CX + sgn * 125, CY, gy=260)
                for j in range(4):
                    solid(k, "Fold%d" % j, lambda q: g_rect(q, 8, 500, r=4), "55000000",
                          CX + sgn * (40 + j * 56), CY)
                solid(k, "Hem", lambda q: g_rect(q, 250, 16), "FF2A6A7E", CX + sgn * 125, 30)
            m = mover(dd, "Curtain" + tag, drape)
            intro(m, 13, [(0, sgn * 260), (46, 0)], interp="easeOut")
        lk = mover(dd, "Lock", lambda k: (
            line(k, "Shackle", lambda q: g_arcup(q, 26, 34, 6), SILVER, 11, x=CX, y=EY - 30),
            grad(k, "Body", lambda q: g_rect(q, 86, 70, r=12), "FFFFD86B", "FFC98A18", CX, EY + 12, gy=35),
            solid(k, "Hole", lambda q: g_ellipse(q, 14, 14), DARK, CX, EY + 6),
            solid(k, "Slot", lambda q: g_rect(q, 6, 18, r=3), DARK, CX, EY + 20)),
            anchor=(CX, EY))
        intro(lk, 16, [(0, 0), (46, 0), (60, 1.2), (70, .95), (78, 1)])
        intro(lk, 17, [(0, 0), (46, 0), (60, 1.2), (70, .95), (78, 1)])

    elif p == P_BIGBATTERY:
        line(dd, "Shell", lambda k: g_rect(k, 240, 120, r=22), RED, 12, x=CX - 10, y=EY, feather=16)
        solid(dd, "Nub", lambda k: g_rect(k, 20, 46, r=6), RED, CX + 124, EY)
        m = mover(dd, "Cell", lambda k: solid(k, "C", lambda q: g_rect(q, 44, 84, r=8), RED, CX - 98, EY))
        track(m, 18, [(0, 1), (30, .15), (60, 1)])

    elif p == P_CRACKED:
        # หน้าจอร้าวเล็กๆ รอบตา -- disappointment, not destruction
        for j, (ox, oy, rot) in enumerate(((RIGHT_X + 60, EY - 70, .4), (RIGHT_X + 20, EY + 96, 2.6))):
            line(dd, "Crack%d" % j, lambda k: g_poly_path(k, ((0, 0), (10, 22), (-4, 40), (12, 64), (4, 86))),
                 CYAN_DIM, 3, x=ox, y=oy, rot=rot, feather=6)

    _cur_reg[0] = None
    _cur_intro[0] = None
    body = reverse_blocks(out[mark:], dd)
    del out[mark:]
    if P_NAMES[p] in PROP_FIT:
        fx, fy, fs = PROP_FIT[P_NAMES[p]]
        w(dd, '<Node x="%g" y="%g" scaleX="%g" scaleY="%g" name="Fit">' % (fx, fy, fs, fs))
        w(dd + 1, '<Node x="%g" y="%g" name="FitAt">' % (-fx, -fy))
        out.extend("        " + ln for ln in body)
        w(dd + 1, '</Node>')
        w(dd, '</Node>')
    else:
        out.extend(body)
    w(d, '</Node>')
    return pid


# ================================================================= BACKGROUNDS
G_NAMES = ["None", "Grid", "BokehHearts", "ChartUp", "ChartDown", "RedAlert",
           "GoldGlow", "BurstRays", "ShatterRed", "DeepBlue", "Spotlight"]
(G_NONE, G_GRID, G_BOKEH, G_CHARTUP, G_CHARTDOWN, G_REDALERT, G_GOLDGLOW,
 G_BURST, G_SHATTER, G_DEEPBLUE, G_SPOTLIGHT) = range(len(G_NAMES))


def candle(d, name, x, y, h, wick, color):
    solid(d, name + "W", lambda k: g_rect(k, 4, wick, r=2), color, x, y)
    solid(d, name + "B", lambda k: g_rect(k, 18, h, r=3), color, x, y)


def emit_bg(d, g):
    gid = nid()
    if g == G_NONE:
        w(d, '<Node name="Bg_None" id="%s"/>' % gid)
        return gid
    w(d, '<Node name="Bg_%s" id="%s">' % (G_NAMES[g], gid))
    dd = d + 1
    mark = len(out)
    _cur_intro[0] = INTROS["bg"].setdefault(g, [])

    if g == G_GRID:
        for i, dx in enumerate((-230, -150, -80, 0, 80, 150, 230)):
            top_x, bot_x = CX + dx * .22, CX + dx
            lx = bot_x - top_x
            L = math.hypot(lx, 132)
            rot = -math.atan2(lx, 132)
            solid(dd, "V%d" % i, lambda k, L=L, rot=rot: g_rect(k, 2.6, L, rot=rot, r=1.3),
                  CYAN_DIM, (top_x + bot_x) / 2.0, 386)
        m = mover(dd, "HLines", lambda k: [
            solid(k, "H%d" % i, lambda q, ww=ww, th=th: g_rect(q, ww, th, r=th / 2), CYAN, CX, yy)
            for i, (yy, ww, th) in enumerate(((320, 104, 1.6), (334, 140, 1.9), (354, 196, 2.2),
                                              (382, 274, 2.6), (418, 372, 3.0), (452, 470, 3.4)))])
        track(m, 14, [(0, 0), (90, 22)], interp="linear")
        track(m, 18, [(0, .2), (16, 1), (74, 1), (90, .2)])

    elif g == G_BOKEH:
        for i, (bx, by, br, al) in enumerate(((90, 120, 60, "33"), (400, 150, 44, "2A"),
                                              (130, 380, 52, "26"), (392, 392, 70, "22"),
                                              (250, 96, 36, "2E"))):
            m = mover(dd, "Bok%d" % i, lambda k, bx=bx, by=by, br=br, al=al:
                      glowdisc(k, "B", br, al + "FF5C8A", "00FF5C8A", bx, by))
            track(m, 14, [(0, 0), (90 + i * 20, -26), (180 + i * 40, 0)])
            track(m, 18, [(0, .5), (60, 1), (180 + i * 40, .5)])

    elif g in (G_CHARTUP, G_CHARTDOWN):
        up = (g == G_CHARTUP)
        col = GREEN if up else RED
        base = 430 if up else 150
        m = mover(dd, "Chart", lambda k: [
            candle(k, "C%d" % i,
                   40 + i * 48,
                   base + (-i * 34 if up else i * 34) + (8 if i % 2 else -8),
                   38 + (i % 3) * 12, 66 + (i % 2) * 18, col)
            for i in range(9)])
        track(m, 13, [(0, 0), (120, -48)], interp="linear")
        track(m, 18, [(0, .0), (20, .55), (100, .55), (120, 0)])
        wash(dd, "Wash", ("140F6B3C" if up else "148E0F26"))

    elif g == G_REDALERT:
        m = mover(dd, "Alert", lambda k: glowdisc(k, "R", 245, "44FF2A3A", "00FF2A3A", CX, CY))
        track(m, 18, [(0, .25), (18, 1), (36, .25)])
        wash(dd, "Vign", "20FF2A3A")

    elif g == G_GOLDGLOW:
        glowdisc(dd, "G", 225, "3AFFC53D", "00FFC53D", CX, CY - 20)

    elif g == G_BURST:
        m = mover(dd, "Rays", lambda k: [
            shape(k, "R%d" % i, lambda q, i=i: (g_rect(q, 12, 500, r=6, rot=i * (math.pi / 9)),
                                              p_radial(q, "44FFE7A0", "00FFE7A0", 245)),
                  CX, CY) for i in range(9)], anchor=(CX, CY))
        track(m, 15, [(0, 0), (240, 6.2831855)], interp="linear")
        track(m, 16, [(0, .9), (30, 1.05), (60, .9)])

    elif g == G_SHATTER:
        glowdisc(dd, "R", 245, "3AFF2A3A", "00FF2A3A", CX, CY)
        for i, (ax, ay, rot) in enumerate(((120, 90, .6), (380, 120, -.7), (150, 400, -.4),
                                           (370, 380, .5), (250, 60, 1.2))):
            solid(dd, "Crack%d" % i, lambda k, rot=rot: g_rect(k, 4, 260, r=2, rot=rot),
                  "AAFF7080", ax, ay)

    elif g == G_DEEPBLUE:
        wash(dd, "Sea", "FF0A1D4A", y=CY - 20, hold=.5)

    elif g == G_SPOTLIGHT:
        glowdisc(dd, "S", 220, "2EFFFFFF", "00FFFFFF", CX, CY - 30)

    body = out[mark:]
    del out[mark:]
    out.extend(reverse_blocks(body, dd))
    w(d, '</Node>')
    return gid


# ================================================================= FOREGROUNDS
F_NAMES = ["None", "Water", "Explosion", "Cracks", "LowerThird", "Confetti",
           "ArrowUp", "ArrowDown", "RedFlash", "MoneyRain", "Smoke", "BulletHoles"]
(F_NONE, F_WATER, F_EXPLOSION, F_CRACKS, F_LOWERTHIRD, F_CONFETTI, F_ARROWUP,
 F_ARROWDOWN, F_REDFLASH, F_MONEY, F_SMOKE, F_HOLES) = range(len(F_NAMES))


def emit_fg(d, f):
    fid = nid()
    if f == F_NONE:
        w(d, '<Node name="Fg_None" id="%s"/>' % fid)
        return fid
    w(d, '<Node name="Fg_%s" id="%s">' % (F_NAMES[f], fid))
    dd = d + 1
    mark = len(out)
    _cur_intro[0] = INTROS["fg"].setdefault(f, [])

    if f == F_WATER:
        m = mover(dd, "Water", lambda k: (
            grad(k, "Body", lambda q: g_rect(q, 620, 420), "882FA8FF", "CC0A3F8A", CX, 470, gy=210),
            solid(k, "Crest", lambda q: g_rect(q, 620, 16, r=8), "AA9FE0FF", CX, 262),
            solid(k, "Foam1", lambda q: g_ellipse(q, 90, 34), "889FE0FF", 120, 262),
            solid(k, "Foam2", lambda q: g_ellipse(q, 120, 30), "889FE0FF", 300, 258),
            solid(k, "Foam3", lambda q: g_ellipse(q, 70, 26), "889FE0FF", 430, 264)),
                  anchor=(CX, 380))
        # the flood RISES when it is shown, then only rocks
        intro(m, 14, [(0, 190), (150, 20)], interp="easeOut")
        track(m, 15, [(0, -.02), (75, .02), (150, -.02), (225, .02), (300, -.02)])
        for i, (bx, bs, dl) in enumerate(((150, 22, 0), (330, 16, 25), (410, 26, 50), (80, 14, 70))):
            mb = mover(dd, "WB%d" % i, lambda k, bx=bx, bs=bs:
                       line(k, "B", lambda q, bs=bs: g_ellipse(q, bs, bs), "AA9FE0FF", 3, x=bx, y=430))
            track(mb, 14, [(0 + dl, 0), (110 + dl, -190)], interp="linear")
            track(mb, 18, [(0 + dl, 0), (14 + dl, 1), (90 + dl, 1), (110 + dl, 0)])

    elif f == F_EXPLOSION:
        for i, (ex, ey, es, dl) in enumerate(((150, 180, 1.0, 0), (350, 300, .8, 18),
                                              (250, 130, .6, 34))):
            m = mover(dd, "Boom%d" % i, lambda k, ex=ex, ey=ey, es=es:
                      (solid(k, "Burst", lambda q, es=es: g_star(q, 210 * es, points=10, inner=.42, corner=6),
                             "AAFF8A3D", ex, ey),
                       solid(k, "Hot", lambda q, es=es: g_star(q, 120 * es, points=8, inner=.5, corner=4),
                             "DDFFE07A", ex, ey),
                       glowdisc(k, "Core", 90 * es, "CCFFF0A0", "00FF8A3D", ex, ey)), anchor=(ex, ey))
            track(m, 16, [(0 + dl, .1), (12 + dl, 1.15), (30 + dl, 1.3)])
            track(m, 17, [(0 + dl, .1), (12 + dl, 1.15), (30 + dl, 1.3)])
            track(m, 18, [(0 + dl, 0), (6 + dl, 1), (30 + dl, 0), (70, 0)])

    elif f == F_CRACKS:
        # a real impact: jagged rays out of one point plus a broken ring
        ix, iy = 316, 176
        for i in range(7):
            th = i * 2 * math.pi / 7 + .3
            pts, r = [(0.0, 0.0)], 0.0
            for j, step in enumerate((26, 38, 54, 70, 96, 130)):
                r += step
                jit = (9 if (i + j) % 2 else -9) * (1 + j * .25)
                pts.append((math.cos(th) * r - math.sin(th) * jit, math.sin(th) * r + math.cos(th) * jit))
            line(dd, "Ray%d" % i, lambda k, pts=pts: g_poly_path(k, pts), "EEE8F4FF", 4 if i % 2 else 3,
                 x=ix, y=iy, feather=6)
        ring = [(math.cos(a / 9.0 * 2 * math.pi) * (34 + (a % 2) * 8),
                 math.sin(a / 9.0 * 2 * math.pi) * (34 + (a % 2) * 8)) for a in range(10)]
        line(dd, "Ring", lambda k: g_poly_path(k, ring), "CCE8F4FF", 3, x=ix, y=iy)
        solid(dd, "Hit", lambda k: g_ellipse(k, 16, 16), "EEE8F4FF", ix, iy)

    elif f == F_LOWERTHIRD:
        m = mover(dd, "Chyron", lambda k: (
            solid(k, "Bar", lambda q: g_rect(q, 470, 62, r=8), "EE0B1020", CX, 418),
            solid(k, "Accent", lambda q: g_rect(q, 96, 62, rtl=8, rtr=0, rbl=8, rbr=0), RED, 62, 418),
            solid(k, "AccDot", lambda q: g_ellipse(q, 16, 16), WHITE, 62, 418),
            solid(k, "Line1", lambda q: g_rect(q, 250, 12, r=6), "FFDCE6F5", 250, 406),
            solid(k, "Line2", lambda q: g_rect(q, 170, 9, r=5), "99A8B8D0", 210, 430)))
        intro(m, 13, [(0, -520), (26, 8), (34, 0)], interp="easeOut")
        mf = mover(dd, "Flash", lambda k: glowdisc(k, "F", 210, "66FFFFFF", "00FFFFFF", 400, 120))
        track(mf, 18, [(0, 0), (4, 1), (12, 0), (70, 0), (74, 1), (82, 0), (200, 0)])

    elif f == F_CONFETTI:
        for i in range(10):
            cx0 = 40 + i * 46
            col = (CYAN, PINK, GOLD, GREEN, PURPLE)[i % 5]
            m = mover(dd, "Cf%d" % i, lambda k, cx0=cx0, col=col, i=i:
                      solid(k, "C", lambda q, i=i: g_rect(q, 14, 22, r=3, rot=i * .5), col, cx0, 520),
                      anchor=(cx0, 520))
            track(m, 14, [(0, 0), (120 + (i % 4) * 20, -560)], interp="linear")
            track(m, 15, [(0, 0), (120, 3.14 * (1 if i % 2 else -1))], interp="linear")

    elif f in (F_ARROWUP, F_ARROWDOWN):
        up = (f == F_ARROWUP)
        col = GREEN if up else RED
        m = mover(dd, "Arrow", lambda k: (
            solid(k, "Head", lambda q: g_tri(q, 96, 84, rot=0 if up else 3.1416), col, 434, 150 if up else 350),
            solid(k, "Stem", lambda q: g_rect(q, 40, 96, r=8), col, 434, 236 if up else 264)))
        track(m, 14, [(0, 24 if up else -24), (40, -14 if up else 14), (80, 24 if up else -24)])
        track(m, 18, [(0, .7), (40, 1), (80, .7)])

    elif f == F_REDFLASH:
        m = mover(dd, "Flash", lambda k: wash(k, "F", "55FF2A3A"))
        track(m, 18, [(0, 0), (6, 1), (22, 0), (60, 0)])

    elif f == F_MONEY:
        for i in range(8):
            bx = 46 + i * 58
            m = mover(dd, "Bill%d" % i, lambda k, bx=bx, i=i: (
                solid(k, "B", lambda q, i=i: g_rect(q, 56, 30, r=4, rot=-.3 + i * .16),
                      "FF3FA96B", bx, 540),
                solid(k, "S", lambda q, i=i: g_ellipse(q, 16, 16), "FFBFF0D2", bx, 540)),
                anchor=(bx, 540))
            track(m, 14, [(0, 0), (150 + (i % 3) * 26, -620)], interp="linear")
            track(m, 13, [(0, 0), (40, 18), (80, -18), (120, 0)])
            track(m, 15, [(0, -.2), (60, .25), (120, -.2)])

    elif f == F_SMOKE:
        for i, (sx, ss, dl) in enumerate(((170, 1.0, 0), (300, .8, 30), (240, .6, 60))):
            m = mover(dd, "Sm%d" % i, lambda k, sx=sx, ss=ss:
                      solid(k, "S", lambda q, ss=ss: (g_ellipse(q, 90 * ss, 70 * ss, x=-30 * ss),
                                                      g_ellipse(q, 110 * ss, 90 * ss),
                                                      g_ellipse(q, 76 * ss, 60 * ss, x=36 * ss, y=10 * ss)),
                            "66707A94", sx, 470), anchor=(sx, 470))
            track(m, 14, [(0 + dl, 0), (150 + dl, -260)], interp="linear")
            track(m, 16, [(0 + dl, .4), (150 + dl, 1.5)], interp="linear")
            track(m, 17, [(0 + dl, .4), (150 + dl, 1.5)], interp="linear")
            track(m, 18, [(0 + dl, 0), (24 + dl, .8), (150 + dl, 0)])

    elif f == F_HOLES:
        pts = ((132, 172), (356, 148), (196, 330), (392, 300), (268, 96),
               (108, 286), (322, 386), (240, 226))
        for i, (hx, hy) in enumerate(pts):
            # first declared paints on top: rim over the punched hole, hole over
            # the halo, cracks radiating out from underneath all of it
            # back to front: cracks, halo, the punched hole, its rim
            m = mover(dd, "Hole%d" % i, lambda k, hx=hx, hy=hy, i=i: (
                solid(k, "Crack", lambda q, i=i: (g_rect(q, 2.5, 104, r=1, rot=.4 + i),
                                                  g_rect(q, 2.5, 78, r=1, rot=1.9 + i),
                                                  g_rect(q, 2, 62, r=1, rot=2.9 + i),
                                                  g_rect(q, 2, 48, r=1, rot=4.4 + i)),
                      "55C8DCFF", hx, hy),
                solid(k, "Halo", lambda q: g_ellipse(q, 52, 52), "55101828", hx, hy),
                solid(k, "Ring", lambda q: g_ellipse(q, 34, 34), "F205070C", hx, hy),
                line(k, "Rim", lambda q: g_ellipse(q, 34, 34), "CCD6E8FF", 3, x=hx, y=hy, feather=8)),
                anchor=(hx, hy))
            punch = [(0, 0), (i * 26, 0), (i * 26 + 8, 1.25), (i * 26 + 16, 1)] if i else                 [(0, 0), (8, 1.25), (16, 1)]
            intro(m, 16, punch)
            intro(m, 17, punch)

    body = out[mark:]
    del out[mark:]
    out.extend(reverse_blocks(body, dd))
    w(d, '</Node>')
    return fid


# ================================================================= FACES
# A face is the expression channel only: eyes, brow, mouth.
# (name, eyeL, eyeR, brow, mouth, noblink)
#
# LOOI style: the eyes carry the emotion on their own. Brows and mouths are the
# exception, kept only where the eyes alone would be ambiguous (Thinking's one
# raised brow, Disgusted's wavy lip, Crying's frown next to U-shaped eyes).
# Indices 0-31 are the public contract -- append, never reorder.
FACES = [
    ("Neutral",    V_ROUND,   V_ROUND,   B_NONE,     M_NONE,      0),
    ("Happy",      V_ARC,     V_ARC,     B_NONE,     M_NONE,      1),
    ("Angry",      V_ANGRY,   V_ANGRY,   B_NONE,     M_FANG,      0),
    ("Sleepy",     V_BAR,     V_BAR,     B_NONE,     M_NONE,      1),
    ("Curious",    V_BAR,     V_BARTILT, B_NONE,     M_NONE,      1),
    ("Wink",       V_ROUND,   V_BAR,     B_NONE,     M_NONE,      1),
    ("Dead",       V_CROSS,   V_CROSS,   B_NONE,     M_NONE,      1),
    ("Laughing",   V_CHEV,    V_CHEV,    B_NONE,     M_NONE,      1),
    ("Evil",       V_EVIL,    V_EVIL,    B_NONE,     M_FANG,      0),
    ("Focused",    V_WEDGE,   V_WEDGE,   B_NONE,     M_NONE,      0),
    ("Excited",    V_DOME,    V_DOME,    B_NONE,     M_NONE,      0),
    ("Shy",        V_ROUND,   V_ROUND,   B_NONE,     M_NONE,      0),
    ("Shock",      V_WIDE,    V_WIDE,    B_NONE,     M_NONE,      1),
    ("Disgusted",  V_CHEV,    V_CHEV,    B_NONE,     M_WAVY,      1),
    ("Love",       V_HEART,   V_HEART,   B_NONE,     M_NONE,      1),
    ("Crying",     V_ARCDOWN, V_ARCDOWN, B_NONE,     M_FROWN,     1),
    ("Sad",        V_DROOP,   V_DROOP,   B_NONE,     M_NONE,      0),
    ("Thinking",   V_ROUND,   V_ROUND,   B_ONEUP,    M_NONE,      0),
    ("Listening",  V_ROUND,   V_ROUND,   B_NONE,     M_NONE,      0),
    ("Confused",   V_TINY,    V_ROUND,   B_ONEUP,    M_WAVY,      0),
    ("Pout",       V_HALFLID, V_HALFLID, B_NONE,     M_FROWN,     0),
    ("Dizzy",      V_SPIRAL,  V_SPIRAL,  B_NONE,     M_WAVY,      1),
    ("Bored",      V_HALFLID, V_HALFLID, B_NONE,     M_NONE,      0),
    ("Scared",     V_TINY,    V_TINY,    B_NONE,     M_WAVY,      0),
    ("Tired",      V_HALFLID, V_HALFLID, B_NONE,     M_FLAT,      1),
    ("Blank",      V_EMPTY,   V_EMPTY,   B_NONE,     M_NONE,      1),
    ("Money",      V_DOLLAR,  V_DOLLAR,  B_NONE,     M_NONE,      1),
    ("Grin",       V_ROUND,   V_ROUND,   B_NONE,     M_GRIN,      0),
    ("Smug",       V_HALFLID, V_HALFLID, B_NONE,     M_SMIRK,     0),
    ("Determined", V_WEDGE,   V_WEDGE,   B_NONE,     M_FLAT,      0),
    ("Calm",       V_ROUND,   V_ROUND,   B_NONE,     M_SMILE,     0),
    ("Rage",       V_ANGRY,   V_ANGRY,   B_ANGRYRED, M_ZIGZAGRED, 1),   # one colour: red all over
    # 32+ -- the LOOI status set
    ("OneEye",     V_ROUND,   V_EMPTY,   B_NONE,     M_NONE,      0),   # the other eye is a prop
    ("Recognized", V_RING,    V_RING,    B_NONE,     M_NONE,      0),
    ("Starry",     V_STAREYE, V_STAREYE, B_NONE,     M_NONE,      1),   # scenes: dazzled by food / a win
    # 35+ -- jelly mood stories
    ("Weepy",      V_DROOP,   V_DROOP,   B_SAD,      M_WAVY,      0),   # brows fall, lip trembles
    ("Quizzical",  V_BAR,     V_WIDE,    B_RAISED,   M_NONE,      0),   # one eye squints, one goes wide
    ("Fume",       V_ANGRY,   V_ANGRY,   B_ANGRYRED, M_NONE,      0),
    ("Beam",       V_ARC,     V_ARC,     B_NONE,     M_SMILEOPEN, 1),
    ("Glad",       V_DOME,    V_DOME,    B_RAISED,   M_NONE,      0),
    ("Bashful",    V_ROUND,   V_ROUND,   B_NONE,     M_CAT,       0),
    ("Fluster",    V_ROUND,   V_ROUND,   B_WORRIED,  M_WAVY,      0),
    ("Haughty",    V_HALFLID, V_HALFLID, B_RAISED,   M_NONE,      0),
]
F_INDEX = {n: i for i, (n, *_r) in enumerate(FACES)}

# Ready-made combinations, exported for the host to use as presets.
# (name, face, prop, bg, fg). Indices are the public contract -- append only.
PRESETS = [
    ("Normal",       "Neutral",    P_NONE,       G_NONE,      F_NONE),
    ("Happy",        "Happy",      P_NONE,       G_NONE,      F_NONE),
    ("Angry",        "Angry",      P_ANGRYMARK,  G_NONE,      F_NONE),
    ("Sleepy",       "Sleepy",     P_ZZZ,        G_NONE,      F_NONE),
    ("Curious",      "Curious",    P_QUESTION,   G_NONE,      F_NONE),
    ("Eating",       "Neutral",    P_BURGER,     G_NONE,      F_NONE),
    ("Drinking",     "Neutral",    P_BEER,       G_NONE,      F_NONE),
    ("Wink",         "Wink",       P_SPARKLE,    G_NONE,      F_NONE),
    ("Dead",         "Dead",       P_NONE,       G_NONE,      F_NONE),
    ("Laughing",     "Laughing",   P_NONE,       G_NONE,      F_NONE),
    ("Music",        "Calm",       P_HEADPHONES, G_NONE,      F_NONE),
    ("VRMode",       "Blank",      P_VR,         G_NONE,      F_NONE),
    ("Diving",       "Neutral",    P_SNORKEL,    G_DEEPBLUE,  F_NONE),
    ("Evil",         "Evil",       P_DEVIL,      G_NONE,      F_NONE),
    ("Focused",      "Focused",    P_NONE,       G_GRID,      F_NONE),
    ("Excited",      "Excited",    P_SPARKLES,   G_NONE,      F_NONE),
    ("Shy",          "Shy",        P_BLUSH,      G_NONE,      F_NONE),
    ("Shock",        "Shock",      P_EXCLAIM,    G_NONE,      F_NONE),
    ("Disgusted",    "Disgusted",  P_TRASH,      G_NONE,      F_NONE),
    ("CameraMode",   "Sleepy",     P_CAMERA,     G_NONE,      F_NONE),
    ("Love",         "Love",       P_HEARTS,     G_BOKEH,     F_NONE),
    ("Crying",       "Crying",     P_TEARS,      G_NONE,      F_NONE),
    ("Sad",          "Sad",        P_NONE,       G_NONE,      F_NONE),
    ("Thinking",     "Thinking",   P_THINKDOTS,  G_NONE,      F_NONE),
    ("Listening",    "Listening",  P_SOUNDWAVE,  G_NONE,      F_NONE),
    ("Confused",     "Confused",   P_QUESTIONS,  G_NONE,      F_NONE),
    ("Pout",         "Pout",       P_STEAM,      G_NONE,      F_NONE),
    ("Dizzy",        "Dizzy",      P_DIZZYSTARS, G_NONE,      F_NONE),
    ("Bored",        "Bored",      P_SWEAT,      G_NONE,      F_NONE),
    ("Scared",       "Scared",     P_SHIVER,     G_NONE,      F_NONE),
    ("Proud",        "Happy",      P_MEDAL,      G_SPOTLIGHT, F_NONE),
    ("LowBattery",   "Tired",      P_BATTERYLOW, G_NONE,      F_NONE),
    ("AngryMissile", "Rage",       P_MISSILES,   G_REDALERT,  F_EXPLOSION),
    ("CryFlood",     "Crying",     P_TEARS,      G_DEEPBLUE,  F_WATER),
    ("Interview",    "Excited",    P_MIC,        G_SPOTLIGHT, F_LOWERTHIRD),
    ("MindBlown",    "Dizzy",      P_NONE,       G_BURST,     F_EXPLOSION),
    ("DealWithIt",   "Blank",      P_SUNGLASSES, G_NONE,      F_NONE),
    ("MoneyRain",    "Money",      P_NONE,       G_GOLDGLOW,  F_MONEY),
    ("MarketUp",     "Excited",    P_NONE,       G_CHARTUP,   F_ARROWUP),
    ("MarketCrash",  "Shock",      P_NONE,       G_CHARTDOWN, F_ARROWDOWN),
    ("PortBlown",    "Dead",       P_NONE,       G_SHATTER,   F_CRACKS),
    ("StopLoss",     "Determined", P_SLTAG,      G_CHARTDOWN, F_REDFLASH),
    ("Smug",         "Smug",       P_NONE,       G_NONE,      F_NONE),
    ("Party",        "Laughing",   P_NONE,       G_BOKEH,     F_CONFETTI),
    # 45+ -- LOOI moodset IDs 21-40: status and AI features
    ("Idea",           "OneEye",     P_BULB,        G_NONE,  F_NONE),
    ("Disappointed",   "Sad",        P_CRACKED,     G_NONE,  F_NONE),
    ("Pained",         "Laughing",   P_SWEAT,       G_NONE,  F_NONE),
    ("Scanning",       "Blank",      P_BARCODE,     G_NONE,  F_NONE),
    ("Processing",     "Blank",      P_GEARS,       G_NONE,  F_NONE),
    ("Charging",       "Sleepy",     P_BOLT,        G_NONE,  F_NONE),
    ("SoftwareUpdate", "Blank",      P_UPDATE,      G_NONE,  F_NONE),
    ("BatteryEmpty",   "Blank",      P_BIGBATTERY,  G_NONE,  F_NONE),
    ("SignalSearch",   "Blank",      P_SIGNAL,      G_NONE,  F_NONE),
    ("Searching",      "Blank",      P_MAGNIFIER,   G_NONE,  F_NONE),
    ("GestureInput",   "OneEye",     P_POINTHAND,   G_NONE,  F_NONE),
    ("FaceRecognized", "Recognized", P_BRACKETS,    G_NONE,  F_NONE),
    ("WeatherSunny",   "Blank",      P_SUN,         G_NONE,  F_NONE),
    ("WeatherRainy",   "Blank",      P_RAIN,        G_NONE,  F_NONE),
    ("Alarm",          "Neutral",    P_ALARM,       G_NONE,  F_NONE),
    ("Calendar",       "Neutral",    P_CALENDAR,    G_NONE,  F_NONE),
    ("StandbyClock",   "Blank",      P_CLOCK,       G_NONE,  F_NONE),
    ("DrawingMode",    "Bored",      P_PENCIL,      G_NONE,  F_NONE),
    ("InvalidCommand", "Dead",       P_WARNING,     G_NONE,  F_NONE),
    ("SystemSecured",  "Blank",      P_CURTAINS,    G_NONE,  F_NONE),
]

# ================================================================= EYE ACTS
# What the eyes are *doing*, independent of which shape they are.
# (name, [(frame, scaleX, scaleY)], loop, eyeOverride)
# eyeOverride swaps the eye SHAPE: a LOOI squint is ^ ^, not a flattened
# circle, and closed eyes are a line you can still see.
EYE_ACTS = [
    ("Auto",     [], "loop", None),                                   # let the Blink layer run
    ("Squint",   [], "loop", V_ARC),
    ("Wide",     [(0, 1.12, 1.14)], "loop", None),
    ("Pop",      [(0, 1.0, 1.0), (8, 1.16, 0.40), (18, 0.94, 1.22),
                  (28, 1.04, 0.97), (40, 1.0, 1.0), (110, 1.0, 1.0)], "loop", None),
    ("Twinkle",  [(0, 1.0, 1.0), (12, 1.10, 1.10), (24, 0.95, 0.95),
                  (36, 1.07, 1.07), (48, 1.0, 1.0)], "loop", None),
    ("Closed",   [], "loop", V_BAR),
    ("SlowBlink", [(0, 1.0, 1.0), (60, 1.0, 1.0), (78, 1.0, 0.08),
                   (96, 1.0, 0.08), (118, 1.0, 1.0), (200, 1.0, 1.0)], "loop", None),
]

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



# ================================================================= SEQUENCES
# Every preset is a story, not a pose: at least ~5.5 s of beats that build the
# mood out of expression + prop + scene, with motion accents on top.
#
# beat  = (frame, faceName, prop, bg, fg)
# extra = (nodeKey, propertyKey, [(frame, value)])
#   nodeKey: "face" | "mouth" | "blink" | "blinkL" | "blinkR" | "prop:<idx>:<name>"

def osc(f0, f1, step, lo, hi):
    """Alternate lo/hi every `step` frames between f0 and f1."""
    out_ = []
    i = 0
    f = f0
    while f <= f1:
        out_.append((f, hi if i % 2 else lo))
        i += 1
        f += step
    return out_


def pulse(f0, f1, step, on=4):
    """A strobe: 0 -> 1 -> 0 every `step` frames (muzzle flashes)."""
    out_ = [(0, 0), (max(f0 - 1, 0), 0)]
    f = f0
    while f <= f1:
        out_.append((f, 1))
        out_.append((f + on, 0))
        f += step
    return out_


# extra motion appended to a preset's automatic story (same format as extras)
PRESET_EXTRAS = {
    # glance at the calendar in the corner
    "Calendar": [("blinkL", 13, [(0, 0), (150, 0), (175, 20), (340, 20)]),
                 ("blinkR", 13, [(0, 0), (150, 0), (175, 20), (340, 20)]),
                 ("blinkL", 14, [(0, 0), (150, 0), (175, -14), (340, -14)]),
                 ("blinkR", 14, [(0, 0), (150, 0), (175, -14), (340, -14)])],
    # look down at the drawing
    "DrawingMode": [("blinkL", 14, [(0, 0), (95, 0), (120, 16), (340, 16)]),
                    ("blinkR", 14, [(0, 0), (95, 0), (120, 16), (340, 16)])],
    # the alarm rattles the whole head
    "Alarm": [("face", 13, [(0, 0), (149, 0)] + osc(150, 330, 4, -5, 5) + [(340, 0)])],
}

LEADIN = {
    "Neutral": "Sleepy", "Happy": "Neutral", "Angry": "Determined", "Rage": "Angry",
    "Sleepy": "Tired", "Curious": "Neutral", "Wink": "Happy", "Dead": "Shock",
    "Laughing": "Happy", "Evil": "Smug", "Focused": "Determined", "Excited": "Shock",
    "Shy": "Neutral", "Shock": "Neutral", "Disgusted": "Confused", "Love": "Shy",
    "Crying": "Sad", "Sad": "Neutral", "Thinking": "Neutral", "Listening": "Curious",
    "Confused": "Curious", "Pout": "Sad", "Dizzy": "Shock", "Bored": "Neutral",
    "Scared": "Shock", "Tired": "Bored", "Blank": "Neutral", "Money": "Shock",
    "Grin": "Happy", "Smug": "Neutral", "Determined": "Focused", "Calm": "Neutral",
    "OneEye": "Neutral", "Recognized": "Neutral", "Starry": "Neutral",
}


def auto_story(name, face, prop, bg, fg):
    """The default arc every preset gets: settle -> scene -> expression -> prop
    -> overlay -> hold, with a small landing beat on the eyes and head."""
    lead = LEADIN.get(face, "Neutral")
    dur = 340
    # a face that hides an eye (Blank / OneEye, replaced by a visor or an icon) only
    # takes over when the prop that replaces the eyes arrives -- otherwise
    # the screen sits empty for a second
    land = 150 if face in ("Blank", "OneEye") else 95
    beats = [
        (0,    lead, P_NONE, G_NONE, F_NONE),
        (55,   lead, P_NONE, bg,     F_NONE),
        (land, face, P_NONE if land < 150 else prop, bg, F_NONE),
        (150,  face, prop,   bg,     F_NONE),
        (210,  face, prop,   bg,     fg),
        (dur,  face, prop,   bg,     fg),
    ]
    extra = [
        ("blink", 17, [(0, 1), (land - 9, 1), (land, .3), (land + 10, 1.1), (land + 19, 1), (dur, 1)]),
        ("face", 14, [(0, 0), (land, -9), (land + 21, 5), (land + 41, 0), (dur, 0)]),
        ("face", 16, [(0, .985), (land, 1.03), (land + 45, 1), (dur, 1)]),
        ("face", 17, [(0, .985), (land, 1.03), (land + 45, 1), (dur, 1)]),
    ] + PRESET_EXTRAS.get(name, [])
    return dur, beats, extra


ANGRY_SX = 0.88
ANGRY_CONV = round(max_converge(V_ANGRY, V_ANGRY, ANGRY_SX), 1)

# Hand-written stories that override the automatic arc, keyed by preset name.
HAND_STORIES = {
    # ง่วง: หลับๆตื่นๆ -> หลับ -> กรน
    "Sleepy": (480, [
        (0,   "Bored",  P_NONE,  G_NONE, F_NONE),
        (300, "Sleepy", P_ZZZ,   G_NONE, F_NONE),
        (390, "Sleepy", P_SNORE, G_NONE, F_NONE),
        (480, "Sleepy", P_SNORE, G_NONE, F_NONE),
    ], [
        ("blink", 17, [(0, 1), (42, .22), (74, .95), (112, .14), (148, .9),
                       (188, .1), (224, .85), (270, .07), (296, .07), (302, 1), (480, 1)]),
        ("face", 14, [(0, 0), (42, 15), (74, -3), (112, 19), (148, 0),
                      (188, 23), (224, 2), (270, 27), (300, 24), (480, 24)]),
        ("face", 15, [(0, 0), (148, .04), (270, .09), (480, .09)]),
    ]),

    # โกรธ: ขึงขัง -> ตาแดง -> ตาเฉียงเข้าหากัน -> ป้อมปืนขึ้น -> ยิงส่ายใส่จอ
    # the eyes narrow to 0.88 while closing in, which is what buys the room
    # to converge at all -- max_converge() keeps MIN_EYE_GAP between them.
    "Angry": (540, [
        (0,   "Determined", P_NONE,      G_NONE,     F_NONE),
        (90,  "Angry",      P_NONE,      G_NONE,     F_NONE),
        (150, "Angry",      P_ANGRYMARK, G_REDALERT, F_NONE),
        (240, "Rage",       P_TURRETS,   G_REDALERT, F_NONE),
        (310, "Rage",       P_TURRETS,   G_REDALERT, F_HOLES),
        (540, "Rage",       P_TURRETS,   G_REDALERT, F_HOLES),
    ], [
        # the eyes slide toward each other as the anger focuses
        ("blinkL", 13, [(0, 0), (150, 0), (215, ANGRY_CONV), (540, ANGRY_CONV)]),
        ("blinkR", 13, [(0, 0), (150, 0), (215, -ANGRY_CONV), (540, -ANGRY_CONV)]),
        ("blink", 16, [(0, 1), (90, .96), (215, ANGRY_SX), (540, ANGRY_SX)]),
        ("blinkL", 15, [(0, 0), (150, 0), (215, .10), (540, .10)]),
        ("blinkR", 15, [(0, 0), (150, 0), (215, -.10), (540, -.10)]),
        # turrets rise out of frame bottom, overshoot, settle
        ("prop:%d:TurretL" % P_TURRETS, 14,
         [(0, TURRET_BASE_Y + 170), (240, TURRET_BASE_Y + 170), (278, TURRET_BASE_Y - 8),
          (292, TURRET_BASE_Y + 6), (304, TURRET_BASE_Y), (540, TURRET_BASE_Y)]),
        ("prop:%d:TurretR" % P_TURRETS, 14,
         [(0, TURRET_BASE_Y + 170), (248, TURRET_BASE_Y + 170), (286, TURRET_BASE_Y - 8),
          (300, TURRET_BASE_Y + 6), (312, TURRET_BASE_Y), (540, TURRET_BASE_Y)]),
        # barrels sweep back and forth across the screen
        ("prop:%d:BarrelL" % P_TURRETS, 15,
         [(0, 0), (300, -.12)] + osc(330, 540, 34, -.12, .82)),
        ("prop:%d:BarrelR" % P_TURRETS, 15,
         [(0, 0), (300, .12)] + osc(330, 540, 34, .12, -.82)),
        # muzzle flashes
        ("prop:%d:FlashL" % P_TURRETS, 18, pulse(312, 536, 16)),
        ("prop:%d:FlashR" % P_TURRETS, 18, pulse(320, 536, 16)),
        # recoil shake once the guns open up
        ("face", 13, [(0, 0)] + osc(312, 540, 8, -4, 4)),
    ]),

    # ร้องไห้: เศร้า -> สะอื้น -> ร้องไห้ -> น้ำตาไหล
    "Crying": (560, [
        (0,   "Sad",    P_NONE,  G_NONE, F_NONE),
        (150, "Sad",    P_NONE,  G_NONE, F_NONE),
        (170, "Crying", P_NONE,  G_NONE, F_NONE),
        (300, "Crying", P_TEARS, G_NONE, F_NONE),
        (560, "Crying", P_TEARS, G_NONE, F_NONE),
    ], [
        ("blink", 17, [(0, 1), (150, 1), (162, .55), (174, 1), (186, .5), (198, 1),
                       (300, 1), (560, 1)]),
        ("face", 13, [(0, 0), (160, -5), (172, 5), (184, -5), (196, 0),
                      (330, -3), (350, 3), (370, 0), (560, 0)]),
    ]),

    # ร้องไห้จนน้ำท่วมจอ
    "CryFlood": (620, [
        (0,   "Sad",    P_NONE,  G_NONE,     F_NONE),
        (120, "Crying", P_NONE,  G_DEEPBLUE, F_NONE),
        (220, "Crying", P_TEARS, G_DEEPBLUE, F_NONE),
        (340, "Crying", P_TEARS, G_DEEPBLUE, F_WATER),
        (620, "Crying", P_TEARS, G_DEEPBLUE, F_WATER),
    ], [
        ("blink", 17, [(0, 1), (110, 1), (122, .5), (134, 1), (146, .45), (158, 1), (620, 1)]),
        ("face", 13, [(0, 0)] + osc(340, 620, 26, -4, 4)),
    ]),

    # โกรธยิงจรวด
    "AngryMissile": (520, [
        (0,   "Determined", P_NONE,      G_NONE,     F_NONE),
        (80,  "Angry",      P_ANGRYMARK, G_NONE,     F_NONE),
        (170, "Rage",       P_ANGRYMARK, G_REDALERT, F_NONE),
        (240, "Rage",       P_MISSILES,  G_REDALERT, F_NONE),
        (300, "Rage",       P_MISSILES,  G_REDALERT, F_EXPLOSION),
        (520, "Rage",       P_MISSILES,  G_REDALERT, F_EXPLOSION),
    ], [
        ("blinkL", 13, [(0, 0), (170, 0), (230, ANGRY_CONV), (520, ANGRY_CONV)]),
        ("blinkR", 13, [(0, 0), (170, 0), (230, -ANGRY_CONV), (520, -ANGRY_CONV)]),
        ("blink", 16, [(0, 1), (170, .98), (230, ANGRY_SX), (520, ANGRY_SX)]),
        ("face", 13, [(0, 0)] + osc(300, 520, 9, -5, 5)),
    ]),

    # สัมภาษณ์นักข่าว
    "Interview": (460, [
        (0,   "Neutral",  P_NONE, G_NONE,      F_NONE),
        (70,  "Shock",    P_NONE, G_SPOTLIGHT, F_NONE),
        (140, "Grin",     P_MIC,  G_SPOTLIGHT, F_NONE),
        (210, "Excited",  P_MIC,  G_SPOTLIGHT, F_LOWERTHIRD),
        (460, "Excited",  P_MIC,  G_SPOTLIGHT, F_LOWERTHIRD),
    ], [
        ("blink", 17, [(0, 1), (62, 1), (70, 1.22), (92, 1), (460, 1)]),
        ("face", 14, [(0, 0), (70, -12), (100, 2), (130, 0), (460, 0)]),
    ]),

    # พอร์ตแตก
    "PortBlown": (520, [
        (0,   "Neutral", P_NONE, G_NONE,      F_NONE),
        (70,  "Focused", P_NONE, G_CHARTDOWN, F_NONE),
        (150, "Shock",   P_NONE, G_CHARTDOWN, F_ARROWDOWN),
        (240, "Scared",  P_NONE, G_CHARTDOWN, F_REDFLASH),
        (320, "Dead",    P_NONE, G_SHATTER,   F_CRACKS),
        (520, "Dead",    P_NONE, G_SHATTER,   F_CRACKS),
    ], [
        ("blink", 16, [(0, 1), (150, 1.08), (240, .8), (320, 1), (520, 1)]),
        ("face", 14, [(0, 0), (150, -10), (240, 10), (320, 22), (520, 22)]),
        ("face", 13, [(0, 0)] + osc(320, 400, 7, -6, 6) + [(520, 0)]),
    ]),
}


# ================================================================= SCENE STORIES
# The storyboards in .obsidian-wiki/02_Components/Pet_Scene_Scripts.md, frame by
# frame (60 fps). Beats swap the face / prop / scene; extras move the eyes
# (blinkL / blinkR x,y = where the eyes look), the head (face) and every part
# of the scene prop ("prop:<index>:<mover>"). The app plays the sound cues.

def _sp(scene, mover):
    return "prop:%d:%s" % (P_NAMES.index(scene), mover)


def _look(keys, kfs):
    """The same gaze keyframes on both eyes."""
    return [(k, keys[0], kfs) for k in ("blinkL", "blinkR")]


def _both(key, kfs):
    return [("face", 16, kfs), ("face", 17, kfs)] if key == "facescale" else \
        [(n, key, kfs) for n in ("blinkL", "blinkR")]


def _scale(scene, mover, kfs):
    return [(_sp(scene, mover), 16, kfs), (_sp(scene, mover), 17, kfs)]


FOOD, DRINK, BATH, GAME, STUDY, RAIN = (P_NAMES.index(n) for n in
                                        ("SceneFood", "SceneDrink", "SceneBath", "SceneGame", "SceneStudy", "SceneRain"))
THUG, RICH, ROYAL, FIRE, THUNDER, SOUL = (P_NAMES.index(n) for n in
                                          ("SceneThug", "SceneRich", "SceneRoyal", "SceneFire", "SceneThunder", "SceneSoul"))
LOVE, CRY, PARTY, VR, MUSIC = (P_NAMES.index(n) for n in ("SceneLove", "SceneCry", "SceneParty", "SceneVR", "SceneMusic"))

SCENE_STORIES = {}

# 1. EATING ----------------------------------------------------------------
SCENE_STORIES["SceneEating"] = (480, [
    (0, "Neutral", FOOD, G_NONE, F_NONE),
    (84, "Starry", FOOD, G_NONE, F_NONE),
    (144, "Happy", FOOD, G_NONE, F_NONE),
    (336, "Love", FOOD, G_NONE, F_NONE),
    (396, "Happy", FOOD, G_NONE, F_NONE),
    (480, "Happy", FOOD, G_NONE, F_NONE),
], [
    ("blinkL", 14, [(0, 0), (36, 0), (54, 20), (84, 20), (100, 0), (480, 0)]),
    ("blinkR", 14, [(0, 0), (36, 0), (54, 20), (84, 20), (100, 0), (480, 0)]),
    ("face", 14, [(0, 0), (36, 0), (54, 8), (84, -10), (96, 4), (108, 0), (174, 0), (186, 10), (198, 0),
                  (246, 10), (258, 0), (306, 10), (318, 0), (480, 0)]),
    ("blink", 17, [(0, 1), (174, 1), (180, .55), (192, 1), (240, 1), (246, .55), (258, 1), (300, 1), (306, .55),
                   (318, 1), (480, 1)]),
    ("face", 15, [(0, 0), (396, 0), (420, .06), (444, -.06), (468, .04), (480, 0)]),
    (_sp("SceneFood", "Plate"), 14, [(0, 190), (36, 190), (72, -10), (84, 0), (396, 0), (440, 200), (480, 200)]),
    (_sp("SceneFood", "Food"), 14, [(0, 0), (144, 0), (170, -60), (480, -60)]),
    (_sp("SceneFood", "Food"), 16, [(0, 1), (84, 1), (94, 1.25), (106, .95), (116, 1), (174, 1), (186, .66), (246, .66),
                                    (258, .33), (306, .33), (318, 0), (480, 0)]),
    (_sp("SceneFood", "Food"), 17, [(0, 1), (84, 1), (94, 1.25), (106, .95), (116, 1), (174, 1), (186, .66), (246, .66),
                                    (258, .33), (306, .33), (318, 0), (480, 0)]),
    (_sp("SceneFood", "Crumbs"), 18, [(0, 0), (180, 0), (186, 1), (210, 0), (252, 0), (258, 1), (282, 0), (312, 0),
                                      (318, 1), (342, 0), (480, 0)]),
    (_sp("SceneFood", "Crumbs"), 14, [(0, 0), (186, 0), (210, 34), (252, 0), (258, 0), (282, 34), (312, 0), (318, 0),
                                      (342, 34), (480, 34)]),
    (_sp("SceneFood", "Hearts"), 18, [(0, 0), (330, 0), (342, 1), (420, 1), (450, 0), (480, 0)]),
    (_sp("SceneFood", "Hearts"), 14, [(0, 20), (336, 20), (450, -60), (480, -60)]),
    (_sp("SceneFood", "Sparkles"), 18, [(0, 0), (80, 0), (88, 1), (136, 0), (480, 0)]),
])

# 2. DRINKING --------------------------------------------------------------
SCENE_STORIES["SceneDrinking"] = (450, [
    (0, "Neutral", DRINK, G_NONE, F_NONE),
    (78, "Excited", DRINK, G_NONE, F_NONE),
    (120, "Happy", DRINK, G_NONE, F_NONE),
    (300, "Wink", DRINK, G_NONE, F_NONE),
    (348, "Calm", DRINK, G_NONE, F_NONE),
    (450, "Calm", DRINK, G_NONE, F_NONE),
], [
    ("blinkL", 13, [(0, 0), (24, 0), (40, 18), (78, 18), (92, 0), (450, 0)]),
    ("blinkR", 13, [(0, 0), (24, 0), (40, 18), (78, 18), (92, 0), (450, 0)]),
    ("blinkL", 14, [(0, 0), (24, 0), (40, 16), (78, 16), (92, 0), (450, 0)]),
    ("blinkR", 14, [(0, 0), (24, 0), (40, 16), (78, 16), (92, 0), (450, 0)]),
    ("face", 14, [(0, 0), (120, 0), (140, -12), (276, -12), (296, 0), (450, 0)]),
    ("face", 15, [(0, 0), (348, 0), (378, .05), (408, -.05), (438, .03), (450, 0)]),
    (_sp("SceneDrink", "Cup"), 13, [(0, 280), (30, 280), (66, 0), (110, -60), (276, -60), (300, 0), (348, 0), (420, 190), (450, 190)]),
    (_sp("SceneDrink", "Cup"), 14, [(0, 220), (30, 220), (66, 0), (110, -40), (276, -40), (300, 0), (348, 0), (420, 110), (450, 110)]),
    (_sp("SceneDrink", "Cup"), 15, [(0, 0), (78, 0), (110, -.45), (276, -.45), (300, 0), (450, 0)]),
] + _scale("SceneDrink", "Cup", [(0, 1.35), (450, 1.35)]) + [
    (_sp("SceneDrink", "Sparkles"), 18, [(0, 0), (296, 0), (304, 1), (348, 0), (450, 0)]),
])

# 3. BATH ------------------------------------------------------------------
SCENE_STORIES["SceneBath"] = (480, [
    (0, "Neutral", BATH, G_NONE, F_NONE),
    (72, "Sleepy", BATH, G_NONE, F_NONE),
    (120, "Laughing", BATH, G_NONE, F_NONE),
    (300, "Excited", BATH, G_NONE, F_NONE),
    (360, "Happy", BATH, G_NONE, F_NONE),
    (480, "Happy", BATH, G_NONE, F_NONE),
], [
    ("blinkL", 14, [(0, 0), (24, 0), (36, -20), (66, -20), (72, 0), (480, 0)]),
    ("blinkR", 14, [(0, 0), (24, 0), (36, -20), (66, -20), (72, 0), (480, 0)]),
    ("face", 13, [(0, 0), (119, 0)] + osc(120, 270, 15, -10, 10) + [(290, 0), (359, 0)] + osc(360, 468, 12, -14, 14) + [(480, 0)]),
    ("face", 14, [(0, 0), (72, 0), (78, 12), (96, 0), (480, 0)]),
    (_sp("SceneBath", "Shower"), 14, [(0, -150), (24, -150), (56, 0), (420, 0), (470, -160), (480, -160)]),
    (_sp("SceneBath", "Water"), 18, [(0, 0), (66, 0), (72, 1), (290, 1), (300, 0), (480, 0)]),
    (_sp("SceneBath", "Water"), 14, [(0, 0), (72, 0)] + osc(72, 290, 10, 0, 16) + [(300, 0)]),
] + _scale("SceneBath", "Bubbles", [(0, 0), (118, 0), (150, 1.1), (160, 1), (290, 1), (300, 1.3), (306, 0), (480, 0)]) + [
    (_sp("SceneBath", "Sponge"), 13, [(0, 0), (119, 0)] + osc(120, 280, 20, -80, 80) + [(300, 0)]),
    (_sp("SceneBath", "Sponge"), 18, [(0, 0), (110, 0), (120, 1), (282, 1), (296, 0), (480, 0)]),
    (_sp("SceneBath", "Clean"), 18, [(0, 0), (296, 0), (304, 1), (360, 0), (480, 0)]),
    (_sp("SceneBath", "Duck"), 13, [(0, -420), (360, -420), (470, 120), (480, 120)], "linear"),
    (_sp("SceneBath", "Duck"), 18, [(0, 0), (356, 0), (362, 1), (480, 1)]),
])

# 4. GAMING ----------------------------------------------------------------
SCENE_STORIES["SceneGaming"] = (510, [
    (0, "Neutral", GAME, G_NONE, F_NONE),
    (72, "Determined", GAME, G_NONE, F_NONE),
    (324, "Shock", GAME, G_NONE, F_NONE),
    (372, "Starry", GAME, G_NONE, F_CONFETTI),
    (432, "Happy", GAME, G_NONE, F_CONFETTI),
    (510, "Happy", GAME, G_NONE, F_CONFETTI),
], [
    ("blinkL", 14, [(0, 0), (30, 0), (44, 22), (70, 22), (84, -10), (324, -10), (336, 0), (510, 0)]),
    ("blinkR", 14, [(0, 0), (30, 0), (44, 22), (70, 22), (84, -10), (324, -10), (336, 0), (510, 0)]),
    ("blinkL", 13, [(0, 0), (120, 0), (140, -18), (170, 18), (200, -14), (230, 20), (260, -18), (290, 16), (310, 0), (510, 0)]),
    ("blinkR", 13, [(0, 0), (120, 0), (140, -18), (170, 18), (200, -14), (230, 20), (260, -18), (290, 16), (310, 0), (510, 0)]),
    ("face", 15, [(0, 0), (120, 0), (140, -.05), (170, .05), (200, -.04), (230, .05), (260, -.05), (290, .04), (310, 0), (510, 0)]),
    ("face", 14, [(0, 0), (324, 0), (330, 12), (344, 0), (372, 0), (384, -26), (400, 4), (414, 0), (510, 0)]),
    (_sp("SceneGame", "Controller"), 14, [(0, 170), (30, 170), (66, 0), (510, 0)]),
    (_sp("SceneGame", "Controller"), 15, [(0, 0), (119, 0)] + osc(120, 300, 10, -.08, .08) + [(310, 0), (510, 0)]),
] + _scale("SceneGame", "Screen", [(0, 0), (66, 0), (84, 1.08), (96, 1), (510, 1)]) + [
    (_sp("SceneGame", "Enemy"), 13, [(0, 90), (296, 90), (330, -90), (510, -90)], "linear"),
    (_sp("SceneGame", "Enemy"), 18, [(0, 0), (292, 0), (298, 1), (336, 1), (342, 0), (510, 0)]),
] + _scale("SceneGame", "Win", [(0, 0), (370, 0), (384, 1.2), (396, 1), (510, 1)]))

# 5. STUDY / WORK ----------------------------------------------------------
SCENE_STORIES["SceneStudy"] = (480, [
    (0, "Neutral", STUDY, G_NONE, F_NONE),
    (78, "Focused", STUDY, G_NONE, F_NONE),
    (258, "Thinking", STUDY, G_NONE, F_NONE),
    (318, "Shock", STUDY, G_NONE, F_NONE),
    (360, "Happy", STUDY, G_NONE, F_NONE),
    (480, "Happy", STUDY, G_NONE, F_NONE),
], [
    ("blinkL", 14, [(0, 0), (30, 0), (44, 20), (78, 20), (90, 14), (250, 14), (262, -18), (312, -18), (320, 0), (480, 0)]),
    ("blinkR", 14, [(0, 0), (30, 0), (44, 20), (78, 20), (90, 14), (250, 14), (262, -18), (312, -18), (320, 0), (480, 0)]),
    ("blinkL", 13, [(0, 0), (78, 0), (84, -18), (132, 18), (138, -18), (186, 18), (192, -18), (240, 18), (252, 0),
                    (262, 16), (312, 16), (320, 0), (480, 0)]),
    ("blinkR", 13, [(0, 0), (78, 0), (84, -18), (132, 18), (138, -18), (186, 18), (192, -18), (240, 18), (252, 0),
                    (262, 16), (312, 16), (320, 0), (480, 0)]),
    ("face", 14, [(0, 0), (318, 0), (324, -10), (336, 0), (360, 0), (372, 10), (384, 0), (396, 10), (408, 0), (480, 0)]),
    (_sp("SceneStudy", "Desk"), 14, [(0, 170), (30, 170), (70, 0), (480, 0)]),
    (_sp("SceneStudy", "Lines"), 14, [(0, 0), (78, 0), (250, -26), (480, -26)], "linear"),
    (_sp("SceneStudy", "Dots"), 18, [(0, 0), (252, 0), (260, 1), (312, 1), (318, 0), (480, 0)]),
] + _scale("SceneStudy", "Bulb", [(0, 0), (316, 0), (328, 1.25), (340, 1), (480, 1)])
  + _scale("SceneStudy", "Check", [(0, 0), (360, 0), (372, 1.3), (384, 1), (480, 1)]))

# 6. RAIN / UMBRELLA -------------------------------------------------------
SCENE_STORIES["SceneRain"] = (480, [
    (0, "Neutral", RAIN, G_DEEPBLUE, F_NONE),
    (84, "Sleepy", RAIN, G_DEEPBLUE, F_NONE),
    (144, "Sad", RAIN, G_DEEPBLUE, F_NONE),
    (204, "Shock", RAIN, G_DEEPBLUE, F_NONE),
    (252, "Happy", RAIN, G_DEEPBLUE, F_NONE),
    (480, "Happy", RAIN, G_NONE, F_NONE),
], [
    ("blinkL", 14, [(0, 0), (24, 0), (40, -20), (84, -20), (96, 0), (480, 0)]),
    ("blinkR", 14, [(0, 0), (24, 0), (40, -20), (84, -20), (96, 0), (480, 0)]),
    ("face", 14, [(0, 0), (84, 0), (92, 14), (110, 6), (204, 6), (212, -8), (230, 0), (480, 0)]),
    ("face", 15, [(0, 0), (251, 0)] + osc(252, 408, 26, -.05, .05) + [(420, 0), (480, 0)]),
    (_sp("SceneRain", "Cloud"), 13, [(0, -380), (24, -380), (70, 0), (420, 0), (470, 400), (480, 400)]),
    (_sp("SceneRain", "Drops"), 18, [(0, 0), (80, 0), (86, 1), (410, 1), (420, 0), (480, 0)]),
    (_sp("SceneRain", "HeadDrop"), 18, [(0, 0), (140, 0), (146, 1), (200, 1), (210, 0), (480, 0)]),
    (_sp("SceneRain", "HeadDrop"), 14, [(0, 0), (140, 0), (200, 30), (480, 30)]),
    (_sp("SceneRain", "Umbrella"), 14, [(0, 640), (200, 640), (232, 0), (480, 0)]),
    (_sp("SceneRain", "Canopy"), 16, [(0, .15), (232, .15), (250, 1.1), (260, 1), (480, 1)]),
    (_sp("SceneRain", "Rainbow"), 18, [(0, 0), (420, 0), (446, .8), (480, .8)]),
])

# 7. THUG LIFE -------------------------------------------------------------
SCENE_STORIES["SceneThug"] = (450, [
    (0, "Neutral", THUG, G_NONE, F_NONE),
    (30, "Smug", THUG, G_NONE, F_NONE),
    (450, "Smug", THUG, G_NONE, F_NONE),
], [
    ("blinkL", 14, [(0, 0), (30, 0), (44, -14), (140, -14), (150, 0), (450, 0)]),
    ("blinkR", 14, [(0, 0), (30, 0), (44, -14), (140, -14), (150, 0), (450, 0)]),
    ("face", 14, [(0, 0), (180, 0), (200, 12), (220, 0), (240, 12), (260, 0), (450, 0)]),
    ("face", 15, [(0, 0), (251, 0)] + osc(252, 444, 48, -.04, .04) + [(450, 0)]),
    (_sp("SceneThug", "Shades"), 14, [(0, -340), (72, -340), (144, -14), (152, 6), (162, 0), (450, 0)], "easeIn"),
    (_sp("SceneThug", "Chain"), 14, [(0, -560), (170, -560), (196, 0), (206, -10), (216, 0), (450, 0)]),
    (_sp("SceneThug", "Spot"), 18, [(0, 0), (250, 0), (272, 1), (450, 1)]),
])

# 8. RICH ------------------------------------------------------------------
SCENE_STORIES["SceneRich"] = (480, [
    (0, "Neutral", RICH, G_NONE, F_NONE),
    (36, "Confused", RICH, G_NONE, F_NONE),
    (84, "Shock", RICH, G_NONE, F_NONE),
    (144, "Money", RICH, G_GOLDGLOW, F_NONE),
    (192, "Money", RICH, G_GOLDGLOW, F_MONEY),
    (480, "Money", RICH, G_GOLDGLOW, F_MONEY),
], [
    ("blinkL", 14, [(0, 0), (30, 0), (40, -18), (84, -18), (96, 0), (480, 0)]),
    ("blinkR", 14, [(0, 0), (30, 0), (40, -18), (84, -18), (96, 0), (480, 0)]),
    ("blinkL", 13, [(0, 0), (191, 0)] + osc(192, 372, 30, -14, 14) + [(390, 0), (480, 0)]),
    ("blinkR", 13, [(0, 0), (191, 0)] + osc(192, 372, 30, -14, 14) + [(390, 0), (480, 0)]),
    ("face", 13, [(0, 0), (191, 0)] + osc(192, 372, 24, -10, 10) + [(390, 0), (480, 0)]),
    ("face", 14, [(0, 0), (144, 0), (154, -18), (166, 4), (176, 0), (480, 0)]),
    (_sp("SceneRich", "Coin0"), 14, [(0, -520), (36, -520), (70, 0), (78, -12), (86, 0), (480, 0)], "easeIn"),
    (_sp("SceneRich", "Coin1"), 14, [(0, -520), (84, -520), (116, 0), (124, -12), (132, 0), (480, 0)], "easeIn"),
    (_sp("SceneRich", "Coin2"), 14, [(0, -520), (96, -520), (128, 0), (136, -12), (144, 0), (480, 0)], "easeIn"),
    (_sp("SceneRich", "Gold"), 14, [(0, 160), (200, 160), (260, 0), (480, 0)]),
    (_sp("SceneRich", "Sparkles"), 16, [(0, 0), (386, 0), (396, 1), (480, 1)]),
])

# 9. ROYAL -----------------------------------------------------------------
SCENE_STORIES["SceneRoyal"] = (480, [
    (0, "Neutral", ROYAL, G_NONE, F_NONE),
    (36, "Listening", ROYAL, G_SPOTLIGHT, F_NONE),
    (84, "Starry", ROYAL, G_SPOTLIGHT, F_NONE),
    (176, "Happy", ROYAL, G_SPOTLIGHT, F_NONE),
    (216, "Smug", ROYAL, G_SPOTLIGHT, F_NONE),
    (480, "Smug", ROYAL, G_SPOTLIGHT, F_NONE),
], [
    ("blinkL", 14, [(0, 0), (30, 0), (44, -20), (170, -20), (180, 0), (480, 0)]),
    ("blinkR", 14, [(0, 0), (30, 0), (44, -20), (170, -20), (180, 0), (480, 0)]),
    ("face", 14, [(0, 0), (170, 0), (186, 14), (206, 0), (276, -8), (480, -8)]),
    ("face", 15, [(0, 0), (275, 0)] + osc(276, 470, 40, -.03, .03) + [(480, 0)]),
    (_sp("SceneRoyal", "Crown"), 14, [(0, -260), (84, -260), (176, 0), (186, 8), (196, 0), (480, 0)], "soft"),
    (_sp("SceneRoyal", "Crown"), 15, [(0, -.6), (84, -.6), (176, 0), (480, 0)]),
    (_sp("SceneRoyal", "Cape"), 16, [(0, 0), (210, 0), (236, 1.1), (246, 1), (480, 1)]),
    (_sp("SceneRoyal", "Petals"), 18, [(0, 0), (270, 0), (290, 1), (480, 1)]),
    (_sp("SceneRoyal", "Sparkles"), 18, [(0, 0), (210, 0), (220, 1), (480, 1)]),
])

# 10. FIRE -----------------------------------------------------------------
_run = osc(140, 310, 14, -60, 60)
SCENE_STORIES["SceneFire"] = (450, [
    (0, "Neutral", FIRE, G_NONE, F_NONE),
    (48, "Confused", FIRE, G_NONE, F_NONE),
    (96, "Shock", FIRE, G_NONE, F_NONE),
    (132, "Dead", FIRE, G_REDALERT, F_NONE),
    (324, "Happy", FIRE, G_NONE, F_NONE),
    (372, "Dizzy", FIRE, G_NONE, F_SMOKE),
    (450, "Dizzy", FIRE, G_NONE, F_SMOKE),
], [
    ("blinkL", 14, [(0, 0), (40, 0), (56, 22), (96, 22), (106, 0), (450, 0)]),
    ("blinkR", 14, [(0, 0), (40, 0), (56, 22), (96, 22), (106, 0), (450, 0)]),
    ("face", 13, [(0, 0), (139, 0)] + _run + [(324, 0), (450, 0)]),
    ("face", 14, [(0, 0), (139, 0)] + osc(140, 310, 7, 0, -10) + [(324, 0), (450, 0)]),
    (_sp("SceneFire", "Flames"), 13, [(0, 0), (139, 0)] + _run + [(324, 0), (450, 0)]),
] + _scale("SceneFire", "Flames", [(0, 0), (44, 0), (50, .2), (60, .5), (96, .5), (110, 1.2), (310, 1.2), (330, 0), (450, 0)]) + [
    (_sp("SceneFire", "Sweat"), 18, [(0, 0), (130, 0), (136, 1), (316, 1), (324, 0), (450, 0)]),
    (_sp("SceneFire", "Bucket"), 14, [(0, -160), (300, -160), (320, 0), (372, 0), (400, -180), (450, -180)]),
    (_sp("SceneFire", "Bucket"), 15, [(0, 0), (300, 0), (318, 1.9), (372, 1.9), (400, 0), (450, 0)]),
    (_sp("SceneFire", "Splash"), 18, [(0, 0), (318, 0), (324, 1), (360, 0), (450, 0)]),
    (_sp("SceneFire", "Splash"), 14, [(0, 0), (318, 0), (360, 80), (450, 80)]),
    (_sp("SceneFire", "Soot"), 18, [(0, 0), (368, 0), (380, 1), (450, 1)]),
])

# 11. THUNDER --------------------------------------------------------------
SCENE_STORIES["SceneThunder"] = (450, [
    (0, "Neutral", THUNDER, G_NONE, F_NONE),
    (36, "Neutral", THUNDER, G_DEEPBLUE, F_NONE),
    (96, "Confused", THUNDER, G_DEEPBLUE, F_NONE),
    (144, "Shock", THUNDER, G_DEEPBLUE, F_NONE),
    (168, "Dizzy", THUNDER, G_DEEPBLUE, F_NONE),
    (450, "Dizzy", THUNDER, G_NONE, F_NONE),
], [
    ("blinkL", 14, [(0, 0), (30, 0), (44, -20), (144, -20), (150, 0), (450, 0)]),
    ("blinkR", 14, [(0, 0), (30, 0), (44, -20), (144, -20), (150, 0), (450, 0)]),
    ("face", 13, [(0, 0), (167, 0)] + osc(168, 276, 3, -7, 7) + [(284, 0), (450, 0)]),
    ("face", 15, [(0, 0), (299, 0)] + osc(300, 440, 40, -.06, .06) + [(450, 0)]),
    ("face", 14, [(0, 0), (144, 0), (150, 18), (176, 8), (450, 8)]),
    (_sp("SceneThunder", "Cloud"), 13, [(0, -400), (30, -400), (80, 0), (450, 0)]),
    (_sp("SceneThunder", "Glow"), 18, [(0, 0), (96, 0), (100, 1), (106, 0), (112, 1), (118, 0), (140, 0), (144, 1), (160, 0), (450, 0)], "hold"),
    (_sp("SceneThunder", "Bolt"), 17, [(0, 0), (140, 0), (146, 1), (162, 1), (170, 0), (450, 0)]),
    (_sp("SceneThunder", "Flash"), 18, [(0, 0), (144, 0), (146, .9), (162, 0), (450, 0)]),
    (_sp("SceneThunder", "Sparks"), 18, [(0, 0), (160, 0), (166, 1), (280, 1), (290, 0), (450, 0)]),
    (_sp("SceneThunder", "Soot"), 18, [(0, 0), (296, 0), (312, 1), (450, 1)]),
])

# 12. SOUL OUT -------------------------------------------------------------
SCENE_STORIES["SceneSoulOut"] = (480, [
    (0, "Tired", SOUL, G_NONE, F_NONE),
    (60, "Bored", SOUL, G_NONE, F_NONE),
    (120, "Dead", SOUL, G_NONE, F_NONE),
    (324, "Shock", SOUL, G_NONE, F_NONE),
    (372, "Dizzy", SOUL, G_NONE, F_NONE),
    (480, "Dizzy", SOUL, G_NONE, F_NONE),
], [
    ("face", 14, [(0, 0), (60, 0), (110, 20), (324, 20), (330, -12), (344, 0), (480, 0)]),
    ("face", 15, [(0, 0), (60, 0), (110, .08), (324, .08), (334, 0), (480, 0)]),
    ("blink", 17, [(0, 1), (60, 1), (90, .6), (120, 1), (480, 1)]),
    (_sp("SceneSoul", "Sweat"), 18, [(0, 0), (56, 0), (62, 1), (120, 1), (130, 0), (480, 0)]),
    (_sp("SceneSoul", "Sweat"), 14, [(0, 0), (56, 0), (120, 34), (480, 34)]),
    (_sp("SceneSoul", "Soul"), 14, [(0, 40), (120, 40), (300, -90), (324, -90), (340, 40), (480, 40)]),
    (_sp("SceneSoul", "Soul"), 18, [(0, 0), (118, 0), (132, .95), (330, .95), (344, 0), (480, 0)]),
    (_sp("SceneSoul", "Soul"), 13, [(0, 0), (129, 0)] + osc(130, 320, 32, -22, 22) + [(330, 0), (480, 0)]),
] + _scale("SceneSoul", "Soul", [(0, 1), (324, 1), (344, .2), (480, .2)]) + [
    (_sp("SceneSoul", "Stars"), 18, [(0, 0), (370, 0), (380, 1), (480, 1)]),
])

# 14. SUPER LOVE -----------------------------------------------------------
_beat = [(0, 0), (126, 0), (140, 1)] + osc(150, 378, 15, 1, 1.15) + [(390, 1.4), (398, 0), (480, 0)]
SCENE_STORIES["SceneSuperLove"] = (480, [
    (0, "Neutral", LOVE, G_NONE, F_NONE),
    (36, "Shy", LOVE, G_NONE, F_NONE),
    (84, "Listening", LOVE, G_NONE, F_NONE),
    (132, "Love", LOVE, G_BOKEH, F_NONE),
    (384, "Happy", LOVE, G_BOKEH, F_NONE),
    (480, "Happy", LOVE, G_BOKEH, F_NONE),
], [
    ("blinkL", 13, [(0, 0), (30, 0), (44, 14), (80, 14), (90, 0), (480, 0)]),
    ("blinkR", 13, [(0, 0), (30, 0), (44, 14), (80, 14), (90, 0), (480, 0)]),
    ("blinkL", 14, [(0, 0), (30, 0), (44, 16), (80, 16), (90, -16), (128, -16), (136, 0), (480, 0)]),
    ("blinkR", 14, [(0, 0), (30, 0), (44, 16), (80, 16), (90, -16), (128, -16), (136, 0), (480, 0)]),
    ("blink", 16, [(0, 1), (131, 1)] + osc(132, 372, 15, 1, 1.08) + [(384, 1), (480, 1)]),
    ("blink", 17, [(0, 1), (131, 1)] + osc(132, 372, 15, 1, 1.12) + [(384, 1), (480, 1)]),
    ("face", 15, [(0, 0), (167, 0)] + osc(168, 372, 36, -.05, .05) + [(384, 0), (480, 0)]),
    (_sp("SceneLove", "Blush"), 18, [(0, 0), (30, 0), (42, 1), (132, 1), (150, 0), (480, 0)]),
    (_sp("SceneLove", "Arrow"), 13, [(0, -440), (84, -440), (112, 0), (480, 0)], "easeIn"),
    (_sp("SceneLove", "Arrow"), 18, [(0, 1), (378, 1), (396, 0), (480, 0)]),
] + _scale("SceneLove", "BigHeart", _beat) + [
    (_sp("SceneLove", "Small"), 18, [(0, 0), (160, 0), (176, 1), (380, 1), (396, 0), (480, 0)]),
] + _scale("SceneLove", "Burst", [(0, 0), (392, 0), (400, .6), (440, 1.4), (460, 0), (480, 0)]))

# 15. DRAMATIC CRY ---------------------------------------------------------
SCENE_STORIES["SceneCry"] = (510, [
    (0, "Sad", CRY, G_DEEPBLUE, F_NONE),
    (108, "Shock", CRY, G_DEEPBLUE, F_NONE),
    (156, "Sad", CRY, G_DEEPBLUE, F_NONE),
    (240, "Crying", CRY, G_DEEPBLUE, F_NONE),
    (432, "Sad", CRY, G_DEEPBLUE, F_NONE),
    (510, "Sad", CRY, G_DEEPBLUE, F_NONE),
], [
    ("blink", 17, [(0, 1), (48, 1), (52, .85), (56, 1), (60, .85), (64, 1), (156, 1), (164, .4), (176, 1), (190, .4),
                   (202, 1), (510, 1)]),
    ("face", 14, [(0, 0), (156, 0), (164, 10), (176, 0), (190, 10), (202, 0), (510, 0)]),
    ("face", 13, [(0, 0), (239, 0)] + osc(240, 420, 20, -4, 4) + [(432, 0), (510, 0)]),
    (_sp("SceneCry", "HalfL"), 13, [(0, -360), (40, -360), (80, 0), (108, 0), (118, -26), (510, -26)]),
    (_sp("SceneCry", "HalfR"), 13, [(0, -360), (40, -360), (80, 0), (108, 0), (118, 26), (510, 26)]),
    (_sp("SceneCry", "HalfL"), 15, [(0, 0), (108, 0), (118, -.4), (200, -.9), (510, -.9)]),
    (_sp("SceneCry", "HalfR"), 15, [(0, 0), (108, 0), (118, .4), (200, .9), (510, .9)]),
    (_sp("SceneCry", "HalfL"), 14, [(0, 0), (118, 0), (210, 420), (510, 420)], "easeIn"),
    (_sp("SceneCry", "HalfR"), 14, [(0, 0), (118, 0), (210, 420), (510, 420)], "easeIn"),
    (_sp("SceneCry", "HalfL"), 18, [(0, 1), (196, 1), (212, 0), (510, 0)]),
    (_sp("SceneCry", "HalfR"), 18, [(0, 1), (196, 1), (212, 0), (510, 0)]),
    (_sp("SceneCry", "Crack"), 18, [(0, 0), (96, 0), (100, 1), (110, 1), (114, 0), (510, 0)]),
    (_sp("SceneCry", "Cloud"), 14, [(0, -160), (160, -160), (196, 0), (510, 0)]),
    (_sp("SceneCry", "Drops"), 18, [(0, 0), (240, 0), (250, 1), (420, 1), (432, 0), (510, 0)]),
    (_sp("SceneCry", "Tissue"), 13, [(0, 160), (430, 160), (470, 0), (510, 0)]),
    (_sp("SceneCry", "Tissue"), 15, [(0, 0), (470, 0), (480, .15), (490, -.15), (500, .1), (510, 0)]),
])

# 16. CELEBRATION ----------------------------------------------------------
SCENE_STORIES["SceneCelebrate"] = (510, [
    (0, "Neutral", PARTY, G_NONE, F_NONE),
    (96, "Excited", PARTY, G_NONE, F_NONE),
    (144, "Laughing", PARTY, G_BOKEH, F_CONFETTI),
    (180, "Starry", PARTY, G_BOKEH, F_CONFETTI),
    (408, "Happy", PARTY, G_BOKEH, F_CONFETTI),
    (510, "Happy", PARTY, G_BOKEH, F_CONFETTI),
], [
    ("blinkL", 14, [(0, 0), (30, 0), (40, -20), (66, -20), (72, 0), (96, 20), (140, 20), (148, 0), (510, 0)]),
    ("blinkR", 14, [(0, 0), (30, 0), (40, -20), (66, -20), (72, 0), (96, 20), (140, 20), (148, 0), (510, 0)]),
    ("blinkL", 13, [(0, 0), (96, 0), (106, -18), (140, -18), (148, 0), (510, 0)]),
    ("blinkR", 13, [(0, 0), (96, 0), (106, -18), (140, -18), (148, 0), (510, 0)]),
    ("face", 14, [(0, 0), (179, 0)] + osc(180, 384, 30, 0, -22) + [(400, 0), (510, 0)]),
    (_sp("SceneParty", "Hat"), 14, [(0, -260), (36, -260), (64, 0), (70, 8), (76, 0), (510, 0)]),
    (_sp("SceneParty", "Hat"), 15, [(0, .6), (36, .6), (64, -.12), (510, -.12)]),
    (_sp("SceneParty", "Popper"), 14, [(0, 170), (90, 170), (120, 0), (144, 0), (150, 14), (160, 0), (510, 0)]),
] + _scale("SceneParty", "Burst", [(0, 0), (140, 0), (150, 1.3), (176, 1.7), (510, 1.7)]) + [
    (_sp("SceneParty", "Burst"), 18, [(0, 0), (140, 0), (144, 1), (182, 0), (510, 0)]),
    (_sp("SceneParty", "Balloons"), 14, [(0, 120), (180, 120), (420, -300), (500, -600), (510, -600)], "linear"),
])

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

# 18. MUSIC ----------------------------------------------------------------
SCENE_STORIES["SceneMusic"] = (510, [
    (0, "Neutral", MUSIC, G_NONE, F_NONE),
    (96, "Happy", MUSIC, G_NONE, F_NONE),
    (510, "Happy", MUSIC, G_NONE, F_NONE),
], [
    ("blinkL", 14, [(0, 0), (30, 0), (44, -20), (90, -20), (96, 0), (510, 0)]),
    ("blinkR", 14, [(0, 0), (30, 0), (44, -20), (90, -20), (96, 0), (510, 0)]),
    ("face", 15, [(0, 0), (155, 0)] + osc(156, 456, 30, -.08, .08) + [(468, 0), (510, 0)]),
    ("face", 13, [(0, 0), (155, 0)] + osc(156, 456, 30, -14, 14) + [(468, 0), (510, 0)]),
    ("blink", 17, [(0, 1), (155, 1)] + osc(156, 456, 15, 1, .45) + [(468, 1), (510, 1)]),
    (_sp("SceneMusic", "Phones"), 14, [(0, -320), (36, -320), (90, 0), (96, 8), (104, 0), (510, 0)]),
    (_sp("SceneMusic", "EQ"), 18, [(0, 0), (128, 0), (136, 1), (468, 1), (480, 0), (510, 0)]),
    (_sp("SceneMusic", "Notes"), 18, [(0, 0), (150, 0), (166, 1), (456, 1), (480, 0), (510, 0)]),
])

HAND_STORIES.update(SCENE_STORIES)

(MOOD_BALL, MOOD_YOYO, MOOD_STARS, MOOD_SHOCK, MOOD_SAD, MOOD_CURIOUS, MOOD_MISSILE, MOOD_FUME,
 MOOD_GLITCH, MOOD_THINK, MOOD_HAPPY, MOOD_LOVE, MOOD_GLAD, MOOD_COOL, MOOD_SHY, MOOD_EMBARRASSED,
 MOOD_SHOWOFF, MOOD_LISTEN, MOOD_ARROGANT) = (P_NAMES.index(n) for n in MOOD_NAMES)

# ================================================================= MOOD STORIES
# The jelly-eye mood storyboards (.obsidian-wiki/02_Components/Pet_Mood_Scripts.md):
# 7 s each at 60 fps. Only blinkL / blinkR key the eyes here (never "blink"),
# so each eye can squash, stretch and wander on its own.
MD = 420


def _mp(scene, mover):
    return "prop:%d:%s" % (P_NAMES.index(scene), mover)


def _eyes(key, kl, kr=None):
    """Keyframes for both eyes; kr defaults to kl (mirror x / rotation by hand)."""
    return [("blinkL", key, kl), ("blinkR", key, kl if kr is None else kr)]


def _neg(kfs):
    return [(f, -v) for f, v in kfs]


def _circle(f0, f1, period, r, step=10):
    xs, ys = [], []
    f = f0
    while f <= f1:
        a = 2 * math.pi * (f - f0) / period
        xs.append((f, round(r * math.sin(a), 2)))
        ys.append((f, round(-r * math.cos(a), 2)))
        f += step
    return xs, ys


def _pulse_op(frames, on=3, level=1.0):
    out_ = [(0, 0)]
    for f in frames:
        out_ += [(f, level), (f + on, 0)]
    return out_


def _sc(prop, mover, kfs, interp=None):
    t = [(_mp(prop, mover), 16, kfs), (_mp(prop, mover), 17, kfs)]
    return [e + (interp,) for e in t] if interp else t


MOOD_STORIES = {}

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

# 2. IDLE: YO-YO -----------------------------------------------------------
_str = [(0, 0), (30, 0), (100, 1), (180, 1)] + osc(192, 300, 12, .35, 1.25) + [(312, 1), (324, 0), (MD, 0)]
_yo_y = [(f, (s - 1) * 120) for f, s in _str if f < 312] + [(312, 0), (336, -170), (MD, -170)]
_bounce_y = osc(180, 300, 12, 26, 10)
MOOD_STORIES["MoodYoYo"] = (MD, [
    (0, "Neutral", MOOD_YOYO, G_NONE, F_NONE),
    (330, "Laughing", MOOD_YOYO, G_NONE, F_NONE),
    (372, "Happy", MOOD_YOYO, G_NONE, F_NONE),
    (MD, "Happy", MOOD_YOYO, G_NONE, F_NONE),
], [
    # the left eye holds the string; the right eye watches, twitching big-small
    ("blinkL", 14, [(0, 0), (30, 0), (60, 18), (170, 18)] + _bounce_y + [(312, 0), (MD, 0)]),
    ("blinkL", 17, [(0, 1), (30, 1), (60, 1.3), (100, 1.25), (170, 1.25)] + osc(180, 300, 12, 1.35, 1.15) +
     [(312, 1), (330, 1), (336, .35), (356, .35), (366, 1.25), (376, .92), (386, 1), (MD, 1)]),
    ("blinkR", 17, [(0, 1), (39, 1)] + osc(40, 170, 20, .85, 1.1) + [(180, 1)] + osc(190, 300, 12, 1.08, .9) +
     [(312, 1), (330, 1), (336, .35), (356, .35), (366, 1.25), (376, .92), (386, 1), (MD, 1)]),
    ("blinkR", 16, [(0, 1), (39, 1)] + osc(40, 170, 20, .9, 1.02) + [(180, 1), (330, 1), (336, 1.1), (356, 1.1),
                                                                     (366, .92), (376, 1.02), (386, 1), (MD, 1)]),
    ("blinkL", 16, [(0, 1), (330, 1), (336, 1.1), (356, 1.1), (366, .92), (376, 1.02), (386, 1), (MD, 1)]),
    ("blinkR", 13, [(0, 0), (40, -22), (300, -22), (320, 0), (MD, 0)]),
    ("blinkR", 14, [(0, 0), (40, 22), (170, 22)] + _bounce_y + [(312, 0), (324, -20), (340, 0), (MD, 0)]),
    ("face", 13, [(0, 0), (179, 0)] + osc(180, 300, 24, -6, 6) + [(310, 0), (MD, 0)]),
    ("face", 14, [(0, 0), (330, 0), (336, 12), (352, 0), (MD, 0)]),
    (_mp("MoodYoYo", "String"), 17, _str),
    (_mp("MoodYoYo", "String"), 18, [(0, 0), (30, 0), (34, 1), (312, 1), (322, 0), (MD, 0)]),
    (_mp("MoodYoYo", "Yo"), 14, _yo_y),
    (_mp("MoodYoYo", "Yo"), 13, [(0, 0), (312, 0), (336, 96), (MD, 96)]),
    (_mp("MoodYoYo", "Yo"), 15, [(0, 0), (180, 0), (300, 20), (336, 40), (MD, 40)], "linear"),
    (_mp("MoodYoYo", "Yo"), 18, [(0, 0), (30, 0), (34, 1), (350, 1), (366, 0), (MD, 0)]),
] + _sc("MoodYoYo", "Yo", [(0, 1), (312, 1), (336, 2.8), (350, 2.6), (366, .2), (MD, .2)]))

# 3. IDLE: STAR COUNTING ----------------------------------------------------
_breath = osc(0, 176, 44, .96, 1.05)
MOOD_STORIES["MoodStars"] = (MD, [
    (0, "Neutral", MOOD_STARS, G_NONE, F_NONE),
    (300, "Bored", MOOD_STARS, G_NONE, F_NONE),
    (MD, "Bored", MOOD_STARS, G_NONE, F_NONE),
], [
    ("blinkL", 16, _breath + [(190, 1.1), (246, 1.1), (252, 1.12), (262, .9), (272, 1.06), (282, 1.1), (300, 1.1), (320, 1), (MD, 1)]),
    ("blinkR", 16, _breath + [(190, 1.1), (300, 1.1), (320, 1), (MD, 1)]),
    ("blinkL", 17, _breath + [(190, .86), (246, .86), (252, .5), (262, 1.2), (272, .8), (282, .95), (300, .86), (320, .72), (MD, .72)]),
    ("blinkR", 17, _breath + [(190, .86), (250, .86), (256, .7), (268, .86), (300, .86), (320, .72), (MD, .72)]),
    ("blinkL", 13, [(0, -26), (150, 0), (246, 22), (252, 0), (266, 18), (300, 26), (320, 0), (MD, 0)]),
    ("blinkR", 13, [(0, -26), (150, 0), (300, 26), (320, 0), (MD, 0)]),
    ("blinkL", 14, [(0, -12), (300, -12), (320, 6), (MD, 6)]),
    ("blinkR", 14, [(0, -12), (300, -12), (320, 6), (MD, 6)]),
    ("face", 14, [(0, 0), (330, 0), (338, -10), (350, 6), (364, 0), (MD, 0)]),
    ("face", 16, [(0, 1), (330, 1), (336, 1.04), (350, .97), (364, 1), (MD, 1)]),
    ("face", 17, [(0, 1), (330, 1), (336, 1.04), (350, .97), (364, 1), (MD, 1)]),
    (_mp("MoodStars", "Stars"), 13, [(0, -420), (300, 380), (MD, 380)], "linear"),
    (_mp("MoodStars", "Stars"), 14, osc(0, 300, 60, -6, 6) + [(MD, 0)]),
    (_mp("MoodStars", "HitStar"), 13, [(0, 90), (200, 90), (250, -336), (270, -380), (MD, -380)]),
    (_mp("MoodStars", "HitStar"), 14, [(0, 0), (200, 0), (250, 170), (270, 250), (MD, 250)]),
    (_mp("MoodStars", "HitStar"), 18, [(0, 0), (200, 0), (206, 1), (262, 1), (280, 0), (MD, 0)]),
    (_mp("MoodStars", "Puff"), 18, [(0, 0), (330, 0), (336, 1), (390, 0), (MD, 0)]),
    (_mp("MoodStars", "Puff"), 14, [(0, 0), (330, 0), (390, -60), (MD, -60)]),
] + _sc("MoodStars", "Puff", [(0, .5), (330, .5), (390, 1.5), (MD, 1.5)]))

# 4. SHOCKED ---------------------------------------------------------------
_fall = [(0, 0), (330, 0), (360, 190), (366, 172), (374, 190), (MD, 190)]
MOOD_STORIES["MoodShocked"] = (MD, [
    (0, "Neutral", MOOD_SHOCK, G_NONE, F_NONE),
    (130, "Shock", MOOD_SHOCK, G_NONE, F_NONE),
    (250, "Scared", MOOD_SHOCK, G_NONE, F_NONE),
    (372, "Dead", MOOD_SHOCK, G_NONE, F_NONE),
    (MD, "Dead", MOOD_SHOCK, G_NONE, F_NONE),
], _eyes(17, [(0, 1), (56, 1), (60, .08), (66, .08), (72, 1), (126, 1), (132, .5), (142, 1.15), (152, 1),
              (360, 1), (366, .7), (374, 1.05), (382, 1), (MD, 1)]) +
   _eyes(14, _fall) + _eyes(15, [(0, 0), (366, 0), (374, .25), (MD, .25)], [(0, 0), (366, 0), (374, -.25), (MD, -.25)]) + [
    ("face", 16, [(0, 1), (128, 1), (136, 1.26), (146, 1.18), (154, 1.22), (330, 1.22), (345, 1), (MD, 1)]),
    ("face", 17, [(0, 1), (128, 1), (136, 1.26), (146, 1.18), (154, 1.22), (330, 1.22), (345, 1), (MD, 1)]),
    ("face", 13, [(0, 0), (239, 0)] + osc(240, 330, 3, -9, 9) + [(334, 0), (MD, 0)]),
    (_mp("MoodShock", "Spider"), 14, [(0, -320), (120, -320), (150, 10), (160, -8), (170, 0), (330, 0), (380, -340), (MD, -340)]),
    (_mp("MoodShock", "Spider"), 15, [(0, 0), (170, 0), (200, .15), (230, -.15), (260, .1), (290, -.08), (330, 0), (MD, 0)]),
    (_mp("MoodShock", "Bang"), 15, [(0, 0), (151, 0)] + osc(152, 330, 10, -.12, .12) + [(340, 0), (MD, 0)]),
    (_mp("MoodShock", "Flash"), 18, [(0, 0), (130, 0), (133, .55), (150, 0), (MD, 0)]),
] + _sc("MoodShock", "Bang", [(0, 0), (132, 0), (142, 1.35), (152, 1), (330, 1), (340, 0), (MD, 0)]))

# 5. SAD -------------------------------------------------------------------
_drop_y = [(0, 0), (300, 0), (360, 180), (362, 0), (394, 180), (MD, 180)]
_drop_o = [(0, 0), (300, 0), (304, 1), (352, 1), (360, 0), (364, 1), (388, 1), (394, 0), (MD, 0)]
MOOD_STORIES["MoodSad"] = (MD, [
    (0, "Neutral", MOOD_SAD, G_NONE, F_NONE),
    (70, "Weepy", MOOD_SAD, G_NONE, F_NONE),
    (MD, "Weepy", MOOD_SAD, G_NONE, F_NONE),
], _eyes(17, [(0, 1), (60, 1), (120, .78), (300, .78)] + osc(308, 372, 8, .25, .78) + [(384, .78), (MD, .78)]) +
   _eyes(14, [(0, 0), (120, 10), (MD, 10)]) + [
    ("mouth", 13, [(0, 0), (119, 0)] + osc(120, 300, 4, -2, 2) + [(304, 0), (MD, 0)]),
    ("face", 14, [(0, 0), (120, 8), (MD, 8)]),
    ("face", 15, [(0, 0), (120, .04), (MD, .04)]),
] + [e for nm in ("PoolL", "PoolR") for e in (
    (_mp("MoodSad", nm), 17, [(0, 0), (180, 0), (290, 1), (MD, 1)]),
    (_mp("MoodSad", nm), 18, [(0, 0), (178, 0), (184, 1), (MD, 1)]),
    (_mp("MoodSad", nm), 13, [(0, 0), (199, 0)] + osc(200, MD, 20, -4, 4)),
)] + [e for nm in ("DropL", "DropR") for e in (
    (_mp("MoodSad", nm), 14, _drop_y, "easeIn"),
    (_mp("MoodSad", nm), 18, _drop_o),
)])

# 6. CURIOUS ---------------------------------------------------------------
_lens_x = [(0, 0), (190, 0), (215, -30), (245, 30), (275, -30), (300, 30), (320, 0), (MD, 0)]
MOOD_STORIES["MoodCurious"] = (MD, [
    (0, "Quizzical", MOOD_CURIOUS, G_NONE, F_NONE),
    (380, "Starry", MOOD_CURIOUS, G_NONE, F_NONE),
    (MD, "Starry", MOOD_CURIOUS, G_NONE, F_NONE),
], [
    ("blinkR", 17, [(0, 1), (19, 1)] + osc(20, 120, 20, .9, 1.1) + [(170, 1), (186, 1.3), (320, 1.3), (340, 1),
                                                                    (378, .6), (388, 1.15), (398, 1), (MD, 1)]),
    ("blinkR", 16, [(0, 1), (19, 1)] + osc(20, 120, 20, .92, 1.0) + [(170, 1), (186, 1.12), (320, 1.12), (340, 1), (MD, 1)]),
    ("blinkL", 17, [(0, 1), (378, 1), (382, .6), (392, 1.15), (402, 1), (MD, 1)]),
    ("blinkR", 13, _lens_x),
    ("blinkL", 13, [(0, 0), (190, 0), (215, -10), (245, 10), (275, -10), (300, 10), (320, 0), (MD, 0)]),
    ("face", 16, [(0, 1), (300, 1), (312, 1.2), (324, 1), (336, 1.2), (348, 1), (MD, 1)]),
    ("face", 17, [(0, 1), (300, 1), (312, 1.2), (324, 1), (336, 1.2), (348, 1), (MD, 1)]),
    ("face", 15, [(0, 0), (20, .08), (120, .08), (160, 0), (MD, 0)]),
    (_mp("MoodCurious", "Q"), 15, [(0, 0), (33, 0)] + osc(34, 350, 30, -.15, .15) + [(362, 0), (MD, 0)]),
    (_mp("MoodCurious", "Glass"), 14, [(0, -360), (120, -360), (170, 0), (178, 8), (186, 0), (340, 0), (372, -380), (MD, -380)]),
    (_mp("MoodCurious", "Glass"), 13, _lens_x),
    (_mp("MoodCurious", "Bulb"), 18, [(0, 0), (366, 0), (372, 1), (378, .5), (384, 1), (MD, 1)]),
] + _sc("MoodCurious", "Q", [(0, 0), (10, 0), (24, 1.2), (34, 1), (350, 1), (362, 0), (MD, 0)]) +
    _sc("MoodCurious", "Bulb", [(0, 0), (366, 0), (378, 1.25), (390, 1), (MD, 1)]))

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

# 8. ANGRY: FUMING ---------------------------------------------------------
MOOD_STORIES["MoodFuming"] = (MD, [
    (0, "Fume", MOOD_FUME, G_NONE, F_NONE),
    (300, "Rage", MOOD_FUME, G_NONE, F_NONE),
    (MD, "Rage", MOOD_FUME, G_NONE, F_NONE),
], _eyes(17, [(0, 1), (30, .45), (180, .45), (290, 1.25), (300, 1.25)] + osc(303, MD, 6, .9, 1.1)) +
   _eyes(14, [(0, 0), (30, 32), (180, 32), (290, 0), (MD, 0)]) + [
    ("face", 13, [(0, 0), (179, 0)] + osc(180, 298, 4, -3, 3) + osc(303, MD, 3, -12, 12)),
    ("face", 16, [(0, 1), (180, 1), (290, 1.3), (300, 1.3), (312, 1), (MD, 1)]),
    ("face", 17, [(0, 1), (180, 1), (290, 1.3), (300, 1.3), (312, 1), (MD, 1)]),
    (_mp("MoodFume", "Heat"), 18, [(0, 0), (300, 0), (306, .45)] + osc(316, MD, 10, .25, .45)),
] + [e for nm in ("JetL", "JetR") for e in (
    (_mp("MoodFume", nm), 18, [(0, 0), (100, 0), (108, 1), (MD, 1)]),
    (_mp("MoodFume", nm), 14, [(0, 0), (99, 0)] + osc(100, MD, 20, 0, -24)),
)] + _sc("MoodFume", "JetL", [(0, 0), (100, 0), (120, 1), (180, .8), (290, 1.6), (300, .4), (310, 2.2), (MD, 2.2)]) +
    _sc("MoodFume", "JetR", [(0, 0), (100, 0), (120, 1), (180, .8), (290, 1.6), (300, .4), (310, 2.2), (MD, 2.2)]))

# 9. ANGRY: FURIOUS GLITCH -------------------------------------------------
_gf = (10, 34, 52, 70, 140, 160, 200, 230, 280, 316, 350, 390)
MOOD_STORIES["MoodGlitch"] = (MD, [
    (0, "Rage", MOOD_GLITCH, G_NONE, F_NONE),
    (312, "Rage", MOOD_GLITCH, G_NONE, F_CRACKS),
    (MD, "Rage", MOOD_GLITCH, G_NONE, F_CRACKS),
], [
    ("blinkL", 13, osc(0, 176, 4, -6, 6) + [(180, 0), (220, 55), (300, 55), (330, 0)] + osc(334, MD, 5, -4, 4)),
    ("blinkR", 13, osc(0, 176, 4, 6, -6) + [(180, 0), (220, -55), (300, -55), (330, 0)] + osc(334, MD, 5, 4, -4)),
] + _eyes(16, [(0, 1), (80, 1), (90, .05), (112, .05), (124, 1), (180, 1), (220, .8), (300, .8), (330, 1), (MD, 1)]) +
    _eyes(17, [(0, 1), (80, 1), (90, .05), (112, .05), (124, 1.1), (134, 1), (MD, 1)]) + [
    ("face", 16, [(0, 1), (300, 1), (312, 1.6), (322, .85), (334, 1.05), (344, 1), (MD, 1)]),
    ("face", 17, [(0, 1), (300, 1), (312, 1.6), (322, .85), (334, 1.05), (344, 1), (MD, 1)]),
    ("face", 14, [(0, 0), (300, 0), (312, -20), (322, 10), (334, 0), (MD, 0)]),
    (_mp("MoodGlitch", "GhostR"), 18, _pulse_op(_gf, 3, .9)),
    (_mp("MoodGlitch", "GhostC"), 18, _pulse_op([f + 2 for f in _gf], 3, .8)),
    (_mp("MoodGlitch", "GhostR"), 13, osc(0, MD, 5, -6, 14)),
    (_mp("MoodGlitch", "Scan"), 14, osc(0, MD, 6, -20, 20)),
    (_mp("MoodGlitch", "Scan"), 18, osc(0, MD, 9, .2, 1)),
    (_mp("MoodGlitch", "Pixels"), 18, [(0, 0), (86, 0), (90, 1), (112, 1), (124, 0), (MD, 0)]),
] + _sc("MoodGlitch", "Pixels", [(0, .6), (86, .6), (90, 1), (104, 2.2), (118, .8), (124, .3), (MD, .3)]) +
    _sc("MoodGlitch", "Bang", [(0, 0), (180, 0), (192, 1.3), (202, 1), (300, 1), (310, 0), (MD, 0)]))

# 10. THINKING -------------------------------------------------------------
_cx, _cy = _circle(180, 290, 80, 16)
MOOD_STORIES["MoodThinking"] = (MD, [
    (0, "Neutral", MOOD_THINK, G_NONE, F_NONE),
    (20, "Thinking", MOOD_THINK, G_NONE, F_NONE),
    (300, "Neutral", MOOD_THINK, G_NONE, F_NONE),
    (MD, "Neutral", MOOD_THINK, G_NONE, F_NONE),
], [
    ("blinkL", 15, [(0, 0), (30, .2), (290, .2), (300, 0), (MD, 0)]),
    ("blinkR", 15, [(0, 0), (30, -.2), (290, -.2), (300, 0), (MD, 0)]),
] + _eyes(17, [(0, 1), (30, .82), (180, .82)] + osc(190, 290, 20, .95, .8) + [(300, .82), (306, 1.35), (316, .9), (326, 1.05), (336, 1), (MD, 1)]) +
    _eyes(16, [(0, 1), (300, 1), (306, .85), (316, 1.06), (326, 1), (MD, 1)]) +
    _eyes(13, [(0, 0), (170, 0)] + _cx + [(300, 0), (MD, 0)]) +
    _eyes(14, [(0, 0), (170, 0)] + _cy + [(300, 0), (MD, 0)]) + [
    ("face", 15, [(0, 0), (30, .05), (290, .05), (300, 0), (MD, 0)]),
    ("face", 14, [(0, 0), (300, 0), (306, -14), (320, 0), (MD, 0)]),
    (_mp("MoodThink", "Gear"), 15, [(0, 0), (290, 12), (MD, 12)], "linear"),
    (_mp("MoodThink", "Gear2"), 15, [(0, 0), (290, -16), (MD, -16)], "linear"),
    (_mp("MoodThink", "Bulb"), 18, [(0, 0), (304, 0), (308, 1), (MD, 1)]),
] + _sc("MoodThink", "Bubble", [(0, 0), (30, 0), (50, 1.1), (60, 1), (290, 1), (300, 0), (MD, 0)]) +
    _sc("MoodThink", "Bulb", [(0, 0), (304, 0), (316, 1.3), (326, 1), (MD, 1)]))

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

# 12. IN LOVE --------------------------------------------------------------
_beat = []
for _b in (0, 60, 120):
    _beat += [(_b, 1), (_b + 6, 1.14), (_b + 12, 1), (_b + 18, 1.1), (_b + 26, 1)]
MOOD_STORIES["MoodInLove"] = (MD, [
    (0, "Love", MOOD_LOVE, G_BOKEH, F_NONE),
    (MD, "Love", MOOD_LOVE, G_BOKEH, F_NONE),
], [
    ("face", 16, _beat + [(180, 1), (210, 1.35), (300, 1.35), (320, 1), (MD, 1)]),
    ("face", 17, _beat + [(180, 1), (210, 1.35), (300, 1.35), (320, 1), (MD, 1)]),
    ("face", 15, [(0, 0), (329, 0)] + osc(330, MD, 40, -.06, .06)),
    ("blinkL", 13, [(0, 0), (300, 0), (330, 28), (MD, 28)]),
    ("blinkR", 13, [(0, 0), (300, 0), (330, -28), (MD, -28)]),
    ("blinkL", 15, [(0, 0), (300, 0), (330, .3)] + osc(360, MD, 30, .18, .3)),
    ("blinkR", 15, [(0, 0), (300, 0), (330, -.3)] + osc(360, MD, 30, -.18, -.3)),
] + _eyes(14, [(0, 0), (180, 0), (210, -12), (300, -12), (320, 0), (MD, 0)]) +
    _eyes(17, [(0, 1), (210, 1)] + osc(220, 300, 30, 1.1, 1) + [(MD, 1)]) + [
    (_mp("MoodLove", "Bubbles"), 14, [(0, 0), (180, 0), (MD, -470)], "linear"),
    (_mp("MoodLove", "Bubbles"), 18, [(0, 0), (180, 0), (190, 1), (400, 1), (MD, 0)]),
    (_mp("MoodLove", "Blush"), 18, [(0, 0), (300, 0), (316, 1), (MD, 1)]),
])

# 13. GLAD -----------------------------------------------------------------
MOOD_STORIES["MoodGlad"] = (MD, [
    (0, "Glad", MOOD_GLAD, G_NONE, F_NONE),
    (190, "Glad", MOOD_GLAD, G_NONE, F_CONFETTI),
    (MD, "Glad", MOOD_GLAD, G_NONE, F_CONFETTI),
], [
    ("blinkL", 14, [(0, 0), (299, 0)] + osc(300, MD, 15, -10, 10)),
    ("blinkR", 14, [(0, 0), (307, 0)] + osc(308, MD, 15, -10, 10)),
    ("blinkL", 15, [(0, 0), (299, 0)] + osc(300, MD, 15, -.12, .12)),
    ("blinkR", 15, [(0, 0), (299, 0)] + osc(300, MD, 15, .12, -.12)),
] + _eyes(17, [(0, .7), (16, 1.12), (28, 1), (189, 1)] + osc(190, 298, 6, 1, .2) + [(304, 1), (MD, 1)]) + [
    ("face", 14, [(0, 10), (16, -10), (30, 0), (189, 0)] + osc(190, 298, 15, 0, -16) + [(304, 0), (MD, 0)]),
    ("face", 13, [(0, 0), (299, 0)] + osc(300, MD, 30, -6, 6)),
    (_mp("MoodGlad", "PopL"), 13, [(0, -130), (170, -130), (186, 0), (190, -10), (198, 0), (MD, 0)]),
    (_mp("MoodGlad", "PopR"), 13, [(0, 130), (170, 130), (186, 0), (190, 10), (198, 0), (MD, 0)]),
    (_mp("MoodGlad", "Burst"), 18, [(0, 0), (188, 0), (190, 1), (240, 0), (MD, 0)]),
] + _sc("MoodGlad", "Gleam", osc(0, MD, 20, .6, 1.1)) +
    _sc("MoodGlad", "Burst", [(0, 0), (188, 0), (196, 1.2), (230, 1.8), (MD, 1.8)], "easeOut"))

# 14. AWESOME --------------------------------------------------------------
_shades_y = [(0, -320), (20, -320), (60, 0), (66, 8), (72, 0)]
MOOD_STORIES["MoodAwesome"] = (MD, [
    (0, "Neutral", MOOD_COOL, G_NONE, F_NONE),
    (60, "Smug", MOOD_COOL, G_NONE, F_NONE),
    (MD, "Smug", MOOD_COOL, G_NONE, F_NONE),
], _eyes(14, [(0, 0), (20, -14), (56, -14), (60, 0), (MD, 0)]) +
   _eyes(17, [(0, 1), (303, 1)] + osc(304, MD, 20, 1.08, .92)) + [
    ("face", 15, [(0, 0), (180, 0), (214, .12), (300, .12), (310, 0), (MD, 0)]),
    ("face", 14, [(0, 0), (299, 0)] + osc(300, MD, 20, 0, 12)),
    (_mp("MoodCool", "Shades"), 14, _shades_y + [(299, 0)] + osc(300, MD, 20, 0, 12)),
    (_mp("MoodCool", "Shades"), 15, [(0, 0), (180, 0), (214, .12), (300, .12), (310, 0), (MD, 0)]),
    (_mp("MoodCool", "Glint"), 15, [(0, 0), (84, 0), (104, 1.5), (150, 1.5), (170, 3), (MD, 3)]),
    (_mp("MoodCool", "Thumb"), 14, [(0, 260), (180, 260), (210, -14), (220, 6), (228, 0), (MD, 0)]),
    (_mp("MoodCool", "Thumb"), 15, [(0, 0), (229, 0)] + osc(230, 300, 20, -.1, .1) + [(310, 0), (MD, 0)]),
] + _sc("MoodCool", "Glint", [(0, 0), (84, 0), (92, 1.4), (104, 0), (150, 0), (158, 1.2), (170, 0), (MD, 0)]) +
    _sc("MoodCool", "Thumb", [(0, 1), (210, 1), (220, 1.15), (230, 1), (MD, 1)]))

# 15. SHY ------------------------------------------------------------------
MOOD_STORIES["MoodShy"] = (MD, [
    (0, "Bashful", MOOD_SHY, G_NONE, F_NONE),
    (MD, "Bashful", MOOD_SHY, G_NONE, F_NONE),
], [
    ("blinkL", 13, [(0, 0), (189, 0)] + osc(190, 300, 10, -26, 26) + [(306, 0), (MD, 0)]),
    ("blinkR", 13, [(0, 0), (189, 0)] + osc(190, 300, 10, -26, 26) + [(306, 0), (356, 0), (370, 26), (MD, 26)]),
    ("blinkL", 15, [(0, 0), (40, .18), (MD, .18)]),
    ("blinkR", 15, [(0, 0), (40, -.18), (MD, -.18)]),
] + _eyes(16, [(0, 1), (40, .62), (MD, .62)]) + _eyes(17, [(0, 1), (40, .5), (MD, .5)]) +
    _eyes(14, [(0, 0), (40, 24), (MD, 24)]) + [
    ("face", 17, [(0, 1), (180, 1), (210, .8), (300, .8), (330, 1), (MD, 1)]),
    ("face", 14, [(0, 0), (180, 0), (210, 50), (300, 50), (330, 20), (350, 10), (MD, 10)]),
    (_mp("MoodShy", "Blush"), 18, [(0, 0), (30, 0), (50, 1), (MD, 1)]),
    (_mp("MoodShy", "HandL"), 14, [(0, 460), (300, 460), (330, 0), (MD, 0)]),
    (_mp("MoodShy", "HandR"), 14, [(0, 460), (300, 460), (330, 0), (MD, 0)]),
    (_mp("MoodShy", "HandR"), 13, [(0, 0), (356, 0), (370, 58), (MD, 58)]),
])

# 16. EMBARRASSED ----------------------------------------------------------
MOOD_STORIES["MoodEmbarrassed"] = (MD, [
    (0, "Fluster", MOOD_EMBARRASSED, G_NONE, F_NONE),
    (MD, "Fluster", MOOD_EMBARRASSED, G_NONE, F_NONE),
], _eyes(14, [(0, 0), (20, -10), (60, 120), (200, 120), (220, 170), (250, 120), (270, 170), (300, 150), (390, 200), (MD, 200)]) +
   _eyes(17, [(0, 1), (60, .65), (70, .8), (180, .7), (300, .7), (390, .12), (MD, .12)]) +
   _eyes(16, [(0, 1), (60, 1.08), (180, 1.05), (300, 1.05), (390, 1.12), (MD, 1.12)]) +
   _eyes(13, [(0, 0), (299, 0)] + osc(300, 390, 3, -3, 3) + [(394, 0), (MD, 0)]) + [
    (_mp("MoodEmbarrassed", "Sweat"), 18, [(0, 0), (40, 0), (46, 1), (170, 1), (180, 0), (MD, 0)]),
    (_mp("MoodEmbarrassed", "Sweat"), 13, [(0, 0), (45, 0)] + osc(46, 180, 10, -10, 10) + [(MD, 0)]),
    (_mp("MoodEmbarrassed", "Wall"), 14, [(0, 320), (180, 320), (210, -12), (220, 0), (300, 0), (330, 320), (MD, 320)]),
    (_mp("MoodEmbarrassed", "Dim"), 18, [(0, 0), (300, 0), (330, .4), (MD, .4)]),
    (_mp("MoodEmbarrassed", "Puddle"), 18, [(0, 0), (360, 0), (390, 1), (MD, 1)]),
    (_mp("MoodEmbarrassed", "Puddle"), 16, [(0, .3), (360, .3), (400, 1), (MD, 1)]),
])

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

# 19. ARROGANT -------------------------------------------------------------
MOOD_STORIES["MoodArrogant"] = (MD, [
    (0, "Haughty", MOOD_ARROGANT, G_NONE, F_NONE),
    (350, "Sleepy", MOOD_ARROGANT, G_NONE, F_NONE),
    (MD, "Sleepy", MOOD_ARROGANT, G_NONE, F_NONE),
], _eyes(14, [(0, 0), (40, -16), (180, -16), (220, -28), (300, -28), (330, -8), (MD, -8)]) +
   _eyes(13, [(0, 0), (180, 0), (290, -18), (MD, -18)]) +
   _eyes(17, [(0, 1), (300, 1), (340, .5), (MD, .5)]) + [
    ("face", 14, [(0, 0), (40, -40), (MD, -40)]),
    ("face", 17, [(0, 1), (40, 1.1), (300, 1.1), (330, 1), (MD, 1)]),
    ("face", 13, [(0, 0), (200, 0), (290, -34), (330, -34), (380, -70), (MD, -70)]),
    ("face", 15, [(0, 0), (200, 0), (290, -.1), (MD, -.1)]),
    ("face", 16, [(0, 1), (330, 1), (380, .86), (MD, .86)]),
    (_mp("MoodArrogant", "Crown"), 14, [(0, -260), (180, -260), (210, -40), (218, -48), (226, -40), (MD, -40)]),
    (_mp("MoodArrogant", "Crown"), 13, [(0, 0), (200, 0), (290, -34), (330, -34), (380, -70), (MD, -70)]),
    (_mp("MoodArrogant", "Crown"), 15, [(0, 0), (200, 0), (290, -.1), (MD, -.1)]),
    (_mp("MoodArrogant", "Fan"), 13, [(0, 200), (190, 200), (220, 0), (MD, 0)]),
    (_mp("MoodArrogant", "Fan"), 15, [(0, 0), (219, 0)] + osc(220, MD, 12, -.25, .25)),
    (_mp("MoodArrogant", "Puff"), 18, [(0, 0), (310, 0), (314, 1), (360, 0), (MD, 0)]),
    (_mp("MoodArrogant", "Puff"), 13, [(0, 0), (310, 0), (360, 50), (MD, 50)]),
] + _sc("MoodArrogant", "Puff", [(0, .4), (310, .4), (360, 1.6), (MD, 1.6)]))


def _check_mood_frames():
    bad = []
    for sn, (dur, beats, extra) in MOOD_STORIES.items():
        fr = [b[0] for b in beats]
        if fr != sorted(set(fr)):
            bad.append("%s beats" % sn)
        seen = set()
        for ex in extra:
            fs = [f for f, _v in ex[2]]
            if fs != sorted(set(fs)) or fs[-1] > dur:
                bad.append("%s %s/%s %s" % (sn, ex[0], ex[1], fs))
            k = (ex[0], ex[1])
            if k in seen:
                bad.append("%s duplicate track %s" % (sn, k))
            seen.add(k)
    if bad:
        raise SystemExit("mood story frames out of order:\n  " + "\n  ".join(bad))


_check_mood_frames()
HAND_STORIES.update(MOOD_STORIES)

# presets.json order (appended after the status set; seq = index + 1)
SCENE_PRESETS = [
    ("SceneEating", "Happy", FOOD), ("SceneDrinking", "Calm", DRINK), ("SceneBath", "Happy", BATH),
    ("SceneGaming", "Starry", GAME), ("SceneStudy", "Focused", STUDY), ("SceneRain", "Happy", RAIN),
    ("SceneThug", "Smug", THUG), ("SceneRich", "Money", RICH), ("SceneRoyal", "Smug", ROYAL),
    ("SceneFire", "Dizzy", FIRE), ("SceneThunder", "Dizzy", THUNDER), ("SceneSoulOut", "Dizzy", SOUL),
    ("SceneSuperLove", "Love", LOVE), ("SceneCry", "Sad", CRY), ("SceneCelebrate", "Happy", PARTY),
    ("SceneVR", "Blank", VR), ("SceneMusic", "Happy", MUSIC),
]
PRESETS.extend((n, f, p, G_NONE, F_NONE) for n, f, p in SCENE_PRESETS)

# jelly mood stories (seq 82+), named after their story; face = where each one lands
MOOD_PRESETS = [
    ("MoodIdleBall", "Sleepy", P_ZZZ), ("MoodYoYo", "Happy", MOOD_YOYO), ("MoodStars", "Bored", MOOD_STARS),
    ("MoodShocked", "Dead", MOOD_SHOCK), ("MoodSad", "Weepy", MOOD_SAD), ("MoodCurious", "Starry", MOOD_CURIOUS),
    ("MoodAngryMissile", "Evil", MOOD_MISSILE), ("MoodFuming", "Rage", MOOD_FUME), ("MoodGlitch", "Rage", MOOD_GLITCH),
    ("MoodThinking", "Neutral", MOOD_THINK), ("MoodHappy", "Beam", MOOD_HAPPY), ("MoodInLove", "Love", MOOD_LOVE),
    ("MoodGlad", "Glad", MOOD_GLAD), ("MoodAwesome", "Smug", MOOD_COOL), ("MoodShy", "Bashful", MOOD_SHY),
    ("MoodEmbarrassed", "Fluster", MOOD_EMBARRASSED), ("MoodShowOff", "Smug", MOOD_SHOWOFF),
    ("MoodListening", "Happy", MOOD_LISTEN), ("MoodArrogant", "Sleepy", MOOD_ARROGANT),
]
PRESETS.extend((n, f, p, G_NONE, F_NONE) for n, f, p in MOOD_PRESETS)


def build_seqs():
    seqs = [("None", 1, [], [])]
    for name, face, prop, bg, fg in PRESETS:
        if name in HAND_STORIES:
            dur, beats, extra = HAND_STORIES[name]
        else:
            dur, beats, extra = auto_story(name, face, prop, bg, fg)
        seqs.append((name, dur, beats, extra))
    return seqs


SEQS = build_seqs()


# ================================================================= STATES
# Operational states, not emotions. A mood story (`seq`) is a one-shot arc the
# app fires and then clears; a state is what the robot is *doing right now*
# while it waits on the camera, the mic, the gyro or the user -- so it loops
# forever and has to be swappable on any frame.
#
# Two kinds, and the difference matters for mixing:
#   * motion-only states (empty beat list) key nothing but movement, so they
#     compose with whatever `face` / `prop` / `bg` the host has set. Idle,
#     Ready, Speaking and Standby are the plain face *behaving*.
#   * look states pin the expression and prop, because the state IS the look --
#     Listening, Thinking, Detected, Scanning, WaitCmd, Working.
# Beat format is a sequence's: (name, duration, beats, extras).
STATES = [
    # 0 -- keys nothing at all: the manual face/prop/bg/fg channels own the face.
    ("Off", 1, [], []),

    # ตื่นอยู่ รอเฉยๆ: สายตาลอยไปมาช้าๆ หัวขยับนิดๆ กะพริบ 2 ครั้งต่อรอบ
    ("Idle", 480, [], [
        ("face", 13, [(0, 0), (120, -13), (200, -13), (260, 11), (340, 11), (420, 0), (480, 0)]),
        ("face", 14, [(0, 0), (140, 5), (300, -4), (480, 0)]),
        ("face", 15, [(0, 0), (200, -0.015), (340, 0.015), (480, 0)]),
        ("blink", 17, [(0, 1), (150, 1), (158, .08), (168, 1), (330, 1), (338, .08), (348, 1), (480, 1)]),
    ]),

    # พร้อมรับคำสั่ง: ตาสว่างเบิกขึ้นเล็กน้อย ขยับถี่และสั้น
    ("Ready", 240, [], [
        ("blink", 16, [(0, 1.05), (240, 1.05)]),
        ("blink", 17, [(0, 1.05), (110, 1.05), (118, .1), (128, 1.05), (240, 1.05)]),
        ("face", 13, [(0, 0), (40, -5), (80, 4), (120, -3), (160, 5), (240, 0)]),
    ]),

    # กำลังฟัง: ตานิ่งจ้อง เบิกเป็นจังหวะ + คลื่นเสียง (ความสูงปากมาจาก audioLevel)
    ("Listening", 200, [
        (0, "Listening", P_SOUNDWAVE, G_NONE, F_NONE),
    ], [
        ("blink", 16, [(0, 1.03), (100, 1.09), (200, 1.03)]),
        ("blink", 17, [(0, 1.03), (100, 1.09), (200, 1.03)]),
        ("face", 14, [(0, 0), (100, -3), (200, 0)]),
    ]),

    # กำลังคิด: ตาเหลือบขึ้นบนแล้วกวาดไปข้างๆ + จุดไล่
    ("Thinking", 300, [
        (0, "Thinking", P_THINKDOTS, G_NONE, F_NONE),
    ], [
        ("blinkL", 14, [(0, 0), (90, -9), (200, -9), (280, 0), (300, 0)]),
        ("blinkR", 14, [(0, 0), (90, -9), (200, -9), (280, 0), (300, 0)]),
        ("blinkL", 13, [(0, 0), (90, -7), (200, 7), (300, 0)]),
        ("blinkR", 13, [(0, 0), (90, -7), (200, 7), (300, 0)]),
        ("blink", 17, [(0, 1), (140, 1), (150, .35), (162, 1), (300, 1)]),
    ]),

    # กำลังพูด: หัวขยับตามจังหวะคำ ปากเต้น (คูณกับ audioLevel ที่ผูกไว้แล้ว)
    ("Speaking", 120, [], [
        ("face", 14, [(0, 0), (30, -3), (60, 2), (90, -3), (120, 0)]),
        ("face", 13, [(0, 0), (45, 3), (95, -3), (120, 0)]),
        ("mouth", 17, [(0, 1), (18, 1.22), (38, .95), (58, 1.18), (80, 1), (104, 1.14), (120, 1)]),
        ("blink", 17, [(0, 1), (70, 1), (78, .1), (88, 1), (120, 1)]),
    ]),

    # กล้องเจอคน: ตาเบิกโพลง หัวเด้ง แล้วยิ้มรับ
    ("Detected", 260, [
        (0,   "Shock", P_SPARKLE, G_NONE, F_NONE),
        (70,  "Happy", P_SPARKLE, G_NONE, F_NONE),
        (170, "Happy", P_NONE,    G_NONE, F_NONE),
    ], [
        ("blink", 16, [(0, .8), (14, 1.08), (34, 1.03), (70, 1), (260, 1)]),
        ("blink", 17, [(0, .8), (14, 1.30), (34, 1.06), (70, 1), (260, 1)]),
        ("face", 14, [(0, 10), (16, -12), (40, 3), (64, 0), (260, 0)]),
    ]),

    # กำลังสแกนวัตถุ: ตาลิ่มกวาดซ้ายขวาเป็นจังหวะเครื่องจักร + กริดสแกน
    ("Scanning", 240, [
        (0, "Focused", P_NONE, G_GRID, F_NONE),
    ], [
        ("blinkL", 13, osc(0, 240, 60, -15, 15)),
        ("blinkR", 13, osc(0, 240, 60, -15, 15)),
        ("blink", 17, [(0, .9), (240, .9)]),
    ]),

    # สแตนด์บาย: เปลือกตาหนัก หัวห้อย กะพริบช้ามาก
    ("Standby", 600, [], [
        ("blink", 17, [(0, .5), (280, .5), (300, .06), (350, .06), (380, .5), (600, .5)]),
        ("face", 16, [(0, .96), (300, .93), (600, .96)]),
        ("face", 17, [(0, .96), (300, .93), (600, .96)]),
        ("face", 14, [(0, 6), (300, 13), (600, 6)]),
    ]),

    # รอคำสั่ง: เอียงหัวค้าง + เครื่องหมายคำถาม
    ("WaitCmd", 360, [
        (0, "Neutral", P_QUESTION, G_NONE, F_NONE),
    ], [
        ("face", 15, [(0, 0), (90, .07), (270, .07), (360, 0)]),
        ("face", 13, [(0, 0), (90, 9), (270, 9), (360, 0)]),
        ("blink", 17, [(0, 1), (180, 1), (190, .1), (202, 1), (360, 1)]),
    ]),

    # กำลังประมวลผล: ตาเพ่งเต้นเป็นจังหวะ + จุดไล่บนกริด
    ("Working", 300, [
        (0, "Focused", P_THINKDOTS, G_GRID, F_NONE),
    ], [
        ("blink", 16, [(0, 1), (150, .92), (300, 1)]),
        ("blink", 17, [(0, 1), (150, 1.06), (300, 1)]),
        ("face", 14, [(0, 0), (150, -4), (300, 0)]),
    ]),
]

# ---- the sensor states the app already has hardware for -----------------
# เอียงฟัง / เวียนหัว / เล่นกับตัวเอง / หลับ
STATES += [
    # 11-12 ได้ยินเสียงมาจากทางไหน ก็เอียงหูไปทางนั้น ตาข้างนั้นโตกว่า
    ("TiltL", 200, [
        (0, "Listening", P_SOUNDWAVE, G_NONE, F_NONE),
    ], [
        ("face", 15, [(0, 0), (26, -.20), (170, -.18), (200, -.20)]),
        ("face", 13, [(0, 0), (26, -18), (200, -18)]),
        ("blinkL", 16, [(0, 1), (26, 1.20), (110, 1.26), (200, 1.20)]),
        ("blinkL", 17, [(0, 1), (26, 1.20), (110, 1.26), (200, 1.20)]),
        ("blinkR", 16, [(0, 1), (26, .86), (200, .86)]),
        ("blinkR", 17, [(0, 1), (26, .86), (200, .86)]),
    ]),
    ("TiltR", 200, [
        (0, "Listening", P_SOUNDWAVE, G_NONE, F_NONE),
    ], [
        ("face", 15, [(0, 0), (26, .20), (170, .18), (200, .20)]),
        ("face", 13, [(0, 0), (26, 18), (200, 18)]),
        ("blinkR", 16, [(0, 1), (26, 1.20), (110, 1.26), (200, 1.20)]),
        ("blinkR", 17, [(0, 1), (26, 1.20), (110, 1.26), (200, 1.20)]),
        ("blinkL", 16, [(0, 1), (26, .86), (200, .86)]),
        ("blinkL", 17, [(0, 1), (26, .86), (200, .86)]),
    ]),

    # 13 โยกมือถือซ้ายขวาติดๆกัน -> เวียนหัว
    ("Dizzy", 240, [
        (0, "Dizzy", P_DIZZYSTARS, G_NONE, F_NONE),
    ], [
        ("face", 13, osc(0, 240, 30, -22, 22)),
        ("face", 15, [(0, -.10), (60, .10), (120, -.10), (180, .10), (240, -.10)]),
        ("face", 14, [(0, 0), (60, 8), (120, 0), (180, 8), (240, 0)]),
        ("blinkL", 13, [(0, -8), (60, 8), (120, -8), (180, 8), (240, -8)]),
        ("blinkR", 13, [(0, 8), (60, -8), (120, 8), (180, -8), (240, 8)]),
    ]),

    # 14-18 เล่นกับตัวเอง 5 แบบ -- โฮสต์สุ่มวนไปเรื่อยๆ ตอนไม่มีคำสั่งเกิน 10 วิ
    # ทั้ง 5 แบบเป็น motion only จึงเล่นกับสีหน้าไหนก็ได้ที่โฮสต์ตั้งไว้
    ("PlayBounce", 420, [], [
        # เด้งชนขอบจอ -- linear ระหว่างขอบ แล้วบี้ตอนกระแทก
        ("face", 13, [(0, 0), (70, -84), (76, -84), (190, 84), (196, 84),
                      (310, -84), (316, -84), (420, 0)], "linear"),
        ("face", 14, [(0, 0), (52, 124), (58, 124), (150, -116), (156, -116),
                      (250, 124), (256, 124), (350, -116), (356, -116), (420, 0)], "linear"),
        ("face", 16, [(0, 1), (70, .76), (82, 1.08), (96, 1), (190, .76), (202, 1.08),
                      (216, 1), (310, .76), (322, 1.08), (336, 1), (420, 1)]),
        ("face", 17, [(0, 1), (52, .74), (64, 1.10), (78, 1), (150, .74), (162, 1.10),
                      (176, 1), (250, .74), (262, 1.10), (276, 1), (420, 1)]),
    ]),
    ("PlayChase", 300, [], [
        # ไล่จับแมลงวันที่มองไม่เห็น -- ตาสะบัดเป็นจุดๆ แล้วหยีตอนคิดว่าจับได้
        ("blinkL", 13, [(0, 0), (24, 26), (48, -30), (72, 18), (96, -22), (150, 30),
                        (180, -12), (210, 8), (240, 0), (300, 0)]),
        ("blinkR", 13, [(0, 0), (24, 26), (48, -30), (72, 18), (96, -22), (150, 30),
                        (180, -12), (210, 8), (240, 0), (300, 0)]),
        ("blinkL", 14, [(0, 0), (24, -18), (48, 14), (72, -20), (96, 10), (150, -16),
                        (180, 12), (210, -6), (240, 0), (300, 0)]),
        ("blinkR", 14, [(0, 0), (24, -18), (48, 14), (72, -20), (96, 10), (150, -16),
                        (180, 12), (210, -6), (240, 0), (300, 0)]),
        ("face", 13, [(0, 0), (72, 12), (150, -14), (240, 0), (300, 0)]),
        ("blink", 17, [(0, 1), (238, 1), (248, .18), (262, 1.12), (274, 1), (300, 1)]),
    ]),
    ("PlaySpin", 300, [], [
        # หมุนตัวเองเล่นหนึ่งรอบแล้วหน้ามืดนิดๆ
        ("face", 15, [(0, 0), (30, 0), (230, 6.2831855), (300, 6.2831855)], "linear"),
        ("face", 16, [(0, 1), (230, 1), (250, 1.10), (266, .94), (280, 1), (300, 1)]),
        ("face", 17, [(0, 1), (230, 1), (250, .90), (266, 1.06), (280, 1), (300, 1)]),
        ("blink", 17, [(0, 1), (232, 1), (244, .3), (258, 1), (300, 1)]),
    ]),
    ("PlayPeek", 300, [], [
        # จุ๊บแบ๊ะ -- มุดลงขอบล่างแล้วโผล่ขึ้นมาตาโต
        ("face", 14, [(0, 0), (60, 300), (140, 300), (176, -46), (198, 16), (216, 0), (300, 0)]),
        ("blink", 16, [(0, 1), (150, 1), (178, 1.18), (206, 1), (300, 1)]),
        ("blink", 17, [(0, 1), (150, 1), (178, 1.30), (206, 1), (300, 1)]),
        ("face", 15, [(0, 0), (60, .12), (140, -.12), (176, .05), (216, 0), (300, 0)]),
    ]),
    ("PlayWiggle", 240, [], [
        # เต้นเองอยู่คนเดียว
        ("face", 13, [(0, 0), (30, -40), (60, 0), (90, 40), (120, 0),
                      (150, -40), (180, 0), (210, 40), (240, 0)]),
        ("face", 15, [(0, 0), (30, .09), (60, 0), (90, -.09), (120, 0),
                      (150, .09), (180, 0), (210, -.09), (240, 0)]),
        ("face", 14, [(0, 0), (15, -10), (30, 0), (45, -10), (60, 0), (75, -10),
                      (90, 0), (105, -10), (120, 0), (135, -10), (150, 0),
                      (165, -10), (180, 0), (195, -10), (210, 0), (225, -10), (240, 0)]),
        ("blink", 17, [(0, 1), (30, .72), (60, 1), (90, .72), (120, 1),
                       (150, .72), (180, 1), (210, .72), (240, 1)]),
    ]),

    # 19 หลับสนิท -- ปลายทางของ PlayX เมื่อยังไม่มีใครมายุ่ง
    ("Asleep", 480, [
        (0, "Sleepy", P_SNORE, G_NONE, F_NONE),
    ], [
        # หน้า Sleepy เป็นตาแท่งแบนอยู่แล้ว ถ้าบีบอีกจะหายไปเลย -- แค่หายใจช้าๆ พอ
        ("blink", 17, [(0, .92), (240, 1.06), (480, .92)]),
        ("face", 14, [(0, 22), (240, 30), (480, 22)]),
        ("face", 15, [(0, .09), (240, .11), (480, .09)]),
        ("face", 16, [(0, .99), (240, 1.02), (480, .99)]),
        ("face", 17, [(0, .99), (240, 1.02), (480, .99)]),
    ]),
]
ST_NAMES = [s[0] for s in STATES]

# ================================================================= REACTIONS
# Touch and shock: what the robot does *to you* when you do something to it.
# These are one-shot arcs like a story, but they must be able to cut in over a
# running story -- poke the cheek in the middle of the Angry story and it should
# flinch -- so the React layer is declared LAST of the content layers and wins
# over everything. The host writes `react`, waits `durationMs`, writes 0.
REACTS = [
    ("None", 1, [], []),

    # ลูบหัว -- แตะเหนือดวงตา
    ("HeadPat", 240, [
        (0,   "Neutral", P_NONE,    G_NONE,  F_NONE),
        (16,  "Happy",   P_HOLOPAT, G_BOKEH, F_NONE),
        (200, "Happy",   P_NONE,    G_BOKEH, F_NONE),
        (240, "Happy",   P_NONE,    G_NONE,  F_NONE),
    ], [
        # หน้าเอียงตามมือ ไหล่ยุบลงนิดๆ อย่างคนที่กำลังพอใจ
        ("face", 15, [(0, 0), (40, -.07), (90, .07), (140, -.07), (190, 0), (240, 0)]),
        ("face", 14, [(0, 0), (30, 12), (90, 8), (150, 12), (200, 4), (240, 0)]),
        ("blink", 17, [(0, 1), (24, .55), (110, .48), (190, .62), (240, 1)]),
        ("blink", 16, [(0, 1), (24, 1.06), (240, 1)]),
    ]),

    # จิ้มแก้มซ้าย -- หน้าถูกดันไปทางขวา ตาข้างที่โดนบี้
    ("PokeL", 180, [
        (0,   "Neutral", P_NONE,      G_NONE, F_NONE),
        (12,  "Shy",     P_HOLOPOKEL, G_NONE, F_NONE),
        (150, "Shy",     P_NONE,      G_NONE, F_NONE),
        (180, "Neutral", P_NONE,      G_NONE, F_NONE),
    ], [
        ("face", 13, [(0, 0), (25, 0), (30, 20), (46, 6), (53, 6), (58, 18), (80, 2), (140, 0), (180, 0)]),
        ("face", 15, [(0, 0), (25, 0), (30, .07), (58, .06), (140, 0), (180, 0)]),
        ("blinkL", 16, [(0, 1), (25, 1), (30, .74), (46, 1), (53, 1), (58, .78), (80, 1), (180, 1)]),
        ("blinkL", 17, [(0, 1), (25, 1), (30, 1.14), (46, 1), (53, 1), (58, 1.12), (80, 1), (180, 1)]),
        ("blink", 17, [(0, 1), (24, .6), (120, .72), (180, 1)]),
    ]),
    ("PokeR", 180, [
        (0,   "Neutral", P_NONE,      G_NONE, F_NONE),
        (12,  "Shy",     P_HOLOPOKER, G_NONE, F_NONE),
        (150, "Shy",     P_NONE,      G_NONE, F_NONE),
        (180, "Neutral", P_NONE,      G_NONE, F_NONE),
    ], [
        ("face", 13, [(0, 0), (25, 0), (30, -20), (46, -6), (53, -6), (58, -18), (80, -2), (140, 0), (180, 0)]),
        ("face", 15, [(0, 0), (25, 0), (30, -.07), (58, -.06), (140, 0), (180, 0)]),
        ("blinkR", 16, [(0, 1), (25, 1), (30, .74), (46, 1), (53, 1), (58, .78), (80, 1), (180, 1)]),
        ("blinkR", 17, [(0, 1), (25, 1), (30, 1.14), (46, 1), (53, 1), (58, 1.12), (80, 1), (180, 1)]),
        ("blink", 17, [(0, 1), (24, .6), (120, .72), (180, 1)]),
    ]),

    # จิ้มซ้ำๆ ขั้นที่ 1 -- เริ่มรำคาญ
    ("PokeAnnoyed", 200, [
        (0,   "Shy",  P_HOLOPOKER, G_NONE, F_NONE),
        (40,  "Pout", P_HOLOPOKER, G_NONE, F_NONE),
        (150, "Pout", P_STEAM,     G_NONE, F_NONE),
        (200, "Pout", P_STEAM,     G_NONE, F_NONE),
    ], [
        ("face", 13, [(0, 0), (14, -16), (28, 8), (42, -14), (56, 6), (70, 0), (200, 0)]),
        ("face", 15, [(0, 0), (40, .05), (90, -.05), (140, .04), (200, 0)]),
        ("blink", 17, [(0, 1), (40, .68), (200, .72)]),
    ]),
    # จิ้มซ้ำๆ ขั้นที่ 2 -- โกรธจริง
    ("PokeAngry", 280, [
        (0,   "Pout",  P_STEAM,      G_NONE,     F_NONE),
        (30,  "Angry", P_ANGRYMARK,  G_REDALERT, F_NONE),
        (110, "Rage",  P_ANGRYMARK,  G_REDALERT, F_NONE),
        (240, "Angry", P_ANGRYMARK,  G_REDALERT, F_NONE),
        (280, "Angry", P_NONE,       G_NONE,     F_NONE),
    ], [
        ("face", 13, osc(30, 150, 6, -13, 13) + [(170, 0), (280, 0)]),
        ("face", 15, [(0, 0), (40, -.05), (70, .05), (100, -.05), (130, .04), (170, 0), (280, 0)]),
        ("face", 16, [(0, 1), (110, 1.09), (240, 1.04), (280, 1)]),
        ("face", 17, [(0, 1), (110, 1.09), (240, 1.04), (280, 1)]),
        ("blinkL", 13, [(0, 0), (110, ANGRY_CONV), (240, ANGRY_CONV), (280, 0)]),
        ("blinkR", 13, [(0, 0), (110, -ANGRY_CONV), (240, -ANGRY_CONV), (280, 0)]),
    ]),

    # เกาคาง -- แตะใต้ดวงตา
    ("ChinScratch", 260, [
        (0,   "Neutral", P_NONE,     G_NONE,  F_NONE),
        (18,  "Happy",   P_HOLOCHIN, G_NONE,  F_NONE),
        (70,  "Love",    P_HOLOCHIN, G_BOKEH, F_NONE),
        (210, "Love",    P_NONE,     G_BOKEH, F_NONE),
        (260, "Happy",   P_NONE,     G_NONE,  F_NONE),
    ], [
        # เชิดคางขึ้นรับ แล้วสั่นเล็กๆ ตามจังหวะเกา
        ("face", 14, [(0, 0), (40, -14), (150, -17), (220, -8), (260, 0)]),
        ("face", 15, [(0, 0), (40, .04), (80, -.04), (120, .04), (160, -.04), (220, 0), (260, 0)]),
        ("blink", 17, [(0, 1), (30, .5), (62, .5), (74, 1), (210, 1), (230, .8), (260, 1)]),
        ("face", 16, [(0, 1), (70, 1.05), (210, 1.03), (260, 1)]),
        ("face", 17, [(0, 1), (70, 1.05), (210, 1.03), (260, 1)]),
    ]),

    # เสียงดังเกินไป -- ผงะถอย ตาโต
    ("Startled", 210, [
        (0,   "Neutral", P_NONE,    G_NONE, F_NONE),
        (6,   "Shock",   P_EXCLAIM, G_NONE, F_REDFLASH),
        (40,  "Shock",   P_EXCLAIM, G_NONE, F_NONE),
        (110, "Scared",  P_SWEAT,   G_NONE, F_NONE),
        (190, "Neutral", P_NONE,    G_NONE, F_NONE),
        (210, "Neutral", P_NONE,    G_NONE, F_NONE),
    ], [
        # ผงะ = เล็กลงและถอยขึ้น ไม่ใช่ใหญ่ขึ้น
        ("face", 16, [(0, 1), (8, .82), (30, 1.04), (54, 1), (210, 1)]),
        ("face", 17, [(0, 1), (8, .82), (30, 1.04), (54, 1), (210, 1)]),
        ("face", 14, [(0, 0), (8, -26), (34, 6), (58, 0), (210, 0)]),
        ("face", 13, osc(8, 56, 6, -9, 9) + [(70, 0), (210, 0)]),
        # ตาโตได้แค่ 1.12 ก่อนจะชนกัน จึงดันความ "ตกใจ" ไปที่แนวตั้งกับการผงะแทน
        ("blink", 16, [(0, 1), (8, 1.08), (44, 1.04), (110, 1), (210, 1)]),
        ("blink", 17, [(0, 1), (8, 1.46), (44, 1.18), (110, 1), (210, 1)]),
    ]),

    # เขย่ามือถือ -- เวียนหัวก่อน แล้วค่อยโกรธ
    ("ShakeAngry", 330, [
        (0,   "Neutral", P_NONE,       G_NONE,     F_NONE),
        (20,  "Dizzy",   P_DIZZYSTARS, G_NONE,     F_NONE),
        (150, "Dizzy",   P_DIZZYSTARS, G_NONE,     F_NONE),
        (180, "Angry",   P_ANGRYMARK,  G_REDALERT, F_NONE),
        (300, "Angry",   P_ANGRYMARK,  G_REDALERT, F_NONE),
        (330, "Angry",   P_NONE,       G_NONE,     F_NONE),
    ], [
        ("face", 13, osc(0, 150, 5, -26, 26) + osc(156, 250, 7, -12, 12) + [(270, 0), (330, 0)]),
        ("face", 14, osc(0, 150, 7, -18, 18) + [(160, 0), (330, 0)]),
        ("face", 15, [(0, 0), (40, -.12), (80, .12), (120, -.12), (150, 0),
                      (190, .05), (230, -.05), (270, 0), (330, 0)]),
        ("face", 16, [(0, 1), (180, 1.08), (300, 1.03), (330, 1)]),
        ("face", 17, [(0, 1), (180, 1.08), (300, 1.03), (330, 1)]),
        ("blinkL", 13, [(0, 0), (180, ANGRY_CONV), (300, ANGRY_CONV), (330, 0)]),
        ("blinkR", 13, [(0, 0), (180, -ANGRY_CONV), (300, -ANGRY_CONV), (330, 0)]),
    ]),
]
RE_NAMES = [r[0] for r in REACTS]


def check_eye_scale(table, label):
    """A timeline that pins a face must not blow those eyes up past the point
    where the pair touches. Only checkable where the face is known, so
    motion-only entries (no beats) are the host's responsibility -- see the
    ceiling noted in AVATAR_CONTRACT.md."""
    bad = []
    for name, _dur, beats, extra in table:
        if not beats:
            continue
        # a face that is already tight at rest is the FACE's problem, not this
        # beat's -- never fail a timeline for returning to scale 1
        lim = max(1.0, min(max_eye_scale(FACES[F_INDEX[b[1]]][1], FACES[F_INDEX[b[1]]][2])
                           for b in beats))
        peak = 0.0
        for ex in extra:
            if ex[0] in ("blink", "blinkL", "blinkR") and ex[1] == 16:
                peak = max(peak, max(v for _f, v in ex[2]))
        if peak > lim + 1e-6:
            bad.append("%s (peak %.2f > %.2f)" % (name, peak, lim))
    if bad:
        raise SystemExit("%s: eyes would overlap when widened: %s"
                         % (label, ", ".join(bad)))


check_eye_scale(STATES, "states")
check_eye_scale(REACTS, "reactions")
check_eye_scale(SEQS, "sequences")


# ================================================================= 2.5D POSES
# yaw layer writes x / scaleX / rotation; pitch layer writes y / scaleY.
YAW = {                       # node          left        centre      right
    "face":  {"x": (-40, 0, 40), "rot": (0.06, 0, -0.06), "sx": (1.0, 1.0, 1.0)},
    "eyeL":  {"x": (-10, 0, 22), "sx": (0.80, 1.0, 1.05), "rot": (0, 0, 0)},
    "eyeR":  {"x": (-22, 0, 10), "sx": (1.05, 1.0, 0.80), "rot": (0, 0, 0)},
    "brow":  {"x": (-24, 0, 24), "sx": (0.94, 1.0, 0.94), "rot": (0, 0, 0)},
    "mouth": {"x": (-26, 0, 26), "sx": (0.92, 1.0, 0.92), "rot": (0, 0, 0)},
}
PITCH = {                     # node            up         centre      down
    "face":  {"y": (-34, 0, 34), "sy": (1.0, 1.0, 1.0)},
    "eyeL":  {"y": (-10, 0, 10), "sy": (0.90, 1.0, 0.94)},
    "eyeR":  {"y": (-10, 0, 10), "sy": (0.90, 1.0, 0.94)},
    "brow":  {"y": (-22, 0, 24), "sy": (0.94, 1.0, 0.90)},
    "mouth": {"y": (10, 0, -8), "sy": (0.86, 1.0, 0.92)},
}

# ================================================================= DOCUMENT
# Motion-only states (no beats) key the *state blink* nodes instead of the
# blink nodes, so a face that must not blink (Dead crosses, heart eyes, happy
# arcs) can veto them through the BlinkGate layer without also vetoing the
# look-states and stories, which already know which face they are wearing.
def _to_sblink(extra):
    ren = {"blink": "sblink", "blinkL": "sblinkL", "blinkR": "sblinkR"}
    return [(ren.get(e[0], e[0]),) + tuple(e[1:]) for e in extra]


STATES = [(n, dur, beats, extra if beats else _to_sblink(extra)) for n, dur, beats, extra in STATES]

w(0, '<Rive version="1" kind="fragment">')
w(1, '<!-- =====================================================================')
w(1, '     LOOI-style neon robot face, v6.')
w(1, '     GENERATED by tools/build_scene.py -- edit that, never this file.')
w(1, '     Independent channels, mix freely:')
w(1, '       face = 0..%d   prop = 0..%d   bg = 0..%d   fg = 0..%d'
   % (len(FACES) - 1, len(P_NAMES) - 1, len(G_NAMES) - 1, len(F_NAMES) - 1))
w(1, '       eyeAct = 0..%d   state = 0..%d   react = 0..%d'
   % (len(EYE_ACTS) - 1, len(STATES) - 1, len(REACTS) - 1))
w(1, '       seq = 0..%d  -- a one-shot story; non-zero takes over face/prop/bg/fg'
   % (len(SEQS) - 1))
w(1, '     ===================================================================== -->')
w(1, '<Artboard defaultStateMachineId="%s" viewModelId="%s" viewModelInstanceId="%s"'
     ' clip="true" width="500" height="500" styleId="%s" name="RobotFace" id="%s">'
   % (SM_ID, VM_ID, VMI_ID, STYLE_ID, ART_ID))
w(2, '<LayoutComponentStyle name="Artboard Style" id="%s"/>' % STYLE_ID)
w(2, '<Fill name="Background"><SolidColor colorValue="%s" name="Color"/></Fill>' % BG)
w(2, '')

fg_solo, prop_solo = nid(), nid()
face_tilt = nid()
face_yaw, face_pitch, face_speak = nid(), nid(), nid()
eyeL_yaw, eyeL_pitch, eyeL_blink, eyeL_sblink, eyeL_solo = nid(), nid(), nid(), nid(), nid()
eyeR_yaw, eyeR_pitch, eyeR_blink, eyeR_sblink, eyeR_solo = nid(), nid(), nid(), nid(), nid()
# iris: the front shape's own offset. Gaze poses write IrisGaze, stories /
# states / reactions write IrisLook (derived from where they move the eye).
eyeL_back, eyeR_back = nid(), nid()
irisL_gaze, irisR_gaze, irisL_look, irisR_look = nid(), nid(), nid(), nid()
BACK_SOLO = {}       # front solo -> back solo
brow_yaw, brow_pitch, brow_solo = nid(), nid(), nid()
mouth_yaw, mouth_pitch, mouth_audio, mouth_scale, mouth_solo = nid(), nid(), nid(), nid(), nid()
bg_solo = nid()
face_breathe = nid()

# ---- foreground -------------------------------------------------------
w(2, '<!-- ============ FOREGROUND ============ -->')
w(2, '<Solo activeComponentId="0:0" name="FgSolo" id="%s">' % fg_solo)
fg_ids = [emit_fg(3, f) for f in range(len(F_NAMES))]
w(2, '</Solo>')
w(2, '')

# ---- props ------------------------------------------------------------
w(2, '<!-- ============ PROPS ============ -->')
w(2, '<Solo activeComponentId="0:0" name="PropsSolo" id="%s">' % prop_solo)
prop_ids = [emit_prop(3, p) for p in range(len(P_NAMES))]
w(2, '</Solo>')
w(2, '')

# ---- face -------------------------------------------------------------
w(2, '<!-- ============ FACE (2.5D yaw/pitch rig) ============ -->')
w(2, '<!-- FaceTilt is driven by the gyro through plain data binds, so it never')
w(2, '     collides with the two gaze blend-state layers below it. -->')
w(2, '<Node x="0" y="0" name="FaceTilt" id="%s">' % face_tilt)
w(3, '<DataBindContext sourcePathIds="%s-%s" propertyKey="13" converterId="%s"/>' % (VM_ID, VP_TILTX, CONV_TILTX))
w(3, '<DataBindContext sourcePathIds="%s-%s" propertyKey="14" converterId="%s"/>' % (VM_ID, VP_TILTY, CONV_TILTY))
w(3, '<DataBindContext sourcePathIds="%s-%s" propertyKey="15" converterId="%s"/>' % (VM_ID, VP_TILTX, CONV_TILTR))
w(3, '<Node x="%g" y="%g" name="FaceYaw" id="%s">' % (CX, CY, face_yaw))
w(4, '<Node name="FacePitch" id="%s">' % face_pitch)
# FaceSpeak: only the Speech layer writes it -- the little talking bob
w(5, '<Node name="FaceSpeak" id="%s">' % face_speak)
w(6, '<Node name="FaceBreathe" id="%s">' % face_breathe)

w(7, '<Node x="%g" y="%g" name="BrowYaw" id="%s">' % (0, BROW_Y, brow_yaw))
w(8, '<Node name="BrowPitch" id="%s">' % brow_pitch)
w(9, '<Solo activeComponentId="0:0" name="BrowSolo" id="%s">' % brow_solo)
brow_ids = [emit_brow_variant(10, b) for b in range(len(B_NAMES))]
w(9, '</Solo>')
w(8, '</Node>')
w(7, '</Node>')

eye_ids = {}
for tag, dx, s, yaw, pitch, blink, sblink, solo, back_solo, igaze, ilook in (
        ("L", -EYE_DX, 1, eyeL_yaw, eyeL_pitch, eyeL_blink, eyeL_sblink, eyeL_solo, eyeL_back, irisL_gaze, irisL_look),
        ("R", EYE_DX, -1, eyeR_yaw, eyeR_pitch, eyeR_blink, eyeR_sblink, eyeR_solo, eyeR_back, irisR_gaze, irisR_look)):
    w(7, '<Node x="%g" y="%g" name="%sEyeYaw" id="%s">' % (dx, EYE_Y, tag, yaw))
    w(8, '<Node name="%sEyePitch" id="%s">' % (tag, pitch))
    w(9, '<Node name="%sBlink" id="%s">' % (tag, blink))
    w(10, '<Node name="%sStateBlink" id="%s">' % (tag, sblink))
    # earlier in the file = drawn on top: front first, back after it
    w(11, '<Node name="%sIrisGaze" id="%s">' % (tag, igaze))
    w(12, '<Node name="%sIrisLook" id="%s">' % (tag, ilook))
    w(13, '<Solo activeComponentId="0:0" name="%sEyeSolo" id="%s">' % (tag, solo))
    eye_ids[tag] = [emit_eye_variant(14, v, s, tag) for v in range(len(V_NAMES))]
    w(13, '</Solo>')
    w(12, '</Node>')
    w(11, '</Node>')
    w(11, '<Solo activeComponentId="0:0" name="%sEyeBackSolo" id="%s">' % (tag, back_solo))
    backs = [emit_eye_variant(12, v, s, tag, "back") for v in range(len(V_NAMES))]
    w(11, '</Solo>')
    EYE_BACK.update(zip(eye_ids[tag], backs))
    BACK_SOLO[solo] = back_solo
    w(10, '</Node>')
    w(9, '</Node>')
    w(8, '</Node>')
    w(7, '</Node>')

# MouthAudio sits between the pitch node and the scale node: nothing else ever
# writes it, so the mic level composes with whatever the Speech layer is doing.
w(7, '<Node x="0" y="%g" name="MouthYaw" id="%s">' % (MOUTH_Y, mouth_yaw))
w(8, '<Node name="MouthPitch" id="%s">' % mouth_pitch)
w(9, '<Node name="MouthAudio" id="%s">' % mouth_audio)
w(10, '<DataBindContext sourcePathIds="%s-%s" propertyKey="17" converterId="%s"/>' % (VM_ID, VP_AUDIO, CONV_AUDIO))
w(10, '<DataBindContext sourcePathIds="%s-%s" propertyKey="16" converterId="%s"/>' % (VM_ID, VP_AUDIO, CONV_AUDIO_X))
w(10, '<Node name="MouthScale" id="%s">' % mouth_scale)
w(11, '<Solo activeComponentId="0:0" name="MouthSolo" id="%s">' % mouth_solo)
mouth_ids = [emit_mouth_variant(12, m) for m in range(len(M_NAMES))]
w(11, '</Solo>')
w(10, '</Node>')
w(9, '</Node>')
w(8, '</Node>')
w(7, '</Node>')

w(6, '</Node>')
w(5, '</Node>')
w(4, '</Node>')
w(3, '</Node>')
w(2, '</Node>')
w(2, '')

# ---- background -------------------------------------------------------
w(2, '<!-- ============ BACKGROUND ============ -->')
w(2, '<Solo activeComponentId="0:0" name="BgSolo" id="%s">' % bg_solo)
bg_ids = [emit_bg(3, g) for g in range(len(G_NAMES))]
w(2, '</Solo>')
w(2, '')


def patch_default(marker, value):
    for i, ln in enumerate(out):
        if marker in ln:
            out[i] = ln.replace('activeComponentId="0:0"', 'activeComponentId="%s"' % value)
            return
    raise SystemExit("marker not found: " + marker)


patch_default('name="FgSolo"', fg_ids[F_NONE])
patch_default('name="PropsSolo"', prop_ids[P_NONE])
patch_default('name="BrowSolo"', brow_ids[B_NONE])
patch_default('name="LEyeSolo"', eye_ids["L"][V_ROUND])
patch_default('name="REyeSolo"', eye_ids["R"][V_ROUND])
patch_default('name="LEyeBackSolo"', EYE_BACK[eye_ids["L"][V_ROUND]])
patch_default('name="REyeBackSolo"', EYE_BACK[eye_ids["R"][V_ROUND]])
patch_default('name="MouthSolo"', mouth_ids[M_NONE])
patch_default('name="BgSolo"', bg_ids[G_NONE])
for sid, ids in ITEM_SOLOS:
    for i, ln in enumerate(out):
        if 'id="%s"' % sid in ln and "<Solo" in ln:
            out[i] = ln.replace('activeComponentId="0:0"', 'activeComponentId="%s"' % ids[0])
            break
for slot in range(len(CLOCK_SOLOS)):
    patch_default('name="Digit%d"' % slot, CLOCK_DIGITS[slot][(1, 2, 0, 0)[slot]])

# ================================================================= ANIMATIONS
w(2, '<!-- ============================ ANIMATIONS ============================ -->')
w(2, '<!-- FACE: ' + ", ".join("%d=%s" % (i, f[0]) for i, f in enumerate(FACES)) + ' -->')
w(2, '<!-- PROP: ' + ", ".join("%d=%s" % (i, n) for i, n in enumerate(P_NAMES)) + ' -->')
w(2, '<!-- BG:   ' + ", ".join("%d=%s" % (i, n) for i, n in enumerate(G_NAMES)) + ' -->')
w(2, '<!-- FG:   ' + ", ".join("%d=%s" % (i, n) for i, n in enumerate(F_NAMES)) + ' -->')

# every (object, property) something animates, so RestPose can cover them all
ANIMATED = {}          # (obj, key) -> rest value
ALWAYS_ON = set()      # keyed every frame by a layer that never stops


def keyed_id(d, obj, value):
    w(d, '<KeyedObject objectId="%s">' % obj)
    w(d + 1, '<KeyedProperty propertyKey="296">')
    w(d + 2, '<KeyFrameId value="%s" frame="0"/>' % value)
    w(d + 1, '</KeyedProperty>')
    w(d, '</KeyedObject>')
    if obj in BACK_SOLO:
        keyed_id(d, BACK_SOLO[obj], EYE_BACK[value])


def emit_tracks(d, tracks):
    """tracks: {(obj, key): [(frame, value, interp)]} -> KeyedObject blocks."""
    by_obj = {}
    for (obj, key), kfs in tracks.items():
        by_obj.setdefault(obj, []).append((key, kfs))
    for obj, entries in by_obj.items():
        w(d, '<KeyedObject objectId="%s">' % obj)
        for key, kfs in entries:
            w(d + 1, '<KeyedProperty propertyKey="%d">' % key)
            seen = set()
            for fr, vv, itp in sorted(kfs, key=lambda t: t[0]):
                if fr in seen:
                    continue
                seen.add(fr)
                kf(d + 2, vv, fr, itp)
            w(d + 1, '</KeyedProperty>')
        w(d, '</KeyedObject>')


def rest_value(obj, key):
    if obj == VOICE_BAR[0] and key == 18:
        return 0.0
    base = MOVER_BASE.get(obj)
    if key == 13:
        return base[0] if base else 0.0
    if key == 14:
        return base[1] if base else 0.0
    if key == 15:
        return 0.0
    return 1.0          # scaleX / scaleY / opacity


def note(obj, key, value=None):
    if (obj, key) not in ANIMATED:
        ANIMATED[(obj, key)] = rest_value(obj, key) if value is None else value


def intro_tracks(kind, idx, offset=0, skip=()):
    res = {}
    for obj, key, kfs, itp in INTROS[kind].get(idx, []):
        if (obj, key) in skip:
            continue
        note(obj, key, kfs[-1][1])        # rest where the entrance lands
        res[(obj, key)] = [(fr + offset, vv, itp) for fr, vv in kfs]
    return res


# ---- face ------------------------------------------------------------
face_anims = []
for i, (fn, el, er, br, mo, noblink) in enumerate(FACES):
    aid = nid()
    face_anims.append(aid)
    w(2, '<LinearAnimation duration="1" fps="60" name="Face%02d_%s" id="%s">' % (i, fn, aid))
    for solo, val in ((eyeL_solo, eye_ids["L"][el]), (eyeR_solo, eye_ids["R"][er]),
                      (brow_solo, brow_ids[br]), (mouth_solo, mouth_ids[mo])):
        keyed_id(3, solo, val)
    if noblink:
        # the Face layer is declared after Blink, so pinning scaleY here wins
        emit_tracks(3, {(b, 17): [(0, 1, "hold")] for b in (eyeL_blink, eyeR_blink)})
    w(2, '</LinearAnimation>')

# ---- blink gate: noblink faces veto the motion-only states' blinks -----
gate_anims = []
for i, (fn, _el, _er, _br, _mo, noblink) in enumerate(FACES):
    aid = nid()
    gate_anims.append(aid)
    w(2, '<LinearAnimation duration="1" fps="60" name="Gate%02d_%s" id="%s">' % (i, fn, aid))
    if noblink:
        emit_tracks(3, {(b, 17): [(0, 1, "hold")] for b in (eyeL_sblink, eyeR_sblink)})
    w(2, '</LinearAnimation>')

# ---- prop / bg / fg: show the item AND play its entrance ---------------
prop_anims, bg_anims, fg_anims = [], [], []
for label, kind, names, solo, ids_list, bucket in (
        ("Prop", "prop", P_NAMES, prop_solo, prop_ids, prop_anims),
        ("Bg", "bg", G_NAMES, bg_solo, bg_ids, bg_anims),
        ("Fg", "fg", F_NAMES, fg_solo, fg_ids, fg_anims)):
    for i, n in enumerate(names):
        aid = nid()
        bucket.append(aid)
        tr = intro_tracks(kind, i)
        dur = max([1] + [kf_[0] for kfs in tr.values() for kf_ in kfs])
        w(2, '<LinearAnimation duration="%d" fps="60" name="%s%02d_%s" id="%s">' % (dur, label, i, n, aid))
        keyed_id(3, solo, ids_list[i])
        emit_tracks(3, tr)
        w(2, '</LinearAnimation>')

# ---- standby clock digits -----------------------------------------------
clock_anims = []
for slot, solo in enumerate(CLOCK_SOLOS):
    row = []
    for n in range(10):
        aid = nid()
        row.append(aid)
        w(2, '<LinearAnimation duration="1" fps="60" name="Clock%d_%d" id="%s">' % (slot, n, aid))
        keyed_id(3, solo, CLOCK_DIGITS[slot][n])
        w(2, '</LinearAnimation>')
    clock_anims.append(row)

# ---- scene items (food / drink / device) ----------------------------------
item_anims = []
ITEM_COUNT = max([len(ids) for _sid, ids in ITEM_SOLOS] + [1])
for n in range(ITEM_COUNT):
    aid = nid()
    item_anims.append(aid)
    w(2, '<LinearAnimation duration="1" fps="60" name="Item%d" id="%s">' % (n, aid))
    for sid, ids in ITEM_SOLOS:
        keyed_id(3, sid, ids[min(n, len(ids) - 1)])
    w(2, '</LinearAnimation>')

w(2, '')

# ---- eye acts ---------------------------------------------------------
act_anims = []
for i, (an, kfs, lv, override) in enumerate(EYE_ACTS):
    aid = nid()
    act_anims.append(aid)
    dur = max([f for f, _sx, _sy in kfs] + [1])
    w(2, '<LinearAnimation loopValue="%s" duration="%d" fps="60" name="Act%02d_%s" id="%s">'
      % (lv, dur, i, an, aid))
    if override is not None:
        keyed_id(3, eyeL_solo, eye_ids["L"][override])
        keyed_id(3, eyeR_solo, eye_ids["R"][override])
    if kfs:
        tr = {}
        for oid in (eyeL_blink, eyeR_blink):
            for key, idx in ((16, 1), (17, 2)):
                tr[(oid, key)] = [(k_[0], k_[idx], "cubic") for k_ in kfs]
                note(oid, key)
        emit_tracks(3, tr)
    w(2, '</LinearAnimation>')

# ---- sequences / states / reactions ------------------------------------
SEQ_NODES = {"blink": (eyeL_blink, eyeR_blink), "blinkL": (eyeL_blink,),
             "blinkR": (eyeR_blink,), "sblink": (eyeL_sblink, eyeR_sblink),
             "sblinkL": (eyeL_sblink,), "sblinkR": (eyeR_sblink,),
             "face": (face_breathe,), "mouth": (mouth_scale,)}


def seq_targets(nkey):
    if nkey.startswith("prop:"):
        _p, idx, nm = nkey.split(":")
        node = PROP_NODES.get(int(idx), {}).get(nm)
        if node is None:
            raise SystemExit("sequence references unknown prop node: " + nkey)
        return (node,)
    return SEQ_NODES[nkey]


JELLY_NODES = ("blink", "blinkL", "blinkR", "sblink", "sblinkL", "sblinkR", "face", "mouth")
IRIS_X, IRIS_Y, IRIS_GAIN = 13.0, 13.0, 0.45
IRIS_FROM = {"blink": (irisL_look, irisR_look), "blinkL": (irisL_look,), "blinkR": (irisR_look,),
             "sblink": (irisL_look, irisR_look), "sblinkL": (irisL_look,), "sblinkR": (irisR_look,)}
explicit_iris = set()


def emit_one_story(i, sn, dur, beats, extra, label, loop):
    aid = nid()
    w(2, '<LinearAnimation loopValue="%s" duration="%d" fps="60" name="%s%02d_%s" id="%s">'
      % (loop, dur, label, i, sn, aid))
    tracks = {}
    for ex in extra:
        nkey, key, kfs = ex[0], ex[1], ex[2]
        itp = ex[3] if len(ex) > 3 else "cubic"
        if itp == "cubic" and nkey in JELLY_NODES and key in (13, 14, 16, 17):
            itp = "jelly"
        for oid in seq_targets(nkey):
            note(oid, key)
            tracks[(oid, key)] = [(fr, vv, itp) for fr, vv in rebase(oid, key, kfs)]
        # where the story moves an eye, its front shape leans the same way
        if key in (13, 14) and nkey in IRIS_FROM:
            lim = IRIS_X if key == 13 else IRIS_Y
            for oid in IRIS_FROM[nkey]:
                if (oid, key) in explicit_iris:
                    continue
                note(oid, key)
                tracks[(oid, key)] = [(fr, max(-lim, min(lim, vv * IRIS_GAIN)), itp) for fr, vv in kfs]
    if beats:
        chans = {eyeL_solo: [], eyeR_solo: [], brow_solo: [], mouth_solo: [],
                 prop_solo: [], bg_solo: [], fg_solo: []}
        shown = {"prop": [], "bg": [], "fg": []}
        chans[eyeL_back] = []
        chans[eyeR_back] = []
        for fr, fname, pr, bgv, fgv in beats:
            fi = F_INDEX[fname]
            _n, el, er, br, mo, _nb = FACES[fi]
            chans[eyeL_solo].append((fr, eye_ids["L"][el]))
            chans[eyeR_solo].append((fr, eye_ids["R"][er]))
            chans[eyeL_back].append((fr, EYE_BACK[eye_ids["L"][el]]))
            chans[eyeR_back].append((fr, EYE_BACK[eye_ids["R"][er]]))
            chans[brow_solo].append((fr, brow_ids[br]))
            chans[mouth_solo].append((fr, mouth_ids[mo]))
            chans[prop_solo].append((fr, prop_ids[pr]))
            chans[bg_solo].append((fr, bg_ids[bgv]))
            chans[fg_solo].append((fr, fg_ids[fgv]))
            for kind, v in (("prop", pr), ("bg", bgv), ("fg", fgv)):
                if not shown[kind] or shown[kind][-1][1] != v:
                    shown[kind].append((fr, v))
        for solo, kfs in chans.items():
            w(3, '<KeyedObject objectId="%s">' % solo)
            w(4, '<KeyedProperty propertyKey="296">')
            last = None
            for fr, val in kfs:
                if val == last:
                    continue
                last = val
                w(5, '<KeyFrameId value="%s" frame="%d"/>' % (val, fr))
            w(4, '</KeyedProperty>')
            w(3, '</KeyedObject>')
        # every time a beat switches to an item with an entrance, play it from
        # that frame -- explicit extras on the same property win
        explicit = set(tracks)
        for kind, changes in shown.items():
            for fr, v in changes:
                for ok, kfs in intro_tracks(kind, v, fr, explicit).items():
                    prev = tracks.get(ok, [])
                    if prev:
                        # hold the previous entrance's end until this one starts
                        prev[-1] = (prev[-1][0], prev[-1][1], "hold")
                    tracks[ok] = prev + kfs
    emit_tracks(3, tracks)
    w(2, '</LinearAnimation>')
    return aid


def emit_stories(table, label, loop):
    return [emit_one_story(i, sn, dur, beats, extra, label, loop)
            for i, (sn, dur, beats, extra) in enumerate(table)]


# a story and a reaction play ONCE and hold their last frame until the host
# clears them; a state is an indefinite loop
seq_anims = emit_stories(SEQS, "Seq", "oneShot")
state_anims = emit_stories(STATES, "State", "loop")
react_anims = emit_stories(REACTS, "React", "oneShot")

# ---- blink ------------------------------------------------------------
blink_anim = nid()
w(2, '<LinearAnimation loopValue="loop" duration="260" fps="60" name="AnimBlink" id="%s">' % blink_anim)
emit_tracks(3, {(oid, 17): [(0, 1, "linear"), (218, 1, "easeIn"), (226, .06, "hold"),
                            (231, .06, "easeOut"), (240, 1, "linear"), (260, 1, "linear")]
                for oid in (eyeL_blink, eyeR_blink)})
w(2, '</LinearAnimation>')
ALWAYS_ON |= {(eyeL_blink, 17), (eyeR_blink, 17)}

# ---- idle breathing: the whole face drifts, like a head resting -------
breathe_anim = nid()
w(2, '<LinearAnimation loopValue="pingPong" duration="180" fps="60" name="AnimBreathe" id="%s">' % breathe_anim)
emit_tracks(3, {(face_breathe, 14): [(0, 4, "cubic"), (180, -4, "cubic")],
                (face_breathe, 16): [(0, 1.014, "cubic"), (180, .992, "cubic")],
                (face_breathe, 17): [(0, .99, "cubic"), (180, 1.018, "cubic")]})
w(2, '</LinearAnimation>')
ALWAYS_ON |= {(face_breathe, 14), (face_breathe, 16), (face_breathe, 17)}

# ---- 2.5D gaze poses --------------------------------------------------
NODE_YAW = {"face": face_yaw, "eyeL": eyeL_yaw, "eyeR": eyeR_yaw,
            "brow": brow_yaw, "mouth": mouth_yaw}
NODE_PITCH = {"face": face_pitch, "eyeL": eyeL_pitch, "eyeR": eyeR_pitch,
              "brow": brow_pitch, "mouth": mouth_pitch}
BASE_X = {"face": CX, "eyeL": -EYE_DX, "eyeR": EYE_DX, "brow": 0.0, "mouth": 0.0}


def yaw_pose(name, idx):
    aid = nid()
    w(2, '<LinearAnimation duration="1" fps="60" name="%s" id="%s">' % (name, aid))
    tr = {}
    for iris in (irisL_gaze, irisR_gaze):
        tr[(iris, 13)] = [(0, (-IRIS_X, 0.0, IRIS_X)[idx], "hold")]
    for k, spec in YAW.items():
        tr[(NODE_YAW[k], 13)] = [(0, BASE_X[k] + spec["x"][idx], "hold")]
        tr[(NODE_YAW[k], 16)] = [(0, spec["sx"][idx], "hold")]
        tr[(NODE_YAW[k], 15)] = [(0, spec["rot"][idx], "hold")]
    emit_tracks(3, tr)
    w(2, '</LinearAnimation>')
    return aid


def pitch_pose(name, idx):
    aid = nid()
    w(2, '<LinearAnimation duration="1" fps="60" name="%s" id="%s">' % (name, aid))
    tr = {}
    for iris in (irisL_gaze, irisR_gaze):
        tr[(iris, 14)] = [(0, (-IRIS_Y, 0.0, IRIS_Y)[idx], "hold")]
    for k, spec in PITCH.items():
        tr[(NODE_PITCH[k], 14)] = [(0, spec["y"][idx], "hold")]
        tr[(NODE_PITCH[k], 17)] = [(0, spec["sy"][idx], "hold")]
    emit_tracks(3, tr)
    w(2, '</LinearAnimation>')
    return aid


look_l, look_ch, look_r = yaw_pose("LookLeft", 0), yaw_pose("LookCenterH", 1), yaw_pose("LookRight", 2)
look_u, look_cv, look_d = pitch_pose("LookUp", 0), pitch_pose("LookCenterV", 1), pitch_pose("LookDown", 2)

# ---- speech -----------------------------------------------------------
# VoiceIdle keys NOTHING: it must not pin the mouth, or the Speaking state and
# the stories could never move it. RestPose owns the resting values.
voice_idle, voice_talk = nid(), nid()
w(2, '<LinearAnimation loopValue="loop" duration="1" fps="60" name="VoiceIdle" id="%s"/>' % voice_idle)
w(2, '<LinearAnimation loopValue="loop" duration="34" fps="60" name="VoiceTalk" id="%s">' % voice_talk)
talk = {
    (mouth_scale, 17): [(0, .9, "cubic"), (8, 1.5, "cubic"), (17, 1.0, "cubic"), (25, 1.35, "cubic"), (34, .9, "cubic")],
    (mouth_scale, 16): [(0, 1.0, "cubic"), (8, 1.1, "cubic"), (17, .97, "cubic"), (25, 1.06, "cubic"), (34, 1.0, "cubic")],
    (VOICE_BAR[0], 18): [(0, 1, "hold")],
    # LOOI talks with its whole head: a small bob and squash on the syllables
    (face_speak, 14): [(0, 0, "cubic"), (8, -5, "cubic"), (17, 0, "cubic"), (25, -3, "cubic"), (34, 0, "cubic")],
    (face_speak, 17): [(0, 1, "cubic"), (8, 1.03, "cubic"), (17, .985, "cubic"), (25, 1.02, "cubic"), (34, 1, "cubic")],
}
for ok in talk:
    note(*ok)
emit_tracks(3, talk)
w(2, '</LinearAnimation>')

# ---- prop / bg / fg loops --------------------------------------------
by_obj = {}
for obj, key, kfs, interp in TRACKS:
    by_obj.setdefault(obj, []).append((key, kfs, interp))
    ALWAYS_ON.add((obj, key))

loop_anim = nid()
LOOP_DUR = 360
DIVS = [dv for dv in range(2, LOOP_DUR + 1) if LOOP_DUR % dv == 0]


def snap_period(p):
    """Round a track's natural cycle to a divisor of LOOP_DUR so tiling it
    across the shared timeline never pops at the seam."""
    return min(DIVS, key=lambda dv: (abs(dv - p), dv))


w(2, '<LinearAnimation loopValue="loop" duration="%d" fps="60" name="PropLoops" id="%s">'
   % (LOOP_DUR, loop_anim))
loops = {}
for obj, entries in by_obj.items():
    for key, kfs, interp in entries:
        period = max(f for f, _v in kfs) or 1
        tgt = snap_period(period)
        sc = float(tgt) / period
        base = [(int(round(f * sc)), v) for f, v in kfs]
        if base[0][0] != 0:
            base.insert(0, (0, base[-1][1]))
        res, seen = [], set()
        for rep_ in range(LOOP_DUR // tgt):
            off = rep_ * tgt
            for f, v in base:
                ff = f + off
                if ff > LOOP_DUR or ff in seen:
                    continue
                seen.add(ff)
                res.append((ff, v, interp))
        if LOOP_DUR not in seen:
            res.append((LOOP_DUR, base[0][1], interp))
        loops[(obj, key)] = res
emit_tracks(3, loops)
w(2, '</LinearAnimation>')

# ---- rest pose: what every animated property returns to ---------------
# Rive never restores a property no layer is keying any more. Without this,
# clearing a story mid-way leaves its last pose frozen on the face: eyes still
# converged after Angry, head still tilted after Sleepy. RestPose is the first
# layer, so anything else that runs overrides it.
rest_anim = nid()
w(2, '<LinearAnimation duration="1" fps="60" name="RestPose" id="%s">' % rest_anim)
REST = {ok: v for ok, v in ANIMATED.items() if ok not in ALWAYS_ON}
emit_tracks(3, {ok: [(0, v, "hold")] for ok, v in REST.items()})
w(2, '</LinearAnimation>')
w(2, '')


# ================================================================= STATE MACHINE
def cond_number(d, prop_id, value):
    w(d, '<TransitionViewModelCondition opValue="equal">')
    w(d + 1, '<TransitionPropertyViewModelComparator>')
    w(d + 2, '<BindablePropertyNumber>')
    w(d + 3, '<DataBindContext sourcePathIds="%s-%s" propertyKey="636"/>' % (VM_ID, prop_id))
    w(d + 2, '</BindablePropertyNumber>')
    w(d + 1, '</TransitionPropertyViewModelComparator>')
    w(d + 1, '<TransitionValueNumberComparator value="%d"/>' % value)
    w(d, '</TransitionViewModelCondition>')


def channel_layer(lname, prop_id, anims, y0, duration=140):
    """AnyState -> one AnimationState per index, selected by a view model number.
    reset="true": re-entering a state restarts its timeline instead of resuming
    wherever it was cut off last time."""
    states = [nid() for _ in anims]
    w(3, '<StateMachineLayer name="%s" id="%s">' % (lname, nid()))
    w(4, '<AnyState x="-40" y="%d">' % y0)
    for i, sid in enumerate(states):
        w(5, '<StateTransition stateToId="%s" duration="%d">' % (sid, duration))
        cond_number(6, prop_id, i)
        w(5, '</StateTransition>')
    w(4, '</AnyState>')
    w(4, '<ExitState x="-40" y="%d"/>' % (y0 + 50))
    w(4, '<EntryState x="-240" y="%d">' % y0)
    w(5, '<StateTransition stateToId="%s"/>' % states[0])
    w(4, '</EntryState>')
    for i, (sid, aid) in enumerate(zip(states, anims)):
        w(4, '<AnimationState x="%d" y="%d" reset="true" animationId="%s" id="%s"/>'
          % (160 + (i % 6) * 190, y0 + 90 + (i // 6) * 80, aid, sid))
    w(3, '</StateMachineLayer>')


def cond_speaking(d, value):
    w(d, '<TransitionViewModelCondition opValue="equal">')
    w(d + 1, '<TransitionPropertyViewModelComparator>')
    w(d + 2, '<BindablePropertyBoolean>')
    w(d + 3, '<DataBindContext sourcePathIds="%s-%s" propertyKey="634"/>' % (VM_ID, VP_SPEAKING))
    w(d + 2, '</BindablePropertyBoolean>')
    w(d + 1, '</TransitionPropertyViewModelComparator>')
    w(d + 1, '<TransitionValueBooleanComparator value="%s"/>' % ("true" if value else "false"))
    w(d, '</TransitionViewModelCondition>')


def simple_layer(lname, anim_id, y):
    lid, sid = nid(), nid()
    w(3, '<StateMachineLayer name="%s" id="%s">' % (lname, lid))
    w(4, '<AnyState x="520" y="%d"/>' % y)
    w(4, '<ExitState x="700" y="%d"/>' % y)
    w(4, '<EntryState x="-40" y="%d">' % y)
    w(5, '<StateTransition stateToId="%s"/>' % sid)
    w(4, '</EntryState>')
    w(4, '<AnimationState x="160" y="%d" animationId="%s" id="%s"/>' % (y, anim_id, sid))
    w(3, '</StateMachineLayer>')


def blend_layer(lname, poses, prop_id, y):
    lid, sid = nid(), nid()
    w(3, '<StateMachineLayer name="%s" id="%s">' % (lname, lid))
    w(4, '<AnyState x="520" y="%d"/>' % y)
    w(4, '<ExitState x="700" y="%d"/>' % y)
    w(4, '<EntryState x="-40" y="%d">' % y)
    w(5, '<StateTransition stateToId="%s"/>' % sid)
    w(4, '</EntryState>')
    w(4, '<BlendState1DViewModel x="160" y="%d" id="%s">' % (y, sid))
    w(5, '<BindablePropertyNumber>')
    w(6, '<DataBindContext sourcePathIds="%s-%s" propertyKey="636" converterId="%s"/>'
      % (VM_ID, prop_id, CONV_GAZE))
    w(5, '</BindablePropertyNumber>')
    for a, v in zip(poses, (0, 50, 100)):
        w(5, '<BlendAnimation1D animationId="%s" value="%d"/>' % (a, v))
    w(4, '</BlendState1DViewModel>')
    w(3, '</StateMachineLayer>')


w(2, '<!-- ============================ STATE MACHINE ============================ -->')
w(2, '<StateMachine name="State Machine 1" id="%s">' % SM_ID)

# Declaration order is priority: a later layer wins a property both key.
simple_layer("RestPose", rest_anim, -1600)
simple_layer("Blink", blink_anim, 40)          # before Face: a noblink face pins it
simple_layer("Breathe", breathe_anim, 130)
w(3, '')
channel_layer("Face", VP_FACE, face_anims, -1400)
channel_layer("EyeAct", VP_EYEACT, act_anims, -1150)
# Items that carry props switch in on the frame they are asked for: a blended
# transition mixes their movers from the REST pose (a scene's climax, everything
# on screen) toward frame 0 while the Solo has already swapped the prop in, so
# every part of the scene flashed up in a pile for the first moments.
channel_layer("Prop", VP_PROP, prop_anims, -900, duration=0)
channel_layer("Bg", VP_BG, bg_anims, -400, duration=0)
channel_layer("Fg", VP_FG, fg_anims, -100, duration=0)
channel_layer("Item", VP_ITEM, item_anims, 2400, duration=0)
for slot, row in enumerate(clock_anims):
    channel_layer("Clock%d" % slot, VP_CLOCK[slot], row, 1300 + slot * 260, duration=0)
w(3, '')
simple_layer("PropLoops", loop_anim, 100)
# State: the resting behaviour -- an indefinite loop the host swaps any frame.
channel_layer("State", VP_STATE, state_anims, 150)
# BlinkGate after State: a noblink face vetoes the motion-only states' blinks
channel_layer("BlinkGate", VP_FACE, gate_anims, 400, duration=0)
# Seq after every channel layer AND PropLoops: a story owns prop and motion.
channel_layer("Seq", VP_SEQ, seq_anims, 200, duration=0)
# React last of the content layers: a touch cuts in over a running story.
channel_layer("React", VP_REACT, react_anims, 620, duration=0)
w(3, '')
blend_layer("GazeYaw", (look_l, look_ch, look_r), VP_GAZEX, 900)
blend_layer("GazePitch", (look_u, look_cv, look_d), VP_GAZEY, 990)
w(3, '')

sp_lid, sp_idle, sp_talk = nid(), nid(), nid()
w(3, '<StateMachineLayer name="Speech" id="%s">' % sp_lid)
w(4, '<AnyState x="520" y="1170"/>')
w(4, '<ExitState x="700" y="1170"/>')
w(4, '<EntryState x="-40" y="1170">')
w(5, '<StateTransition stateToId="%s"/>' % sp_idle)
w(4, '</EntryState>')
w(4, '<AnimationState x="160" y="1170" animationId="%s" id="%s">' % (voice_idle, sp_idle))
w(5, '<StateTransition stateToId="%s" duration="90">' % sp_talk)
cond_speaking(6, True)
w(5, '</StateTransition>')
w(4, '</AnimationState>')
w(4, '<AnimationState x="360" y="1170" animationId="%s" id="%s">' % (voice_talk, sp_talk))
w(5, '<StateTransition stateToId="%s" duration="160">' % sp_idle)
cond_speaking(6, False)
w(5, '</StateTransition>')
w(4, '</AnimationState>')
w(3, '</StateMachineLayer>')

w(2, '</StateMachine>')
w(1, '</Artboard>')
w(1, '')
w(1, '<!-- ============================ DATA ============================ -->')
w(1, '<DataConverterRangeMapper minInput="-1" maxInput="1" minOutput="0" maxOutput="100"'
     ' clampLower="true" clampUpper="true" name="GazeToAxis" id="%s"/>' % CONV_GAZE)
# Gyro: tiltX/tiltY are -1..1 device tilt. The whole face slides a little and
# banks slightly against the tilt, which is what sells the head as physical.
w(1, '<DataConverterRangeMapper minInput="-1" maxInput="1" minOutput="-26" maxOutput="26"'
     ' clampLower="true" clampUpper="true" name="TiltToX" id="%s"/>' % CONV_TILTX)
w(1, '<DataConverterRangeMapper minInput="-1" maxInput="1" minOutput="-20" maxOutput="20"'
     ' clampLower="true" clampUpper="true" name="TiltToY" id="%s"/>' % CONV_TILTY)
w(1, '<DataConverterRangeMapper minInput="-1" maxInput="1" minOutput="0.06" maxOutput="-0.06"'
     ' clampLower="true" clampUpper="true" name="TiltToRoll" id="%s"/>' % CONV_TILTR)
# Mic level 0..1: a mouth opens a little (strokes distort if stretched hard),
# the voice bar -- a thin pill -- can stretch a lot.
w(1, '<DataConverterRangeMapper minInput="0" maxInput="1" minOutput="0.94" maxOutput="1.22"'
     ' clampLower="true" clampUpper="true" name="AudioToMouth" id="%s"/>' % CONV_AUDIO)
w(1, '<DataConverterRangeMapper minInput="0" maxInput="1" minOutput="1" maxOutput="1.08"'
     ' clampLower="true" clampUpper="true" name="AudioToMouthX" id="%s"/>' % CONV_AUDIO_X)
w(1, '<DataConverterRangeMapper minInput="0" maxInput="1" minOutput="1" maxOutput="2.2"'
     ' clampLower="true" clampUpper="true" name="AudioToBar" id="%s"/>' % CONV_AUDIO_BAR)

VM_NUMBERS = [("face", VP_FACE, 0), ("prop", VP_PROP, 0), ("bg", VP_BG, 0), ("fg", VP_FG, 0),
              ("eyeAct", VP_EYEACT, 0), ("seq", VP_SEQ, 0), ("state", VP_STATE, 1),
              ("react", VP_REACT, 0), ("gazeX", VP_GAZEX, 0), ("gazeY", VP_GAZEY, 0)]
VM_TAIL = [("mouthOpen", VP_MOUTH, 0), ("tiltX", VP_TILTX, 0), ("tiltY", VP_TILTY, 0),
           ("audioLevel", VP_AUDIO, 0)] + \
    [("clockD%d" % i, VP_CLOCK[i], (1, 2, 0, 0)[i]) for i in range(4)] + [("item", VP_ITEM, 0)]
w(1, '<ViewModel defaultInstanceId="%s" name="Avatar" id="%s">' % (VMI_ID, VM_ID))
for n, pid_, _v in VM_NUMBERS:
    w(2, '<ViewModelPropertyNumber name="%s" id="%s"/>' % (n, pid_))
w(2, '<ViewModelPropertyBoolean name="isSpeaking" id="%s"/>' % VP_SPEAKING)
for n, pid_, _v in VM_TAIL:
    w(2, '<ViewModelPropertyNumber name="%s" id="%s"/>' % (n, pid_))
w(2, '<ViewModelInstance exports="true" name="Default" id="%s">' % VMI_ID)
for _n, pid_, v in VM_NUMBERS:
    w(3, '<ViewModelInstanceNumber propertyValue="%g" viewModelPropertyId="%s"/>' % (v, pid_))
w(3, '<ViewModelInstanceBoolean propertyValue="false" viewModelPropertyId="%s"/>' % VP_SPEAKING)
for _n, pid_, v in VM_TAIL:
    w(3, '<ViewModelInstanceNumber propertyValue="%g" viewModelPropertyId="%s"/>' % (v, pid_))
w(2, '</ViewModelInstance>')
w(1, '</ViewModel>')
w(0, '</Rive>')


# ================================================================= SELF-CHECKS
# Things that compile clean and still break the avatar -- fail the build.
def self_check():
    bad = []
    for i, ln in enumerate(out):
        if 'interpolationType="cubic"' in ln and ln.rstrip().endswith('/>'):
            bad.append("line %d: cubic keyframe without an interpolator" % (i + 1))
            break
    in_sm = False
    for ln in out:
        s_ = ln.strip()
        if s_.startswith('<StateMachineLayer name="'):
            lname = s_.split('"')[1]
            in_sm = lname not in ("RestPose", "Blink", "Breathe", "PropLoops", "Speech")
        if in_sm and s_.startswith('<AnimationState') and 'reset="true"' not in s_:
            bad.append("channel layer %s has a state without reset" % lname)
            break
    if sum(1 for ln in out if 'loopValue="oneShot"' in ln) != len(SEQS) + len(REACTS):
        bad.append("stories / reactions must be oneShot")
    if bad:
        raise SystemExit("self-check failed:\n  " + "\n  ".join(bad))


self_check()

import sys
path = sys.argv[1] if len(sys.argv) > 1 else "scene.rml"
open(path, "w", encoding="utf-8", newline="\n").write("\n".join(out) + "\n")
sys.stderr.write("wrote %s (%d lines, %d ids | faces=%d props=%d bg=%d fg=%d acts=%d seqs=%d "
                 "states=%d reacts=%d rest=%d)\n"
                 % (path, len(out), _next[0] - 1000, len(FACES), len(P_NAMES),
                    len(G_NAMES), len(F_NAMES), len(EYE_ACTS), len(SEQS), len(STATES),
                    len(REACTS), len(REST)))

import json, os
meta = {
    "faces": [f[0] for f in FACES],
    "props": list(P_NAMES),
    "bg": list(G_NAMES),
    "fg": list(F_NAMES),
    "eyeActs": [a[0] for a in EYE_ACTS],
    "seqs": [{"name": q[0], "durationMs": round(q[1] / 60 * 1000)} for q in SEQS],
    # A state loops forever; "pinsFace" says whether it owns the expression or
    # just adds movement on top of whatever `face` the host has set.
    "states": [{"name": s[0], "durationMs": round(s[1] / 60 * 1000),
                "pinsFace": bool(s[2])} for s in STATES],
    # one-shot touch / shock reactions; the host clears them after durationMs
    "reacts": [{"name": r[0], "durationMs": round(r[1] / 60 * 1000)} for r in REACTS],
    # the five idle self-play loops, for the host to shuffle between
    "selfPlay": [ST_NAMES.index(n) for n in
                 ("PlayBounce", "PlayChase", "PlaySpin", "PlayPeek", "PlayWiggle")],
    "presets": [{"name": n, "seq": i + 1, "face": F_INDEX[f], "prop": p, "bg": b, "fg": g,
                 "durationMs": round(SEQS[i + 1][1] / 60 * 1000)}
                for i, (n, f, p, b, g) in enumerate(PRESETS)],
}
mp = os.path.join(os.path.dirname(os.path.abspath(path)), "presets.json")
open(mp, "w", encoding="utf-8", newline="\n").write(json.dumps(meta, indent=2, ensure_ascii=False) + "\n")
sys.stderr.write("wrote %s\n" % mp)
