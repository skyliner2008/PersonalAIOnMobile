# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

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
