# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# ================================================================= FACES
# A face is the expression channel only: eyes, brow, mouth.
# (name, eyeL, eyeR, brow, mouth, noblink)
#
# LOOI style: the eyes carry the emotion on their own. Brows and mouths are the
# exception, kept only where the eyes alone would be ambiguous (Thinking's one
# raised brow, Disgusted's wavy lip, Crying's frown next to U-shaped eyes).
# Indices 0-31 are the public contract -- append, never reorder.
FACES = [
    ("Neutral",    V_ROUND,   V_ROUND,   B_NONE,     M_NONE,      0),
    ("Happy",      V_ARC,     V_ARC,     B_NONE,     M_NONE,      1),
    ("Angry",      V_ANGRY,   V_ANGRY,   B_NONE,     M_FANG,      0),
    ("Sleepy",     V_BAR,     V_BAR,     B_NONE,     M_NONE,      1),
    ("Curious",    V_BAR,     V_BARTILT, B_NONE,     M_NONE,      1),
    ("Wink",       V_ROUND,   V_BAR,     B_NONE,     M_NONE,      1),
    ("Dead",       V_CROSS,   V_CROSS,   B_NONE,     M_NONE,      1),
    ("Laughing",   V_CHEV,    V_CHEV,    B_NONE,     M_NONE,      1),
    ("Evil",       V_EVIL,    V_EVIL,    B_NONE,     M_FANG,      0),
    ("Focused",    V_WEDGE,   V_WEDGE,   B_NONE,     M_NONE,      0),
    ("Excited",    V_DOME,    V_DOME,    B_NONE,     M_NONE,      0),
    ("Shy",        V_ROUND,   V_ROUND,   B_NONE,     M_NONE,      0),
    ("Shock",      V_WIDE,    V_WIDE,    B_NONE,     M_NONE,      1),
    ("Disgusted",  V_CHEV,    V_CHEV,    B_NONE,     M_WAVY,      1),
    ("Love",       V_HEART,   V_HEART,   B_NONE,     M_NONE,      1),
    ("Crying",     V_ARCDOWN, V_ARCDOWN, B_NONE,     M_FROWN,     1),
    ("Sad",        V_DROOP,   V_DROOP,   B_NONE,     M_NONE,      0),
    ("Thinking",   V_ROUND,   V_ROUND,   B_ONEUP,    M_NONE,      0),
    ("Listening",  V_ROUND,   V_ROUND,   B_NONE,     M_NONE,      0),
    ("Confused",   V_TINY,    V_ROUND,   B_ONEUP,    M_WAVY,      0),
    ("Pout",       V_HALFLID, V_HALFLID, B_NONE,     M_FROWN,     0),
    ("Dizzy",      V_SPIRAL,  V_SPIRAL,  B_NONE,     M_WAVY,      1),
    ("Bored",      V_HALFLID, V_HALFLID, B_NONE,     M_NONE,      0),
    ("Scared",     V_TINY,    V_TINY,    B_NONE,     M_WAVY,      0),
    ("Tired",      V_HALFLID, V_HALFLID, B_NONE,     M_FLAT,      1),
    ("Blank",      V_EMPTY,   V_EMPTY,   B_NONE,     M_NONE,      1),
    ("Money",      V_DOLLAR,  V_DOLLAR,  B_NONE,     M_NONE,      1),
    ("Grin",       V_ROUND,   V_ROUND,   B_NONE,     M_GRIN,      0),
    ("Smug",       V_HALFLID, V_HALFLID, B_NONE,     M_SMIRK,     0),
    ("Determined", V_WEDGE,   V_WEDGE,   B_NONE,     M_FLAT,      0),
    ("Calm",       V_ROUND,   V_ROUND,   B_NONE,     M_SMILE,     0),
    ("Rage",       V_ANGRY,   V_ANGRY,   B_ANGRYRED, M_ZIGZAGRED, 1),   # one colour: red all over
    # 32+ -- the LOOI status set
    ("OneEye",     V_ROUND,   V_EMPTY,   B_NONE,     M_NONE,      0),   # the other eye is a prop
    ("Recognized", V_RING,    V_RING,    B_NONE,     M_NONE,      0),
    ("Starry",     V_STAREYE, V_STAREYE, B_NONE,     M_NONE,      1),   # scenes: dazzled by food / a win
    # 35+ -- jelly mood stories
    ("Weepy",      V_DROOP,   V_DROOP,   B_SAD,      M_WAVY,      0),   # brows fall, lip trembles
    ("Quizzical",  V_BAR,     V_WIDE,    B_RAISED,   M_NONE,      0),   # one eye squints, one goes wide
    ("Fume",       V_ANGRY,   V_ANGRY,   B_ANGRYRED, M_NONE,      0),
    ("Beam",       V_ARC,     V_ARC,     B_NONE,     M_SMILEOPEN, 1),
    ("Glad",       V_DOME,    V_DOME,    B_RAISED,   M_NONE,      0),
    ("Bashful",    V_ROUND,   V_ROUND,   B_NONE,     M_CAT,       0),
    ("Fluster",    V_ROUND,   V_ROUND,   B_WORRIED,  M_WAVY,      0),
    ("Haughty",    V_HALFLID, V_HALFLID, B_RAISED,   M_NONE,      0),
]
F_INDEX = {n: i for i, (n, *_r) in enumerate(FACES)}

# Ready-made combinations, exported for the host to use as presets.
# (name, face, prop, bg, fg). Indices are the public contract -- append only.
PRESETS = [
    ("Normal",       "Neutral",    P_NONE,       G_NONE,      F_NONE),
    ("Happy",        "Happy",      P_NONE,       G_NONE,      F_NONE),
    ("Angry",        "Angry",      P_ANGRYMARK,  G_NONE,      F_NONE),
    ("Sleepy",       "Sleepy",     P_ZZZ,        G_NONE,      F_NONE),
    ("Curious",      "Curious",    P_QUESTION,   G_NONE,      F_NONE),
    ("Eating",       "Neutral",    P_BURGER,     G_NONE,      F_NONE),
    ("Drinking",     "Neutral",    P_BEER,       G_NONE,      F_NONE),
    ("Wink",         "Wink",       P_SPARKLE,    G_NONE,      F_NONE),
    ("Dead",         "Dead",       P_NONE,       G_NONE,      F_NONE),
    ("Laughing",     "Laughing",   P_NONE,       G_NONE,      F_NONE),
    ("Music",        "Calm",       P_HEADPHONES, G_NONE,      F_NONE),
    ("VRMode",       "Blank",      P_VR,         G_NONE,      F_NONE),
    ("Diving",       "Neutral",    P_SNORKEL,    G_DEEPBLUE,  F_NONE),
    ("Evil",         "Evil",       P_DEVIL,      G_NONE,      F_NONE),
    ("Focused",      "Focused",    P_NONE,       G_GRID,      F_NONE),
    ("Excited",      "Excited",    P_SPARKLES,   G_NONE,      F_NONE),
    ("Shy",          "Shy",        P_BLUSH,      G_NONE,      F_NONE),
    ("Shock",        "Shock",      P_EXCLAIM,    G_NONE,      F_NONE),
    ("Disgusted",    "Disgusted",  P_TRASH,      G_NONE,      F_NONE),
    ("CameraMode",   "Sleepy",     P_CAMERA,     G_NONE,      F_NONE),
    ("Love",         "Love",       P_HEARTS,     G_BOKEH,     F_NONE),
    ("Crying",       "Crying",     P_TEARS,      G_NONE,      F_NONE),
    ("Sad",          "Sad",        P_NONE,       G_NONE,      F_NONE),
    ("Thinking",     "Thinking",   P_THINKDOTS,  G_NONE,      F_NONE),
    ("Listening",    "Listening",  P_SOUNDWAVE,  G_NONE,      F_NONE),
    ("Confused",     "Confused",   P_QUESTIONS,  G_NONE,      F_NONE),
    ("Pout",         "Pout",       P_STEAM,      G_NONE,      F_NONE),
    ("Dizzy",        "Dizzy",      P_DIZZYSTARS, G_NONE,      F_NONE),
    ("Bored",        "Bored",      P_SWEAT,      G_NONE,      F_NONE),
    ("Scared",       "Scared",     P_SHIVER,     G_NONE,      F_NONE),
    ("Proud",        "Happy",      P_MEDAL,      G_SPOTLIGHT, F_NONE),
    ("LowBattery",   "Tired",      P_BATTERYLOW, G_NONE,      F_NONE),
    ("AngryMissile", "Rage",       P_MISSILES,   G_REDALERT,  F_EXPLOSION),
    ("CryFlood",     "Crying",     P_TEARS,      G_DEEPBLUE,  F_WATER),
    ("Interview",    "Excited",    P_MIC,        G_SPOTLIGHT, F_LOWERTHIRD),
    ("MindBlown",    "Dizzy",      P_NONE,       G_BURST,     F_EXPLOSION),
    ("DealWithIt",   "Blank",      P_SUNGLASSES, G_NONE,      F_NONE),
    ("MoneyRain",    "Money",      P_NONE,       G_GOLDGLOW,  F_MONEY),
    ("MarketUp",     "Excited",    P_NONE,       G_CHARTUP,   F_ARROWUP),
    ("MarketCrash",  "Shock",      P_NONE,       G_CHARTDOWN, F_ARROWDOWN),
    ("PortBlown",    "Dead",       P_NONE,       G_SHATTER,   F_CRACKS),
    ("StopLoss",     "Determined", P_SLTAG,      G_CHARTDOWN, F_REDFLASH),
    ("Smug",         "Smug",       P_NONE,       G_NONE,      F_NONE),
    ("Party",        "Laughing",   P_NONE,       G_BOKEH,     F_CONFETTI),
    # 45+ -- LOOI moodset IDs 21-40: status and AI features
    ("Idea",           "OneEye",     P_BULB,        G_NONE,  F_NONE),
    ("Disappointed",   "Sad",        P_CRACKED,     G_NONE,  F_NONE),
    ("Pained",         "Laughing",   P_SWEAT,       G_NONE,  F_NONE),
    ("Scanning",       "Blank",      P_BARCODE,     G_NONE,  F_NONE),
    ("Processing",     "Blank",      P_GEARS,       G_NONE,  F_NONE),
    ("Charging",       "Sleepy",     P_BOLT,        G_NONE,  F_NONE),
    ("SoftwareUpdate", "Blank",      P_UPDATE,      G_NONE,  F_NONE),
    ("BatteryEmpty",   "Blank",      P_BIGBATTERY,  G_NONE,  F_NONE),
    ("SignalSearch",   "Blank",      P_SIGNAL,      G_NONE,  F_NONE),
    ("Searching",      "Blank",      P_MAGNIFIER,   G_NONE,  F_NONE),
    ("GestureInput",   "OneEye",     P_POINTHAND,   G_NONE,  F_NONE),
    ("FaceRecognized", "Recognized", P_BRACKETS,    G_NONE,  F_NONE),
    ("WeatherSunny",   "Blank",      P_SUN,         G_NONE,  F_NONE),
    ("WeatherRainy",   "Blank",      P_RAIN,        G_NONE,  F_NONE),
    ("Alarm",          "Neutral",    P_ALARM,       G_NONE,  F_NONE),
    ("Calendar",       "Neutral",    P_CALENDAR,    G_NONE,  F_NONE),
    ("StandbyClock",   "Blank",      P_CLOCK,       G_NONE,  F_NONE),
    ("DrawingMode",    "Bored",      P_PENCIL,      G_NONE,  F_NONE),
    ("InvalidCommand", "Dead",       P_WARNING,     G_NONE,  F_NONE),
    ("SystemSecured",  "Blank",      P_CURTAINS,    G_NONE,  F_NONE),
]
