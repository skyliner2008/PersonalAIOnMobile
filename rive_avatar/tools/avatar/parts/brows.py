# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

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
