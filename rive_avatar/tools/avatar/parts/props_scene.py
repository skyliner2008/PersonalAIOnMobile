# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

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
