# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

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
