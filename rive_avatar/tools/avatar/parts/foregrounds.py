# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

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
