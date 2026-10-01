"""Check that no clip pushes an arm into the head / ears / antennas (background Blender):

    "C:\\Program Files\\Blender Foundation\\Blender 5.2\\blender.exe" -b --factory-startup ^
        --python rive_avatar/blender/check_arms.py

Prints one line per clip: worst penetration depth (<= 0 is clear), frame and arm side.
"""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ns = {}
for f in ("robot_build.py", "robot_game.py"):
    with open(os.path.join(HERE, f), encoding="utf-8") as fh:
        exec(fh.read(), ns)
ns["build"](uv=False)
ns["rig"]()
ns["make_clips"]()
bad = 0
for clip, (depth, frame, side) in ns["check_arms"]().items():
    ok = depth <= 0
    bad += 0 if ok else 1
    print(f"ARMS {clip:16s} {'ok ' if ok else 'HIT'} depth={depth:+.3f} frame={frame} side={side}")
print("ARMS RESULT", "clear" if bad == 0 else f"{bad} clip(s) hit the head")
