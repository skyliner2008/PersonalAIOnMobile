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

# ---- Eye Lab 3D head: when data/eye_lab.json has rig.head, the poses above are
# rebuilt from a sphere model (the same maths as tools/eyelab/index.html headProject):
# every feature sits on a sphere at its offset from the face centre, the head turns /
# nods, and the projected offset + foreshortening become the pose's x / y / scale.
EYE_HEAD = {"radius": 300, "maxYaw": 12, "maxPitch": 9, "yawRoll": 3.4, "travelX": 0, "travelY": 0,
            "pupil": 13, "persp": 0.15}
EYE_HEAD.update(EYE_RIG.get("head", {}))


def head_project(x0, y0, yaw, pitch, h=EYE_HEAD):
    """-> (dx, dy, sx, sy): how a feature at (x0, y0) from the face centre moves and
    foreshortens when the head turns by yaw / nods by pitch (radians)."""
    R = max(float(h["radius"]), abs(x0) + 1, abs(y0) + 1)
    a, b = math.asin(x0 / R), math.asin(y0 / R)

    def at(yw, pt):
        px, py, pz = R * math.sin(a) * math.cos(b), R * math.sin(b), R * math.cos(a) * math.cos(b)
        x1, z1 = px * math.cos(yw) + pz * math.sin(yw), -px * math.sin(yw) + pz * math.cos(yw)
        y2, z2 = py * math.cos(pt) + z1 * math.sin(pt), -py * math.sin(pt) + z1 * math.cos(pt)
        k = 1 + h["persp"] * (z2 - R) / R
        nx, ny = x1 / R, y2 / R
        return x1 * k, y2 * k, math.sqrt(max(0.05, 1 - nx * nx)) * k, math.sqrt(max(0.05, 1 - ny * ny)) * k

    bx, by, bfx, bfy = at(0, 0)
    qx, qy, qfx, qfy = at(yaw, pitch)
    return qx - bx, qy - by, qfx / bfx, qfy / bfy


if EYE_RIG.get("head"):
    _yaws = (-EYE_HEAD["maxYaw"] * math.pi / 180, 0.0, EYE_HEAD["maxYaw"] * math.pi / 180)
    _pitches = (-EYE_HEAD["maxPitch"] * math.pi / 180, 0.0, EYE_HEAD["maxPitch"] * math.pi / 180)
    _roll = EYE_HEAD["yawRoll"] * math.pi / 180
    # the face node carries the centre's motion; the parts move relative to it
    _feat = {"eyeL": (-EYE_DX, EYE_Y), "eyeR": (EYE_DX, EYE_Y), "brow": (0.0, BROW_Y), "mouth": (0.0, MOUTH_Y)}
    _cx = [head_project(0.0, 0.0, yw, 0.0)[0] for yw in _yaws]
    _cy = [head_project(0.0, 0.0, 0.0, pt)[1] for pt in _pitches]
    YAW = {"face": {"x": tuple(round(_cx[i] + EYE_HEAD["travelX"] * (i - 1), 2) for i in range(3)),
                    "rot": (_roll, 0.0, -_roll), "sx": (1.0, 1.0, 1.0)}}
    PITCH = {"face": {"y": tuple(round(_cy[i] + EYE_HEAD["travelY"] * (i - 1), 2) for i in range(3)),
                      "sy": (1.0, 1.0, 1.0)}}
    for _k, (_x0, _y0) in _feat.items():
        _py = [head_project(_x0, _y0, yw, 0.0) for yw in _yaws]
        _pp = [head_project(_x0, _y0, 0.0, pt) for pt in _pitches]
        YAW[_k] = {"x": tuple(round(_py[i][0] - _cx[i], 2) for i in range(3)),
                   "sx": tuple(round(_py[i][2], 4) for i in range(3)), "rot": (0, 0, 0)}
        PITCH[_k] = {"y": tuple(round(_pp[i][1] - _cy[i], 2) for i in range(3)),
                     "sy": tuple(round(_pp[i][3], 4) for i in range(3))}
