# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# ================================================================= 2.5D POSES
# yaw layer writes x / scaleX / rotation; pitch layer writes y / scaleY.
YAW = {                       # node          left        centre      right
    "face":  {"x": (-40, 0, 40), "rot": (0.06, 0, -0.06), "sx": (1.0, 1.0, 1.0)},
    "eyeL":  {"x": (-10, 0, 22), "sx": (0.80, 1.0, 1.05), "rot": (0, 0, 0)},
    "eyeR":  {"x": (-22, 0, 10), "sx": (1.05, 1.0, 0.80), "rot": (0, 0, 0)},
    "brow":  {"x": (-24, 0, 24), "sx": (0.94, 1.0, 0.94), "rot": (0, 0, 0)},
    "mouth": {"x": (-26, 0, 26), "sx": (0.92, 1.0, 0.92), "rot": (0, 0, 0)},
}
PITCH = {                     # node            up         centre      down
    "face":  {"y": (-34, 0, 34), "sy": (1.0, 1.0, 1.0)},
    "eyeL":  {"y": (-10, 0, 10), "sy": (0.90, 1.0, 0.94)},
    "eyeR":  {"y": (-10, 0, 10), "sy": (0.90, 1.0, 0.94)},
    "brow":  {"y": (-22, 0, 24), "sy": (0.94, 1.0, 0.90)},
    "mouth": {"y": (10, 0, -8), "sy": (0.86, 1.0, 0.92)},
}
