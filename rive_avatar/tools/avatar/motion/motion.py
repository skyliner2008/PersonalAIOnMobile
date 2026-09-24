# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# ================================================================= MOTION HELPERS
# Building blocks that make the eyes act instead of sliding like stamped pictures:
#   - irregular trembling (never a clean sine: random steps, random sizes)
#   - the two eyes out of step (one leads, the other follows a few frames later)
#   - the eye outline itself quivering (morph vertices), e.g. a trembling lower lid
# Every helper is deterministic (seeded), so rebuilding gives the same file.
# Keyframe lists may carry their own interpolation: (frame, value, interp).

import random as _rnd


def piecewise(kfs):
    """(frame, value) keys -> f(frame), linear in between, held outside."""
    pts = sorted((k[0], k[1]) for k in kfs)

    def f(fr):
        if fr <= pts[0][0]:
            return pts[0][1]
        for (f0, v0), (f1, v1) in zip(pts, pts[1:]):
            if fr <= f1:
                return v0 + (v1 - v0) * (fr - f0) / float(f1 - f0 or 1)
        return pts[-1][1]
    return f


def envelope(*pts):
    """Amplitude over time, e.g. envelope((100, 0), (140, 1.5), (300, 1.5), (330, 0))."""
    return piecewise(pts)


def tremble(f0, f1, amp, seed, base=None, step=(3, 6), interp="soft"):
    """Irregular shaking between f0 and f1, added on top of base(frame).
    amp: a number or a function of the frame (an envelope)."""
    r = _rnd.Random(seed)
    base = base or (lambda fr: 0.0)
    ampf = amp if callable(amp) else (lambda fr: amp)
    out_ = [(f0, base(f0), interp)]
    fr = f0
    while True:
        fr += r.randint(*step)
        if fr >= f1:
            break
        a = ampf(fr)
        out_.append((fr, base(fr) + r.uniform(-a, a), interp))
    out_.append((f1, base(f1), interp))
    return out_


def lagged(kfs, lag, gain=1.0, mirror=False, around=0.0):
    """The same motion started lag frames later, scaled about `around` (and mirrored)."""
    sgn = -1.0 if mirror else 1.0
    return [(k[0] + lag, around + sgn * (k[1] - around) * gain) + tuple(k[2:]) for k in kfs]


def join(*parts):
    """Concatenate keyframe lists; a later part's keys win on the same frame."""
    by_frame = {}
    for part in parts:
        for k in part:
            by_frame[k[0]] = k
    return [by_frame[f] for f in sorted(by_frame)]


def eye_pair(key, kl, kr):
    """Extras for both eyes with their own keyframes (key 13 x, 14 y, 16/17 scale)."""
    return [("blinkL", key, kl), ("blinkR", key, kr)]


def sniffs(frames, depth=0.84, lag=3, gain_r=0.85):
    """Quick squash-and-recover of the eyes (a sniff / a sob hitch); right eye later."""
    kl, kr = [], []
    for fr in frames:
        for f, v in ((0, 1.0), (4, depth), (9, 1.05), (14, 1.0)):
            kl.append((fr + f, v, "cubic"))
            kr.append((fr + lag + f, 1 + (v - 1) * gain_r, "cubic"))
    return kl, kr


def shape_base(tag, i, key, shape_keys):
    """Keyframes of one morph vertex property following the story's shapes:
    shape_keys [(frame, shape)] -> jelly reshape over MORPH_F frames at each change."""
    out_ = []
    prev = None
    for fr, shape_name in shape_keys:
        val = morph_shape(shape_name, tag)[i][key]
        if prev is None:
            out_.append((fr, val, "hold"))
        elif prev[1] != shape_name:
            out_ += [(fr, prev[0], "jelly"), (fr + MORPH_F, val, "hold")]
        prev = (val, shape_name)
    return out_


def vertex_quiver(tag, verts, shape_keys, windows, seed, key=VY):
    """Extras that quiver the given morph vertices (default: up/down) inside the
    windows [(f0, f1, amp)], while still following the story's shape changes.
    Windows must sit where the shape is steady (not inside a MORPH_F reshape)."""
    extras = []
    for i in verts:
        base_keys = shape_base(tag, i, key, shape_keys)
        base = piecewise(base_keys)
        keys = list(base_keys)
        for w_i, (f0, f1, amp) in enumerate(windows):
            keys = join(keys, tremble(f0, f1, amp, seed * 97 + i * 13 + w_i, base=base, step=(3, 5)))
        extras.append(("vtx%s:%d" % (tag, i), key, keys))
    return extras
