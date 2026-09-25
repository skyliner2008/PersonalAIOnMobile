# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# ================================================================= 3D HEAD MODEL
# The Eye Lab's head (data/eye_lab.json rig.head): every feature sits on a sphere at its
# offset from the face centre; turning / nodding moves and foreshortens it. Used by the
# gaze poses (machine/poses.py) and the lab's timeline stories (stories/lab.py).
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
