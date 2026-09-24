# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

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
