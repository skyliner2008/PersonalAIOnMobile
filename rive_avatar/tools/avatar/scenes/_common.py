# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# ================================================================= SCENE STORIES
# The storyboards in .obsidian-wiki/02_Components/Pet_Scene_Scripts.md, frame by
# frame (60 fps). Beats swap the face / prop / scene; extras move the eyes
# (blinkL / blinkR x,y = where the eyes look), the head (face) and every part
# of the scene prop ("prop:<index>:<mover>"). The app plays the sound cues.

def _sp(scene, mover):
    return "prop:%d:%s" % (P_NAMES.index(scene), mover)


def _look(keys, kfs):
    """The same gaze keyframes on both eyes."""
    return [(k, keys[0], kfs) for k in ("blinkL", "blinkR")]


def _both(key, kfs):
    return [("face", 16, kfs), ("face", 17, kfs)] if key == "facescale" else \
        [(n, key, kfs) for n in ("blinkL", "blinkR")]


def _scale(scene, mover, kfs):
    return [(_sp(scene, mover), 16, kfs), (_sp(scene, mover), 17, kfs)]


FOOD, DRINK, BATH, GAME, STUDY, RAIN = (P_NAMES.index(n) for n in
                                        ("SceneFood", "SceneDrink", "SceneBath", "SceneGame", "SceneStudy", "SceneRain"))
THUG, RICH, ROYAL, FIRE, THUNDER, SOUL = (P_NAMES.index(n) for n in
                                          ("SceneThug", "SceneRich", "SceneRoyal", "SceneFire", "SceneThunder", "SceneSoul"))
LOVE, CRY, PARTY, VR, MUSIC = (P_NAMES.index(n) for n in ("SceneLove", "SceneCry", "SceneParty", "SceneVR", "SceneMusic"))

SCENE_STORIES = {}
