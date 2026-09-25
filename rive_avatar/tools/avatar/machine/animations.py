# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# ================================================================= ANIMATIONS
w(2, '<!-- ============================ ANIMATIONS ============================ -->')
w(2, '<!-- FACE: ' + ", ".join("%d=%s" % (i, f[0]) for i, f in enumerate(FACES)) + ' -->')
w(2, '<!-- PROP: ' + ", ".join("%d=%s" % (i, n) for i, n in enumerate(P_NAMES)) + ' -->')
w(2, '<!-- BG:   ' + ", ".join("%d=%s" % (i, n) for i, n in enumerate(G_NAMES)) + ' -->')
w(2, '<!-- FG:   ' + ", ".join("%d=%s" % (i, n) for i, n in enumerate(F_NAMES)) + ' -->')

# every (object, property) something animates, so RestPose can cover them all
ANIMATED = {}          # (obj, key) -> rest value
ALWAYS_ON = set()      # keyed every frame by a layer that never stops


def keyed_id(d, obj, value):
    w(d, '<KeyedObject objectId="%s">' % obj)
    w(d + 1, '<KeyedProperty propertyKey="296">')
    w(d + 2, '<KeyFrameId value="%s" frame="0"/>' % value)
    w(d + 1, '</KeyedProperty>')
    w(d, '</KeyedObject>')
    if obj in BACK_SOLO:
        keyed_id(d, BACK_SOLO[obj], EYE_BACK[value])


def emit_tracks(d, tracks):
    """tracks: {(obj, key): [(frame, value, interp)]} -> KeyedObject blocks."""
    by_obj = {}
    for (obj, key), kfs in tracks.items():
        by_obj.setdefault(obj, []).append((key, kfs))
    for obj, entries in by_obj.items():
        w(d, '<KeyedObject objectId="%s">' % obj)
        for key, kfs in entries:
            w(d + 1, '<KeyedProperty propertyKey="%d">' % key)
            seen = set()
            for fr, vv, itp in sorted(kfs, key=lambda t: t[0]):
                if fr in seen:
                    continue
                seen.add(fr)
                kf(d + 2, vv, fr, itp)
            w(d + 1, '</KeyedProperty>')
        w(d, '</KeyedObject>')


def rest_value(obj, key):
    if obj == VOICE_BAR[0] and key == 18:
        return 0.0
    base = MOVER_BASE.get(obj)
    if key == 13:
        return base[0] if base else 0.0
    if key == 14:
        return base[1] if base else 0.0
    if key == 15:
        return 0.0
    return 1.0          # scaleX / scaleY / opacity


def note(obj, key, value=None):
    if (obj, key) not in ANIMATED:
        ANIMATED[(obj, key)] = rest_value(obj, key) if value is None else value


def intro_tracks(kind, idx, offset=0, skip=()):
    res = {}
    for obj, key, kfs, itp in INTROS[kind].get(idx, []):
        if (obj, key) in skip:
            continue
        note(obj, key, kfs[-1][1])        # rest where the entrance lands
        res[(obj, key)] = [(fr + offset, vv, itp) for fr, vv in kfs]
    return res


# ---- face ------------------------------------------------------------
# the morph eye vertices rest as the round eye whenever no layer keys them
for _ok, _v in MORPH_REST.items():
    ANIMATED.setdefault(_ok, _v)
face_anims = []
for i, (fn, el, er, br, mo, noblink) in enumerate(FACES):
    aid = nid()
    face_anims.append(aid)
    # long enough for the pop (squash, overshoot, settle) every face change plays
    w(2, '<LinearAnimation duration="%d" fps="60" name="Face%02d_%s" id="%s">'
      % (POP_KEYS[-1][0] + POP_LAG + 1, i, fn, aid))
    for solo, val in ((eyeL_solo, eye_ids["L"][el]), (eyeR_solo, eye_ids["R"][er]),
                      (brow_solo, brow_ids[br]), (mouth_solo, mouth_ids[mo])):
        keyed_id(3, solo, val)
    if fn in MORPH_FACE:
        # the eye shape itself: the Face layer's eased mix morphs between faces
        shape_keys = [(0, MORPH_FACE[fn], "hold")]
        emit_tracks(3, morph_tracks(shape_keys))
        emit_color_frames(3, morph_color_frames(shape_keys), interp="hold")
    emit_tracks(3, pop_tracks(0))
    if noblink:
        # the Face layer is declared after Blink, so pinning scaleY here wins
        emit_tracks(3, {(b, 17): [(0, 1, "hold")] for b in (eyeL_blink, eyeR_blink)})
    w(2, '</LinearAnimation>')

# ---- blink gate: noblink faces veto the motion-only states' blinks -----
gate_anims = []
for i, (fn, _el, _er, _br, _mo, noblink) in enumerate(FACES):
    aid = nid()
    gate_anims.append(aid)
    w(2, '<LinearAnimation duration="1" fps="60" name="Gate%02d_%s" id="%s">' % (i, fn, aid))
    if noblink:
        emit_tracks(3, {(b, 17): [(0, 1, "hold")] for b in (eyeL_sblink, eyeR_sblink)})
    w(2, '</LinearAnimation>')

# ---- prop / bg / fg: show the item AND play its entrance ---------------
prop_anims, bg_anims, fg_anims = [], [], []
for label, kind, names, solo, ids_list, bucket in (
        ("Prop", "prop", P_NAMES, prop_solo, prop_ids, prop_anims),
        ("Bg", "bg", G_NAMES, bg_solo, bg_ids, bg_anims),
        ("Fg", "fg", F_NAMES, fg_solo, fg_ids, fg_anims)):
    for i, n in enumerate(names):
        aid = nid()
        bucket.append(aid)
        tr = intro_tracks(kind, i)
        dur = max([1] + [kf_[0] for kfs in tr.values() for kf_ in kfs])
        w(2, '<LinearAnimation duration="%d" fps="60" name="%s%02d_%s" id="%s">' % (dur, label, i, n, aid))
        keyed_id(3, solo, ids_list[i])
        emit_tracks(3, tr)
        w(2, '</LinearAnimation>')

# ---- standby clock digits -----------------------------------------------
clock_anims = []
for slot, solo in enumerate(CLOCK_SOLOS):
    row = []
    for n in range(10):
        aid = nid()
        row.append(aid)
        w(2, '<LinearAnimation duration="1" fps="60" name="Clock%d_%d" id="%s">' % (slot, n, aid))
        keyed_id(3, solo, CLOCK_DIGITS[slot][n])
        w(2, '</LinearAnimation>')
    clock_anims.append(row)

# ---- scene items (food / drink / device) ----------------------------------
item_anims = []
ITEM_COUNT = max([len(ids) for _sid, ids in ITEM_SOLOS] + [1])
for n in range(ITEM_COUNT):
    aid = nid()
    item_anims.append(aid)
    w(2, '<LinearAnimation duration="1" fps="60" name="Item%d" id="%s">' % (n, aid))
    for sid, ids in ITEM_SOLOS:
        keyed_id(3, sid, ids[min(n, len(ids) - 1)])
    w(2, '</LinearAnimation>')

w(2, '')

# ---- eye acts ---------------------------------------------------------
act_anims = []
for i, (an, kfs, lv, override) in enumerate(EYE_ACTS):
    aid = nid()
    act_anims.append(aid)
    dur = max([f for f, _sx, _sy in kfs] + [1])
    w(2, '<LinearAnimation loopValue="%s" duration="%d" fps="60" name="Act%02d_%s" id="%s">'
      % (lv, dur, i, an, aid))
    if override is not None:
        keyed_id(3, eyeL_solo, eye_ids["L"][override])
        keyed_id(3, eyeR_solo, eye_ids["R"][override])
    if kfs:
        tr = {}
        for oid in (eyeL_blink, eyeR_blink):
            for key, idx in ((16, 1), (17, 2)):
                tr[(oid, key)] = [(k_[0], k_[idx], "cubic") for k_ in kfs]
                note(oid, key)
        emit_tracks(3, tr)
    w(2, '</LinearAnimation>')

# ---- sequences / states / reactions ------------------------------------
SEQ_NODES = {"blink": (eyeL_blink, eyeR_blink), "blinkL": (eyeL_blink,),
             "blinkR": (eyeR_blink,), "sblink": (eyeL_sblink, eyeR_sblink),
             "sblinkL": (eyeL_sblink,), "sblinkR": (eyeR_sblink,),
             "face": (face_breathe,), "mouth": (mouth_scale,),
             # Eye Lab timeline stories (stories/lab.py)
             "irisL": (irisL_look,), "irisR": (irisR_look,),
             "tiltL": (eyeL_tilt,), "tiltR": (eyeR_tilt,),
             "popL": (eyeL_pop,), "popR": (eyeR_pop,), "props": (prop_solo,)}
# the eye tilt nodes rest on the lab's fixed per-eye rotation, not on 0 / 1
for _tn, _tg in ((eyeL_tilt, "L"), (eyeR_tilt, "R")):
    _er = eye_rotation(_tg)
    ANIMATED.setdefault((_tn, 16), math.cos(math.radians(_er["y"])))
    ANIMATED.setdefault((_tn, 17), math.cos(math.radians(_er["x"])))
    ANIMATED.setdefault((_tn, 15), math.radians(_er["z"]))


def seq_targets(nkey):
    # "vtxL:3" -> vertex 3 of the left morph eye, front and back layers together
    if nkey.startswith("vtx"):
        tag, idx = nkey[3], int(nkey.split(":")[1])
        return tuple(MORPH_VERTS[(tag, part)][idx] for part in ("front", "back"))
    if nkey.startswith("prop:"):
        _p, idx, nm = nkey.split(":")
        node = PROP_NODES.get(int(idx), {}).get(nm)
        if node is None:
            raise SystemExit("sequence references unknown prop node: " + nkey)
        return (node,)
    return SEQ_NODES[nkey]


JELLY_NODES = ("blink", "blinkL", "blinkR", "sblink", "sblinkL", "sblinkR", "face", "mouth")
# how far the front (pupil) layer leads the look -- Eye Lab "ลูกตาขยับนำหัว"
IRIS_X = IRIS_Y = float(EYE_HEAD["pupil"])
IRIS_GAIN = 0.45
IRIS_FROM = {"blink": (irisL_look, irisR_look), "blinkL": (irisL_look,), "blinkR": (irisR_look,),
             "sblink": (irisL_look, irisR_look), "sblinkL": (irisL_look,), "sblinkR": (irisR_look,)}
explicit_iris = set()
MORPH_STORY_COLORS = {}   # story animation id -> morph eye colour frames


def lab_color_frames(keys):
    """Eye Lab story colours [(frame, {"L", "R"})] -> colour frames: each eye keeps its
    colour, then crossfades into the next key's over the last MORPH_F frames before it."""
    frames = {}
    for tag in ("L", "R"):
        for part in ("front", "back"):
            for cid, role in MORPH_PAINT[(tag, part)]:
                kk = []
                for j, (fr, cols) in enumerate(keys):
                    front, back = _LAB_COLORS.get(cols[tag], (CYAN, BLUE))
                    argb = {"front": front, "glow": glow_argb(front)}.get(role, back)
                    if kk and kk[-1][1] != argb:
                        kk.append((max(kk[-1][0], fr - MORPH_F), kk[-1][1]))
                    kk.append((fr, argb))
                frames[cid] = kk
    return frames


def emit_one_story(i, sn, dur, beats, extra, label, loop):
    aid = nid()
    w(2, '<LinearAnimation loopValue="%s" duration="%d" fps="60" name="%s%02d_%s" id="%s">'
      % (loop, dur, label, i, sn, aid))
    tracks = {}
    for ex in extra:
        nkey, key, kfs = ex[0], ex[1], ex[2]
        itp = ex[3] if len(ex) > 3 else "cubic"
        if itp == "cubic" and nkey in JELLY_NODES and key in (13, 14, 16, 17):
            itp = "jelly"
        for oid in seq_targets(nkey):
            note(oid, key)
            # a keyframe may carry its own interpolation: (frame, value, interp)
            own = [k[2] if len(k) > 2 else itp for k in kfs]
            vals = rebase(oid, key, [(k[0], k[1]) for k in kfs])
            tracks[(oid, key)] = [(fr, vv, ow) for (fr, vv), ow in zip(vals, own)]
        # where the story moves an eye, its front shape leans the same way
        if key in (13, 14) and nkey in IRIS_FROM:
            lim = IRIS_X if key == 13 else IRIS_Y
            for oid in IRIS_FROM[nkey]:
                if (oid, key) in explicit_iris:
                    continue
                note(oid, key)
                tracks[(oid, key)] = [(k[0], max(-lim, min(lim, k[1] * IRIS_GAIN)), k[2] if len(k) > 2 else itp)
                                      for k in kfs]
    if beats:
        chans = {eyeL_solo: [], eyeR_solo: [], brow_solo: [], mouth_solo: [],
                 prop_solo: [], bg_solo: [], fg_solo: []}
        shown = {"prop": [], "bg": [], "fg": []}
        chans[eyeL_back] = []
        chans[eyeR_back] = []
        for fr, fname, pr, bgv, fgv in beats:
            fi = F_INDEX[fname]
            _n, el, er, br, mo, _nb = FACES[fi]
            chans[eyeL_solo].append((fr, eye_ids["L"][el]))
            chans[eyeR_solo].append((fr, eye_ids["R"][er]))
            chans[eyeL_back].append((fr, EYE_BACK[eye_ids["L"][el]]))
            chans[eyeR_back].append((fr, EYE_BACK[eye_ids["R"][er]]))
            chans[brow_solo].append((fr, brow_ids[br]))
            chans[mouth_solo].append((fr, mouth_ids[mo]))
            chans[prop_solo].append((fr, prop_ids[pr]))
            chans[bg_solo].append((fr, bg_ids[bgv]))
            chans[fg_solo].append((fr, fg_ids[fgv]))
            for kind, v in (("prop", pr), ("bg", bgv), ("fg", fgv)):
                if not shown[kind] or shown[kind][-1][1] != v:
                    shown[kind].append((fr, v))
        # beats onto morph faces reshape the eyes (jelly) instead of swapping them;
        # a story's own vertex keys (quiver, melt) win over the beat morph
        morph_keys = []
        prev_shape = None
        for fr, fname, _pr, _bgv, _fgv in beats:
            shape_name = MORPH_FACE.get(fname)
            if shape_name is None:
                prev_shape = None
                continue
            if prev_shape is not None and prev_shape != shape_name and fr > 0:
                morph_keys += [(fr, prev_shape, "jelly"), (fr + MORPH_F, shape_name, "hold")]
            else:
                morph_keys.append((fr, shape_name, "hold"))
            prev_shape = shape_name
        # every beat that changes the face pops the eyes (right eye a beat later)
        pops = {}
        last_face = None
        for fr, fname, _pr, _bgv, _fgv in beats:
            if last_face is not None and fname != last_face and fr > 0:
                for ok, kfs in pop_tracks(fr).items():
                    if ok not in tracks:
                        pops.setdefault(ok, []).extend(kfs)
            last_face = fname
        for ok, kfs in pops.items():
            tracks[ok] = [(min(f, dur), v, itp) for f, v, itp in kfs]
            note(*ok)
        if morph_keys:
            for ok, kfs in morph_tracks(morph_keys, skip=set(tracks)).items():
                tracks[ok] = [(min(f, dur), v, itp) for f, v, itp in kfs]
            MORPH_STORY_COLORS[aid] = morph_color_frames(morph_keys)
        for solo, kfs in chans.items():
            w(3, '<KeyedObject objectId="%s">' % solo)
            w(4, '<KeyedProperty propertyKey="296">')
            last = None
            for fr, val in kfs:
                if val == last:
                    continue
                last = val
                w(5, '<KeyFrameId value="%s" frame="%d"/>' % (val, fr))
            w(4, '</KeyedProperty>')
            w(3, '</KeyedObject>')
        # every time a beat switches to an item with an entrance, play it from
        # that frame -- explicit extras on the same property win
        explicit = set(tracks)
        for kind, changes in shown.items():
            for fr, v in changes:
                for ok, kfs in intro_tracks(kind, v, fr, explicit).items():
                    prev = tracks.get(ok, [])
                    if prev:
                        # hold the previous entrance's end until this one starts
                        prev[-1] = (prev[-1][0], prev[-1][1], "hold")
                    tracks[ok] = prev + kfs
    for ok in tracks:
        if ok in MORPH_REST:
            note(*ok)
    emit_tracks(3, tracks)
    if aid in MORPH_STORY_COLORS:
        emit_color_frames(3, MORPH_STORY_COLORS[aid], interp="hold")
    if sn in LAB_COLOR_KEYS:
        emit_color_frames(3, lab_color_frames(LAB_COLOR_KEYS[sn]))
    w(2, '</LinearAnimation>')
    return aid


def emit_stories(table, label, loop):
    return [emit_one_story(i, sn, dur, beats, extra, label, loop)
            for i, (sn, dur, beats, extra) in enumerate(table)]


# a story and a reaction play ONCE and hold their last frame until the host
# clears them; a state is an indefinite loop
seq_anims = emit_stories(SEQS, "Seq", "oneShot")
state_anims = emit_stories(STATES, "State", "loop")
react_anims = emit_stories(REACTS, "React", "oneShot")

# ---- blink ------------------------------------------------------------
blink_anim = nid()
w(2, '<LinearAnimation loopValue="loop" duration="260" fps="60" name="AnimBlink" id="%s">' % blink_anim)
emit_tracks(3, {(oid, 17): [(0, 1, "linear"), (218, 1, "easeIn"), (226, .06, "hold"),
                            (231, .06, "easeOut"), (240, 1, "linear"), (260, 1, "linear")]
                for oid in (eyeL_blink, eyeR_blink)})
w(2, '</LinearAnimation>')
ALWAYS_ON |= {(eyeL_blink, 17), (eyeR_blink, 17)}

# ---- idle breathing: the whole face drifts, like a head resting -------
breathe_anim = nid()
w(2, '<LinearAnimation loopValue="pingPong" duration="180" fps="60" name="AnimBreathe" id="%s">' % breathe_anim)
emit_tracks(3, {(face_breathe, 14): [(0, 4, "cubic"), (180, -4, "cubic")],
                (face_breathe, 16): [(0, 1.014, "cubic"), (180, .992, "cubic")],
                (face_breathe, 17): [(0, .99, "cubic"), (180, 1.018, "cubic")]})
w(2, '</LinearAnimation>')
ALWAYS_ON |= {(face_breathe, 14), (face_breathe, 16), (face_breathe, 17)}

# ---- 2.5D gaze poses --------------------------------------------------
NODE_YAW = {"face": face_yaw, "eyeL": eyeL_yaw, "eyeR": eyeR_yaw,
            "brow": brow_yaw, "mouth": mouth_yaw}
NODE_PITCH = {"face": face_pitch, "eyeL": eyeL_pitch, "eyeR": eyeR_pitch,
              "brow": brow_pitch, "mouth": mouth_pitch}
BASE_X = {"face": CX, "eyeL": -EYE_DX, "eyeR": EYE_DX, "brow": 0.0, "mouth": 0.0}


def yaw_pose(name, idx):
    aid = nid()
    w(2, '<LinearAnimation duration="1" fps="60" name="%s" id="%s">' % (name, aid))
    tr = {}
    for iris in (irisL_gaze, irisR_gaze):
        tr[(iris, 13)] = [(0, (-IRIS_X, 0.0, IRIS_X)[idx], "hold")]
    for k, spec in YAW.items():
        tr[(NODE_YAW[k], 13)] = [(0, BASE_X[k] + spec["x"][idx], "hold")]
        tr[(NODE_YAW[k], 16)] = [(0, spec["sx"][idx], "hold")]
        tr[(NODE_YAW[k], 15)] = [(0, spec["rot"][idx], "hold")]
    emit_tracks(3, tr)
    w(2, '</LinearAnimation>')
    return aid


def pitch_pose(name, idx):
    aid = nid()
    w(2, '<LinearAnimation duration="1" fps="60" name="%s" id="%s">' % (name, aid))
    tr = {}
    for iris in (irisL_gaze, irisR_gaze):
        tr[(iris, 14)] = [(0, (-IRIS_Y, 0.0, IRIS_Y)[idx], "hold")]
    for k, spec in PITCH.items():
        tr[(NODE_PITCH[k], 14)] = [(0, spec["y"][idx], "hold")]
        tr[(NODE_PITCH[k], 17)] = [(0, spec["sy"][idx], "hold")]
    emit_tracks(3, tr)
    w(2, '</LinearAnimation>')
    return aid


look_l, look_ch, look_r = yaw_pose("LookLeft", 0), yaw_pose("LookCenterH", 1), yaw_pose("LookRight", 2)
look_u, look_cv, look_d = pitch_pose("LookUp", 0), pitch_pose("LookCenterV", 1), pitch_pose("LookDown", 2)

# ---- speech -----------------------------------------------------------
# VoiceIdle keys NOTHING: it must not pin the mouth, or the Speaking state and
# the stories could never move it. RestPose owns the resting values.
voice_idle, voice_talk = nid(), nid()
w(2, '<LinearAnimation loopValue="loop" duration="1" fps="60" name="VoiceIdle" id="%s"/>' % voice_idle)
w(2, '<LinearAnimation loopValue="loop" duration="34" fps="60" name="VoiceTalk" id="%s">' % voice_talk)
talk = {
    (mouth_scale, 17): [(0, .9, "cubic"), (8, 1.5, "cubic"), (17, 1.0, "cubic"), (25, 1.35, "cubic"), (34, .9, "cubic")],
    (mouth_scale, 16): [(0, 1.0, "cubic"), (8, 1.1, "cubic"), (17, .97, "cubic"), (25, 1.06, "cubic"), (34, 1.0, "cubic")],
    (VOICE_BAR[0], 18): [(0, 1, "hold")],
    # LOOI talks with its whole head: a small bob and squash on the syllables
    (face_speak, 14): [(0, 0, "cubic"), (8, -5, "cubic"), (17, 0, "cubic"), (25, -3, "cubic"), (34, 0, "cubic")],
    (face_speak, 17): [(0, 1, "cubic"), (8, 1.03, "cubic"), (17, .985, "cubic"), (25, 1.02, "cubic"), (34, 1, "cubic")],
}
for ok in talk:
    note(*ok)
emit_tracks(3, talk)
w(2, '</LinearAnimation>')

# ---- prop / bg / fg loops --------------------------------------------
by_obj = {}
for obj, key, kfs, interp in TRACKS:
    by_obj.setdefault(obj, []).append((key, kfs, interp))
    ALWAYS_ON.add((obj, key))

loop_anim = nid()
LOOP_DUR = 360
DIVS = [dv for dv in range(2, LOOP_DUR + 1) if LOOP_DUR % dv == 0]


def snap_period(p):
    """Round a track's natural cycle to a divisor of LOOP_DUR so tiling it
    across the shared timeline never pops at the seam."""
    return min(DIVS, key=lambda dv: (abs(dv - p), dv))


w(2, '<LinearAnimation loopValue="loop" duration="%d" fps="60" name="PropLoops" id="%s">'
   % (LOOP_DUR, loop_anim))
loops = {}
for obj, entries in by_obj.items():
    for key, kfs, interp in entries:
        period = max(f for f, _v in kfs) or 1
        tgt = snap_period(period)
        sc = float(tgt) / period
        base = [(int(round(f * sc)), v) for f, v in kfs]
        if base[0][0] != 0:
            base.insert(0, (0, base[-1][1]))
        res, seen = [], set()
        for rep_ in range(LOOP_DUR // tgt):
            off = rep_ * tgt
            for f, v in base:
                ff = f + off
                if ff > LOOP_DUR or ff in seen:
                    continue
                seen.add(ff)
                res.append((ff, v, interp))
        if LOOP_DUR not in seen:
            res.append((LOOP_DUR, base[0][1], interp))
        loops[(obj, key)] = res
emit_tracks(3, loops)
w(2, '</LinearAnimation>')

# ---- rest pose: what every animated property returns to ---------------
# Rive never restores a property no layer is keying any more. Without this,
# clearing a story mid-way leaves its last pose frozen on the face: eyes still
# converged after Angry, head still tilted after Sleepy. RestPose is the first
# layer, so anything else that runs overrides it.
rest_anim = nid()
w(2, '<LinearAnimation duration="1" fps="60" name="RestPose" id="%s">' % rest_anim)
REST = {ok: v for ok, v in ANIMATED.items() if ok not in ALWAYS_ON}
emit_tracks(3, {ok: [(0, v, "hold")] for ok, v in REST.items()})
w(2, '</LinearAnimation>')
w(2, '')
