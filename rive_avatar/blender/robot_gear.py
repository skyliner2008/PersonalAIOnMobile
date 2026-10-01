"""Armour-style gear for the game robot: hand tools, back gear, more headgear.

Runs after robot_build.py and robot_game.py in the same namespace (export_game.py does this);
build_game() calls make_gear() when it exists, and GEAR is merged into ACCESSORIES so the export
writes one glb per item and describes it in robot_meta.json.

    hand tools (per arm, slot hand_l / hand_r): cannon (rockets), laser, flashlight, shield
    back gear: jetpack (slot back, flies), cape (slot cape, waves in the wind)
    headgear:  propeller_cap, cat_ears, halo, devil_horns, viking_helmet, astronaut_helmet, fedora,
               vr_headset

Everything is modelled in place on the rest pose (world space), like the other accessories.
Empties inside an item mark where the game spawns effects: *_muzzle (tools), *_nozzle (jetpack),
ACC_propeller_cap_spin (the part that spins), ACC_halo_ring (the part that bobs).
"""
import math

import bmesh
import bpy
from mathutils import Matrix, Vector

TOOLS = ("cannon", "laser", "flashlight", "shield")

GEAR = {}
for _tool in TOOLS:
    for _side in ("L", "R"):
        GEAR[f"{_tool}_{_side}"] = (f"PR_Arm{_side}", f"hand_{_side.lower()}", None,
                                    {"kind": "tool", "tool": _tool, "side": _side,
                                     "muzzle": f"ACC_{_tool}_{_side}_muzzle"})
GEAR.update({
    "jetpack": ("PR_BodyPivot", "back", None, {"kind": "gear", "nozzles": ["ACC_jetpack_nozzle_L", "ACC_jetpack_nozzle_R"]}),
    "cape": ("PR_BodyPivot", "cape", "SUPERHERO_CAPE", {"kind": "gear", "wave": "ACC_cape_cloth"}),
    "propeller_cap": ("PR_HeadPivot", "hat", None, {"spin": "ACC_propeller_cap_spin"}),
    "cat_ears": ("PR_HeadPivot", "hat", None, {}),
    "halo": ("PR_HeadPivot", "hat", None, {"bob": "ACC_halo_ring"}),
    "devil_horns": ("PR_HeadPivot", "hat", "DEVIL_HORNS", {}),
    "viking_helmet": ("PR_HeadPivot", "hat", "VIKING_HELMET", {}),
    "astronaut_helmet": ("PR_HeadPivot", "hat", "ASTRONAUT_HELMET", {}),
    "fedora": ("PR_HeadPivot", "hat", "FEDORA_HAT", {}),
    "vr_headset": ("PR_HeadPivot", "face", "VR_HEADSET", {}),
})
ACCESSORIES.update(GEAR)


def _gear_materials():
    m = _acc_materials()
    m.update({
        "gunmetal": material("ACC_Gunmetal", "#4B525C", rough=0.38, metal=0.8, spec=0.6),
        "dark": material("ACC_Dark", "#1C2026", rough=0.5, metal=0.3),
        "accent": material("ACC_Accent", "#FF7A3D", rough=0.4, coat=0.2),
        "cyan_glow": material("ACC_CyanGlow", "#3FF6FF", rough=0.2, emit=6.0),
        "warm_glow": material("ACC_WarmGlow", "#FFF1C4", rough=0.1, emit=8.0),
        "suit_white": material("ACC_SuitWhite", "#ECEFF3", rough=0.46, coat=0.1),
        "suit_blue": material("ACC_SuitBlue", "#6FA6DE", rough=0.55, coat=0.05),
        "red": material("ACC_Red", "#D8343A", rough=0.55),
        "cape_red": material("ACC_CapeRed", "#D8343A", rough=0.7, spec=0.3),
        "gold_glow": material("ACC_GoldGlow", "#FFD76A", rough=0.2, emit=3.0),
        "horn": material("ACC_Horn", "#F1E4C6", rough=0.5),
        "steel": material("ACC_Steel", "#9AA3AD", rough=0.3, metal=0.9),
        "bronze": material("ACC_Bronze", "#B07A3C", rough=0.35, metal=0.8),
        "glass": material("ACC_Glass", "#DDEFFF", rough=0.03, spec=0.9, alpha=0.18),
        "felt": material("ACC_Felt", "#5A4033", rough=0.85),
        "visor": material("ACC_Visor", "#101522", rough=0.08, coat=1.0),
    })
    return m


def _gear_holder(name):
    coll = bpy.data.collections["PR_Accessories"]
    e = bpy.data.objects.new(f"ACC_{name}", None)
    coll.objects.link(e)
    return e


def _mark(name, holder, matrix):
    """An empty the game looks up (muzzle, nozzle, spinning part...)."""
    e = bpy.data.objects.new(name, None)
    e.empty_display_size = 0.05
    bpy.data.collections["PR_Accessories"].objects.link(e)
    e.parent = holder
    e.matrix_world = matrix
    return e


def _place(ob, matrix):
    ob.matrix_world = matrix
    return ob


def _hand_frame(s, t):
    """Frame on the arm's centre line at t (0 top .. 1 tip): local -Z runs along the arm towards
    the tip, local Y is front-back (world Y), local X points out of the body for either arm."""
    c, tan = _bezier(ARM_PATH, t)                  # right arm, (x, 0, z)
    c = Vector((s * c.x, 0.0, c.z))
    fwd = Vector((s * tan.x, 0.0, tan.z)).normalized()
    z_axis = -fwd
    y_axis = Vector((0.0, 1.0, 0.0))
    x_axis = y_axis.cross(z_axis).normalized()
    if (x_axis.x > 0) != (s > 0):                  # keep local +X pointing away from the body
        x_axis = -x_axis
        y_axis = z_axis.cross(x_axis).normalized()
    rot = Matrix((x_axis, y_axis, z_axis)).transposed().to_4x4()
    return Matrix.Translation(c) @ rot


def _local(frame, loc=(0, 0, 0), rot=(0, 0, 0)):
    r = Matrix.Rotation(math.radians(rot[0]), 4, "X") @ Matrix.Rotation(math.radians(rot[1]), 4, "Y") \
        @ Matrix.Rotation(math.radians(rot[2]), 4, "Z")
    return frame @ Matrix.Translation(loc) @ r


# ---------------------------------------------------------------- hand tools

def _tool(name, s, m):
    side = "L" if s < 0 else "R"
    e = _gear_holder(f"{name}_{side}")
    f = _hand_frame(s, 0.8)
    part = lambda n, bm, mat, loc=(0, 0, 0), rot=(0, 0, 0), sub=2: _place(      # noqa: E731
        add(f"ACC_{name}_{side}_{n}", bm, mat, e, subsurf=sub), _local(f, loc, rot))
    if name == "cannon":
        part("sleeve", cylinder_bm(0.17, 0.28), m["gunmetal"], (0, 0, -0.04))
        part("band", cylinder_bm(0.178, 0.05), m["suit_blue"], (0, 0, 0.06))
        part("barrel", cylinder_bm(0.125, 0.24), m["dark"], (0, 0, -0.28))
        part("ring", cylinder_bm(0.15, 0.06), m["accent"], (0, 0, -0.39))
        part("bore", cylinder_bm(0.07, 0.03), m["dark"], (0, 0, -0.43), sub=1)
        for k in (-1, 1):                                   # mini rockets on the sides
            part(f"pod{k}", cylinder_bm(0.045, 0.2), m["suit_white"], (0.2, k * 0.08, -0.14))
            part(f"tip{k}", blob_bm((0.045, 0.045, 0.06), cuts=2, taper_bottom=0.9), m["red"], (0.2, k * 0.08, -0.26))
        _mark(f"ACC_cannon_{side}_muzzle", e, _local(f, (0, 0, -0.47)))
    elif name == "laser":
        part("sleeve", cylinder_bm(0.165, 0.24), m["suit_white"], (0, 0, -0.02))
        part("emitter", _cone_bm(0.14, 0.09, 0.22), m["suit_blue"], (0, 0, -0.12), (180, 0, 0), sub=1)
        for k, z in enumerate((-0.16, -0.26)):
            part(f"ring{k}", cylinder_bm(0.125 - 0.02 * k, 0.025), m["cyan_glow"], (0, 0, z))
        part("lens", cylinder_bm(0.08, 0.03), m["cyan_glow"], (0, 0, -0.35))
        _mark(f"ACC_laser_{side}_muzzle", e, _local(f, (0, 0, -0.39)))
    elif name == "flashlight":
        part("sleeve", cylinder_bm(0.165, 0.22), m["suit_blue"], (0, 0, -0.02))
        part("neck", cylinder_bm(0.11, 0.1), m["gunmetal"], (0, 0, -0.17))
        part("head", _cone_bm(0.11, 0.17, 0.13), m["gunmetal"], (0, 0, -0.21), (180, 0, 0), sub=1)
        part("lens", cylinder_bm(0.15, 0.025), m["warm_glow"], (0, 0, -0.345))
        _mark(f"ACC_flashlight_{side}_muzzle", e, _local(f, (0, 0, -0.37)))
    elif name == "shield":
        # a generator disc on the outside of the forearm; the game grows the energy shield from it
        g = _hand_frame(s, 0.66)
        dev = lambda n, bm, mat, loc, sub=2: _place(add(f"ACC_shield_{side}_{n}", bm, mat, e, subsurf=sub),  # noqa: E731
                                                     _local(g, loc, (0, 90, 0)))
        dev("base", cylinder_bm(0.15, 0.07, hub=(0.1, 0.03)), m["suit_white"], (0.12, 0, 0))
        dev("ring", cylinder_bm(0.12, 0.02), m["cyan_glow"], (0.17, 0, 0))
        dev("core", blob_bm((0.06, 0.06, 0.02), cuts=2), m["cyan_glow"], (0.19, 0, 0))
        _mark(f"ACC_shield_{side}_muzzle", e, _local(g, (0.32, 0, 0), (0, 90, 0)))
    return e


# ---------------------------------------------------------------- back gear

def _jetpack(m):
    e = _gear_holder("jetpack")
    back_y = VEST_R[1] + 0.1
    add("ACC_jetpack_plate", blob_bm((0.2, 0.06, 0.2), cuts=3), m["suit_white"], e, loc=(0, back_y - 0.02, 0.8))
    for k, sx in (("L", -1), ("R", 1)):
        x = sx * 0.19
        add(f"ACC_jetpack_tank_{k}", blob_bm((0.11, 0.11, 0.24), cuts=4), m["suit_white"], e, loc=(x, back_y + 0.07, 0.78))
        add(f"ACC_jetpack_cap_{k}", blob_bm((0.1, 0.1, 0.06), cuts=3), m["suit_blue"], e, loc=(x, back_y + 0.07, 1.0))
        add(f"ACC_jetpack_stripe_{k}", cylinder_bm(0.113, 0.04), m["accent"], e, loc=(x, back_y + 0.07, 0.86))
        noz = add(f"ACC_jetpack_noz_{k}", _cone_bm(0.07, 0.1, 0.12), m["gunmetal"], e, loc=(x, back_y + 0.07, 0.55),
                  rot=(180, 0, 0), subsurf=1)
        add(f"ACC_jetpack_fin_{k}", blob_bm((0.02, 0.1, 0.1), cuts=2, taper_bottom=0.6), m["suit_blue"], e,
            loc=(x + sx * 0.12, back_y + 0.1, 0.66), rot=(0, sx * -15, 0))
        _mark(f"ACC_jetpack_nozzle_{k}", e, Matrix.Translation((x, back_y + 0.07, 0.42)))
    return e


def _cape(m):
    """Cloth over the shoulders and down the back; UV v runs from the shoulders (0) to the hem (1)
    so the game can wave the lower part more."""
    e = _gear_holder("cape")
    nu, nv = 14, 16
    bm = bmesh.new()
    uv = bm.loops.layers.uv.new("UVMap")
    grid = []
    for j in range(nv + 1):
        v = j / nv
        half_w = 0.36 + 0.3 * v                     # flares towards the hem
        z = 1.0 - 0.9 * v
        row = []
        for i in range(nu + 1):
            u = i / nu * 2 - 1
            # wraps around the back of the vest at the top, falls behind the belly lower down
            y = 0.44 + 0.2 * v + 0.14 * (1 - u * u) * (1 - 0.5 * v) - 0.18 * (u * u) * (1 - v)
            row.append(bm.verts.new((u * half_w, y, z)))
        grid.append(row)
    for j in range(nv):
        for i in range(nu):
            f = bm.faces.new((grid[j][i], grid[j + 1][i], grid[j + 1][i + 1], grid[j][i + 1]))
            for loop, (a, b) in zip(f.loops, ((i, j), (i, j + 1), (i + 1, j + 1), (i + 1, j))):
                loop[uv].uv = (a / nu, 1 - b / nv)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
    cloth = add("ACC_cape_cloth", bm, m["cape_red"], e, subsurf=1)
    cloth.modifiers.new("Thickness", "SOLIDIFY").thickness = 0.012
    for sx in (-1, 1):                                 # gold clasps on the shoulders
        add(f"ACC_cape_clasp{sx}", cylinder_bm(0.06, 0.03, hub=(0.035, 0.015)), m["gold"], e,
            loc=(sx * 0.33, 0.12, 1.0), rot=(-60, 0, 0))
    return e


# ---------------------------------------------------------------- headgear

def _flat_dome_bm(half, squash=0.06):
    """Half ellipsoid with a flat, rounded underside (caps, helmets)."""
    bm = grid_cube(4)
    for v in bm.verts:
        n = v.co.normalized()
        v.co = Vector((n.x * half[0], n.y * half[1], n.z * (half[2] if n.z > 0 else squash)))
    return bm


def _headgear(m):
    top = _head_top()
    hz = HEAD_Z
    face_y = -HEAD_HALF[1] + SCREEN_DEPTH - SCREEN_BULGE - 0.02
    made = []

    e = _gear_holder("propeller_cap")                  # beanie with a spinning propeller
    add("ACC_propeller_cap_dome", _flat_dome_bm((0.36, 0.33, 0.2)), m["yellow"], e, loc=(0, 0.02, top - 0.01))
    add("ACC_propeller_cap_band", cylinder_bm(0.355, 0.05), m["red"], e, loc=(0, 0.02, top + 0.01))
    add("ACC_propeller_cap_brim", blob_bm((0.2, 0.14, 0.018), cuts=3), m["gem_blue"], e,
        loc=(0, -0.33, top + 0.02), rot=(-10, 0, 0))
    add("ACC_propeller_cap_stem", cylinder_bm(0.025, 0.1), m["red"], e, loc=(0, 0.02, top + 0.22), subsurf=1)
    spin = _mark("ACC_propeller_cap_spin", e, Matrix.Translation((0, 0.02, top + 0.28)))
    for k in (-1, 1):
        blade = add(f"ACC_propeller_cap_blade{k}", blob_bm((0.2, 0.05, 0.012), cuts=3), m["gem_blue" if k > 0 else "red"],
                    spin, subsurf=2)
        blade.location = (k * 0.2, 0, 0)
        blade.rotation_euler = (math.radians(k * 12), 0, 0)
    made.append(e)

    e = _gear_holder("cat_ears")
    for sx in (-1, 1):
        add(f"ACC_cat_ear{sx}", blob_bm((0.13, 0.05, 0.15), cuts=3, taper_top=0.85), m["suit_white"], e,
            loc=(sx * 0.3, 0.02, top + 0.08), rot=(0, sx * 18, 0))
        add(f"ACC_cat_inner{sx}", blob_bm((0.08, 0.02, 0.1), cuts=2, taper_top=0.85), m["pink"], e,
            loc=(sx * 0.3 - sx * 0.01, -0.03, top + 0.07), rot=(0, sx * 18, 0))
    made.append(e)

    e = _gear_holder("halo")
    ring = _mark("ACC_halo_ring", e, Matrix.Translation((0, 0.05, top + 0.24)) @ Matrix.Rotation(math.radians(-8), 4, "X"))
    halo = add("ACC_halo_torus", _torus_arc_bm(0.3, 0.3, 0.035, 0, 360, segs=48), m["gold_glow"], ring, subsurf=1)
    halo.rotation_euler = (math.radians(90), 0, 0)
    made.append(e)

    e = _gear_holder("devil_horns")
    for sx in (-1, 1):
        add(f"ACC_devil_horn{sx}", blob_bm((0.07, 0.07, 0.17), cuts=3, taper_top=0.85, bend=sx * 0.08), m["red"], e,
            loc=(sx * 0.27, -0.05, top + 0.1), rot=(0, sx * 20, 0))
    made.append(e)

    e = _gear_holder("viking_helmet")
    add("ACC_viking_dome", _flat_dome_bm((0.64, 0.52, 0.32)), m["steel"], e, loc=(0, 0.0, top - 0.2))
    add("ACC_viking_band", cylinder_bm(0.645, 0.08), m["bronze"], e, loc=(0, 0.0, top - 0.17))
    add("ACC_viking_ridge", blob_bm((0.05, 0.46, 0.06), cuts=2), m["bronze"], e, loc=(0, 0.0, top + 0.1))
    for sx in (-1, 1):
        add(f"ACC_viking_horn{sx}", blob_bm((0.09, 0.09, 0.28), cuts=3, taper_top=0.85, bend=sx * 0.14), m["horn"], e,
            loc=(sx * 0.6, -0.1, top - 0.02), rot=(-15, sx * 58, 0))
    made.append(e)

    e = _gear_holder("astronaut_helmet")               # glass bubble over the whole head, open at the neck
    bubble = blob_bm((0.94, 0.82, 0.72), cuts=6)
    cut = hz - 0.44
    bmesh.ops.translate(bubble, verts=bubble.verts[:], vec=(0, 0, hz + 0.08))
    bmesh.ops.delete(bubble, geom=[v for v in bubble.verts if v.co.z < cut], context="VERTS")
    add("ACC_astronaut_glass", bubble, m["glass"], e, subsurf=1)
    ring_r = 0.94 * math.sqrt(max(0.0, 1 - ((cut - hz - 0.08) / 0.72) ** 2))
    add("ACC_astronaut_collar", _torus_arc_bm(ring_r, ring_r * 0.87, 0.06, 0, 360, segs=48), m["suit_white"], e,
        loc=(0, 0, cut), rot=(90, 0, 0), subsurf=1)
    made.append(e)

    e = _gear_holder("fedora")
    brim = _flat_dome_bm((0.7, 0.62, 0.06), squash=0.025)
    for v in brim.verts:                               # curl the brim up at the sides
        v.co.z += 0.1 * (abs(v.co.x) / 0.7) ** 3
    add("ACC_fedora_brim", brim, m["felt"], e, loc=(0, 0, top - 0.02))
    crown = blob_bm((0.39, 0.36, 0.22), cuts=4, taper_top=0.15)
    for v in crown.verts:                              # pinch the front of the crown
        if v.co.z > 0.05 and v.co.y < 0:
            v.co.x *= 0.85
    add("ACC_fedora_crown", crown, m["felt"], e, loc=(0, 0.02, top + 0.15))
    add("ACC_fedora_band", cylinder_bm(0.39, 0.06), m["black"], e, loc=(0, 0.02, top + 0.03))
    for ob in list(e.children):
        ob.rotation_euler.y += math.radians(6)
    made.append(e)

    e = _gear_holder("vr_headset")
    add("ACC_vr_body", blob_bm((0.46, 0.13, 0.25), cuts=4), m["suit_white"], e, loc=(0, face_y - 0.08, hz))
    add("ACC_vr_visor", blob_bm((0.42, 0.04, 0.2), cuts=3), m["visor"], e, loc=(0, face_y - 0.2, hz))
    add("ACC_vr_strip", blob_bm((0.34, 0.012, 0.03), cuts=2), m["cyan_glow"], e, loc=(0, face_y - 0.24, hz + 0.02), subsurf=1)
    add("ACC_vr_strap", _torus_arc_bm(HEAD_HALF[0] + 0.05, top - hz + 0.04, 0.03, 12, 168, y=0.05), m["black"], e,
        loc=(0, 0, hz), subsurf=1)
    made.append(e)
    return made


def make_gear():
    """Model the gear, then parent each holder to its pivot at rest and hide it (like make_accessories)."""
    m = _gear_materials()
    for name in [n for n in GEAR if f"ACC_{n}" in bpy.data.objects]:
        holder = bpy.data.objects[f"ACC_{name}"]
        for ob in [holder, *holder.children_recursive]:
            bpy.data.objects.remove(ob, do_unlink=True)
    use_clips(False)
    for tool in TOOLS:
        for s in (-1, 1):
            _tool(tool, s, m)
    _jetpack(m)
    _cape(m)
    _headgear(m)
    for name, (pivot, _slot, _prop, _extra) in GEAR.items():
        e = bpy.data.objects[f"ACC_{name}"]
        _reparent(e, bpy.data.objects[pivot])
        _set_visible(e, False)
    use_clips(True)
    return sorted(GEAR)
