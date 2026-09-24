# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# ================================================================= MORPH EYE
# One eye that can BECOME any of the soft shapes instead of swapping between
# separate drawings. Each eye (front + back layer) is a single closed path of
# MORPH_N cubic vertices. Every shape is described by the same MORPH_N points in the
# same order (top, then clockwise), so any two shapes blend vertex by vertex:
#   - a face change mixes them over the Face layer's transition (FACE_MIX_MS)
#   - a story beat keys them with the jelly ease (MORPH_F frames)
# The discrete variants in eyes.py remain for shapes a closed blob cannot be
# (cross, chevron, spiral, dollar, star, ring).

import math as _m

MORPH_N = 8
MORPH_F = 16                   # frames a story beat takes to reshape the eye (~0.27 s)
FACE_MIX_MS = 220              # Face layer transition: live emotion changes morph this long
V_NAMES.append("Morph")
V_MORPH = len(V_NAMES) - 1

# property keys of a CubicDetachedVertex (rive schema CubicDetachedVertex)
VX, VY, V_IN_ROT, V_IN_DIST, V_OUT_ROT, V_OUT_DIST = 24, 25, 84, 85, 86, 87
MORPH_KEYS = (VX, VY, V_IN_ROT, V_IN_DIST, V_OUT_ROT, V_OUT_DIST)

# circle factor: on a circle drawn with 8 cubic points, handle = 0.3466 * chord
_K8 = 4.0 / 3.0 * _m.tan(_m.pi / 16) / (2 * _m.sin(_m.pi / 8))


def _wrap_near(a, ref):
    """a (radians) moved by whole turns to within pi of ref -- keeps handle angles of
    the same vertex close across shapes, so a morph never spins a handle round."""
    while a - ref > _m.pi:
        a -= 2 * _m.pi
    while a - ref < -_m.pi:
        a += 2 * _m.pi
    return a


def outline(pts, smooth):
    """8 anchor points (clockwise from the top) + per-point smoothness (1 = round,
    0 = sharp corner) -> 8 vertex dicts. Handles follow the neighbours' direction
    (Catmull-Rom), so the same numbers describe a circle exactly and a slab softly."""
    n = len(pts)
    out_ = []
    for i in range(n):
        px, py = pts[i - 1]
        x, y = pts[i]
        nx, ny = pts[(i + 1) % n]
        tx, ty = nx - px, ny - py
        tl = _m.hypot(tx, ty) or 1.0
        ang = _m.atan2(ty / tl, tx / tl)
        ref = i * 2 * _m.pi / n          # tangent direction of vertex i on a circle
        ang = _wrap_near(ang, ref)
        s = smooth[i]
        out_.append({VX: x, VY: y,
                     V_OUT_ROT: ang, V_OUT_DIST: s * _K8 * _m.hypot(nx - x, ny - y),
                     V_IN_ROT: _wrap_near(ang + _m.pi, ref + _m.pi),
                     V_IN_DIST: s * _K8 * _m.hypot(x - px, y - py)})
    return out_


def _ellipse_pts(rx, ry, cx=0.0, cy=0.0):
    return [(cx + rx * _m.sin(i * _m.pi / 4), cy - ry * _m.cos(i * _m.pi / 4)) for i in range(8)]


# ---- the shapes, drawn for the LEFT eye (the right eye is its mirror) -------
# Authored units, like eyes.py (the variant node scales them by EYE_SCALE).
RX, RY = EYE_R * EYE_OX, EYE_R * EYE_OY
MORPH_SHAPES = {
    # LOOI resting eye: a soft vertical oval
    "round": (_ellipse_pts(RX, RY), [1] * 8),
    # smaller, pulled-in eye (worried / scared / small)
    "small": (_ellipse_pts(RX * 0.72, RY * 0.72, 0, 6), [1] * 8),
    # happy: the ⌒ band (outer arc on top, inner arc underneath, rounded tips)
    "arc": ([(0, -42), (33, -32), (48, 8), (30, -10), (0, -21), (-30, -10), (-48, 8), (-33, -32)],
            [1, 1, .75, 1, 1, 1, .75, 1]),
    # sad: the lid falls toward the outer corner, a rounder, lower bottom
    "droop": ([(4, -21), (40, -33), (49, 4), (35, 40), (0, 52), (-36, 39), (-49, 10), (-41, -6)],
              [1, .35, 1, 1, 1, 1, 1, .35]),
    # crying: the eye stretches down and melts toward a drip
    "melt": ([(0, -50), (37, -36), (48, 6), (42, 52), (0, 84), (-42, 52), (-48, 6), (-37, -36)],
             [1, 1, 1, 1, 1, 1, 1, 1]),
    # angry: the lid slants DOWN toward the nose, round bottom
    "angry": ([(-2, -13), (42, 9), (50, 24), (36, 46), (0, 54), (-36, 44), (-51, -6), (-44, -33)],
              [1, .3, 1, 1, 1, 1, 1, .3]),
}
MORPH_COLOR = {"angry": (RED, RED_DEEP)}   # (front, back); everything else cyan on blue


def _mirror(pts, smooth):
    """Right eye: mirror x and re-number so the order is still clockwise from the top."""
    n = len(pts)
    idx = [(n - i) % n for i in range(n)]
    return [(-pts[j][0], pts[j][1]) for j in idx], [smooth[j] for j in idx]


def morph_shape(name, tag):
    pts, smooth = MORPH_SHAPES[name]
    if tag == "R":
        pts, smooth = _mirror(pts, smooth)
    return outline(pts, smooth)


def morph_colors(name):
    return MORPH_COLOR.get(name, (CYAN, BLUE))


# ids filled in when the eye rig is written (document.py -> emit_eye_variant)
MORPH_VERTS = {}      # (tag, part) -> [vertex id] * MORPH_N
MORPH_PAINT = {}      # (tag, part) -> [(solid color id, "front" | "back")]
MORPH_REST = {}       # (vertex id, key) -> rest value (the round eye)

_emit_eye_variant_plain = emit_eye_variant


def emit_eye_variant(d, v, s, tag, part="front"):
    if v != V_MORPH:
        return _emit_eye_variant_plain(d, v, s, tag, part)
    vid = nid()
    name = "%s_Morph%s" % (tag, "" if part == "front" else "_Back")
    front, back = morph_colors("round")
    w(d, '<Node scaleX="%g" scaleY="%g" name="%s" id="%s">' % (EYE_SCALE, EYE_SCALE, name, vid))
    if part == "front":
        w(d + 1, '<Shape name="Crisp">')
    else:
        w(d + 1, '<Shape x="0" y="%g" name="Shadow">' % (BACK_DY / EYE_SCALE))
    w(d + 2, '<PointsPath isClosed="true" name="P">')
    ids = []
    for i, vtx in enumerate(morph_shape("round", tag)):
        pid = nid()
        ids.append(pid)
        w(d + 3, '<CubicDetachedVertex x="%g" y="%g" inRotation="%g" inDistance="%g" '
                 'outRotation="%g" outDistance="%g" name="v%d" id="%s"/>'
          % (vtx[VX], vtx[VY], vtx[V_IN_ROT], vtx[V_IN_DIST], vtx[V_OUT_ROT], vtx[V_OUT_DIST], i, pid))
        for key in MORPH_KEYS:
            MORPH_REST[(pid, key)] = vtx[key]
    w(d + 2, '</PointsPath>')
    paints = []
    if part == "front":
        glow_c, fill_c = nid(), nid()
        w(d + 2, '<Stroke thickness="5" cap="round" join="round" name="Glow">')
        w(d + 3, '<SolidColor colorValue="%s" name="C" id="%s"/>' % (front, glow_c))
        w(d + 3, '<Feather strength="%g" name="F"/>' % GLOW)
        w(d + 2, '</Stroke>')
        w(d + 2, '<Fill name="Fill">')
        w(d + 3, '<SolidColor colorValue="%s" name="C" id="%s"/>' % (front, fill_c))
        w(d + 2, '</Fill>')
        paints = [(glow_c, "front"), (fill_c, "front")]
    else:
        fill_c = nid()
        w(d + 2, '<Fill name="Fill">')
        w(d + 3, '<SolidColor colorValue="%s" name="C" id="%s"/>' % (back, fill_c))
        w(d + 2, '</Fill>')
        paints = [(fill_c, "back")]
    w(d + 1, '</Shape>')
    w(d, '</Node>')
    MORPH_VERTS[(tag, part)] = ids
    MORPH_PAINT[(tag, part)] = paints
    return vid


def morph_tracks(shape_keys, eyes=("L", "R"), skip=()):
    """shape_keys: [(frame, shape name, interp)] -> {(vertex id, key): [(f, v, interp)]}
    for the front and back layers of the given eyes. Pairs listed in skip are left
    to whoever keys them explicitly (a story's own vertex quiver)."""
    tracks = {}
    for tag in eyes:
        for part in ("front", "back"):
            ids = MORPH_VERTS[(tag, part)]
            for fr, shape_name, itp in shape_keys:
                verts = morph_shape(shape_name, tag)
                for i, pid in enumerate(ids):
                    for key in MORPH_KEYS:
                        if (pid, key) in skip:
                            continue
                        tracks.setdefault((pid, key), []).append((fr, verts[i][key], itp))
    return tracks


def morph_color_frames(shape_keys, eyes=("L", "R")):
    """[(frame, shape, interp)] -> {solid color id: [(frame, ARGB)]} (colours hold)."""
    frames = {}
    for tag in eyes:
        for part in ("front", "back"):
            for cid, role in MORPH_PAINT[(tag, part)]:
                for fr, shape_name, _itp in shape_keys:
                    front, back = morph_colors(shape_name)
                    frames.setdefault(cid, []).append((fr, front if role == "front" else back))
    return frames


def emit_color_frames(d, frames, interp="linear"):
    for cid, kfs in frames.items():
        w(d, '<KeyedObject objectId="%s">' % cid)
        w(d + 1, '<KeyedProperty propertyKey="37">')
        seen = set()
        for fr, argb in sorted(kfs):
            if fr in seen:
                continue
            seen.add(fr)
            w(d + 2, '<KeyFrameColor value="%s" frame="%d" interpolationType="%s"/>' % (argb, fr, interp))
        w(d + 1, '</KeyedProperty>')
        w(d, '</KeyedObject>')


# ---- the pop: an eye taking a new shape squashes, overshoots, settles ----------
# The right eye follows POP_LAG frames later and a little softer, so the two eyes
# never move as one stamped picture.
POP_LAG = 3
POP_KEYS = [(0, 1.0, 1.0), (4, 1.09, 0.88), (10, 0.93, 1.08), (15, 1.02, 0.98), (20, 1.0, 1.0)]


def pop_tracks(frame, gain_r=0.8):
    """{(pop node, key): kfs} for a reshape starting at frame (scaleX 16 / scaleY 17)."""
    tracks = {}
    for node, lag, gain in ((eyeL_pop, 0, 1.0), (eyeR_pop, POP_LAG, gain_r)):
        for key, col in ((16, 1), (17, 2)):
            kfs = [(frame, 1.0, "hold")] if lag else []
            kfs += [(frame + lag + row[0], 1 + (row[col] - 1) * gain, "cubic") for row in POP_KEYS]
            tracks[(node, key)] = kfs
    return tracks
