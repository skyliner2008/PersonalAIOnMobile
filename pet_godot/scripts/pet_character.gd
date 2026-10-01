extends Node3D
## The pet robot as a game character: the Blender model (assets/robot.glb) with its animation clips,
## a bouncy procedural layer on top, live shader eyes, an emotion-coloured suit and swappable gear.
##
## Public API (what the app bridge will call later):
##   set_emotion(name)          normal / happy / sad / wink / love / surprised / sleepy / angry / talk
##   wear(name) / take_off(name) / toggle(name)    anything in robot_meta.json (one item per slot)
##   use_tool(side)             "L" / "R": fire the cannon or laser, switch the flashlight or shield
##   toggle_fly()               jetpack flight (puts the jetpack on if needed)
##   set_suit_color(color) / suit_follows_emotion   the blue parts of the body
##   look(gaze: Vector2)        -1..1, turns the body a little and moves the eyes
##   poke()                     startled bounce, then back to the last emotion
##   bump(zone)                 a touch lands (head / cheek / chin / body): a bounce, the emotion stays
##   fidget(name := "")         a cheeky idle move: hop / spin / tilt / wiggle / peek / cheeky
##   speaking / voice_level     the mouth moves with the voice (any emotion); level 0..1, 0 = no level known
##   sway                       radians the body leans with the phone's tilt (springs towards it)

signal emotion_changed(name: String)
signal outfit_changed(worn: Dictionary)

const META_PATH := "res://assets/robot_meta.json"
const ROBOT_PATH := "res://assets/robot.glb"
const ACCESSORY_DIR := "res://assets/accessories/%s.glb"
const EYES_SHADER := preload("res://shaders/robot_eyes.gdshader")
const SHADOW_SHADER := preload("res://shaders/blob_shadow.gdshader")
const CAPE_SHADER := preload("res://shaders/cape_wave.gdshader")
const FX := preload("res://scripts/effects.gd")

## emotion -> [animation clip, eye expression, eye colour, suit colour]
const EMOTIONS := {
	"normal": ["Idle", 0, Color(0.49, 0.97, 1.0), Color("6FA6DE")],
	"happy": ["Happy", 1, Color(0.49, 0.97, 1.0), Color("FFC247")],
	"sad": ["Sad", 2, Color(0.45, 0.78, 1.0), Color("5B72B8")],
	"wink": ["Wave", 3, Color(0.49, 0.97, 1.0), Color("7ED6A5")],
	"love": ["Love", 4, Color(1.0, 0.36, 0.62), Color("FF8FB8")],
	"surprised": ["Surprised", 5, Color(0.55, 1.0, 1.0), Color("5FD9E6")],
	"sleepy": ["Sleep", 6, Color(0.40, 0.62, 0.80), Color("A99BD8")],
	"angry": ["Idle", 7, Color(1.0, 0.28, 0.25), Color("F2665A")],
	"talk": ["Talk", 0, Color(0.45, 1.0, 0.72), Color("74B9F0")],
}
## eye expressions for set_eyes() (robot_eyes.gdshader); the extra ones are for scenes
const EYES := {
	"normal": 0, "happy": 1, "sad": 2, "wink": 3, "love": 4, "surprised": 5, "sleepy": 6, "angry": 7,
	"dizzy": 8, "cry": 9, "squint": 10,
}
const FIDGETS := ["hop", "spin", "tilt", "wiggle", "peek", "cheeky"]

const MAX_YAW := 0.45          # radians the body turns towards the gaze
const BLEND := 0.3             # seconds of cross-fade between clips
## arm pose per tool: [out to the side (negative = in, across the front), forward (negative = towards
## the viewer)], degrees. A portrait phone screen is narrow, so shots go up-and-forward or diagonally
## across it, and the shield is held across the front of the body.
const TOOL_AIM := {"cannon": [14.0, -104.0], "laser": [12.0, -82.0], "flashlight": [12.0, -78.0], "shield": [-24.0, -80.0]}
## beams aim at a point in front of the pet (pet space), so they cross the screen instead of leaving it
const BEAM_TARGET := {"laser": Vector3(0.0, 0.55, 3.6), "flashlight": Vector3(-0.2, -0.35, 2.6)}
## arm raised up and out towards a hologram panel ("ta-da"), same [out, forward] as TOOL_AIM
const PRESENT_POSE := [118.0, -28.0]

var meta: Dictionary = {}
var emotion := "normal"
var worn := {}                 # slot -> item name
var suit_follows_emotion := true
var flying := false
var scene_active := false      # a scripted scene is driving the pet (no idle fidgets, no gaze wander)

const GROUND_Y := -0.12
const BODY_CENTER := 1.0         # flips, leans and spins turn about this height, not the feet

var body_roll := 0.0           # radians, set by scenes (sideways flips)
var body_pitch := 0.0          # radians, set by scenes (forward flips)
var speaking := false          # the app's voice is talking: the mouth moves whatever the emotion
var voice_level := 0.0         # 0..1 loudness of that voice (0 = unknown: a talking rhythm instead)
var sway := 0.0                # radians, the lean the phone's tilt asks for (app mode)
var mouth_override := -1.0     # >= 0: hold the mouth open this much (sprite renders)
var blink_override := -1.0     # >= 0: hold the eyelids (sprite renders)

var _robot: Node3D
var _anim: AnimationPlayer
var _float: Node3D
var _head: Node3D
var _arms := {}
var _antennas := {}
var _sockets := {}
var _eyes := ShaderMaterial.new()
var _eye_color := Color(0.49, 0.97, 1.0)
var _suit_mat: StandardMaterial3D
var _core_mat: StandardMaterial3D
var _shadow: MeshInstance3D
var _items := {}               # name -> holder Node3D (instanced, may be hidden)

# procedural layer
var _gaze := Vector2.ZERO
var _gaze_now := Vector2.ZERO
var _prev_float_y := 0.0
var _squash := 0.0
var _squash_v := 0.0
var _head_off := 0.0
var _head_off_v := 0.0
var _ant := 0.0
var _ant_v := 0.0
var _hop := 0.0
var _spin := 0.0
var _lean := 0.0
var _head_roll := 0.0
var _head_nod := 0.0             # degrees, positive tips the face down (the "nod" fidget)
var _lift := 0.0
var _aim_l := 0.0                # arm raise weights while a tool is on
var _aim_r := 0.0
var _present_l := 0.0            # arm raise weights while presenting a panel
var _present_r := 0.0
var _blink_timer := 2.0
var _blink := 0.0
var _eyes_serial := 0            # bumps on every expression change, so an old set_eyes() timer can't undo a newer one
var _talk_phase := 0.0
var _voice_now := 0.0
var _sway_now := 0.0
var _sway_v := 0.0
var _poke_left := 0.0
var _emotion_before_poke := ""
var _fidget_timer := 6.0
var _busy := 0.0               # seconds until fidgets may start again
var _t := 0.0

# gear state
var _beams := {}               # side -> flashlight root
var _shields := {}             # side -> shield mesh
var _jets: Array = []          # CPUParticles3D at the jetpack nozzles


func _ready() -> void:
	meta = JSON.parse_string(FileAccess.get_file_as_string(META_PATH))
	_robot = (load(ROBOT_PATH) as PackedScene).instantiate()
	add_child(_robot)
	_anim = _robot.find_child("AnimationPlayer", true, false) as AnimationPlayer
	for clip in _anim.get_animation_list():
		_anim.get_animation(clip).loop_mode = Animation.LOOP_LINEAR
	# the procedural layer is applied on top of the clip every frame, so advance the clip by hand
	_anim.callback_mode_process = AnimationMixer.ANIMATION_CALLBACK_MODE_PROCESS_MANUAL
	_float = _node("PR_Float")
	_head = _node("PR_HeadPivot")
	for side in ["L", "R"]:
		_arms[side] = _node("PR_Arm" + side)
		_antennas[side] = _node("PR_AntennaPivot" + side)
		_sockets[side] = _node("PR_HandSocket" + side)
	_setup_eyes()
	_setup_materials(_robot)
	_setup_shadow()
	set_emotion("normal")


func _node(name: String) -> Node3D:
	return _robot.find_child(name, true, false) as Node3D


func _setup_eyes() -> void:
	var screen := _robot.find_child(meta["screen"]["node"], true, false) as MeshInstance3D
	var half: Array = meta["screen"]["half_size"]
	_eyes.shader = EYES_SHADER
	_eyes.set_shader_parameter("aspect", (half[0] + 0.012) / (half[1] + 0.012))
	screen.material_override = _eyes


## One shared suit material (PR_LightBlue on the body, ACC_SuitBlue on gear) so a single colour
## change recolours everything blue; the chest core follows the eye colour.
func _setup_materials(root: Node) -> void:
	for n in root.find_children("*", "MeshInstance3D", true, false):
		var mi := n as MeshInstance3D
		for i in mi.get_surface_override_material_count():
			var mat := mi.get_active_material(i)
			if mat == null:
				continue
			if mat.resource_name in ["PR_LightBlue", "ACC_SuitBlue"]:
				if _suit_mat == null:
					_suit_mat = (mat as StandardMaterial3D).duplicate()
				mi.set_surface_override_material(i, _suit_mat)
			elif mat.resource_name == "PR_CoreLight":
				if _core_mat == null:
					_core_mat = (mat as StandardMaterial3D).duplicate()
				mi.set_surface_override_material(i, _core_mat)


func _setup_shadow() -> void:
	_shadow = MeshInstance3D.new()
	var quad := QuadMesh.new()
	quad.size = Vector2(1.6, 1.1)
	quad.orientation = PlaneMesh.FACE_Y
	_shadow.mesh = quad
	var mat := ShaderMaterial.new()
	mat.shader = SHADOW_SHADER
	_shadow.material_override = mat
	_shadow.position = Vector3(0.0, -0.12, 0.0)
	add_child(_shadow)


## Finds the imported clip whose name starts with the clip base ("Idle" matches "Idle_loop").
func _clip(base: String) -> String:
	for clip in _anim.get_animation_list():
		if clip.to_lower().begins_with(base.to_lower()):
			return clip
	return _anim.get_animation_list()[0]


# ---------------------------------------------------------------- emotion & colour

func set_emotion(name: String) -> void:
	if not EMOTIONS.has(name):
		push_warning("unknown emotion %s" % name)
		return
	emotion = name
	var e: Array = EMOTIONS[name]
	if not flying:
		_anim.play(_clip(e[0]), BLEND)
	_eyes.set_shader_parameter("expression", e[1])
	_eyes_serial += 1
	var from := _eye_color
	_eye_color = e[2]
	create_tween().tween_method(_set_eye_color, from, _eye_color, 0.25)
	if suit_follows_emotion:
		set_suit_color(e[3])
	boing(0.12)
	emotion_changed.emit(name)


func _set_eye_color(c: Color) -> void:
	_eyes.set_shader_parameter("eye_color", c)
	if _core_mat:
		_core_mat.emission = c
		_core_mat.albedo_color = c


func set_suit_color(color: Color) -> void:
	if _suit_mat:
		create_tween().tween_property(_suit_mat, "albedo_color", color, 0.4).set_trans(Tween.TRANS_SINE)


# ---------------------------------------------------------------- gear

func items() -> Array:
	return (meta["accessories"] as Dictionary).keys()


func info(name: String) -> Dictionary:
	return meta["accessories"][name]


func wear(name: String) -> void:
	var inf := info(name)
	var slot: String = inf["slot"]
	if worn.has(slot) and worn[slot] != name:
		take_off(worn[slot])
	var node := _item(name)
	node.visible = true
	worn[slot] = name
	var home: Vector3 = node.get_meta("home")
	if inf.get("kind", "") == "tool":
		# the gauntlet forms around the hand: grow + twist in (about its own centre), a flash,
		# and the arm comes up to aim
		var side: String = inf["side"]
		node.position = home
		var grow: Node3D = node.get_meta("grow")
		grow.scale = Vector3.ONE * 0.05
		grow.rotation = Vector3(0, TAU, 0)
		var t := create_tween().set_parallel(true)
		t.tween_property(grow, "scale", Vector3.ONE, 0.45).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
		t.tween_property(grow, "rotation", Vector3.ZERO, 0.45).set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
		t.tween_property(self, _aim_prop(side), 1.0, 0.4).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
		FX.explosion(_world(), (_sockets[side] as Node3D).global_position)
	else:
		# drop into place from a little above, with a bounce
		node.position = home + Vector3(0.0, 0.35, 0.0)
		create_tween().tween_property(node, "position", home, 0.35).set_trans(Tween.TRANS_BOUNCE).set_ease(Tween.EASE_OUT)
	if name == "jetpack":
		_start_jets()
	boing(0.1)
	outfit_changed.emit(worn)


func take_off(name: String) -> void:
	var inf := info(name)
	if _items.has(name):
		_items[name].visible = false
	if worn.get(inf["slot"], "") == name:
		worn.erase(inf["slot"])
	if inf.get("kind", "") == "tool":
		var side: String = inf["side"]
		_stop_tool_effects(side)
		create_tween().tween_property(self, _aim_prop(side), 0.0, 0.35).set_trans(Tween.TRANS_SINE)
	if name == "jetpack":
		if flying:
			toggle_fly()
		_stop_jets()
	outfit_changed.emit(worn)


func toggle(name: String) -> void:
	if worn.values().has(name):
		take_off(name)
	else:
		wear(name)


## Items are modelled on the rest pose in robot space; parenting one under the pivot it follows
## with the pivot's rest position subtracted keeps it in place while the pivot moves.
func _item(name: String) -> Node3D:
	if _items.has(name):
		return _items[name]
	var inf := info(name)
	var pivot := _node(inf["follows"])
	var rest: Array = meta["pivots_rest"][inf["follows"]]
	var holder := Node3D.new()
	holder.name = "Wear_%s" % name
	holder.position = -Vector3(rest[0], rest[1], rest[2])
	holder.set_meta("home", holder.position)
	var model := (load(ACCESSORY_DIR % name) as PackedScene).instantiate() as Node3D
	var grow := Node3D.new()                     # pop / twist animations turn about the item's centre
	holder.add_child(grow)
	grow.add_child(model)
	pivot.add_child(holder)
	var centre := _local_centre(holder, model)
	grow.position = centre
	model.position = -centre
	holder.set_meta("grow", grow)
	_setup_materials(model)
	if inf.has("wave"):
		var cloth := model.find_child(inf["wave"], true, false) as MeshInstance3D
		var cape := ShaderMaterial.new()
		cape.shader = CAPE_SHADER
		cloth.material_override = cape
		holder.set_meta("cape", cape)
	_items[name] = holder
	return holder


func _local_centre(holder: Node3D, model: Node3D) -> Vector3:
	var box := AABB()
	var first := true
	var to_holder := holder.global_transform.affine_inverse()
	for n in model.find_children("*", "MeshInstance3D", true, false):
		var mi := n as MeshInstance3D
		var b := (to_holder * mi.global_transform) * mi.get_aabb()
		box = b if first else box.merge(b)
		first = false
	return box.get_center()


func _muzzle(side: String) -> Node3D:
	var name: String = worn.get("hand_" + side.to_lower(), "")
	if name == "":
		return null
	return _items[name].find_child(info(name)["muzzle"], true, false) as Node3D


## Where a shot leaves the hand: the worn tool's muzzle, or the bare hand.
func muzzle_position(side: String) -> Vector3:
	var m := _muzzle(side)
	return m.global_position if m else (_sockets[side] as Node3D).global_position


func _aim_dir(side: String, muzzle: Node3D) -> Vector3:
	return (muzzle.global_position - (_sockets[side] as Node3D).global_position).normalized()


## Where shots and flashes live: the 3D scene around the pet (not the pet, so they don't follow it).
func _world() -> Node3D:
	var p := get_parent()
	return p as Node3D if p is Node3D else self


func _beam_dir(muzzle: Node3D, tool := "laser") -> Vector3:
	return (to_global(BEAM_TARGET[tool]) - muzzle.global_position).normalized()


func use_tool(side: String) -> void:
	var name: String = worn.get("hand_" + side.to_lower(), "")
	if name == "":
		return
	var tool: String = info(name)["tool"]
	var muzzle := _muzzle(side)
	var world := _world()
	var dir := _aim_dir(side, muzzle)
	_busy = 3.0
	match tool:
		"cannon":
			FX.rocket(world, muzzle.global_position, dir)
			_recoil(side)
		"laser":
			FX.laser(world, muzzle.global_position, _beam_dir(muzzle))
			_recoil(side, 0.5)
		"flashlight":
			if _beams.has(side):
				_beams[side].queue_free()
				_beams.erase(side)
			else:
				_beams[side] = FX.flashlight(world)
		"shield":
			if _shields.has(side):
				var s: MeshInstance3D = _shields[side]
				_shields.erase(side)
				var t := s.create_tween()
				t.tween_property(s, "scale", Vector3.ONE * 0.02, 0.2)
				t.tween_callback(s.queue_free)
			else:
				_shields[side] = FX.shield(world)


func _recoil(side: String, strength := 1.0) -> void:
	boing(-0.12 * strength)
	var t := create_tween()
	t.tween_property(self, _aim_prop(side), 0.78, 0.06)
	t.tween_property(self, _aim_prop(side), 1.0, 0.25).set_trans(Tween.TRANS_ELASTIC).set_ease(Tween.EASE_OUT)


func _aim_prop(side: String) -> String:
	return "_aim_l" if side == "L" else "_aim_r"


func _stop_tool_effects(side: String) -> void:
	if _beams.has(side):
		_beams[side].queue_free()
		_beams.erase(side)
	if _shields.has(side):
		_shields[side].queue_free()
		_shields.erase(side)


func _start_jets() -> void:
	_stop_jets()
	for noz_name in info("jetpack")["nozzles"]:
		var noz := _items["jetpack"].find_child(noz_name, true, false) as Node3D
		var f := FX.flame_emitter(0.7)
		noz.add_child(f)
		f.emitting = true
		_jets.append(f)


func _stop_jets() -> void:
	for f in _jets:
		if is_instance_valid(f):
			f.queue_free()
	_jets.clear()


func toggle_fly() -> void:
	if not flying and worn.get("back", "") != "jetpack":
		wear("jetpack")
	flying = not flying
	_busy = 3.0
	if flying:
		_anim.play(_clip("Fly"), 0.4)
		create_tween().tween_property(self, "_lift", 0.45, 1.0).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_OUT)
		boing(-0.2)
	else:
		_anim.play(_clip(EMOTIONS[emotion][0]), 0.5)
		create_tween().tween_property(self, "_lift", 0.0, 0.9).set_trans(Tween.TRANS_BOUNCE).set_ease(Tween.EASE_OUT)
	for f in _jets:
		(f as CPUParticles3D).initial_velocity_max = 5.0 if flying else 3.2
		(f as CPUParticles3D).amount = 44 if flying else 28


# ---------------------------------------------------------------- scene helpers

## Show another eye expression (a name in EYES) for a while (0 = until the next emotion), without
## changing the emotion.
func set_eyes(name: String, dur := 0.0) -> void:
	_eyes.set_shader_parameter("expression", EYES.get(name, 0))
	_eyes_serial += 1
	if dur > 0.0:
		var serial := _eyes_serial
		get_tree().create_timer(dur).timeout.connect(func():
			if serial == _eyes_serial:              # a newer set_eyes() / emotion wins
				_eyes.set_shader_parameter("expression", EMOTIONS[emotion][1]))


## Back to the eyes of the current emotion (after set_eyes() without a duration).
func reset_eyes() -> void:
	_eyes.set_shader_parameter("expression", EMOTIONS[emotion][1])
	_eyes_serial += 1


## A touch lands on the pet: a bounce that fits where it was touched. The emotion is the app's to choose.
func bump(zone := "") -> void:
	_busy = 2.0
	match zone:
		"forehead":                             # pressed down on the head: the head spring dips
			boing(-0.18)
			_head_off_v -= 1.4
		"left_cheek", "right_cheek":
			var side := -1.0 if zone == "left_cheek" else 1.0
			boing(-0.1)
			var t := create_tween()
			t.tween_property(self, "_head_roll", 14.0 * side, 0.12).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
			t.tween_property(self, "_head_roll", 0.0, 0.45).set_trans(Tween.TRANS_ELASTIC).set_ease(Tween.EASE_OUT)
		"chin":
			boing(0.12)
			var t := create_tween()
			t.tween_property(self, "_hop", 0.1, 0.12).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_OUT)
			t.tween_property(self, "_hop", 0.0, 0.35).set_trans(Tween.TRANS_BOUNCE).set_ease(Tween.EASE_OUT)
		_:
			boing(-0.2)


func hop(height := 0.25, dur := 0.6) -> void:
	boing(-0.22)
	var t := create_tween()
	t.tween_property(self, "_hop", height, dur * 0.35).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_OUT)
	t.tween_property(self, "_hop", 0.0, dur * 0.65).set_trans(Tween.TRANS_BOUNCE).set_ease(Tween.EASE_OUT)


## Lean the whole body sideways (degrees, stays until the next lean).
func lean(deg: float, dur := 0.4) -> void:
	create_tween().tween_property(self, "_lean", deg_to_rad(deg), dur).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)


## Tilt the head (degrees, stays until the next tilt).
func tilt_head(deg: float, dur := 0.3) -> void:
	create_tween().tween_property(self, "_head_roll", deg, dur).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)


func set_fly(on: bool) -> void:
	if on != flying:
		toggle_fly()


## Raise an arm up and out towards a hologram panel (or put it down again). A tool in that hand wins.
func present(side: String, on := true) -> void:
	var prop := "_present_" + side.to_lower()
	if on:
		boing(-0.1)
	create_tween().tween_property(self, prop, 1.0 if on else 0.0, 0.45 if on else 0.35) \
		.set_trans(Tween.TRANS_BACK if on else Tween.TRANS_SINE).set_ease(Tween.EASE_OUT)


func head_node() -> Node3D:
	return _node("PR_HeadRoot")


## World position just above the head (where emotes and the speech bubble go).
func head_top() -> Vector3:
	var hr := head_node()
	return hr.global_position + hr.global_basis.y.normalized() * 0.62


## The middle of the screen glass, in the head's local space.
func screen_front_local() -> Vector3:
	var c: Array = meta["screen"]["center"]
	var h: Array = meta["pivots_rest"]["PR_HeadRoot"]
	return Vector3(c[0] - h[0], c[1] - h[1], c[2] - h[2] + 0.1)


# ---------------------------------------------------------------- bouncy & cheeky

## Squash (negative) or stretch (positive) kick; the spring rings back with an overshoot.
func boing(amount: float) -> void:
	_squash_v += amount * 14.0


func look(gaze: Vector2) -> void:
	_gaze = gaze.clamp(Vector2(-1, -1), Vector2(1, 1))


func poke() -> void:
	if _poke_left <= 0.0:
		_emotion_before_poke = emotion
	_poke_left = 1.4
	_busy = 3.0
	set_emotion("surprised")
	boing(-0.3)
	var t := create_tween()
	t.tween_property(self, "_hop", 0.22, 0.15).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_OUT)
	t.tween_property(self, "_hop", 0.0, 0.45).set_trans(Tween.TRANS_BOUNCE).set_ease(Tween.EASE_OUT)


func fidget(name := "") -> void:
	if name == "":
		name = FIDGETS[randi() % FIDGETS.size()]
	_busy = 2.0
	var t := create_tween()
	match name:
		"hop":
			boing(-0.25)
			t.tween_interval(0.08)
			t.tween_property(self, "_hop", 0.3, 0.2).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_OUT)
			t.tween_property(self, "_hop", 0.0, 0.5).set_trans(Tween.TRANS_BOUNCE).set_ease(Tween.EASE_OUT)
		"spin":
			boing(-0.15)
			t.tween_property(self, "_spin", TAU, 0.75).from(0.0).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_IN_OUT)
			t.tween_callback(func(): _spin = 0.0)
		"tilt":
			var side := 1.0 if randf() < 0.5 else -1.0
			t.tween_property(self, "_head_roll", 16.0 * side, 0.25).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
			t.tween_interval(0.9)
			t.tween_property(self, "_head_roll", 0.0, 0.35).set_trans(Tween.TRANS_ELASTIC).set_ease(Tween.EASE_OUT)
		"wiggle":
			for k in 4:
				t.tween_property(self, "_lean", 0.14 if k % 2 == 0 else -0.14, 0.12).set_trans(Tween.TRANS_SINE)
			t.tween_property(self, "_lean", 0.0, 0.2).set_trans(Tween.TRANS_ELASTIC).set_ease(Tween.EASE_OUT)
		"peek":
			var side := 1.0 if randf() < 0.5 else -1.0
			look(Vector2(side, 0.2))
			t.tween_property(self, "_lean", -0.22 * side, 0.3).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
			t.tween_interval(0.8)
			t.tween_property(self, "_lean", 0.0, 0.4).set_trans(Tween.TRANS_ELASTIC).set_ease(Tween.EASE_OUT)
		"nod":
			# yes, got it: two nods (not one of the idle FIDGETS -- the app asks for it)
			boing(-0.06)
			for k in 2:
				t.tween_property(self, "_head_nod", 15.0, 0.13).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_OUT)
				t.tween_property(self, "_head_nod", -3.0, 0.17).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)
			t.tween_property(self, "_head_nod", 0.0, 0.3).set_trans(Tween.TRANS_ELASTIC).set_ease(Tween.EASE_OUT)
		"cheeky":
			# a quick wink, a head tilt and a tiny hop -- then straight back to business
			_eyes.set_shader_parameter("expression", 3)
			boing(-0.12)
			t.set_parallel(true)
			t.tween_property(self, "_head_roll", -12.0, 0.2).set_trans(Tween.TRANS_BACK)
			t.tween_property(self, "_hop", 0.12, 0.15)
			t.chain().tween_property(self, "_hop", 0.0, 0.35).set_trans(Tween.TRANS_BOUNCE).set_ease(Tween.EASE_OUT)
			t.chain().tween_interval(0.35)
			t.chain().tween_property(self, "_head_roll", 0.0, 0.3).set_trans(Tween.TRANS_ELASTIC).set_ease(Tween.EASE_OUT)
			t.chain().tween_callback(func(): _eyes.set_shader_parameter("expression", EMOTIONS[emotion][1]))


func _spring(x: float, v: float, target: float, k: float, damp: float, dt: float) -> Vector2:
	v += ((target - x) * k - v * damp) * dt
	return Vector2(x + v * dt, v)


func _process(delta: float) -> void:
	_t += delta
	_anim.advance(delta)

	# body float velocity drives squash & stretch and the springs of the head and antennas
	var fy := _float.position.y
	var vy := (fy - _prev_float_y) / maxf(delta, 1e-4)
	_prev_float_y = fy
	var s := _spring(_squash, _squash_v, clampf(vy * 0.22, -0.1, 0.12), 170.0, 11.0, delta)
	_squash = s.x
	_squash_v = s.y
	_float.scale = Vector3(1.0 - _squash * 0.5, 1.0 + _squash, 1.0 - _squash * 0.5)
	var h := _spring(_head_off, _head_off_v, -vy * 0.06, 90.0, 8.0, delta)
	_head_off = h.x
	_head_off_v = h.y
	_head.position.y += _head_off
	_head.rotate_object_local(Vector3(0, 0, 1), deg_to_rad(_head_roll))
	_head.rotate_object_local(Vector3(1, 0, 0), deg_to_rad(_head_nod))
	var turn := (_gaze.x - _gaze_now.x) * 3.0
	var a := _spring(_ant, _ant_v, clampf(-vy * 0.9 + turn, -0.6, 0.6), 130.0, 5.0, delta)
	_ant = a.x
	_ant_v = a.y
	_antennas["L"].rotate_object_local(Vector3(0, 0, 1), _ant)
	_antennas["R"].rotate_object_local(Vector3(0, 0, 1), -_ant)

	# arms raised to aim while a tool is on, or to present a panel
	for side in ["L", "R"]:
		var w: float = get(_aim_prop(side))
		var name: String = worn.get("hand_" + side.to_lower(), "")
		var shown: float = get("_present_" + side.to_lower())
		var pose: Array = [0.0, 0.0]                 # (a tool being put away lowers the arm to rest)
		if name != "":
			pose = TOOL_AIM.get(info(name)["tool"], [60.0, -45.0])
		elif shown > w:
			w = shown
			pose = [PRESENT_POSE[0] + sin(_t * 2.6) * 5.0, PRESENT_POSE[1]]
		if w > 0.001:
			var sgn := -1.0 if side == "L" else 1.0
			# swing out to the side first, then forward (the other order barely moves a forward arm out)
			var aim := Quaternion(Vector3(1, 0, 0), deg_to_rad(pose[1])) * Quaternion(Vector3(0, 0, 1), sgn * deg_to_rad(pose[0]))
			var arm: Node3D = _arms[side]
			arm.quaternion = arm.quaternion.slerp(aim, clampf(w, 0.0, 1.2))

	# whole-body moves: gaze turn, spin, lean, hop, jetpack lift, and the phone's tilt (a wobbly spring)
	_gaze_now = _gaze_now.lerp(_gaze, 1.0 - exp(-delta * 6.0))
	var sw := _spring(_sway_now, _sway_v, sway, 60.0, 5.5, delta)
	_sway_now = sw.x
	_sway_v = sw.y
	var body := Basis.from_euler(Vector3(-_gaze_now.y * 0.12 + body_pitch, _gaze_now.x * MAX_YAW + _spin, _lean + body_roll + _sway_now))
	var centre := Vector3(0.0, BODY_CENTER, 0.0)
	_robot.transform = Transform3D(body, Vector3(0.0, _hop + _lift, 0.0) + centre - body * centre)
	_eyes.set_shader_parameter("gaze", _gaze_now)

	_blink_timer -= delta
	if _blink_timer <= 0.0:
		_blink_timer = randf_range(2.5, 5.0)
		var bt := create_tween()
		bt.tween_property(self, "_blink", 1.0, 0.07)
		bt.tween_property(self, "_blink", 0.0, 0.1)
	_eyes.set_shader_parameter("blink", _blink if blink_override < 0.0 else blink_override)
	# the mouth: the app's voice level while it speaks (a talking rhythm when no level comes), the
	# "talk" emotion's rhythm otherwise
	_voice_now = lerpf(_voice_now, voice_level if speaking else 0.0, 1.0 - exp(-delta * 18.0))
	if mouth_override >= 0.0:
		_eyes.set_shader_parameter("mouth", mouth_override)
	elif speaking or emotion == "talk":
		_talk_phase += delta * 11.0
		var rhythm := 0.5 + 0.5 * sin(_talk_phase) * sin(_talk_phase * 0.37)
		var m := rhythm
		if speaking and voice_level > 0.01:
			m = clampf(_voice_now * 2.2, 0.12, 1.0) * (0.65 + 0.35 * rhythm)
		_eyes.set_shader_parameter("mouth", m)
	else:
		_eyes.set_shader_parameter("mouth", 0.0)

	# the shadow stays on the ground under the pet wherever a scene moves it (and flat when it flips);
	# when a scene sinks the pet below its usual height the ground goes down with it
	var ground := minf(GROUND_Y, global_position.y + GROUND_Y)
	var height := global_position.y + fy + _hop + _lift - (ground - GROUND_Y)
	var k := clampf(1.0 - height * 1.3, 0.3, 1.1)
	_shadow.global_transform = Transform3D(Basis().scaled(Vector3(k, 1.0, k)), Vector3(global_position.x, ground, global_position.z))
	(_shadow.material_override as ShaderMaterial).set_shader_parameter("strength", 0.38 * k)

	_update_gear(delta)

	if _poke_left > 0.0:
		_poke_left -= delta
		if _poke_left <= 0.0:
			set_emotion(_emotion_before_poke)
	_busy -= delta
	_fidget_timer -= delta
	if _fidget_timer <= 0.0:
		_fidget_timer = randf_range(5.0, 9.0)
		if _busy <= 0.0 and not flying and not scene_active and emotion in ["normal", "happy", "talk", "wink"]:
			fidget()


func _update_gear(delta: float) -> void:
	for side in _beams:
		var m := _muzzle(side)
		if m:
			FX.aim_beam(_beams[side], m.global_position, _beam_dir(m, "flashlight"))
	for side in _shields:
		# the energy shield stands in front of the hand, facing the way the arm points (the
		# generator's own muzzle faces out of the forearm, which would put it off a portrait screen)
		var hand := (_sockets[side] as Node3D).global_position
		var along := (hand - (_arms[side] as Node3D).global_position).normalized()
		FX.aim_shield(_shields[side], hand + along * 0.1, along)
	if _items.has("propeller_cap") and _items["propeller_cap"].visible:
		var spin := _items["propeller_cap"].find_child("ACC_propeller_cap_spin", true, false) as Node3D
		spin.rotate_object_local(Vector3.UP, delta * (22.0 if flying else 12.0))
	if _items.has("halo") and _items["halo"].visible:
		var ring := _items["halo"].find_child("ACC_halo_ring", true, false) as Node3D
		if not ring.has_meta("y0"):
			ring.set_meta("y0", ring.position.y)
		ring.position.y = ring.get_meta("y0") + sin(_t * 2.2) * 0.03
		ring.rotate_object_local(Vector3.UP, delta * 0.8)
	if _items.has("cape") and _items["cape"].visible:
		var cape: ShaderMaterial = _items["cape"].get_meta("cape")
		cape.set_shader_parameter("lift", _lift / 0.45)
