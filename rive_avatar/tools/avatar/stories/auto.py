# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# ================================================================= SEQUENCES
# Every preset is a story, not a pose: at least ~5.5 s of beats that build the
# mood out of expression + prop + scene, with motion accents on top.
#
# beat  = (frame, faceName, prop, bg, fg)
# extra = (nodeKey, propertyKey, [(frame, value)])
#   nodeKey: "face" | "mouth" | "blink" | "blinkL" | "blinkR" | "prop:<idx>:<name>"

def osc(f0, f1, step, lo, hi):
    """Alternate lo/hi every `step` frames between f0 and f1."""
    out_ = []
    i = 0
    f = f0
    while f <= f1:
        out_.append((f, hi if i % 2 else lo))
        i += 1
        f += step
    return out_


def pulse(f0, f1, step, on=4):
    """A strobe: 0 -> 1 -> 0 every `step` frames (muzzle flashes)."""
    out_ = [(0, 0), (max(f0 - 1, 0), 0)]
    f = f0
    while f <= f1:
        out_.append((f, 1))
        out_.append((f + on, 0))
        f += step
    return out_


# extra motion appended to a preset's automatic story (same format as extras)
PRESET_EXTRAS = {
    # glance at the calendar in the corner
    "Calendar": [("blinkL", 13, [(0, 0), (150, 0), (175, 20), (340, 20)]),
                 ("blinkR", 13, [(0, 0), (150, 0), (175, 20), (340, 20)]),
                 ("blinkL", 14, [(0, 0), (150, 0), (175, -14), (340, -14)]),
                 ("blinkR", 14, [(0, 0), (150, 0), (175, -14), (340, -14)])],
    # look down at the drawing
    "DrawingMode": [("blinkL", 14, [(0, 0), (95, 0), (120, 16), (340, 16)]),
                    ("blinkR", 14, [(0, 0), (95, 0), (120, 16), (340, 16)])],
    # the alarm rattles the whole head
    "Alarm": [("face", 13, [(0, 0), (149, 0)] + osc(150, 330, 4, -5, 5) + [(340, 0)])],
}

LEADIN = {
    "Neutral": "Sleepy", "Happy": "Neutral", "Angry": "Determined", "Rage": "Angry",
    "Sleepy": "Tired", "Curious": "Neutral", "Wink": "Happy", "Dead": "Shock",
    "Laughing": "Happy", "Evil": "Smug", "Focused": "Determined", "Excited": "Shock",
    "Shy": "Neutral", "Shock": "Neutral", "Disgusted": "Confused", "Love": "Shy",
    "Crying": "Sad", "Sad": "Neutral", "Thinking": "Neutral", "Listening": "Curious",
    "Confused": "Curious", "Pout": "Sad", "Dizzy": "Shock", "Bored": "Neutral",
    "Scared": "Shock", "Tired": "Bored", "Blank": "Neutral", "Money": "Shock",
    "Grin": "Happy", "Smug": "Neutral", "Determined": "Focused", "Calm": "Neutral",
    "OneEye": "Neutral", "Recognized": "Neutral", "Starry": "Neutral",
}


def auto_story(name, face, prop, bg, fg):
    """The default arc every preset gets: settle -> scene -> expression -> prop
    -> overlay -> hold, with a small landing beat on the eyes and head."""
    lead = LEADIN.get(face, "Neutral")
    dur = 340
    # a face that hides an eye (Blank / OneEye, replaced by a visor or an icon) only
    # takes over when the prop that replaces the eyes arrives -- otherwise
    # the screen sits empty for a second
    land = 150 if face in ("Blank", "OneEye") else 95
    beats = [
        (0,    lead, P_NONE, G_NONE, F_NONE),
        (55,   lead, P_NONE, bg,     F_NONE),
        (land, face, P_NONE if land < 150 else prop, bg, F_NONE),
        (150,  face, prop,   bg,     F_NONE),
        (210,  face, prop,   bg,     fg),
        (dur,  face, prop,   bg,     fg),
    ]
    extra = [
        ("blink", 17, [(0, 1), (land - 9, 1), (land, .3), (land + 10, 1.1), (land + 19, 1), (dur, 1)]),
        ("face", 14, [(0, 0), (land, -9), (land + 21, 5), (land + 41, 0), (dur, 0)]),
        ("face", 16, [(0, .985), (land, 1.03), (land + 45, 1), (dur, 1)]),
        ("face", 17, [(0, .985), (land, 1.03), (land + 45, 1), (dur, 1)]),
    ] + PRESET_EXTRAS.get(name, [])
    return dur, beats, extra


ANGRY_SX = 0.88
ANGRY_CONV = round(max_converge(V_ANGRY, V_ANGRY, ANGRY_SX), 1)

# Hand-written stories that override the automatic arc, keyed by preset name.
HAND_STORIES = {
    # ง่วง: หลับๆตื่นๆ -> หลับ -> กรน
    "Sleepy": (480, [
        (0,   "Bored",  P_NONE,  G_NONE, F_NONE),
        (300, "Sleepy", P_ZZZ,   G_NONE, F_NONE),
        (390, "Sleepy", P_SNORE, G_NONE, F_NONE),
        (480, "Sleepy", P_SNORE, G_NONE, F_NONE),
    ], [
        ("blink", 17, [(0, 1), (42, .22), (74, .95), (112, .14), (148, .9),
                       (188, .1), (224, .85), (270, .07), (296, .07), (302, 1), (480, 1)]),
        ("face", 14, [(0, 0), (42, 15), (74, -3), (112, 19), (148, 0),
                      (188, 23), (224, 2), (270, 27), (300, 24), (480, 24)]),
        ("face", 15, [(0, 0), (148, .04), (270, .09), (480, .09)]),
    ]),

    # โกรธ: ขึงขัง -> ตาแดง -> ตาเฉียงเข้าหากัน -> ป้อมปืนขึ้น -> ยิงส่ายใส่จอ
    # the eyes narrow to 0.88 while closing in, which is what buys the room
    # to converge at all -- max_converge() keeps MIN_EYE_GAP between them.
    "Angry": (540, [
        (0,   "Determined", P_NONE,      G_NONE,     F_NONE),
        (90,  "Angry",      P_NONE,      G_NONE,     F_NONE),
        (150, "Angry",      P_ANGRYMARK, G_REDALERT, F_NONE),
        (240, "Rage",       P_TURRETS,   G_REDALERT, F_NONE),
        (310, "Rage",       P_TURRETS,   G_REDALERT, F_HOLES),
        (540, "Rage",       P_TURRETS,   G_REDALERT, F_HOLES),
    ], [
        # the eyes slide toward each other as the anger focuses
        ("blinkL", 13, [(0, 0), (150, 0), (215, ANGRY_CONV), (540, ANGRY_CONV)]),
        ("blinkR", 13, [(0, 0), (150, 0), (215, -ANGRY_CONV), (540, -ANGRY_CONV)]),
        ("blink", 16, [(0, 1), (90, .96), (215, ANGRY_SX), (540, ANGRY_SX)]),
        ("blinkL", 15, [(0, 0), (150, 0), (215, .10), (540, .10)]),
        ("blinkR", 15, [(0, 0), (150, 0), (215, -.10), (540, -.10)]),
        # turrets rise out of frame bottom, overshoot, settle
        ("prop:%d:TurretL" % P_TURRETS, 14,
         [(0, TURRET_BASE_Y + 170), (240, TURRET_BASE_Y + 170), (278, TURRET_BASE_Y - 8),
          (292, TURRET_BASE_Y + 6), (304, TURRET_BASE_Y), (540, TURRET_BASE_Y)]),
        ("prop:%d:TurretR" % P_TURRETS, 14,
         [(0, TURRET_BASE_Y + 170), (248, TURRET_BASE_Y + 170), (286, TURRET_BASE_Y - 8),
          (300, TURRET_BASE_Y + 6), (312, TURRET_BASE_Y), (540, TURRET_BASE_Y)]),
        # barrels sweep back and forth across the screen
        ("prop:%d:BarrelL" % P_TURRETS, 15,
         [(0, 0), (300, -.12)] + osc(330, 540, 34, -.12, .82)),
        ("prop:%d:BarrelR" % P_TURRETS, 15,
         [(0, 0), (300, .12)] + osc(330, 540, 34, .12, -.82)),
        # muzzle flashes
        ("prop:%d:FlashL" % P_TURRETS, 18, pulse(312, 536, 16)),
        ("prop:%d:FlashR" % P_TURRETS, 18, pulse(320, 536, 16)),
        # recoil shake once the guns open up
        ("face", 13, [(0, 0)] + osc(312, 540, 8, -4, 4)),
    ]),

    # ร้องไห้: เศร้า -> สะอื้น -> ร้องไห้ -> น้ำตาไหล
    "Crying": (560, [
        (0,   "Sad",    P_NONE,  G_NONE, F_NONE),
        (150, "Sad",    P_NONE,  G_NONE, F_NONE),
        (170, "Crying", P_NONE,  G_NONE, F_NONE),
        (300, "Crying", P_TEARS, G_NONE, F_NONE),
        (560, "Crying", P_TEARS, G_NONE, F_NONE),
    ], [
        ("blink", 17, [(0, 1), (150, 1), (162, .55), (174, 1), (186, .5), (198, 1),
                       (300, 1), (560, 1)]),
        ("face", 13, [(0, 0), (160, -5), (172, 5), (184, -5), (196, 0),
                      (330, -3), (350, 3), (370, 0), (560, 0)]),
    ]),

    # ร้องไห้จนน้ำท่วมจอ
    "CryFlood": (620, [
        (0,   "Sad",    P_NONE,  G_NONE,     F_NONE),
        (120, "Crying", P_NONE,  G_DEEPBLUE, F_NONE),
        (220, "Crying", P_TEARS, G_DEEPBLUE, F_NONE),
        (340, "Crying", P_TEARS, G_DEEPBLUE, F_WATER),
        (620, "Crying", P_TEARS, G_DEEPBLUE, F_WATER),
    ], [
        ("blink", 17, [(0, 1), (110, 1), (122, .5), (134, 1), (146, .45), (158, 1), (620, 1)]),
        ("face", 13, [(0, 0)] + osc(340, 620, 26, -4, 4)),
    ]),

    # โกรธยิงจรวด
    "AngryMissile": (520, [
        (0,   "Determined", P_NONE,      G_NONE,     F_NONE),
        (80,  "Angry",      P_ANGRYMARK, G_NONE,     F_NONE),
        (170, "Rage",       P_ANGRYMARK, G_REDALERT, F_NONE),
        (240, "Rage",       P_MISSILES,  G_REDALERT, F_NONE),
        (300, "Rage",       P_MISSILES,  G_REDALERT, F_EXPLOSION),
        (520, "Rage",       P_MISSILES,  G_REDALERT, F_EXPLOSION),
    ], [
        ("blinkL", 13, [(0, 0), (170, 0), (230, ANGRY_CONV), (520, ANGRY_CONV)]),
        ("blinkR", 13, [(0, 0), (170, 0), (230, -ANGRY_CONV), (520, -ANGRY_CONV)]),
        ("blink", 16, [(0, 1), (170, .98), (230, ANGRY_SX), (520, ANGRY_SX)]),
        ("face", 13, [(0, 0)] + osc(300, 520, 9, -5, 5)),
    ]),

    # สัมภาษณ์นักข่าว
    "Interview": (460, [
        (0,   "Neutral",  P_NONE, G_NONE,      F_NONE),
        (70,  "Shock",    P_NONE, G_SPOTLIGHT, F_NONE),
        (140, "Grin",     P_MIC,  G_SPOTLIGHT, F_NONE),
        (210, "Excited",  P_MIC,  G_SPOTLIGHT, F_LOWERTHIRD),
        (460, "Excited",  P_MIC,  G_SPOTLIGHT, F_LOWERTHIRD),
    ], [
        ("blink", 17, [(0, 1), (62, 1), (70, 1.22), (92, 1), (460, 1)]),
        ("face", 14, [(0, 0), (70, -12), (100, 2), (130, 0), (460, 0)]),
    ]),

    # พอร์ตแตก
    "PortBlown": (520, [
        (0,   "Neutral", P_NONE, G_NONE,      F_NONE),
        (70,  "Focused", P_NONE, G_CHARTDOWN, F_NONE),
        (150, "Shock",   P_NONE, G_CHARTDOWN, F_ARROWDOWN),
        (240, "Scared",  P_NONE, G_CHARTDOWN, F_REDFLASH),
        (320, "Dead",    P_NONE, G_SHATTER,   F_CRACKS),
        (520, "Dead",    P_NONE, G_SHATTER,   F_CRACKS),
    ], [
        ("blink", 16, [(0, 1), (150, 1.08), (240, .8), (320, 1), (520, 1)]),
        ("face", 14, [(0, 0), (150, -10), (240, 10), (320, 22), (520, 22)]),
        ("face", 13, [(0, 0)] + osc(320, 400, 7, -6, 6) + [(520, 0)]),
    ]),
}
