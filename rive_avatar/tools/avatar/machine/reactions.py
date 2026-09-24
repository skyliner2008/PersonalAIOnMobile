# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# ================================================================= REACTIONS
# Touch and shock: what the robot does *to you* when you do something to it.
# These are one-shot arcs like a story, but they must be able to cut in over a
# running story -- poke the cheek in the middle of the Angry story and it should
# flinch -- so the React layer is declared LAST of the content layers and wins
# over everything. The host writes `react`, waits `durationMs`, writes 0.
REACTS = [
    ("None", 1, [], []),

    # ลูบหัว -- แตะเหนือดวงตา
    ("HeadPat", 240, [
        (0,   "Neutral", P_NONE,    G_NONE,  F_NONE),
        (16,  "Happy",   P_HOLOPAT, G_BOKEH, F_NONE),
        (200, "Happy",   P_NONE,    G_BOKEH, F_NONE),
        (240, "Happy",   P_NONE,    G_NONE,  F_NONE),
    ], [
        # หน้าเอียงตามมือ ไหล่ยุบลงนิดๆ อย่างคนที่กำลังพอใจ
        ("face", 15, [(0, 0), (40, -.07), (90, .07), (140, -.07), (190, 0), (240, 0)]),
        ("face", 14, [(0, 0), (30, 12), (90, 8), (150, 12), (200, 4), (240, 0)]),
        ("blink", 17, [(0, 1), (24, .55), (110, .48), (190, .62), (240, 1)]),
        ("blink", 16, [(0, 1), (24, 1.06), (240, 1)]),
    ]),

    # จิ้มแก้มซ้าย -- หน้าถูกดันไปทางขวา ตาข้างที่โดนบี้
    ("PokeL", 180, [
        (0,   "Neutral", P_NONE,      G_NONE, F_NONE),
        (12,  "Shy",     P_HOLOPOKEL, G_NONE, F_NONE),
        (150, "Shy",     P_NONE,      G_NONE, F_NONE),
        (180, "Neutral", P_NONE,      G_NONE, F_NONE),
    ], [
        ("face", 13, [(0, 0), (25, 0), (30, 20), (46, 6), (53, 6), (58, 18), (80, 2), (140, 0), (180, 0)]),
        ("face", 15, [(0, 0), (25, 0), (30, .07), (58, .06), (140, 0), (180, 0)]),
        ("blinkL", 16, [(0, 1), (25, 1), (30, .74), (46, 1), (53, 1), (58, .78), (80, 1), (180, 1)]),
        ("blinkL", 17, [(0, 1), (25, 1), (30, 1.14), (46, 1), (53, 1), (58, 1.12), (80, 1), (180, 1)]),
        ("blink", 17, [(0, 1), (24, .6), (120, .72), (180, 1)]),
    ]),
    ("PokeR", 180, [
        (0,   "Neutral", P_NONE,      G_NONE, F_NONE),
        (12,  "Shy",     P_HOLOPOKER, G_NONE, F_NONE),
        (150, "Shy",     P_NONE,      G_NONE, F_NONE),
        (180, "Neutral", P_NONE,      G_NONE, F_NONE),
    ], [
        ("face", 13, [(0, 0), (25, 0), (30, -20), (46, -6), (53, -6), (58, -18), (80, -2), (140, 0), (180, 0)]),
        ("face", 15, [(0, 0), (25, 0), (30, -.07), (58, -.06), (140, 0), (180, 0)]),
        ("blinkR", 16, [(0, 1), (25, 1), (30, .74), (46, 1), (53, 1), (58, .78), (80, 1), (180, 1)]),
        ("blinkR", 17, [(0, 1), (25, 1), (30, 1.14), (46, 1), (53, 1), (58, 1.12), (80, 1), (180, 1)]),
        ("blink", 17, [(0, 1), (24, .6), (120, .72), (180, 1)]),
    ]),

    # จิ้มซ้ำๆ ขั้นที่ 1 -- เริ่มรำคาญ
    ("PokeAnnoyed", 200, [
        (0,   "Shy",  P_HOLOPOKER, G_NONE, F_NONE),
        (40,  "Pout", P_HOLOPOKER, G_NONE, F_NONE),
        (150, "Pout", P_STEAM,     G_NONE, F_NONE),
        (200, "Pout", P_STEAM,     G_NONE, F_NONE),
    ], [
        ("face", 13, [(0, 0), (14, -16), (28, 8), (42, -14), (56, 6), (70, 0), (200, 0)]),
        ("face", 15, [(0, 0), (40, .05), (90, -.05), (140, .04), (200, 0)]),
        ("blink", 17, [(0, 1), (40, .68), (200, .72)]),
    ]),
    # จิ้มซ้ำๆ ขั้นที่ 2 -- โกรธจริง
    ("PokeAngry", 280, [
        (0,   "Pout",  P_STEAM,      G_NONE,     F_NONE),
        (30,  "Angry", P_ANGRYMARK,  G_REDALERT, F_NONE),
        (110, "Rage",  P_ANGRYMARK,  G_REDALERT, F_NONE),
        (240, "Angry", P_ANGRYMARK,  G_REDALERT, F_NONE),
        (280, "Angry", P_NONE,       G_NONE,     F_NONE),
    ], [
        ("face", 13, osc(30, 150, 6, -13, 13) + [(170, 0), (280, 0)]),
        ("face", 15, [(0, 0), (40, -.05), (70, .05), (100, -.05), (130, .04), (170, 0), (280, 0)]),
        ("face", 16, [(0, 1), (110, 1.09), (240, 1.04), (280, 1)]),
        ("face", 17, [(0, 1), (110, 1.09), (240, 1.04), (280, 1)]),
        ("blinkL", 13, [(0, 0), (110, ANGRY_CONV), (240, ANGRY_CONV), (280, 0)]),
        ("blinkR", 13, [(0, 0), (110, -ANGRY_CONV), (240, -ANGRY_CONV), (280, 0)]),
    ]),

    # เกาคาง -- แตะใต้ดวงตา
    ("ChinScratch", 260, [
        (0,   "Neutral", P_NONE,     G_NONE,  F_NONE),
        (18,  "Happy",   P_HOLOCHIN, G_NONE,  F_NONE),
        (70,  "Love",    P_HOLOCHIN, G_BOKEH, F_NONE),
        (210, "Love",    P_NONE,     G_BOKEH, F_NONE),
        (260, "Happy",   P_NONE,     G_NONE,  F_NONE),
    ], [
        # เชิดคางขึ้นรับ แล้วสั่นเล็กๆ ตามจังหวะเกา
        ("face", 14, [(0, 0), (40, -14), (150, -17), (220, -8), (260, 0)]),
        ("face", 15, [(0, 0), (40, .04), (80, -.04), (120, .04), (160, -.04), (220, 0), (260, 0)]),
        ("blink", 17, [(0, 1), (30, .5), (62, .5), (74, 1), (210, 1), (230, .8), (260, 1)]),
        ("face", 16, [(0, 1), (70, 1.05), (210, 1.03), (260, 1)]),
        ("face", 17, [(0, 1), (70, 1.05), (210, 1.03), (260, 1)]),
    ]),

    # เสียงดังเกินไป -- ผงะถอย ตาโต
    ("Startled", 210, [
        (0,   "Neutral", P_NONE,    G_NONE, F_NONE),
        (6,   "Shock",   P_EXCLAIM, G_NONE, F_REDFLASH),
        (40,  "Shock",   P_EXCLAIM, G_NONE, F_NONE),
        (110, "Scared",  P_SWEAT,   G_NONE, F_NONE),
        (190, "Neutral", P_NONE,    G_NONE, F_NONE),
        (210, "Neutral", P_NONE,    G_NONE, F_NONE),
    ], [
        # ผงะ = เล็กลงและถอยขึ้น ไม่ใช่ใหญ่ขึ้น
        ("face", 16, [(0, 1), (8, .82), (30, 1.04), (54, 1), (210, 1)]),
        ("face", 17, [(0, 1), (8, .82), (30, 1.04), (54, 1), (210, 1)]),
        ("face", 14, [(0, 0), (8, -26), (34, 6), (58, 0), (210, 0)]),
        ("face", 13, osc(8, 56, 6, -9, 9) + [(70, 0), (210, 0)]),
        # ตาโตได้แค่ 1.12 ก่อนจะชนกัน จึงดันความ "ตกใจ" ไปที่แนวตั้งกับการผงะแทน
        ("blink", 16, [(0, 1), (8, 1.08), (44, 1.04), (110, 1), (210, 1)]),
        ("blink", 17, [(0, 1), (8, 1.46), (44, 1.18), (110, 1), (210, 1)]),
    ]),

    # เขย่ามือถือ -- เวียนหัวก่อน แล้วค่อยโกรธ
    ("ShakeAngry", 330, [
        (0,   "Neutral", P_NONE,       G_NONE,     F_NONE),
        (20,  "Dizzy",   P_DIZZYSTARS, G_NONE,     F_NONE),
        (150, "Dizzy",   P_DIZZYSTARS, G_NONE,     F_NONE),
        (180, "Angry",   P_ANGRYMARK,  G_REDALERT, F_NONE),
        (300, "Angry",   P_ANGRYMARK,  G_REDALERT, F_NONE),
        (330, "Angry",   P_NONE,       G_NONE,     F_NONE),
    ], [
        ("face", 13, osc(0, 150, 5, -26, 26) + osc(156, 250, 7, -12, 12) + [(270, 0), (330, 0)]),
        ("face", 14, osc(0, 150, 7, -18, 18) + [(160, 0), (330, 0)]),
        ("face", 15, [(0, 0), (40, -.12), (80, .12), (120, -.12), (150, 0),
                      (190, .05), (230, -.05), (270, 0), (330, 0)]),
        ("face", 16, [(0, 1), (180, 1.08), (300, 1.03), (330, 1)]),
        ("face", 17, [(0, 1), (180, 1.08), (300, 1.03), (330, 1)]),
        ("blinkL", 13, [(0, 0), (180, ANGRY_CONV), (300, ANGRY_CONV), (330, 0)]),
        ("blinkR", 13, [(0, 0), (180, -ANGRY_CONV), (300, -ANGRY_CONV), (330, 0)]),
    ]),
]
RE_NAMES = [r[0] for r in REACTS]


def check_eye_scale(table, label):
    """A timeline that pins a face must not blow those eyes up past the point
    where the pair touches. Only checkable where the face is known, so
    motion-only entries (no beats) are the host's responsibility -- see the
    ceiling noted in AVATAR_CONTRACT.md."""
    bad = []
    for name, _dur, beats, extra in table:
        if not beats:
            continue
        # a face that is already tight at rest is the FACE's problem, not this
        # beat's -- never fail a timeline for returning to scale 1
        lim = max(1.0, min(max_eye_scale(FACES[F_INDEX[b[1]]][1], FACES[F_INDEX[b[1]]][2])
                           for b in beats))
        peak = 0.0
        for ex in extra:
            if ex[0] in ("blink", "blinkL", "blinkR") and ex[1] == 16:
                peak = max(peak, max(v for _f, v in ex[2]))
        if peak > lim + 1e-6:
            bad.append("%s (peak %.2f > %.2f)" % (name, peak, lim))
    if bad:
        raise SystemExit("%s: eyes would overlap when widened: %s"
                         % (label, ", ".join(bad)))


check_eye_scale(STATES, "states")
check_eye_scale(REACTS, "reactions")
check_eye_scale(SEQS, "sequences")
