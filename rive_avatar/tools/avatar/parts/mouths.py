# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

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
