# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

def _check_mood_frames():
    bad = []
    for sn, (dur, beats, extra) in MOOD_STORIES.items():
        fr = [b[0] for b in beats]
        if fr != sorted(set(fr)):
            bad.append("%s beats" % sn)
        seen = set()
        for ex in extra:
            fs = [k[0] for k in ex[2]]
            if fs != sorted(set(fs)) or fs[-1] > dur:
                bad.append("%s %s/%s %s" % (sn, ex[0], ex[1], fs))
            k = (ex[0], ex[1])
            if k in seen:
                bad.append("%s duplicate track %s" % (sn, k))
            seen.add(k)
    if bad:
        raise SystemExit("mood story frames out of order:\n  " + "\n  ".join(bad))


_check_mood_frames()
HAND_STORIES.update(MOOD_STORIES)

# presets.json order (appended after the status set; seq = index + 1)
SCENE_PRESETS = [
    ("SceneEating", "Happy", FOOD), ("SceneDrinking", "Calm", DRINK), ("SceneBath", "Happy", BATH),
    ("SceneGaming", "Starry", GAME), ("SceneStudy", "Focused", STUDY), ("SceneRain", "Happy", RAIN),
    ("SceneThug", "Smug", THUG), ("SceneRich", "Money", RICH), ("SceneRoyal", "Smug", ROYAL),
    ("SceneFire", "Dizzy", FIRE), ("SceneThunder", "Dizzy", THUNDER), ("SceneSoulOut", "Dizzy", SOUL),
    ("SceneSuperLove", "Love", LOVE), ("SceneCry", "Sad", CRY), ("SceneCelebrate", "Happy", PARTY),
    ("SceneVR", "Blank", VR), ("SceneMusic", "Happy", MUSIC),
]
PRESETS.extend((n, f, p, G_NONE, F_NONE) for n, f, p in SCENE_PRESETS)

# jelly mood stories (seq 82+), named after their story; face = where each one lands
MOOD_PRESETS = [
    ("MoodIdleBall", "Sleepy", P_ZZZ), ("MoodYoYo", "Happy", MOOD_YOYO), ("MoodStars", "Bored", MOOD_STARS),
    ("MoodShocked", "Dead", MOOD_SHOCK), ("MoodSad", "Weepy", MOOD_SAD), ("MoodCurious", "Starry", MOOD_CURIOUS),
    ("MoodAngryMissile", "Evil", MOOD_MISSILE), ("MoodFuming", "Rage", MOOD_FUME), ("MoodGlitch", "Rage", MOOD_GLITCH),
    ("MoodThinking", "Neutral", MOOD_THINK), ("MoodHappy", "Beam", MOOD_HAPPY), ("MoodInLove", "Love", MOOD_LOVE),
    ("MoodGlad", "Glad", MOOD_GLAD), ("MoodAwesome", "Smug", MOOD_COOL), ("MoodShy", "Bashful", MOOD_SHY),
    ("MoodEmbarrassed", "Fluster", MOOD_EMBARRASSED), ("MoodShowOff", "Smug", MOOD_SHOWOFF),
    ("MoodListening", "Happy", MOOD_LISTEN), ("MoodArrogant", "Sleepy", MOOD_ARROGANT),
]
PRESETS.extend((n, f, p, G_NONE, F_NONE) for n, f, p in MOOD_PRESETS)


def build_seqs():
    seqs = [("None", 1, [], [])]
    for name, face, prop, bg, fg in PRESETS:
        if name in HAND_STORIES:
            dur, beats, extra = HAND_STORIES[name]
        else:
            dur, beats, extra = auto_story(name, face, prop, bg, fg)
        seqs.append((name, dur, beats, extra))
    return seqs


SEQS = build_seqs()
