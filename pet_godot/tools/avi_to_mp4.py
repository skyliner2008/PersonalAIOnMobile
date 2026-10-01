"""Turn the AVI clips Godot's movie writer records into small H.264 MP4s (Blender's FFmpeg).

blender -b --factory-startup --python pet_godot/tools/avi_to_mp4.py -- <clip.avi> [<clip.avi> ...] [--reel <all.mp4>]

Every clip becomes <clip>.mp4 next to it; --reel also joins them all, in order, into one video.
"""
import os
import sys

import bpy


def _args():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    reel = None
    if "--reel" in argv:
        i = argv.index("--reel")
        reel = argv[i + 1]
        argv = argv[:i] + argv[i + 2:]
    return [os.path.abspath(a) for a in argv], reel and os.path.abspath(reel)


def _render(clips, out):
    scene = bpy.data.scenes.new(os.path.basename(out))
    sed = scene.sequence_editor_create()
    strips = sed.strips if hasattr(sed, "strips") else sed.sequences
    frame = 1
    for i, path in enumerate(clips):
        s = strips.new_movie(f"clip{i}", path, channel=1, frame_start=frame)
        frame += s.duration if hasattr(s, "duration") else s.frame_final_duration   # 5.x name / older Blender
        if i == 0:
            w, h = s.elements[0].orig_width, s.elements[0].orig_height
            scene.render.fps = round(s.fps) if s.fps else 30
    scene.frame_start = 1
    scene.frame_end = frame - 1
    r = scene.render
    r.fps_base = 1.0
    r.resolution_x, r.resolution_y, r.resolution_percentage = w, h, 100
    scene.view_settings.view_transform = "Standard"     # pass the clip's colours through unchanged
    scene.view_settings.look = "None"
    if hasattr(r.image_settings, "media_type"):
        r.image_settings.media_type = "VIDEO"
    r.image_settings.file_format = "FFMPEG"
    r.ffmpeg.format = "MPEG4"
    r.ffmpeg.codec = "H264"
    r.ffmpeg.constant_rate_factor = "HIGH"
    r.ffmpeg.ffmpeg_preset = "GOOD"
    r.ffmpeg.audio_codec = "NONE"
    r.filepath = out
    r.use_file_extension = False
    bpy.ops.render.render(animation=True, scene=scene.name)
    print("MP4", out, f"{w}x{h}", scene.frame_end, "frames")


def main():
    clips, reel = _args()
    for path in clips:
        _render([path], os.path.splitext(path)[0] + ".mp4")
    if reel and clips:
        _render(clips, reel)


main()
