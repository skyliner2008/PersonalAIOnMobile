# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# ================================================================= PROPS
P_NAMES = ["None", "Zzz", "Question", "Burger", "Beer", "Sparkle", "Headphones",
           "VR", "Snorkel", "Devil", "Sparkles", "Blush", "Exclaim", "Trash",
           "Camera", "AngryMark", "Hearts", "Tears", "ThinkDots", "SoundWave",
           "Questions", "Steam", "DizzyStars", "Sweat", "Shiver", "Medal",
           "BatteryLow", "Mic", "Sunglasses", "Missiles", "SLTag",
           "Turrets", "Snore", "HoloPat", "HoloPokeL", "HoloPokeR", "HoloChin",
           # LOOI status / AI-feature set
           "Bulb", "Bolt", "Barcode", "Gears", "UpdateArrows", "Magnifier",
           "SignalBars", "PointHand", "FaceBrackets", "Sun", "RainCloud",
           "AlarmClock", "Calendar", "Clock", "Pencil", "Warning", "Curtains",
           "BigBattery", "Cracked"]
(P_NONE, P_ZZZ, P_QUESTION, P_BURGER, P_BEER, P_SPARKLE, P_HEADPHONES, P_VR,
 P_SNORKEL, P_DEVIL, P_SPARKLES, P_BLUSH, P_EXCLAIM, P_TRASH, P_CAMERA,
 P_ANGRYMARK, P_HEARTS, P_TEARS, P_THINKDOTS, P_SOUNDWAVE, P_QUESTIONS,
 P_STEAM, P_DIZZYSTARS, P_SWEAT, P_SHIVER, P_MEDAL, P_BATTERYLOW, P_MIC,
 P_SUNGLASSES, P_MISSILES, P_SLTAG, P_TURRETS, P_SNORE,
 P_HOLOPAT, P_HOLOPOKEL, P_HOLOPOKER, P_HOLOCHIN,
 P_BULB, P_BOLT, P_BARCODE, P_GEARS, P_UPDATE, P_MAGNIFIER, P_SIGNAL,
 P_POINTHAND, P_BRACKETS, P_SUN, P_RAIN, P_ALARM, P_CALENDAR, P_CLOCK,
 P_PENCIL, P_WARNING, P_CURTAINS, P_BIGBATTERY, P_CRACKED) = range(len(P_NAMES))

# The touch props are a hologram of a hand, so they read as something reaching
# into the scene rather than something the robot is wearing: a low-alpha cyan
# body under a bright edge, no shadow plate.
HOLO_FILL = "552FD8FF"
HOLO_EDGE = "EE8FF4FF"

TURRET_BASE_Y = 402.0


def holo_hand(k, s=1.0, fdir=1):
    """Palm plus four separated fingers and a thumb. fdir=+1 fingers point up
    (scratching a chin from below), fdir=-1 fingers point down (patting a head).
    The fingers need real gaps between them or the whole thing reads as a
    mitten rather than a hand."""
    g_rect(k, 78 * s, 56 * s, y=8 * s * fdir, r=22 * s)                      # palm
    for fx, fr in ((-30, -.17), (-10, -.06), (10, .06), (30, .17)):
        g_rect(k, 14 * s, 48 * s, x=fx * s, y=-32 * s * fdir, r=7 * s, rot=fr * fdir)
    g_rect(k, 16 * s, 36 * s, x=-47 * s, y=6 * s * fdir, r=8 * s, rot=-.62 * fdir)  # thumb


def holo_finger(k, dirx=1):
    """A fist with one finger extended, tip at local 0,0, the rest of the hand
    trailing off toward the screen edge behind it."""
    g_rect(k, 100, 26, x=-50 * dirx, r=13)                                   # index
    g_rect(k, 14, 18, x=-12 * dirx, y=-2, r=6)                               # nail
    for j, fy in enumerate((-22, 0, 22)):                                    # curled
        g_rect(k, 32, 19, x=(-108 - j % 2 * 4) * dirx, y=fy, r=9)
    g_rect(k, 70, 80, x=-140 * dirx, r=26)                                   # fist


def holo_point(k):
    """A hand with the index finger pointing straight up (gesture input)."""
    g_rect(k, 80, 76, y=40, r=26)                                            # fist
    g_rect(k, 24, 92, x=-16, y=-36, r=12)                                    # index
    for fx in (6, 24):
        g_rect(k, 20, 30, x=fx, y=6, r=9)                                    # curled
    g_rect(k, 18, 44, x=-44, y=34, r=9, rot=-.5)                             # thumb


def g_bolt(k, s=1.0):
    w(k, '<PointsPath isClosed="true" name="P">')
    for x, y in ((8, -46), (-22, 6), (-2, 6), (-10, 46), (22, -8), (2, -8)):
        w(k + 1, '<StraightVertex x="%g" y="%g" radius="2"/>' % (x * s, y * s))
    w(k, '</PointsPath>')


def g_poly_path(k, pts, closed=False):
    w(k, '<PointsPath%s name="P">' % (' isClosed="true"' if closed else ''))
    for x, y in pts:
        w(k + 1, '<StraightVertex x="%g" y="%g"/>' % (x, y))
    w(k, '</PointsPath>')


def g_gear(k, r, teeth):
    g_star(k, r * 2, points=teeth, inner=.80, corner=3)


def g_anger(k, r=21, hw=12, dep=9):
    """The manga anger vein (💢): four arcs bulging toward a common centre."""
    for i in range(4):
        th = math.pi / 4 + i * math.pi / 2
        w(k, '<PointsPath x="%g" y="%g" rotation="%.4f" name="P">'
          % (math.sin(th) * r, -math.cos(th) * r, th))
        w(k + 1, '<StraightVertex x="%g" y="-4"/>' % -hw)
        w(k + 1, '<CubicMirroredVertex x="0" y="%g" rotation="0" distance="%g"/>' % (dep - 4, hw * .64))
        w(k + 1, '<StraightVertex x="%g" y="-4"/>' % hw)
        w(k, '</PointsPath>')


# 7-segment digits for the standby clock: a Solo per digit, one child per value
SEG = {  # name: (x, y, w, h) in a 56x100 cell
    "a": (0, -44, 40, 12), "b": (22, -22, 12, 40), "c": (22, 22, 12, 40),
    "d": (0, 44, 40, 12), "e": (-22, 22, 12, 40), "f": (-22, -22, 12, 40),
    "g": (0, 0, 40, 12)}
DIGIT_SEGS = ["abcdef", "bc", "abged", "abgcd", "fgbc", "afgcd", "afgedc", "abc",
              "abcdefg", "abcdfg"]
CLOCK_SOLOS = []      # solo id per digit slot
CLOCK_DIGITS = []     # [slot][value] -> node id

CX_ = CX  # alias used in lambdas below
EY = EYE_ABS_Y
CRX, CRY = CORNER_R
CLX, CLY = CORNER_L
