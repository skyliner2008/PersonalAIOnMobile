# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# ================================================================= STATE MACHINE
def cond_number(d, prop_id, value):
    w(d, '<TransitionViewModelCondition opValue="equal">')
    w(d + 1, '<TransitionPropertyViewModelComparator>')
    w(d + 2, '<BindablePropertyNumber>')
    w(d + 3, '<DataBindContext sourcePathIds="%s-%s" propertyKey="636"/>' % (VM_ID, prop_id))
    w(d + 2, '</BindablePropertyNumber>')
    w(d + 1, '</TransitionPropertyViewModelComparator>')
    w(d + 1, '<TransitionValueNumberComparator value="%d"/>' % value)
    w(d, '</TransitionViewModelCondition>')


def channel_layer(lname, prop_id, anims, y0, duration=140):
    """AnyState -> one AnimationState per index, selected by a view model number.
    reset="true": re-entering a state restarts its timeline instead of resuming
    wherever it was cut off last time."""
    states = [nid() for _ in anims]
    w(3, '<StateMachineLayer name="%s" id="%s">' % (lname, nid()))
    w(4, '<AnyState x="-40" y="%d">' % y0)
    for i, sid in enumerate(states):
        w(5, '<StateTransition stateToId="%s" duration="%d">' % (sid, duration))
        cond_number(6, prop_id, i)
        w(5, '</StateTransition>')
    w(4, '</AnyState>')
    w(4, '<ExitState x="-40" y="%d"/>' % (y0 + 50))
    w(4, '<EntryState x="-240" y="%d">' % y0)
    w(5, '<StateTransition stateToId="%s"/>' % states[0])
    w(4, '</EntryState>')
    for i, (sid, aid) in enumerate(zip(states, anims)):
        w(4, '<AnimationState x="%d" y="%d" reset="true" animationId="%s" id="%s"/>'
          % (160 + (i % 6) * 190, y0 + 90 + (i // 6) * 80, aid, sid))
    w(3, '</StateMachineLayer>')


def cond_speaking(d, value):
    w(d, '<TransitionViewModelCondition opValue="equal">')
    w(d + 1, '<TransitionPropertyViewModelComparator>')
    w(d + 2, '<BindablePropertyBoolean>')
    w(d + 3, '<DataBindContext sourcePathIds="%s-%s" propertyKey="634"/>' % (VM_ID, VP_SPEAKING))
    w(d + 2, '</BindablePropertyBoolean>')
    w(d + 1, '</TransitionPropertyViewModelComparator>')
    w(d + 1, '<TransitionValueBooleanComparator value="%s"/>' % ("true" if value else "false"))
    w(d, '</TransitionViewModelCondition>')


def simple_layer(lname, anim_id, y):
    lid, sid = nid(), nid()
    w(3, '<StateMachineLayer name="%s" id="%s">' % (lname, lid))
    w(4, '<AnyState x="520" y="%d"/>' % y)
    w(4, '<ExitState x="700" y="%d"/>' % y)
    w(4, '<EntryState x="-40" y="%d">' % y)
    w(5, '<StateTransition stateToId="%s"/>' % sid)
    w(4, '</EntryState>')
    w(4, '<AnimationState x="160" y="%d" animationId="%s" id="%s"/>' % (y, anim_id, sid))
    w(3, '</StateMachineLayer>')


def blend_layer(lname, poses, prop_id, y):
    lid, sid = nid(), nid()
    w(3, '<StateMachineLayer name="%s" id="%s">' % (lname, lid))
    w(4, '<AnyState x="520" y="%d"/>' % y)
    w(4, '<ExitState x="700" y="%d"/>' % y)
    w(4, '<EntryState x="-40" y="%d">' % y)
    w(5, '<StateTransition stateToId="%s"/>' % sid)
    w(4, '</EntryState>')
    w(4, '<BlendState1DViewModel x="160" y="%d" id="%s">' % (y, sid))
    w(5, '<BindablePropertyNumber>')
    w(6, '<DataBindContext sourcePathIds="%s-%s" propertyKey="636" converterId="%s"/>'
      % (VM_ID, prop_id, CONV_GAZE))
    w(5, '</BindablePropertyNumber>')
    for a, v in zip(poses, (0, 50, 100)):
        w(5, '<BlendAnimation1D animationId="%s" value="%d"/>' % (a, v))
    w(4, '</BlendState1DViewModel>')
    w(3, '</StateMachineLayer>')


w(2, '<!-- ============================ STATE MACHINE ============================ -->')
w(2, '<StateMachine name="State Machine 1" id="%s">' % SM_ID)

# Declaration order is priority: a later layer wins a property both key.
simple_layer("RestPose", rest_anim, -1600)
simple_layer("Blink", blink_anim, 40)          # before Face: a noblink face pins it
simple_layer("Breathe", breathe_anim, 130)
w(3, '')
channel_layer("Face", VP_FACE, face_anims, -1400)
channel_layer("EyeAct", VP_EYEACT, act_anims, -1150)
# Items that carry props switch in on the frame they are asked for: a blended
# transition mixes their movers from the REST pose (a scene's climax, everything
# on screen) toward frame 0 while the Solo has already swapped the prop in, so
# every part of the scene flashed up in a pile for the first moments.
channel_layer("Prop", VP_PROP, prop_anims, -900, duration=0)
channel_layer("Bg", VP_BG, bg_anims, -400, duration=0)
channel_layer("Fg", VP_FG, fg_anims, -100, duration=0)
channel_layer("Item", VP_ITEM, item_anims, 2400, duration=0)
for slot, row in enumerate(clock_anims):
    channel_layer("Clock%d" % slot, VP_CLOCK[slot], row, 1300 + slot * 260, duration=0)
w(3, '')
simple_layer("PropLoops", loop_anim, 100)
# State: the resting behaviour -- an indefinite loop the host swaps any frame.
channel_layer("State", VP_STATE, state_anims, 150)
# BlinkGate after State: a noblink face vetoes the motion-only states' blinks
channel_layer("BlinkGate", VP_FACE, gate_anims, 400, duration=0)
# Seq after every channel layer AND PropLoops: a story owns prop and motion.
channel_layer("Seq", VP_SEQ, seq_anims, 200, duration=0)
# React last of the content layers: a touch cuts in over a running story.
channel_layer("React", VP_REACT, react_anims, 620, duration=0)
w(3, '')
blend_layer("GazeYaw", (look_l, look_ch, look_r), VP_GAZEX, 900)
blend_layer("GazePitch", (look_u, look_cv, look_d), VP_GAZEY, 990)
w(3, '')

sp_lid, sp_idle, sp_talk = nid(), nid(), nid()
w(3, '<StateMachineLayer name="Speech" id="%s">' % sp_lid)
w(4, '<AnyState x="520" y="1170"/>')
w(4, '<ExitState x="700" y="1170"/>')
w(4, '<EntryState x="-40" y="1170">')
w(5, '<StateTransition stateToId="%s"/>' % sp_idle)
w(4, '</EntryState>')
w(4, '<AnimationState x="160" y="1170" animationId="%s" id="%s">' % (voice_idle, sp_idle))
w(5, '<StateTransition stateToId="%s" duration="90">' % sp_talk)
cond_speaking(6, True)
w(5, '</StateTransition>')
w(4, '</AnimationState>')
w(4, '<AnimationState x="360" y="1170" animationId="%s" id="%s">' % (voice_talk, sp_talk))
w(5, '<StateTransition stateToId="%s" duration="160">' % sp_idle)
cond_speaking(6, False)
w(5, '</StateTransition>')
w(4, '</AnimationState>')
w(3, '</StateMachineLayer>')

w(2, '</StateMachine>')
w(1, '</Artboard>')
w(1, '')
w(1, '<!-- ============================ DATA ============================ -->')
w(1, '<DataConverterRangeMapper minInput="-1" maxInput="1" minOutput="0" maxOutput="100"'
     ' clampLower="true" clampUpper="true" name="GazeToAxis" id="%s"/>' % CONV_GAZE)
# Gyro: tiltX/tiltY are -1..1 device tilt. The whole face slides a little and
# banks slightly against the tilt, which is what sells the head as physical.
w(1, '<DataConverterRangeMapper minInput="-1" maxInput="1" minOutput="-26" maxOutput="26"'
     ' clampLower="true" clampUpper="true" name="TiltToX" id="%s"/>' % CONV_TILTX)
w(1, '<DataConverterRangeMapper minInput="-1" maxInput="1" minOutput="-20" maxOutput="20"'
     ' clampLower="true" clampUpper="true" name="TiltToY" id="%s"/>' % CONV_TILTY)
w(1, '<DataConverterRangeMapper minInput="-1" maxInput="1" minOutput="0.06" maxOutput="-0.06"'
     ' clampLower="true" clampUpper="true" name="TiltToRoll" id="%s"/>' % CONV_TILTR)
# Mic level 0..1: a mouth opens a little (strokes distort if stretched hard),
# the voice bar -- a thin pill -- can stretch a lot.
w(1, '<DataConverterRangeMapper minInput="0" maxInput="1" minOutput="0.94" maxOutput="1.22"'
     ' clampLower="true" clampUpper="true" name="AudioToMouth" id="%s"/>' % CONV_AUDIO)
w(1, '<DataConverterRangeMapper minInput="0" maxInput="1" minOutput="1" maxOutput="1.08"'
     ' clampLower="true" clampUpper="true" name="AudioToMouthX" id="%s"/>' % CONV_AUDIO_X)
w(1, '<DataConverterRangeMapper minInput="0" maxInput="1" minOutput="1" maxOutput="2.2"'
     ' clampLower="true" clampUpper="true" name="AudioToBar" id="%s"/>' % CONV_AUDIO_BAR)

VM_NUMBERS = [("face", VP_FACE, 0), ("prop", VP_PROP, 0), ("bg", VP_BG, 0), ("fg", VP_FG, 0),
              ("eyeAct", VP_EYEACT, 0), ("seq", VP_SEQ, 0), ("state", VP_STATE, 1),
              ("react", VP_REACT, 0), ("gazeX", VP_GAZEX, 0), ("gazeY", VP_GAZEY, 0)]
VM_TAIL = [("mouthOpen", VP_MOUTH, 0), ("tiltX", VP_TILTX, 0), ("tiltY", VP_TILTY, 0),
           ("audioLevel", VP_AUDIO, 0)] + \
    [("clockD%d" % i, VP_CLOCK[i], (1, 2, 0, 0)[i]) for i in range(4)] + [("item", VP_ITEM, 0)]
w(1, '<ViewModel defaultInstanceId="%s" name="Avatar" id="%s">' % (VMI_ID, VM_ID))
for n, pid_, _v in VM_NUMBERS:
    w(2, '<ViewModelPropertyNumber name="%s" id="%s"/>' % (n, pid_))
w(2, '<ViewModelPropertyBoolean name="isSpeaking" id="%s"/>' % VP_SPEAKING)
for n, pid_, _v in VM_TAIL:
    w(2, '<ViewModelPropertyNumber name="%s" id="%s"/>' % (n, pid_))
w(2, '<ViewModelInstance exports="true" name="Default" id="%s">' % VMI_ID)
for _n, pid_, v in VM_NUMBERS:
    w(3, '<ViewModelInstanceNumber propertyValue="%g" viewModelPropertyId="%s"/>' % (v, pid_))
w(3, '<ViewModelInstanceBoolean propertyValue="false" viewModelPropertyId="%s"/>' % VP_SPEAKING)
for _n, pid_, v in VM_TAIL:
    w(3, '<ViewModelInstanceNumber propertyValue="%g" viewModelPropertyId="%s"/>' % (v, pid_))
w(2, '</ViewModelInstance>')
w(1, '</ViewModel>')
w(0, '</Rive>')


# ================================================================= SELF-CHECKS
# Things that compile clean and still break the avatar -- fail the build.
def self_check():
    bad = []
    for i, ln in enumerate(out):
        if 'interpolationType="cubic"' in ln and ln.rstrip().endswith('/>'):
            bad.append("line %d: cubic keyframe without an interpolator" % (i + 1))
            break
    in_sm = False
    for ln in out:
        s_ = ln.strip()
        if s_.startswith('<StateMachineLayer name="'):
            lname = s_.split('"')[1]
            in_sm = lname not in ("RestPose", "Blink", "Breathe", "PropLoops", "Speech")
        if in_sm and s_.startswith('<AnimationState') and 'reset="true"' not in s_:
            bad.append("channel layer %s has a state without reset" % lname)
            break
    if sum(1 for ln in out if 'loopValue="oneShot"' in ln) != len(SEQS) + len(REACTS):
        bad.append("stories / reactions must be oneShot")
    if bad:
        raise SystemExit("self-check failed:\n  " + "\n  ".join(bad))


self_check()
