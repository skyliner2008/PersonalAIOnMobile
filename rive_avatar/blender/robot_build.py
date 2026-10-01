"""Pet robot model (v2), built from the model sheet in reference/robot_model_sheet_v2.jpg.

Run inside Blender (through the Blender MCP or Blender's Text editor):

    ns = {}
    exec(open(r"<repo>/rive_avatar/blender/robot_build.py", encoding="utf-8").read(), ns)
    ns["build"]()

It builds into its own scene "PetRobot" and rebuilds from scratch each run, so the scene is
never edited by hand: change the numbers here and run it again.

Modelling: every part is a low-poly all-quad cage (cube grid / cube sphere / cylinder) with a
Subdivision Surface modifier, so shapes stay round and the topology stays clean. The head has a
real recess for the screen (white lip -> grey bezel wall and floor), the screen is its own object
with a 0..1 UV for expression textures, and the vest is a solidified shell over the egg belly.

Objects (all parented to the "PetRobot" empty, grouped for floating animation):
    PR_HeadRoot  -> PR_Head, PR_Screen, PR_Eyes (PR_EyeL/R), PR_Bracket*, PR_EarL/R (pivot, hub, antenna)
    PR_TorsoRoot -> PR_Vest, PR_Belly, PR_CoreRing, PR_Core
    PR_ArmL/R    -> PR_ArmMesh* (one piece, blue + white), PR_HandSocket* (empty at the tip)

Units: 1.0 ~ head width. The robot faces -Y (Blender's front view).
"""
import math

import bmesh
import bpy
from mathutils import Matrix, Vector

SCENE = "PetRobot"

# Palette from the model sheet (sRGB hex).
BODY_WHITE = "#FFFFFF"
LIGHT_BLUE = "#87CEEB"
CORE_LIGHT = "#00FFFF"
SCREEN = "#000000"
BACKDROP = "#E6E8EB"

# ---------------------------------------------------------------- proportions
# Measured on the model sheet with a grid (reference/robot_views_closeup.jpg, 1 cell ~ 0.097 units,
# robot height head top -> egg bottom = 1.88): head 1.15 x 0.90 x 0.91 deep floating just above
# the vest, glass 76% x 63% of the head, round eyes 41% of the glass height, ear discs r 0.16,
# arms float beside the body: one oval piece each, blue top / white bottom (arm_component_sheet.jpg).

HEAD_Z = 1.53
HEAD_HALF = (0.575, 0.44, 0.45)      # half width, depth, height of the cage
HEAD_EDGE = (0.80, 0.55, 0.70)       # support loops: opening size on the front, roll depth on the sides
HEAD_EDGE_BACK = 0.25                # y support loop on the back half (low = long soft roll)
HEAD_BACK_TAPER = 0.88               # back face size relative to the front
HEAD_SQUIRCLE = 3.4                  # front-view outline (2 = ellipse, higher = squarer corners)
HEAD_BACK_BULGE = 0.07               # back of the head swells out (fraction of half depth)
HEAD_PILLOW = 0.03                   # how much the sides / top / bottom swell (fraction of half size)
SCREEN_LIP = 0.012                   # white lip around the opening
SCREEN_DEPTH = -0.012               # bezel height: negative = the grey bezel stands proud of the face
SCREEN_BULGE = 0.075                 # the glass domes out like a CRT (centre in front of the bezel)
SCREEN_BEZEL = 0.016                 # grey bezel floor around the glass

EYE_HALF = (0.105, 0.12)             # review-render eyes (the app draws its own)
EYE_X = 0.176

BELLY_C = (0.0, 0.0, 0.60)
BELLY_R = (0.45, 0.43, 0.50)
BELLY_TAPER = 0.12
VEST_C = (0.0, 0.0, 0.60)
VEST_R = (0.485, 0.47, 0.44)
VEST_PROFILE = 0.72                  # < 1: flat shoulders and near-vertical sides
VEST_HEM = 0.0                       # latitude of the hem, degrees
VEST_TAB_HALF = 11.25                # front tab half width, degrees of longitude
VEST_TAB_ZS = (0.54, 0.47, 0.40, 0.35)
VEST_TAB_OUT = 0.035                 # tab outer surface above the egg
VEST_BACK_HALF = 22.5                 # back shield tab: half width at the hem, degrees
VEST_BACK_ZS = (0.54, 0.46, 0.39, 0.33)
VEST_BACK_NARROW = (0.86, 0.68, 0.5, 0.36)
VEST_THICK = 0.045

EAR_RADIUS = 0.16
ANTENNA_HALF = (0.055, 0.085, 0.19)  # thin from the front, broad from the side, round tip

# arms (reference/arm_component_sheet.jpg): ONE piece per arm in two colours -- an oval body that
# leaves the shoulder going out and down, then bends to hang straight; blue upper part with a
# small lip at the colour seam, white lower part with a round tip. It floats beside the body
# (never touches the vest) and moves on a single pivot at the shoulder.
ARM_PIVOT = (0.6, 0.0, 0.83)         # shoulder pivot (x mirrored): in the blue part, so raising the arm swings its top end down, not into the head
ARM_PATH = ((0.5, 0.93), (0.66, 0.8), (0.86, 0.43), (0.76, 0.15))   # centre line, cubic Bezier (out, z)
ARM_WIDTH = (0.135, 0.2)             # max half width: from the front, from the side
ARM_SEAM = 0.44                      # where blue turns white along the arm (0 top .. 1 tip)
ARM_LIP = 0.06                       # blue lip over the white part at the seam (fraction of width)

def srgb(hex_color, alpha=1.0):
    h = hex_color.lstrip("#")
    c = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    lin = [x / 12.92 if x <= 0.04045 else ((x + 0.055) / 1.055) ** 2.4 for x in c]
    return (*lin, alpha)


# ---------------------------------------------------------------- materials

def material(name, color, rough=0.35, coat=0.0, coat_rough=0.05, emit=0.0, spec=0.5, metal=0.0, alpha=1.0):
    mat = bpy.data.materials.get(name) or bpy.data.materials.new(name)
    mat.use_nodes = True
    nt = mat.node_tree
    nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    bsdf = nt.nodes.new("ShaderNodeBsdfPrincipled")
    nt.links.new(bsdf.outputs["BSDF"], out.inputs["Surface"])
    bsdf.inputs["Base Color"].default_value = srgb(color)
    bsdf.inputs["Roughness"].default_value = rough
    bsdf.inputs["Coat Weight"].default_value = coat
    bsdf.inputs["Coat Roughness"].default_value = coat_rough
    bsdf.inputs["Specular IOR Level"].default_value = spec
    bsdf.inputs["Metallic"].default_value = metal
    if alpha < 1.0:                     # glass etc.: exported as a blended glTF material
        bsdf.inputs["Alpha"].default_value = alpha
        if hasattr(mat, "surface_render_method"):
            mat.surface_render_method = "BLENDED"
    if emit:
        bsdf.inputs["Emission Color"].default_value = srgb(color)
        bsdf.inputs["Emission Strength"].default_value = emit
    mat.diffuse_color = srgb(color)
    return mat


def materials():
    return {
        # satin plastic (user: "less shiny, a bit matte"): soft broad highlights, no mirror coat
        "white": material("PR_BodyWhite", "#ECEFF3", rough=0.46, coat=0.1, coat_rough=0.35, spec=0.4),
        "blue": material("PR_LightBlue", "#6FA6DE", rough=0.55, coat=0.05, coat_rough=0.4, spec=0.35),
        "bezel": material("PR_Bezel", "#9AA1AA", rough=0.5, coat=0.05),
        "screen": material("PR_Screen", "#010203", rough=0.14, coat=0.35, coat_rough=0.1, spec=0.4),
        "core": material("PR_CoreLight", CORE_LIGHT, rough=0.15, emit=5.0),
        "core_ring": material("PR_CoreRing", "#9CCBF2", rough=0.5, coat=0.05),
        "eye": material("PR_Eye", "#7DF7FF", rough=0.2, emit=4.0),
        "bracket": material("PR_Bracket", "#20C8E8", rough=0.3, emit=2.5),
        "joint": material("PR_Joint", "#E4E8EE", rough=0.45, coat=0.1),
        "floor": material("PR_Floor", "#D3D7DC", rough=0.95, spec=0.2),
    }


# ---------------------------------------------------------------- cages

def grid_cube(cuts):
    """A 2x2x2 cube whose faces are (cuts+1)^2 quad grids."""
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=2.0)
    if cuts:
        bmesh.ops.subdivide_edges(bm, edges=bm.edges[:], cuts=cuts, use_grid_fill=True)
    return bm


def blob_bm(half, cuts=4, taper_bottom=0.0, taper_top=0.0, bend=0.0):
    """Cube sphere (all quads) scaled to an ellipsoid; tapers narrow the ends, bend curves it along +X."""
    bm = grid_cube(cuts)
    for v in bm.verts:
        n = v.co.normalized()
        k = 1.0 - taper_bottom * max(0.0, -n.z) ** 1.4 - taper_top * max(0.0, n.z) ** 1.4
        z = n.z * half[2]
        v.co = Vector((n.x * half[0] * k + bend * ((n.z + 1) / 2) ** 2, n.y * half[1] * k, z))
    return bm


def cylinder_bm(radius, depth, segments=32, support=0.012, hub=None):
    """Z-axis disc with support loops on both caps; hub=(radius, height) raises a boss on the +Z cap."""
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, cap_tris=False, segments=segments,
                          radius1=radius, radius2=radius, depth=depth)
    caps = [f for f in bm.faces if len(f.verts) > 4]
    top = max(caps, key=lambda f: f.calc_center_median().z)
    bottom = min(caps, key=lambda f: f.calc_center_median().z)
    for side in (bottom, top):
        bmesh.ops.inset_individual(bm, faces=[side], thickness=support)
    # side walls get a support loop near each cap
    ring_edges = [e for e in bm.edges if abs(e.verts[0].co.z - e.verts[1].co.z) > depth * 0.9]
    bmesh.ops.subdivide_edges(bm, edges=ring_edges, cuts=2, use_grid_fill=True)
    for v in bm.verts:
        if math.hypot(v.co.x, v.co.y) > radius * 0.99 and abs(abs(v.co.z) - depth / 2) > 1e-4:
            v.co.z = math.copysign(depth / 2 - min(depth * 0.18, 0.012), v.co.z)
    if hub:
        hub_r, hub_h = hub
        bmesh.ops.inset_individual(bm, faces=[top], thickness=radius - hub_r - support)
        bmesh.ops.inset_individual(bm, faces=[top], thickness=support * 0.6)
        ret = bmesh.ops.extrude_face_region(bm, geom=[top])
        bmesh.ops.delete(bm, geom=[top], context="FACES_ONLY")
        verts =[e for e in ret["geom"] if isinstance(e, bmesh.types.BMVert)]
        bmesh.ops.translate(bm, verts=verts, vec=(0, 0, hub_h))
        new_top = [f for f in ret["geom"] if isinstance(f, bmesh.types.BMFace) and all(v in verts for v in f.verts)]
        bmesh.ops.inset_individual(bm, faces=new_top, thickness=support * 0.8)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
    return bm


def _bezier(pts, t):
    p0, p1, p2, p3 = (Vector((x, 0.0, z)) for x, z in pts)
    u = 1 - t
    point = u ** 3 * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t ** 3 * p3
    tangent = 3 * u * u * (p1 - p0) + 6 * u * t * (p2 - p1) + 3 * t * t * (p3 - p2)
    return point, tangent.normalized()


def arm_bm(origin):
    """One arm: an oval tube swept along ARM_PATH (right arm, +X), round at both ends.
    Faces above ARM_SEAM use material 0 (blue), below it material 1 (white)."""
    ts = sorted(set([i / 26 for i in range(27)] + [ARM_SEAM - 0.05, ARM_SEAM - 0.012, ARM_SEAM, ARM_SEAM + 0.012]))
    segs = 24
    wf, wd = ARM_WIDTH
    bm = bmesh.new()
    rings, ring_t = [], []
    for t in ts[1:-1]:
        c, tan = _bezier(ARM_PATH, t)
        normal = Vector((tan.z, 0.0, -tan.x))                  # in the front plane, across the arm
        env = math.sin(math.pi * t) ** 0.32 * (1 - 0.18 * t * t)  # blunt round ends, a little thinner to the tip
        if ARM_SEAM - 0.05 <= t <= ARM_SEAM:
            env *= 1 + ARM_LIP                                    # blue lip
        elif abs(t - ARM_SEAM - 0.012) < 1e-6:
            env *= 0.97                                           # tuck under the lip
        rings.append([bm.verts.new(c - origin + normal * (wf * env * math.cos(a)) + Vector((0, wd * env * math.sin(a), 0)))
                      for a in (2 * math.pi * k / segs for k in range(segs))])
        ring_t.append(t)
    top = bm.verts.new(_bezier(ARM_PATH, 0.0)[0] - origin)
    tip = bm.verts.new(_bezier(ARM_PATH, 1.0)[0] - origin)
    for k in range(segs):
        f = bm.faces.new((top, rings[0][(k + 1) % segs], rings[0][k]))
        f.material_index = 0
        f = bm.faces.new((tip, rings[-1][k], rings[-1][(k + 1) % segs]))
        f.material_index = 1
    for (a, ta), (b, tb) in zip(zip(rings, ring_t), zip(rings[1:], ring_t[1:])):
        for k in range(segs):
            f = bm.faces.new((a[k], a[(k + 1) % segs], b[(k + 1) % segs], b[k]))
            f.material_index = 0 if (ta + tb) / 2 < ARM_SEAM else 1
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
    return bm


def _remap(c, e):
    """Grid coordinate -> cage coordinate: the ±0.6 loop moves to ±e (support loop), ends stay at ±1."""
    a = abs(c)
    if a <= 0.6 + 1e-6:
        return c * e / 0.6
    return math.copysign(e + (a - 0.6) / 0.4 * (1 - e), c)


def head_bm():
    """Rounded monitor head: 5x5 quad grid per side, an opening on the front (-Y) framed by a grey bezel."""
    bm = grid_cube(4)
    hx, hy, hz = HEAD_HALF
    ex, ey, ez = HEAD_EDGE
    front_region = []
    for f in bm.faces:
        cs = [v.co for v in f.verts]
        if all(abs(c.y + 1) < 1e-6 for c in cs) and all(abs(c.x) <= 0.6 + 1e-6 and abs(c.z) <= 0.6 + 1e-6 for c in cs):
            front_region.append(f)
    for v in bm.verts:
        x, y, z = v.co
        # the sheet's side view: flat front with tight corners, a big soft roll into the back
        ey_here = ey if y <= 0 else HEAD_EDGE_BACK
        nx, ny, nz = _remap(x, ex), _remap(y, ey_here), _remap(z, ez)
        # even fillets: pull every cage edge / corner vertex in so that, after subdivision,
        # the curve through (e, 1)-(1, 1)-(1, e) lands on a circle of radius (1 - e)
        # (a B-spline passes (e + 4c + 1) / 6 there; a circle passes 1 - (1 - e)(1 - 1/sqrt 2))
        on = [abs(x) > 0.99, abs(y) > 0.99, abs(z) > 0.99]
        if sum(on) >= 2:
            pulled = []
            for flag, value, e in zip(on, (nx, ny, nz), (ex, ey_here, ez)):
                c = (6 * (1 - (1 - e) * (1 - 1 / math.sqrt(2))) - e - 1) / 4
                pulled.append(value * c if flag else value)
            nx, ny, nz = pulled
        if y > 0:                           # the back gets smaller towards the rear (CRT-ish, round)
            w = y * y
            nx *= 1 - (1 - HEAD_BACK_TAPER) * w
            nz *= 1 - (1 - HEAD_BACK_TAPER) * w
        if y > 0.99:                        # and swells a little
            ny += HEAD_BACK_BULGE * (1 - x * x) * (1 - z * z)
        # pillow: sides, top and bottom swell a little (the front stays flat for the screen)
        if abs(x) > 0.99:
            nx += math.copysign(HEAD_PILLOW * (1 - y * y) * (1 - z * z), x)
        if abs(z) > 0.99:
            nz += math.copysign(HEAD_PILLOW * (1 - x * x) * (1 - y * y), z)
        # front-view outline: square -> squircle, so the monitor has big soft corners
        m = max(abs(nx), abs(nz))
        if m > 0:
            dx, dz = abs(nx) / m, abs(nz) / m
            k = (dx ** HEAD_SQUIRCLE + dz ** HEAD_SQUIRCLE) ** (-1 / HEAD_SQUIRCLE)
            nx *= k
            nz *= k
        v.co = Vector((nx * hx, ny * hy, nz * hz))

    bmesh.ops.inset_region(bm, faces=front_region, thickness=SCREEN_LIP, depth=0, use_even_offset=True)
    ret = bmesh.ops.extrude_face_region(bm, geom=front_region)
    bmesh.ops.delete(bm, geom=front_region, context="FACES_ONLY")     # extrude keeps the originals
    moved = [e for e in ret["geom"] if isinstance(e, bmesh.types.BMVert)]
    bmesh.ops.translate(bm, verts=moved, vec=(0, SCREEN_DEPTH, 0))
    region = [f for f in ret["geom"] if isinstance(f, bmesh.types.BMFace) and all(v in moved for v in f.verts)]
    walls = [f for f in ret["geom"] if isinstance(f, bmesh.types.BMFace) and f not in region]
    floor_ret = bmesh.ops.inset_region(bm, faces=region, thickness=SCREEN_BEZEL, depth=0, use_even_offset=True)
    support_ret = bmesh.ops.inset_region(bm, faces=region, thickness=0.006, depth=0, use_even_offset=True)
    for f in walls + floor_ret["faces"] + support_ret["faces"]:
        f.material_index = 1
    opening = [v.co.copy() for f in region for v in f.verts]
    bmesh.ops.delete(bm, geom=region, context="FACES_ONLY")
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
    xs = [c.x for c in opening]
    zs = [c.z for c in opening]
    return bm, (max(xs), max(zs)), -hy + SCREEN_DEPTH


def glass_bulge(u, v):
    """How far the domed glass stands out at (u, v), each -1..1 across / up the screen (0 at the rim)."""
    return SCREEN_BULGE * max(0.0, 1 - u * u) ** 0.7 * max(0.0, 1 - v * v) ** 0.7


def screen_bm(half_w, half_h, nu=24, nv=18, squircle=5.0):
    """Rounded-rectangle glass, domed outwards (convex, like a CRT), all quads,
    UV = 0..1 over the whole screen (expression texture)."""
    bm = bmesh.new()
    uv_layer = bm.loops.layers.uv.new("UVMap")
    grid = []
    for j in range(nv + 1):
        row = []
        for i in range(nu + 1):
            u = i / nu * 2 - 1
            v = j / nv * 2 - 1
            r = max(abs(u), abs(v))
            if r > 0:
                dx, dy = u / r, v / r
                s = 1.0 / (abs(dx) ** squircle + abs(dy) ** squircle) ** (1 / squircle)
            else:
                s = 1.0
            row.append(bm.verts.new((u * s * half_w, -glass_bulge(u, v), v * s * half_h)))
        grid.append(row)
    for j in range(nv):
        for i in range(nu):
            f = bm.faces.new((grid[j][i], grid[j][i + 1], grid[j + 1][i + 1], grid[j + 1][i]))
            for loop in f.loops:
                co = loop.vert.co
                loop[uv_layer].uv = (co.x / (2 * half_w) + 0.5, co.z / (2 * half_h) + 0.5)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
    for f in bm.faces:                      # glass faces the camera (-Y)
        if f.normal.y > 0:
            f.normal_flip()
    return bm


def _vest_point(lon_deg, lat_deg):
    """Vest outer surface: round in plan, boxy in profile (flat shoulders, near-vertical sides)."""
    lo, la = math.radians(lon_deg), math.radians(lat_deg)
    cx, cy, cz = VEST_C
    rx, ry, rz = VEST_R
    cl = abs(math.cos(la)) ** VEST_PROFILE
    sl = math.copysign(abs(math.sin(la)) ** VEST_PROFILE, la)
    return Vector((cx + rx * cl * math.cos(lo), cy + ry * cl * math.sin(lo), cz + rz * sl))


def _belly_point(lon_deg, z, offset):
    """Point on the egg belly at height z, pushed out by offset (the tab hugs the egg)."""
    lo = math.radians(lon_deg)
    ex, ey, ez = BELLY_C
    rx, ry, rz = BELLY_R
    t = (z - ez) / rz
    k = math.sqrt(max(0.0, 1 - t * t)) * (1 - BELLY_TAPER * max(0.0, -t) ** 1.4)
    return Vector((ex + (rx * k + offset) * math.cos(lo), ey + (ry * k + offset) * math.sin(lo), z))


def vest_bm():
    """Blue shell over the belly: full ring down to the hem plus a front tab, capped on top."""
    lon_n = 64
    main_lats = [VEST_HEM, 10.0, 22.0, 34.0, 46.0, 58.0, 70.0, 82.0]
    bm = bmesh.new()
    lons = [i * 360.0 / lon_n - 180.0 for i in range(lon_n)]
    rings = [[bm.verts.new(_vest_point(lo, la)) for lo in lons] for la in main_lats]
    for a, b in zip(rings, rings[1:]):
        for i in range(lon_n):
            bm.faces.new((a[i], a[(i + 1) % lon_n], b[(i + 1) % lon_n], b[i]))
    top = bm.verts.new(_vest_point(0, 90))
    for i in range(lon_n):
        bm.faces.new((rings[-1][i], rings[-1][(i + 1) % lon_n], top))

    def tab(center, half_width, zs, narrow):
        """A tab hanging from the hem, rows following the egg; narrow[k] squeezes row k towards
        the centre line (1 = full width); the bottom corners are lifted to round them."""
        cols = [i for i, lo in enumerate(lons) if abs((lo - center + 180) % 360 - 180) <= half_width + 1e-6]
        cols.sort(key=lambda i: (lons[i] - center + 180) % 360 - 180)
        prev = {i: rings[0][i] for i in cols}
        for step, (z, k) in enumerate(zip(zs, narrow)):
            row = {}
            for n, i in enumerate(cols):
                off = (lons[i] - center + 180) % 360 - 180
                zz = z + (0.035 if step == len(zs) - 1 and n in (0, len(cols) - 1) else 0.0)
                row[i] = bm.verts.new(_belly_point(center + off * k, zz, VEST_TAB_OUT))
            for i, j in zip(cols, cols[1:]):
                bm.faces.new((row[i], row[j], prev[j], prev[i]))
            prev = row

    tab(-90.0, VEST_TAB_HALF, VEST_TAB_ZS, [1.0] * len(VEST_TAB_ZS))       # front (-Y): straight T
    tab(90.0, VEST_BACK_HALF, VEST_BACK_ZS, VEST_BACK_NARROW)             # back: shield, tapering down
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
    for f in bm.faces:                  # outward normals
        if f.normal.dot(f.calc_center_median() - Vector(VEST_C)) < 0:
            f.normal_flip()
    return bm


def vest_surface(lat_deg, lon_deg=-90.0):
    """Point and outward normal on the vest at (lat, lon), for placing the core."""
    eps = 0.5
    c = _vest_point(lon_deg, lat_deg)
    du = _vest_point(lon_deg + eps, lat_deg) - _vest_point(lon_deg - eps, lat_deg)
    dv = _vest_point(lon_deg, lat_deg + eps) - _vest_point(lon_deg, lat_deg - eps)
    n = du.cross(dv).normalized()
    if n.dot(c - Vector(VEST_C)) < 0:
        n = -n
    return c, n


# ---------------------------------------------------------------- objects

def add(name, bm, mat, parent, loc=(0, 0, 0), rot=(0, 0, 0), subsurf=2, mats=()):
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    for p in me.polygons:
        p.use_smooth = True
    ob = bpy.data.objects.new(name, me)
    ob.location = loc
    ob.rotation_euler = [math.radians(r) for r in rot]
    me.materials.append(mat)
    for extra in mats:
        me.materials.append(extra)
    ob.parent = parent
    parent.users_collection[0].objects.link(ob)
    if subsurf:
        mod = ob.modifiers.new("Subdivision", "SUBSURF")
        mod.levels = subsurf
        mod.render_levels = subsurf + 1
        mod.quality = 3
    return ob


def empty(name, parent, coll, loc=(0, 0, 0)):
    ob = bpy.data.objects.new(name, None)
    ob.empty_display_size = 0.2
    ob.location = loc
    ob.parent = parent
    coll.objects.link(ob)
    return ob


def _fresh_scene():
    scn = bpy.data.scenes.get(SCENE)
    if scn is None:
        scn = bpy.data.scenes.new(SCENE)
    for ob in list(scn.collection.all_objects):
        bpy.data.objects.remove(ob, do_unlink=True)
    for coll in list(scn.collection.children):
        bpy.data.collections.remove(coll)
    for me in [m for m in bpy.data.meshes if m.users == 0]:
        bpy.data.meshes.remove(me)
    for wm_win in bpy.context.window_manager.windows:
        wm_win.scene = scn
    return scn


def _aim(ob, target):
    d = Vector(target) - ob.location
    ob.rotation_euler = d.to_track_quat("-Z", "Y").to_euler()


def _stage(scn, mats):
    coll = bpy.data.collections.new("PR_Stage")
    scn.collection.children.link(coll)

    # Cyclorama: floor that curves up into a wall behind, so there is no horizon line.
    profile = [(-30.0, 0.0), (2.0, 0.0)]
    radius = 6.0
    for i in range(1, 24):
        a = math.radians(90 * i / 24)
        profile.append((2.0 + radius * math.sin(a), radius * (1 - math.cos(a))))
    profile += [(2.0 + radius, radius), (2.0 + radius, 30.0)]
    bm = bmesh.new()
    rows = [[bm.verts.new((x, y, z)) for (y, z) in profile] for x in (-30.0, 30.0)]
    for i in range(len(profile) - 1):
        bm.faces.new((rows[0][i], rows[1][i], rows[1][i + 1], rows[0][i + 1]))
    floor = bpy.data.objects.new("PR_Floor", bpy.data.meshes.new("PR_Floor"))
    bm.to_mesh(floor.data)
    bm.free()
    for p in floor.data.polygons:
        p.use_smooth = True
    floor.data.materials.append(mats["floor"])
    floor.location = (0, 0, -0.30)
    coll.objects.link(floor)

    def light(name, loc, energy, size, color=(1, 1, 1), target=(0, 0, 1.1)):
        ld = bpy.data.lights.new(name, "AREA")
        ld.energy = energy
        ld.color = color
        ld.size = size
        ob = bpy.data.objects.new(name, ld)
        ob.location = loc
        coll.objects.link(ob)
        _aim(ob, target)
        return ob

    light("PR_Key", (-3.6, -5.0, 4.4), 950, 2.5)
    light("PR_Fill", (4.6, -4.2, 1.6), 140, 5.0, color=(0.93, 0.96, 1.0))
    light("PR_RimL", (-3.4, 3.6, 3.4), 520, 2.0)
    light("PR_RimR", (3.6, 3.4, 3.0), 460, 2.0, color=(0.92, 0.96, 1.0))
    light("PR_Top", (0, 0.6, 5.0), 260, 3.0)

    cam = bpy.data.objects.new("PR_Camera", bpy.data.cameras.new("PR_Camera"))
    cam.data.lens = 85
    cam.location = (0, -8.8, 1.9)
    coll.objects.link(cam)
    _aim(cam, (0, 0, 1.02))
    scn.camera = cam

    world = bpy.data.worlds.get("PR_World") or bpy.data.worlds.new("PR_World")
    world.use_nodes = True
    bg = world.node_tree.nodes.get("Background")
    bg.inputs["Color"].default_value = srgb(BACKDROP)
    bg.inputs["Strength"].default_value = 0.22
    scn.world = world

    r = scn.render
    r.engine = "BLENDER_EEVEE"
    r.resolution_x = 1024
    r.resolution_y = 1024
    r.film_transparent = False
    vs = scn.view_settings
    vs.view_transform = "AgX"
    for look in ("AgX - Medium High Contrast", "Medium High Contrast"):
        try:
            vs.look = look
            break
        except TypeError:
            continue
    vs.exposure = -0.4
    try:
        scn.eevee.taa_render_samples = 96
        scn.eevee.use_shadows = True
        scn.eevee.use_raytracing = True
        scn.eevee.use_fast_gi = True
    except AttributeError:
        pass
    _bloom(scn)


def _bloom(scn):
    """Soft glow on the eyes and chest core (compositor Glare, Blender 5.x API)."""
    tree = bpy.data.node_groups.get("PR_Compositor")
    if tree is not None:
        bpy.data.node_groups.remove(tree)
    tree = bpy.data.node_groups.new("PR_Compositor", "CompositorNodeTree")
    tree.interface.new_socket(name="Image", in_out="OUTPUT", socket_type="NodeSocketColor")
    rl = tree.nodes.new("CompositorNodeRLayers")
    rl.scene = scn
    glare = tree.nodes.new("CompositorNodeGlare")
    glare.inputs["Type"].default_value = "Bloom"
    glare.inputs["Quality"].default_value = "High"
    glare.inputs["Threshold"].default_value = 3.0
    glare.inputs["Strength"].default_value = 0.35
    glare.inputs["Size"].default_value = 0.35
    out = tree.nodes.new("NodeGroupOutput")
    tree.links.new(rl.outputs["Image"], glare.inputs["Image"])
    tree.links.new(glare.outputs["Image"], out.inputs["Image"])
    scn.compositing_node_group = tree


def _robot(scn, mats):
    coll = bpy.data.collections.new("PR_Robot")
    scn.collection.children.link(coll)
    root = bpy.data.objects.new("PetRobot", None)
    root.empty_display_type = "PLAIN_AXES"
    coll.objects.link(root)

    # ---- head
    head = empty("PR_HeadRoot", root, coll, loc=(0, 0, HEAD_Z))
    hbm, (open_x, open_z), floor_y = head_bm()
    add("PR_Head", hbm, mats["white"], head, subsurf=2, mats=(mats["bezel"],))
    glass_y = floor_y + 0.004
    add("PR_Screen", screen_bm(open_x + 0.012, open_z + 0.012), mats["screen"], head,
        loc=(0, glass_y, 0), subsurf=0)
    anchor = empty("PR_ScreenAnchor", head, coll, loc=(0, glass_y, 0))
    anchor["half_size"] = (open_x, open_z)

    hw, hh = open_x + 0.012, open_z + 0.012
    eyes = empty("PR_Eyes", head, coll, loc=(0, glass_y, 0.01))
    for side, x in (("L", -EYE_X), ("R", EYE_X)):           # sit on the dome
        add(f"PR_Eye{side}", blob_bm((EYE_HALF[0], 0.012, EYE_HALF[1]), cuts=3), mats["eye"], eyes,
            loc=(x, -glass_bulge(x / hw, 0.01 / hh) - 0.006, 0))

    # glowing UI corners on the glass
    arm_len, thick = 0.06, 0.008
    bx, bz = open_x - 0.045, open_z - 0.045
    for cxs in (-1, 1):
        for czs in (-1, 1):
            for horizontal in (True, False):
                half = (arm_len / 2, 0.004, thick) if horizontal else (thick, 0.004, arm_len / 2)
                off = (cxs * (bx - arm_len / 2 + thick), czs * bz) if horizontal else (cxs * bx, czs * (bz - arm_len / 2 + thick))
                add("PR_Bracket", blob_bm(half, cuts=1), mats["bracket"], head,
                    loc=(off[0], glass_y - glass_bulge(off[0] / hw, off[1] / hh) - 0.004, off[1]), subsurf=1)

    # ears: large pivot disc with a raised hub, antenna rising from the disc top and leaning out
    hx = HEAD_HALF[0]
    for s, side in ((-1, "L"), (1, "R")):
        ear = empty(f"PR_Ear{side}", head, coll, loc=(s * (hx + 0.02), 0.0, -0.02))
        add(f"PR_EarPivot{side}", cylinder_bm(EAR_RADIUS, 0.12, hub=(0.1, 0.05)), mats["blue"], ear,
            loc=(s * 0.05, 0, 0), rot=(0, s * 90, 0))
        # rotating about +Y by +t leans the top towards +X, so s * angle leans each antenna outwards
        antenna = blob_bm(ANTENNA_HALF, cuts=3, taper_top=0.3, taper_bottom=0.2, bend=s * 0.04)
        add(f"PR_Antenna{side}", antenna, mats["blue"], ear,
            loc=(s * 0.06, 0.0, EAR_RADIUS + ANTENNA_HALF[2] - 0.02), rot=(0, s * 16, 0))

    # ---- torso
    torso = empty("PR_TorsoRoot", root, coll)
    vest = add("PR_Vest", vest_bm(), mats["blue"], torso, subsurf=0)
    solid = vest.modifiers.new("Shell", "SOLIDIFY")     # thickness first, then subdivide
    solid.thickness = VEST_THICK
    solid.offset = -1.0
    solid.use_even_offset = True
    solid.use_quality_normals = True
    sub = vest.modifiers.new("Subdivision", "SUBSURF")
    sub.levels = 2
    sub.render_levels = 3
    add("PR_Belly", blob_bm(BELLY_R, cuts=5, taper_bottom=BELLY_TAPER), mats["white"], torso, loc=BELLY_C, subsurf=2)

    core_pos, core_n = vest_surface(21.0)
    rot = Vector((0, 0, 1)).rotation_difference(core_n).to_euler()
    ring = add("PR_CoreRing", cylinder_bm(0.125, 0.05, hub=(0.104, 0.004)), mats["core_ring"], torso,
               loc=core_pos + core_n * 0.004, subsurf=2)
    ring.rotation_euler = rot
    core = add("PR_Core", blob_bm((0.102, 0.102, 0.032), cuts=3), mats["core"], torso,
               loc=core_pos + core_n * 0.014, subsurf=2)
    core.rotation_euler = rot

    # three screws on the back shield (two at the top corners, one near the tip)
    for i, (lon, z) in enumerate(((90 - 15.0, 0.55), (90 + 15.0, 0.55), (90.0, 0.37))):
        p = _belly_point(lon, z, VEST_TAB_OUT + 0.004)
        n = Vector((math.cos(math.radians(lon)), math.sin(math.radians(lon)), 0.25)).normalized()
        screw = add(f"PR_Screw{i}", blob_bm((0.022, 0.022, 0.009), cuts=2), mats["bezel"], torso, loc=p, subsurf=1)
        screw.rotation_euler = Vector((0, 0, 1)).rotation_difference(n).to_euler()

    # ---- arms: one two-colour piece each on a single shoulder pivot (see ARM_* above). The hand
    # socket is an empty at the tip for held items later.
    for s, side in ((-1, "L"), (1, "R")):
        arm = empty(f"PR_Arm{side}", root, coll, loc=(s * ARM_PIVOT[0], ARM_PIVOT[1], ARM_PIVOT[2]))
        origin = Vector((ARM_PIVOT[0], 0.0, ARM_PIVOT[2]))
        bm = arm_bm(origin)
        if s < 0:                                   # mirror the right arm to make the left one
            for v in bm.verts:
                v.co.x = -v.co.x
            bmesh.ops.reverse_faces(bm, faces=bm.faces[:])
        add(f"PR_ArmMesh{side}", bm, mats["blue"], arm, mats=(mats["white"],))
        tip = _bezier(ARM_PATH, 0.97)[0] - origin
        empty(f"PR_HandSocket{side}", arm, coll, loc=(s * tip.x, 0.0, tip.z))

    return root


# ---------------------------------------------------------------- UVs

def unwrap(scn):
    """Smart-project every mesh except the screen, which keeps its own 0..1 expression UV."""
    done, failed = [], []
    vl = scn.view_layers[0]
    wins = bpy.context.window_manager.windows          # empty when run with blender -b
    meshes =[o for o in scn.collection.all_objects if o.type == "MESH" and o.name.startswith("PR_")]
    for ob in meshes:
        if ob.name in ("PR_Screen", "PR_Floor"):
            continue
        for other in meshes:
            other.select_set(False, view_layer=vl)
        ob.select_set(True, view_layer=vl)
        vl.objects.active = ob
        try:
            ctx = dict(scene=scn, view_layer=vl, active_object=ob, object=ob,
                       selected_objects=[ob], selected_editable_objects=[ob])
            if wins:
                ctx["window"] = wins[0]
            with bpy.context.temp_override(**ctx):
                bpy.ops.object.mode_set(mode="EDIT")
                bpy.ops.mesh.select_all(action="SELECT")
                bpy.ops.uv.smart_project(angle_limit=math.radians(60), island_margin=0.02)
                bpy.ops.object.mode_set(mode="OBJECT")
            done.append(ob.name)
        except Exception as ex:          # noqa: BLE001 - report and keep building
            failed.append(f"{ob.name}: {ex}")
            try:
                bpy.ops.object.mode_set(mode="OBJECT")
            except Exception:           # noqa: BLE001
                pass
    return {"unwrapped": len(done), "failed": failed}


def build(uv=True):
    mats = materials()
    scn = _fresh_scene()
    _stage(scn, mats)
    root = _robot(scn, mats)
    set_pose("idle")
    info = {"scene": scn.name, "objects": len(scn.collection.all_objects), "root": root.name}
    if uv:
        info["uv"] = unwrap(scn)
    return info


# Arm poses: (outward swing, forward swing) in degrees per arm; the hand hangs straight
# down at (0, 0). Outward lifts the hand away from the body, forward (negative) brings
# it in front of the chest. "L"/"R" are the viewer's left/right in the front view
# (PR_ArmL sits at -X), matching the model sheet: WINK waves the viewer's-left arm.
POSES = {
    "idle":      {"L": (0, 0),      "R": (0, 0)},
    "happy":     {"L": (110, -12),  "R": (110, -12)},
    "sad":       {"L": (-6, -12),   "R": (-6, -12)},
    "wave_a":    {"L": (130, -12),  "R": (0, 0)},
    "wave_b":    {"L": (100, -12),  "R": (0, 0)},
    "hug":       {"L": (-34, -60),  "R": (-34, -60)},
    "surprised": {"L": (45, -6),    "R": (45, -6)},
}


def set_pose(name):
    for side, s in (("L", -1), ("R", 1)):
        outward, forward = POSES[name][side]
        # rotating about +Y by t moves the hanging hand to x = -sin(t): outward is -s * angle
        bpy.data.objects[f"PR_Arm{side}"].rotation_euler = (
            math.radians(forward), math.radians(-s * outward), 0)


def set_view(name):
    """Turn the robot for one of the model-sheet views."""
    angles = {"front": 0, "side": 90, "three_quarter": -32, "back": 180}
    bpy.data.objects["PetRobot"].rotation_euler = (0, 0, math.radians(angles[name]))
    return angles[name]


def render_views(out_dir, views=("front", "side", "three_quarter", "back")):
    scn = bpy.data.scenes[SCENE]
    paths = []
    for v in views:
        set_view(v)
        scn.render.filepath = f"{out_dir}/robot_{v}.png"
        bpy.ops.render.render(write_still=True, scene=scn.name)
        paths.append(scn.render.filepath)
    set_view("front")
    return paths


# ---------------------------------------------------------------- app sprites

SPRITE_SIZE = 640
SPRITE_YAWS = (-30, -20, -10, 0, 10, 20, 30)     # robot turns towards the viewer's right for +yaw
SPRITE_CAMERA = ((0, -6.4, 1.72), (0, 0, 1.14))


def _screen_on_image(scn, cam):
    """Screen centre and size as fractions of the frame (y down), from the glass plane."""
    from bpy_extras.object_utils import world_to_camera_view
    bpy.context.view_layer.update()
    anchor = bpy.data.objects["PR_ScreenAnchor"]
    sx, sz = anchor["half_size"]

    def uv(local):
        p = world_to_camera_view(scn, cam, anchor.matrix_world @ Vector(local))
        return p.x, 1.0 - p.y

    cx, cy = uv((0, 0, 0))
    left, _ = uv((-sx, 0, 0))
    right, _ = uv((sx, 0, 0))
    _, top = uv((0, 0, sz))
    _, bottom = uv((0, 0, -sz))
    return cx, cy, abs(right - left), abs(bottom - top)


def render_sprites(out_dir, kotlin_path, poses=None, yaws=SPRITE_YAWS):
    """Transparent body frames (no eyes: the app draws live eyes into the screen) + a Kotlin table."""
    import os
    scn = bpy.data.scenes[SCENE]
    poses = list(poses or POSES)
    os.makedirs(out_dir, exist_ok=True)

    cam = scn.camera
    saved = (tuple(cam.location), tuple(cam.rotation_euler))
    cam.location = SPRITE_CAMERA[0]
    _aim(cam, SPRITE_CAMERA[1])
    hidden = [bpy.data.objects["PR_Floor"], *bpy.data.objects["PR_Eyes"].children]
    for ob in hidden:
        ob.hide_render = True
    r = scn.render
    r.film_transparent = True
    r.resolution_x = r.resolution_y = SPRITE_SIZE
    r.image_settings.file_format = "WEBP"
    r.image_settings.color_mode = "RGBA"
    r.image_settings.quality = 88

    root = bpy.data.objects["PetRobot"]
    screens = []
    written = []
    try:
        for yaw in yaws:
            root.rotation_euler = (0, 0, math.radians(yaw))
            screens.append(_screen_on_image(scn, cam))
            for pose in poses:
                set_pose(pose)
                r.filepath = os.path.join(out_dir, f"{pose}_{_yaw_tag(yaw)}.webp")
                bpy.ops.render.render(write_still=True, scene=scn.name)
                written.append(r.filepath)
    finally:
        for ob in hidden:
            ob.hide_render = False
        r.film_transparent = False
        r.resolution_x = r.resolution_y = 1024
        r.image_settings.file_format = "PNG"
        cam.location, cam.rotation_euler = saved
        root.rotation_euler = (0, 0, 0)
        set_pose("idle")

    front = screens[list(yaws).index(0)]
    _write_kotlin(kotlin_path, poses, yaws, screens, front)
    return {"frames": len(written), "kotlin": kotlin_path, "screen_front": front}


def _yaw_tag(yaw):
    return f"m{-yaw:02d}" if yaw < 0 else f"p{yaw:02d}"


def _write_kotlin(path, poses, yaws, screens, front):
    def floats(values):
        return ", ".join(f"{v:.4f}f" for v in values)

    # rounded corner of the glass relative to its height (squircle plate)
    corner = 0.16
    lines = [
        "package com.skyliner2008.jarvis.ui.component.avatar",
        "",
        "// GENERATED by rive_avatar/blender/robot_build.py (render_sprites) -- do not edit by hand.",
        "// Re-run the Blender script to change the robot; frames live in composeResources/files/pet3d/.",
        "",
        "/** Pre-rendered frames of the 3D pet robot (body only; the app draws live eyes into its screen). */",
        "internal object PetRobot3dFrames {",
        f"    const val FRAME_SIZE_PX = {SPRITE_SIZE}",
        "",
        "    /** Robot turn per frame, degrees; + turns towards the viewer's right. */",
        f"    val YAWS = intArrayOf({', '.join(str(y) for y in yaws)})",
        "",
        "    /** Arm poses rendered for every yaw. */",
        f"    val POSES = listOf({', '.join(chr(34) + p + chr(34) for p in poses)})",
        "",
        "    /** Screen centre per yaw, as a fraction of the frame (y down). */",
        f"    val SCREEN_CX = floatArrayOf({floats(s[0] for s in screens)})",
        f"    val SCREEN_CY = floatArrayOf({floats(s[1] for s in screens)})",
        "",
        "    /** Screen size facing the camera (yaw 0), as a fraction of the frame. */",
        f"    const val SCREEN_W = {front[2]:.4f}f",
        f"    const val SCREEN_H = {front[3]:.4f}f",
        f"    const val SCREEN_CORNER = {corner:.4f}f",
        "",
        "    fun path(pose: String, yaw: Int): String =",
        "        \"files/pet3d/${pose}_${if (yaw < 0) \"m\" else \"p\"}${kotlin.math.abs(yaw).toString().padStart(2, '0')}.webp\"",
        "}",
        "",
    ]
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(lines))