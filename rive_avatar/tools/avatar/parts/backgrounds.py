# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

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
