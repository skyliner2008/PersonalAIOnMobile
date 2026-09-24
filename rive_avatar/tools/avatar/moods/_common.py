# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# ================================================================= MOOD STORIES
# The jelly-eye mood storyboards (.obsidian-wiki/02_Components/Pet_Mood_Scripts.md):
# 7 s each at 60 fps. Only blinkL / blinkR key the eyes here (never "blink"),
# so each eye can squash, stretch and wander on its own.
MD = 420


def _mp(scene, mover):
    return "prop:%d:%s" % (P_NAMES.index(scene), mover)


def _eyes(key, kl, kr=None):
    """Keyframes for both eyes; kr defaults to kl (mirror x / rotation by hand)."""
    return [("blinkL", key, kl), ("blinkR", key, kl if kr is None else kr)]


def _neg(kfs):
    return [(f, -v) for f, v in kfs]


def _circle(f0, f1, period, r, step=10):
    xs, ys = [], []
    f = f0
    while f <= f1:
        a = 2 * math.pi * (f - f0) / period
        xs.append((f, round(r * math.sin(a), 2)))
        ys.append((f, round(-r * math.cos(a), 2)))
        f += step
    return xs, ys


def _pulse_op(frames, on=3, level=1.0):
    out_ = [(0, 0)]
    for f in frames:
        out_ += [(f, level), (f + on, 0)]
    return out_


def _sc(prop, mover, kfs, interp=None):
    t = [(_mp(prop, mover), 16, kfs), (_mp(prop, mover), 17, kfs)]
    return [e + (interp,) for e in t] if interp else t


MOOD_STORIES = {}
