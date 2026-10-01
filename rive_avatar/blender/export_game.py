"""Build the game robot and export it for Godot, in a background Blender (no UI, no MCP timeouts).

    "C:\\Program Files\\Blender Foundation\\Blender 5.2\\blender.exe" -b --factory-startup ^
        --python rive_avatar/blender/export_game.py -- pet_godot/assets [--blend out.blend] [--preview dir]

Writes robot.glb, accessories/*.glb and robot_meta.json into the output folder (relative paths are
taken from the repo root). --blend also saves the built scene; --preview renders review shots.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))

args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
out_dir = os.path.join(REPO, args[0]) if args and not args[0].startswith("--") else os.path.join(REPO, "pet_godot", "assets")


def _opt(flag):
    return os.path.join(REPO, args[args.index(flag) + 1]) if flag in args else None


ns = {}
for f in ("robot_build.py", "robot_game.py", "robot_gear.py"):
    with open(os.path.join(HERE, f), encoding="utf-8") as fh:
        exec(fh.read(), ns)

info = ns["build_game"]()
print("BUILD", info)
blend = _opt("--blend")
if blend:
    import bpy
    bpy.ops.wm.save_as_mainfile(filepath=blend)
    print("SAVED", blend)
preview_dir = _opt("--preview")
if preview_dir:
    os.makedirs(preview_dir, exist_ok=True)
    shots = [("cannon", "Idle_loop", 0, -30, ["cannon_R", "cannon_L"]),
             ("laser_flashlight", "Idle_loop", 0, -30, ["laser_R", "flashlight_L"]),
             ("shield", "Idle_loop", 0, -30, ["shield_R"]),
             ("jetpack", "Fly_loop", 10, 150, ["jetpack"]),
             ("cape", "Idle_loop", 0, 150, ["cape"]),
             ("propeller_cap", "Happy_loop", 6, -25, ["propeller_cap"]),
             ("cat_ears", "Idle_loop", 0, -25, ["cat_ears"]),
             ("halo", "Love_loop", 10, -25, ["halo"]),
             ("devil_horns", "Idle_loop", 0, -25, ["devil_horns"]),
             ("viking_helmet", "Idle_loop", 0, -25, ["viking_helmet"]),
             ("astronaut_helmet", "Idle_loop", 0, -25, ["astronaut_helmet"]),
             ("fedora", "Wave_loop", 8, -25, ["fedora"]),
             ("vr_headset", "Idle_loop", 0, -25, ["vr_headset"])]
    print("PREVIEW", ns["preview"](preview_dir, shots))
for p in ns["export_game"](out_dir):
    print("WROTE", p, os.path.getsize(p))
