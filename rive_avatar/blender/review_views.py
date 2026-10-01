"""Render the four model-sheet views of the robot in a background Blender, for compare_sheet.ps1.

    "C:\\Program Files\\Blender Foundation\\Blender 5.2\\blender.exe" -b --factory-startup ^
        --python rive_avatar/blender/review_views.py

Writes rive_avatar/blender/renders/robot_{front,side,three_quarter,back}.png (idle pose, review eyes).
"""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ns = {}
with open(os.path.join(HERE, "robot_build.py"), encoding="utf-8") as fh:
    exec(fh.read(), ns)
print("BUILD", ns["build"](uv=False))
for p in ns["render_views"](os.path.join(HERE, "renders")):
    print("WROTE", p)
