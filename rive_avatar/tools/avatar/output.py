# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

import sys
path = sys.argv[1] if len(sys.argv) > 1 else "scene.rml"
open(path, "w", encoding="utf-8", newline="\n").write("\n".join(out) + "\n")
sys.stderr.write("wrote %s (%d lines, %d ids | faces=%d props=%d bg=%d fg=%d acts=%d seqs=%d "
                 "states=%d reacts=%d rest=%d)\n"
                 % (path, len(out), _next[0] - 1000, len(FACES), len(P_NAMES),
                    len(G_NAMES), len(F_NAMES), len(EYE_ACTS), len(SEQS), len(STATES),
                    len(REACTS), len(REST)))

import json, os
meta = {
    "faces": [f[0] for f in FACES],
    "props": list(P_NAMES),
    "bg": list(G_NAMES),
    "fg": list(F_NAMES),
    "eyeActs": [a[0] for a in EYE_ACTS],
    "seqs": [{"name": q[0], "durationMs": round(q[1] / 60 * 1000)} for q in SEQS],
    # A state loops forever; "pinsFace" says whether it owns the expression or
    # just adds movement on top of whatever `face` the host has set.
    "states": [{"name": s[0], "durationMs": round(s[1] / 60 * 1000),
                "pinsFace": bool(s[2])} for s in STATES],
    # one-shot touch / shock reactions; the host clears them after durationMs
    "reacts": [{"name": r[0], "durationMs": round(r[1] / 60 * 1000)} for r in REACTS],
    # the five idle self-play loops, for the host to shuffle between
    "selfPlay": [ST_NAMES.index(n) for n in
                 ("PlayBounce", "PlayChase", "PlaySpin", "PlayPeek", "PlayWiggle")],
    "presets": [{"name": n, "seq": i + 1, "face": F_INDEX[f], "prop": p, "bg": b, "fg": g,
                 "durationMs": round(SEQS[i + 1][1] / 60 * 1000)}
                for i, (n, f, p, b, g) in enumerate(PRESETS)],
}
mp = os.path.join(os.path.dirname(os.path.abspath(path)), "presets.json")
open(mp, "w", encoding="utf-8", newline="\n").write(json.dumps(meta, indent=2, ensure_ascii=False) + "\n")
sys.stderr.write("wrote %s\n" % mp)
