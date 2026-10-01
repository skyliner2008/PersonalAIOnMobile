"""Game-ready pet robot for the Godot prototype: pivots, animation clips, accessories, glTF export.

Run inside Blender, after robot_build.py, in the same namespace:

    ns = {}
    for f in ("robot_build.py", "robot_game.py"):
        exec(open(rf"<repo>/rive_avatar/blender/{f}", encoding="utf-8").read(), ns)
    ns["build_game"]()                                   # model + pivots + clips + accessories
    ns["export_game"](r"<repo>/pet_godot/assets")        # robot.glb + accessories/*.glb

Rig: the robot's parts float apart (no neck, floating arms), so every moving part is an empty
used as a pivot and animated as a glTF node -- no skinning. Hierarchy after build_game():

    PetRobot                     (static; the game turns this for gaze)
      PR_Float                   whole-body float / bounce
        PR_HeadPivot             neck point, lags behind the body
          PR_HeadRoot -> head, screen, ears; PR_AntennaPivotL/R -> antennas
        PR_BodyPivot             chest: vest, belly, core
          PR_ArmL/R              shoulder: the one-piece two-colour arm, hand socket (single pivot)

Clips are NLA tracks with the same name on every pivot; the glTF exporter (NLA_TRACKS) merges
them into one animation per clip. Clip names ending in "_loop" loop in Godot.

Accessories are modelled in place on the rest pose (world space) and exported one glb each;
ACCESSORIES says which pivot each one follows, and the game re-parents it keeping the rest offset.
"""
import math

import bmesh
import bpy
from mathutils import Matrix, Vector

FPS = 30

# pivot name -> world position of the pivot (rest pose)
PIVOTS = {
    "PR_Float": (0.0, 0.0, 0.0),
    "PR_HeadPivot": (0.0, 0.0, 1.03),
    "PR_BodyPivot": (0.0, 0.0, 0.78),
}

# accessory -> (pivot it follows, slot, PropType it stands for in the app)
ACCESSORIES = {
    "crown": ("PR_HeadPivot", "hat", "CROWN"),
    "party_hat": ("PR_HeadPivot", "hat", "PARTY_HAT"),
    "wizard_hat": ("PR_HeadPivot", "hat", "WIZARD_HAT"),
    "chef_hat": ("PR_HeadPivot", "hat", "CHEF_HAT"),
    "sunglasses": ("PR_HeadPivot", "face", "SUNGLASSES"),
    "headphones": ("PR_HeadPivot", "ears", "HEADPHONES"),
    "backpack": ("PR_BodyPivot", "back", "BACKPACK"),
}


# ---------------------------------------------------------------- helpers

def _scene():
    return bpy.data.scenes[SCENE]


def _update():
    _scene().view_layers[0].update()


def _reparent(ob, parent):
    """Re-parent keeping the world transform, with a clean (identity) parent inverse."""
    _update()
    mw = ob.matrix_world.copy()
    ob.parent = parent
    ob.matrix_parent_inverse = Matrix.Identity(4)
    _update()
    ob.matrix_world = mw
    _update()                           # children's world matrices are stale until this runs


def _pivot(name, parent, loc):
    coll = bpy.data.collections["PR_Robot"]
    ob = bpy.data.objects.new(name, None)
    ob.empty_display_size = 0.15
    ob.empty_display_type = "SPHERE"
    coll.objects.link(ob)
    ob.parent = parent
    _update()
    ob.matrix_world = Matrix.Translation(loc)
    _update()
    return ob


def rig():
    """Insert the pivots between the root and the parts (the model keeps its world placement)."""
    ob = bpy.data.objects
    root = ob["PetRobot"]
    for arm in ("PR_ArmL", "PR_ArmR"):          # rest pose: arms straight down, clips add the swing
        ob[arm].rotation_euler = (0, 0, 0)
    float_ = _pivot("PR_Float", root, PIVOTS["PR_Float"])
    head_pivot = _pivot("PR_HeadPivot", float_, PIVOTS["PR_HeadPivot"])
    body_pivot = _pivot("PR_BodyPivot", float_, PIVOTS["PR_BodyPivot"])
    _reparent(ob["PR_HeadRoot"], head_pivot)
    _reparent(ob["PR_TorsoRoot"], body_pivot)
    for s, side in ((-1, "L"), (1, "R")):
        _reparent(ob[f"PR_Arm{side}"], body_pivot)      # one-piece arm: a single shoulder pivot
        ant = ob[f"PR_Antenna{side}"]
        base = ant.matrix_world @ Vector((0, 0, -ant.dimensions.z * 0.45))
        ap = _pivot(f"PR_AntennaPivot{side}", ob[f"PR_Ear{side}"], base)
        _reparent(ant, ap)
    _update()
    for name in ANIMATED:                       # remember the rest pose (clips are offsets from it)
        o = ob[name]
        o["rest_loc"] = list(o.location)
        o["rest_rot"] = list(o.rotation_euler)
    return [n for n in ANIMATED]


def use_clips(on):
    """Turn the NLA clips on (play) or off (every pivot back to its rest pose)."""
    for name in ANIMATED:
        o = bpy.data.objects[name]
        if o.animation_data:
            o.animation_data.use_nla = on
        if not on:
            o.location = o["rest_loc"]
            o.rotation_euler = o["rest_rot"]
    _update()


def solo_clip(clip, frame=0):
    """Preview one clip in Blender: mute every other NLA track and jump to a frame."""
    use_clips(True)
    for name in ANIMATED:
        for track in bpy.data.objects[name].animation_data.nla_tracks:
            track.mute = track.name != clip
    _scene().frame_set(frame)


def preview(out_dir, shots, size=512):
    """Render review shots: [(file_stem, clip, frame, yaw_deg, [accessories])]."""
    scn = _scene()
    r = scn.render
    r.resolution_x = r.resolution_y = size
    paths = []
    for stem, clip, frame, yaw, accs in shots:
        solo_clip(clip, frame)
        show_accessories(*accs)
        bpy.data.objects["PetRobot"].rotation_euler = (0, 0, math.radians(yaw))
        r.filepath = f"{out_dir}/{stem}.png"
        bpy.ops.render.render(write_still=True, scene=scn.name)
        paths.append(r.filepath)
    show_accessories()
    bpy.data.objects["PetRobot"].rotation_euler = (0, 0, 0)
    r.resolution_x = r.resolution_y = 1024
    return paths


def _gltf(v):
    """Blender Z-up -> glTF / Godot Y-up."""
    return [round(v[0], 5), round(v[2], 5), round(-v[1], 5)]


# ---------------------------------------------------------------- clips

def _rest(names):
    return {n: (bpy.data.objects[n].location.copy(), bpy.data.objects[n].rotation_euler.copy()) for n in names}


ANIMATED = ["PR_Float", "PR_HeadPivot", "PR_BodyPivot", "PR_ArmL", "PR_ArmR",
            "PR_AntennaPivotL", "PR_AntennaPivotR"]


def _wave(t, amp, phase=0.0, cycles=1):
    return amp * math.sin(2 * math.pi * (cycles * t - phase))


def _pose(t, clip):
    """Offsets per pivot at normalised time t (0..1) for a clip: {name: (dloc xyz, drot deg xyz)}."""
    p = {}

    def arm(side, outward, forward, bend=-6.0):
        s = -1 if side == "L" else 1
        # bend is kept in the clip tables for readability; the one-piece arm has no elbow
        p[f"PR_Arm{side}"] = ((0, 0, 0), (forward, -s * outward, 0))

    def antennas(wobble, lean=0.0, cycles=1):
        for side, s in (("L", -1), ("R", 1)):
            p[f"PR_AntennaPivot{side}"] = ((0, 0, 0), (_wave(t, wobble * 0.6, 0.35, cycles * 2),
                                                        s * (lean + _wave(t, wobble, 0.3, cycles)), 0))

    if clip == "Idle_loop":
        p["PR_Float"] = ((0, 0, _wave(t, 0.05)), (0, 0, 0))
        p["PR_HeadPivot"] = ((0, 0, _wave(t, 0.022, 0.12)), (_wave(t, 1.6, 0.2), _wave(t, 1.2, 0.45), 0))
        p["PR_BodyPivot"] = ((0, 0, 0), (0, _wave(t, 1.0, 0.25), 0))
        arm("L", _wave(t, 3, 0.15), _wave(t, 2, 0.3), -3 + _wave(t, 3, 0.25))
        arm("R", _wave(t, 3, 0.2), _wave(t, 2, 0.35), -3 + _wave(t, 3, 0.3))
        antennas(3)
    elif clip == "Happy_loop":
        hop = 0.13 * abs(math.sin(2 * math.pi * t))           # two hops per loop
        p["PR_Float"] = ((0, 0, 0.03 + hop), (0, 0, 0))
        p["PR_HeadPivot"] = ((0, 0, 0.3 * hop), (-4, _wave(t, 6, 0.1), 0))
        p["PR_BodyPivot"] = ((0, 0, 0), (0, _wave(t, 3, 0.1), 0))
        arm("L", 90 + _wave(t, 9, 0.0, 2), -20, -10)
        arm("R", 90 + _wave(t, 9, 0.25, 2), -20, -10)
        antennas(8, 0, 2)
    elif clip == "Wave_loop":
        p["PR_Float"] = ((0, 0, _wave(t, 0.04)), (0, 0, 0))
        p["PR_HeadPivot"] = ((0, 0, _wave(t, 0.02, 0.12)), (0, -6 + _wave(t, 2, 0.2), 0))
        p["PR_BodyPivot"] = ((0, 0, 0), (0, -3, 0))
        arm("L", 86 + _wave(t, 12, 0.0, 2), -34, -25)
        arm("R", _wave(t, 3, 0.2), 0)
        antennas(4, 0, 2)
    elif clip == "Sad_loop":
        p["PR_Float"] = ((0, 0, -0.06 + _wave(t, 0.018)), (0, 0, 0))
        p["PR_HeadPivot"] = ((0, 0, 0.0), (9 + _wave(t, 1.5, 0.2), 3, 0))
        p["PR_BodyPivot"] = ((0, 0, 0), (5, 0, 0))
        arm("L", -2, -10 + _wave(t, 2, 0.2), -4)
        arm("R", -2, -10 + _wave(t, 2, 0.3), -4)
        antennas(1.5, 18)
    elif clip == "Surprised_loop":
        jolt = math.exp(-((t - 0.12) / 0.08) ** 2)             # quick jump, then hover
        p["PR_Float"] = ((0, 0.04 * jolt, 0.16 * jolt + _wave(t, 0.02, 0.0, 2)), (0, 0, 0))
        p["PR_HeadPivot"] = ((0, 0, 0.05 * jolt), (-9 * jolt - 3, 0, 0))
        p["PR_BodyPivot"] = ((0, 0, 0), (-4 * jolt, 0, 0))
        arm("L", 42 + 12 * jolt, -6, -8)
        arm("R", 42 + 12 * jolt, -6, -8)
        antennas(10 * jolt + 1, -6)
    elif clip == "Love_loop":
        p["PR_Float"] = ((0, 0, _wave(t, 0.04)), (0, 0, 0))
        p["PR_HeadPivot"] = ((0, 0, _wave(t, 0.02, 0.12)), (-2, _wave(t, 6, 0.1), 0))
        p["PR_BodyPivot"] = ((0, 0, 0), (0, _wave(t, 4, 0.0), 0))
        arm("L", -28, -48 + _wave(t, 3, 0.2), -30)
        arm("R", -28, -48 + _wave(t, 3, 0.3), -30)
        antennas(3)
    elif clip == "Sleep_loop":
        p["PR_Float"] = ((0, 0, -0.08 + _wave(t, 0.025)), (0, 0, 0))
        p["PR_HeadPivot"] = ((0, 0, 0.0), (10 + _wave(t, 2, 0.25), 5, 0))
        p["PR_BodyPivot"] = ((0, 0, 0), (4, 2, 0))
        arm("L", 2, -6, -3)
        arm("R", 2, -6, -3)
        antennas(1, 24)
    elif clip == "Talk_loop":
        p["PR_Float"] = ((0, 0, _wave(t, 0.04)), (0, 0, 0))
        p["PR_HeadPivot"] = ((0, 0, _wave(t, 0.02, 0.12)), (_wave(t, 3, 0.0, 4), _wave(t, 2, 0.2), 0))
        p["PR_BodyPivot"] = ((0, 0, 0), (0, _wave(t, 1.2, 0.25), 0))
        arm("L", 8 + _wave(t, 6, 0.0, 2), -8, -12)
        arm("R", 8 + _wave(t, 6, 0.5, 2), -8, -12)
        antennas(3, 0, 2)
    elif clip == "Fly_loop":                               # jetpack: up high, leaning into the flight
        p["PR_Float"] = ((0, -0.04, 0.34 + _wave(t, 0.05, 0.0, 2)), (9 + _wave(t, 2, 0.1), _wave(t, 3, 0.2), 0))
        p["PR_HeadPivot"] = ((0, 0, _wave(t, 0.02, 0.15, 2)), (-10 + _wave(t, 2, 0.3), _wave(t, 2, 0.4), 0))
        arm("L", 22 + _wave(t, 4, 0.1, 2), 24)
        arm("R", 22 + _wave(t, 4, 0.35, 2), 24)
        antennas(5, -10, 2)
    return p


# clip -> length in frames at FPS
CLIPS = {
    "Idle_loop": 72, "Happy_loop": 48, "Wave_loop": 48, "Sad_loop": 96,
    "Surprised_loop": 60, "Love_loop": 60, "Sleep_loop": 120, "Talk_loop": 48, "Fly_loop": 48,
}


def check_arms(step=2):
    """How far any arm vertex gets into the head (incl. ear discs and antennas), per clip.

    Head, ears and antennas are boxes in PR_HeadRoot space (the head's own frame, so it bobs and
    tilts with the clip). Returns {clip: (worst depth, frame, side)}; depth <= 0 means clear."""
    ob = bpy.data.objects
    scn = _scene()
    hx, hy, hz = HEAD_HALF
    boxes = [  # (centre, half size) in head space
        ((0.0, -0.02, 0.0), (hx + 0.02, hy + SCREEN_BULGE + 0.03, hz + 0.03)),                 # head
        ((hx + 0.1, 0.0, -0.02), (0.12, EAR_RADIUS + 0.02, EAR_RADIUS + 0.02)),                # right ear
        ((-hx - 0.1, 0.0, -0.02), (0.12, EAR_RADIUS + 0.02, EAR_RADIUS + 0.02)),
        ((hx + 0.12, 0.0, 0.34), (0.1, 0.1, 0.24)),                                           # antennas
        ((-hx - 0.12, 0.0, 0.34), (0.1, 0.1, 0.24)),
    ]
    arms = {"L": ob["PR_ArmMeshL"], "R": ob["PR_ArmMeshR"]}
    local = {k: [v.co.copy() for v in a.data.vertices] for k, a in arms.items()}
    result = {}
    for clip, length in CLIPS.items():
        solo_clip(clip, 0)
        worst = (-1.0, 0, "")
        for f in range(0, length + 1, step):
            scn.frame_set(f)
            to_head = ob["PR_HeadRoot"].matrix_world.inverted()
            for side, arm in arms.items():
                m = to_head @ arm.matrix_world
                for co in local[side]:
                    q = m @ co
                    for c, h in boxes:
                        d = min(h[0] - abs(q.x - c[0]), h[1] - abs(q.y - c[1]), h[2] - abs(q.z - c[2]))
                        if d > worst[0]:
                            worst = (round(d, 3), f, side)
        result[clip] = worst
    use_clips(True)
    return result


def make_clips(step=3):
    """Key every animated pivot for every clip and push each into an NLA track named after the clip."""
    rest = _rest(ANIMATED)
    for name in ANIMATED:
        ad = bpy.data.objects[name].animation_data_create()
        for track in list(ad.nla_tracks):
            ad.nla_tracks.remove(track)
        ad.action = None
    for act in [a for a in bpy.data.actions if "__PR_" in a.name]:
        bpy.data.actions.remove(act)

    for clip, length in CLIPS.items():
        frames = list(range(0, length + 1, step))
        if frames[-1] != length:
            frames.append(length)
        for name in ANIMATED:
            ob = bpy.data.objects[name]
            ob.animation_data.action = None
            loc0, rot0 = rest[name]
            for f in frames:
                dloc, drot = _pose(f / length, clip).get(name, ((0, 0, 0), (0, 0, 0)))
                ob.location = loc0 + Vector(dloc)
                ob.rotation_euler = (rot0.x + math.radians(drot[0]), rot0.y + math.radians(drot[1]),
                                     rot0.z + math.radians(drot[2]))
                ob.keyframe_insert("location", frame=f)
                ob.keyframe_insert("rotation_euler", frame=f)
            act = ob.animation_data.action
            act.name = f"{clip}__{name}"
            act.use_fake_user = True
            track = ob.animation_data.nla_tracks.new()
            track.name = clip
            strip = track.strips.new(clip, 0, act)
            if hasattr(strip, "action_slot") and strip.action_slot is None and len(getattr(act, "slots", [])):
                strip.action_slot = act.slots[0]
            ob.animation_data.action = None
            ob.location, ob.rotation_euler = loc0, rot0
    _scene().render.fps = FPS
    return list(CLIPS)


# ---------------------------------------------------------------- accessories

def _acc_materials():
    return {
        "gold": material("ACC_Gold", "#F2BE3C", rough=0.22, coat=0.6, spec=0.8),
        "gem_red": material("ACC_GemRed", "#E0344F", rough=0.1, coat=1.0, emit=0.3),
        "gem_blue": material("ACC_GemBlue", "#2F8CFF", rough=0.1, coat=1.0, emit=0.3),
        "pink": material("ACC_Pink", "#FF6FA8", rough=0.35, coat=0.3),
        "yellow": material("ACC_Yellow", "#FFD23F", rough=0.35, coat=0.3),
        "purple": material("ACC_Purple", "#6A3FB5", rough=0.5, coat=0.2),
        "white": material("ACC_White", "#FAFAFA", rough=0.6),
        "black": material("ACC_Black", "#15171B", rough=0.3, coat=0.5),
        "lens": material("ACC_Lens", "#232B4A", rough=0.05, coat=1.0, spec=1.0),
        "grey": material("ACC_Grey", "#3A3F47", rough=0.35, coat=0.4),
        "cushion": material("ACC_Cushion", "#23262C", rough=0.8),
        "orange": material("ACC_Orange", "#FF8A3D", rough=0.45, coat=0.2),
    }


def _cone_bm(r_bottom, r_top, height, segs=32, rings=6):
    """Open-bottom cone/frustum made of quad rings (+ tip fan), base at z=0."""
    bm = bmesh.new()
    rows = []
    for j in range(rings + 1):
        t = j / rings
        r = r_bottom + (r_top - r_bottom) * t
        rows.append([bm.verts.new((r * math.cos(2 * math.pi * i / segs), r * math.sin(2 * math.pi * i / segs), height * t))
                     for i in range(segs)])
    for a, b in zip(rows, rows[1:]):
        for i in range(segs):
            bm.faces.new((a[i], a[(i + 1) % segs], b[(i + 1) % segs], b[i]))
    if r_top < 1e-4:
        pass
    bottom = bm.faces.new(list(reversed(rows[0])))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
    return bm


def _torus_arc_bm(radius_x, radius_z, tube, a0, a1, segs=40, tube_segs=12, y=0.0):
    """Tube following an elliptical arc in the XZ plane from angle a0 to a1 (deg, 0 = +X)."""
    bm = bmesh.new()
    rings = []
    for i in range(segs + 1):
        a = math.radians(a0 + (a1 - a0) * i / segs)
        c = Vector((radius_x * math.cos(a), y, radius_z * math.sin(a)))
        tangent = Vector((-radius_x * math.sin(a), 0, radius_z * math.cos(a))).normalized()
        n1 = Vector((0, 1, 0))
        n2 = tangent.cross(n1).normalized()
        rings.append([bm.verts.new(c + tube * (math.cos(2 * math.pi * k / tube_segs) * n2 +
                                                math.sin(2 * math.pi * k / tube_segs) * n1))
                      for k in range(tube_segs)])
    for a, b in zip(rings, rings[1:]):
        for k in range(tube_segs):
            bm.faces.new((a[k], a[(k + 1) % tube_segs], b[(k + 1) % tube_segs], b[k]))
    bm.faces.new(list(reversed(rings[0])))
    bm.faces.new(rings[-1])
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
    return bm


def _head_top():
    return HEAD_Z + HEAD_HALF[2] * (1 + HEAD_PILLOW) - 0.03


def make_accessories():
    """Model every accessory in place on the rest pose; each lives under an ACC_<name> empty."""
    scn = _scene()
    coll = bpy.data.collections.get("PR_Accessories")
    if coll is None:
        coll = bpy.data.collections.new("PR_Accessories")
        scn.collection.children.link(coll)
    for ob in list(coll.objects):
        bpy.data.objects.remove(ob, do_unlink=True)
    m = _acc_materials()
    top = _head_top()
    face_y = -HEAD_HALF[1] + SCREEN_DEPTH - SCREEN_BULGE - 0.02     # clear of the raised bezel and domed glass
    ear_x = HEAD_HALF[0] + 0.08

    def holder(name):
        e = bpy.data.objects.new(f"ACC_{name}", None)
        coll.objects.link(e)
        return e

    # crown: gold band with five flat points (each tipped with a ball) and gems
    e = holder("crown")
    cr = 0.3
    add("ACC_crown_band", cylinder_bm(cr, 0.12, support=0.01), m["gold"], e, loc=(0, 0, top + 0.05), subsurf=2)
    for i in range(5):
        a = 2 * math.pi * i / 5 - math.pi / 2              # first point faces the front (-Y)
        x, y = cr * 0.97 * math.cos(a), cr * 0.97 * math.sin(a)
        add(f"ACC_crown_point{i}", blob_bm((0.085, 0.03, 0.13), cuts=2, taper_top=0.85), m["gold"], e,
            loc=(x, y, top + 0.2), rot=(0, 0, math.degrees(a) + 90), subsurf=2)
        add(f"ACC_crown_ball{i}", blob_bm((0.032, 0.032, 0.032), cuts=2), m["gold"], e,
            loc=(x, y, top + 0.335), subsurf=1)
    add("ACC_crown_gem", blob_bm((0.055, 0.025, 0.05), cuts=2), m["gem_red"], e, loc=(0, -cr - 0.01, top + 0.05), subsurf=2)
    for sx in (-1, 1):
        a = math.radians(-90 + sx * 42)
        add("ACC_crown_gem_side", blob_bm((0.035, 0.018, 0.035), cuts=2), m["gem_blue"], e,
            loc=((cr + 0.005) * math.cos(a), (cr + 0.005) * math.sin(a), top + 0.05), rot=(0, 0, math.degrees(a) + 90), subsurf=2)

    # party hat: striped cone tilted to one side with a pompom
    e = holder("party_hat")
    tilt = Matrix.Translation((0.16, 0, top - 0.03)) @ Matrix.Rotation(math.radians(-14), 4, "Y")
    k_h, k_r = 0.62, 0.26                                    # cone height / base radius
    for k, (z0, z1, mat) in enumerate(((0.0, 0.17, "pink"), (0.17, 0.33, "yellow"), (0.33, 0.47, "pink"), (0.47, 0.58, "yellow"))):
        r0 = k_r * (1 - z0 / k_h)
        r1 = k_r * (1 - z1 / k_h)
        ob = add(f"ACC_party_band{k}", _cone_bm(r0, r1, z1 - z0, rings=2), m[mat], e, subsurf=1)
        ob.matrix_world = tilt @ Matrix.Translation((0, 0, z0))
    ob = add("ACC_party_pompom", blob_bm((0.07, 0.07, 0.07), cuts=2), m["white"], e, subsurf=2)
    ob.matrix_world = tilt @ Matrix.Translation((0, 0, k_h - 0.01))

    # wizard hat: wide brim, bent cone, gold band and star
    e = holder("wizard_hat")
    add("ACC_wizard_brim", cylinder_bm(0.44, 0.03, support=0.008), m["purple"], e, loc=(0, 0.02, top + 0.0), subsurf=2)
    cone = _cone_bm(0.26, 0.0, 0.62, rings=8)
    for v in cone.verts:                                   # bend the tip backwards and to the side
        t = v.co.z / 0.62
        v.co.y += 0.16 * t * t
        v.co.x += 0.08 * t * t
    add("ACC_wizard_cone", cone, m["purple"], e, loc=(0, 0.02, top + 0.01), subsurf=2)
    add("ACC_wizard_band", cylinder_bm(0.25, 0.06, support=0.006), m["yellow"], e, loc=(0, 0.02, top + 0.05), subsurf=2)
    add("ACC_wizard_star", blob_bm((0.05, 0.015, 0.05), cuts=1), m["yellow"], e, loc=(0.0, -0.21, top + 0.2),
        rot=(0, 45, 0), subsurf=0)

    # chef hat: band plus a puffy top
    e = holder("chef_hat")
    add("ACC_chef_band", cylinder_bm(0.24, 0.14, support=0.01), m["white"], e, loc=(0, 0, top + 0.06), subsurf=2)
    for i, (x, y, z, r) in enumerate(((0, 0, 0.28, 0.2), (0.14, 0.05, 0.24, 0.15), (-0.14, 0.05, 0.24, 0.15),
                                      (0, -0.13, 0.23, 0.14), (0, 0.15, 0.24, 0.14))):
        add(f"ACC_chef_puff{i}", blob_bm((r, r, r * 0.9), cuts=3), m["white"], e, loc=(x, y, top + z), subsurf=2)

    # sunglasses: two rounded dark lenses in a black frame, arms back to the ears
    e = holder("sunglasses")
    # sized to cover the eyes (±EYE_X, 2 x EYE_HALF) with a margin, sitting just proud of the face
    lz = HEAD_Z + 0.015
    lx = EYE_X + 0.03
    lw, lh = EYE_HALF[0] + 0.1, EYE_HALF[1] + 0.05
    for sx in (-1, 1):
        add(f"ACC_sun_frame{sx}", blob_bm((lw + 0.02, 0.035, lh + 0.02), cuts=3), m["black"], e,
            loc=(sx * lx, face_y - 0.01, lz), subsurf=2)
        add(f"ACC_sun_lens{sx}", blob_bm((lw, 0.022, lh), cuts=3), m["lens"], e,
            loc=(sx * lx, face_y - 0.04, lz), subsurf=2)
        add(f"ACC_sun_arm{sx}", blob_bm((0.016, 0.26, 0.018), cuts=2), m["black"], e,
            loc=(sx * (HEAD_HALF[0] + 0.03), face_y + 0.25, lz + 0.05), subsurf=1)
        add(f"ACC_sun_hinge{sx}", blob_bm((0.05, 0.03, 0.03), cuts=1), m["black"], e,
            loc=(sx * (lx + lw), face_y - 0.005, lz + 0.05), subsurf=1)
    add("ACC_sun_bridge", blob_bm((0.06, 0.02, 0.02), cuts=2), m["black"], e, loc=(0, face_y - 0.02, lz + 0.06), subsurf=1)

    # headphones: band over the head (behind the antennas), cups over the ear pivots
    e = holder("headphones")
    # cups sit over the ear discs (pivot centre hx + 0.07, hub out to hx + 0.18) and swallow the hub
    cup_x = HEAD_HALF[0] + 0.16
    ear_z = HEAD_Z - 0.02
    add("ACC_hp_band", _torus_arc_bm(cup_x, top - ear_z + 0.07, 0.038, 8, 172, y=0.16), m["grey"], e,
        loc=(0, 0, ear_z), subsurf=1)
    for sx in (-1, 1):
        add(f"ACC_hp_cup{sx}", cylinder_bm(EAR_RADIUS + 0.035, 0.14, hub=(EAR_RADIUS - 0.03, 0.02)), m["grey"], e,
            loc=(sx * cup_x, 0.0, ear_z), rot=(0, sx * 90, 0), subsurf=2)
        add(f"ACC_hp_pad{sx}", cylinder_bm(EAR_RADIUS + 0.02, 0.05), m["cushion"], e,
            loc=(sx * (HEAD_HALF[0] + 0.085), 0.0, ear_z), rot=(0, sx * 90, 0), subsurf=2)
    # backpack on the back of the vest, with straps over the shoulders
    e = holder("backpack")
    back_y = VEST_R[1] + 0.13
    add("ACC_bp_body", blob_bm((0.27, 0.13, 0.28), cuts=4), m["orange"], e, loc=(0, back_y, 0.74), subsurf=2)
    add("ACC_bp_pocket", blob_bm((0.17, 0.06, 0.11), cuts=3), m["yellow"], e, loc=(0, back_y + 0.12, 0.64), subsurf=2)
    add("ACC_bp_flap", blob_bm((0.25, 0.1, 0.07), cuts=3), m["orange"], e, loc=(0, back_y + 0.02, 0.98), subsurf=2)
    for sx in (-1, 1):
        add(f"ACC_bp_strap{sx}", _torus_arc_bm(0.25, 0.2, 0.028, -10, 190, segs=24, y=0.0), m["black"], e,
            loc=(sx * 0.2, 0.2, 0.92), rot=(0, 0, 90), subsurf=1)

    # follow the pivot in Blender too (modelled on the rest pose, so parent while at rest)
    use_clips(False)
    for name, (pivot, *_rest) in ACCESSORIES.items():
        if name in globals().get("GEAR", {}):      # robot_gear.py places its own
            continue
        e = bpy.data.objects[f"ACC_{name}"]
        _reparent(e, bpy.data.objects[pivot])
        _set_visible(e, False)
    use_clips(True)
    return sorted(ACCESSORIES)


def _set_visible(holder, visible):
    for ob in [holder, *holder.children_recursive]:
        ob.hide_render = not visible
        ob.hide_viewport = not visible


def show_accessories(*names):
    """Preview in Blender: show only the named accessories."""
    for name in ACCESSORIES:
        _set_visible(bpy.data.objects[f"ACC_{name}"], name in names)


# ---------------------------------------------------------------- build / export

def build_game():
    info = build(uv=True)
    info["pivots"] = rig()
    info["clips"] = make_clips()
    info["accessories"] = make_accessories()
    if "make_gear" in globals():                     # robot_gear.py loaded in the same namespace
        info["gear"] = make_gear()
    return info


def _select_only(objs):
    scn = _scene()
    vl = scn.view_layers[0]
    for o in scn.collection.all_objects:
        o.select_set(False, view_layer=vl)
    for o in objs:
        o.hide_viewport = False
        o.select_set(True, view_layer=vl)
    vl.objects.active = objs[0]


def _export(path, objs, animations):
    import os
    os.makedirs(os.path.dirname(path), exist_ok=True)
    scn = _scene()
    _select_only(objs)                  # selected + enabled in viewports (hidden objects are skipped)
    # call the operator directly: under context.temp_override the exporter crashes Blender 5.2
    # (EXCEPTION_ACCESS_VIOLATION in BKE_base_eval_flags); the scene is already the active one
    if True:
        bpy.ops.export_scene.gltf(
            filepath=path, export_format="GLB", use_selection=True, export_apply=True,
            export_yup=True, export_texcoords=True, export_normals=True, export_materials="EXPORT",
            export_cameras=False, export_lights=False,
            export_animations=animations, export_animation_mode="NLA_TRACKS" if animations else "ACTIONS",
            export_force_sampling=True, export_frame_step=1)
    return path


def export_game(out_dir):
    """robot.glb (model + clips, no review eyes / brackets) and accessories/<name>.glb."""
    import json
    import os
    ob = bpy.data.objects
    use_clips(False)                    # everything at rest while exporting the accessories
    written = []
    for name in ACCESSORIES:
        holder = ob[f"ACC_{name}"]
        pivot = holder.parent
        # visible first: objects disabled in viewports are skipped by the depsgraph, so a hidden
        # holder's matrix_world is stale (e.g. still turned for the last preview shot)
        _set_visible(holder, True)
        _update()
        _reparent(holder, None)         # world space (= model space of the robot at rest)
        written.append(_export(os.path.join(out_dir, "accessories", f"{name}.glb"),
                               [holder, *holder.children_recursive], False))
        _set_visible(holder, False)
        _reparent(holder, pivot)
    pivots = {n: _gltf(ob[n].matrix_world.translation) for n in ANIMATED + ["PR_HeadRoot"]}
    screen = ob["PR_ScreenAnchor"]

    robot = [o for o in ob["PetRobot"].children_recursive
             if not (o.name.startswith(("PR_Eye", "PR_Bracket", "ACC_")) or o.name == "PR_ScreenAnchor"
                     or (o.parent and o.parent.name.startswith("ACC_")))]
    robot.insert(0, ob["PetRobot"])
    use_clips(True)
    written.insert(0, _export(os.path.join(out_dir, "robot.glb"), robot, True))
    use_clips(False)
    meta = {
        "clips": {k: round(v / FPS, 3) for k, v in CLIPS.items()},
        "accessories": {k: {"follows": v[0], "slot": v[1], "prop": v[2], **(v[3] if len(v) > 3 else {})}
                        for k, v in ACCESSORIES.items()},
        "pivots_rest": pivots,          # glTF / Godot space (Y up), for placing accessories
        "screen": {"node": "PR_Screen", "half_size": list(screen["half_size"]),
                   "center": _gltf(screen.matrix_world.translation)},
    }
    with open(os.path.join(out_dir, "robot_meta.json"), "w", encoding="utf-8") as fh:
        json.dump(meta, fh, indent=2)
    return written
