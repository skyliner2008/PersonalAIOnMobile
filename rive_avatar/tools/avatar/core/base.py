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
