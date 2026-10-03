extends Node
## App mode: the pet inside the JARVIS app (Avatar mode). The app's Android plugin "JarvisAvatar" runs
## in this engine's process and relays the app's commands as JSON on its `command` signal; events go
## back with plugin.sendEvent(name, json). The app owns the mood (emotion, outfit, backdrop -- its pet
## brain reacts to sensors and the AI); this side owns the body: it shows what it is told, plays scenes
## and tool results, bounces when touched, leans with the phone's tilt and reports touches.
##
## Commands (scenes/SCHEMA.md, "App bridge"):
##   {"t":"state", "emotion":"happy", "eyes":"", "items":["crown"], "background":"black", "emote":"heart"}
##   {"t":"look", "x":0.2, "y":-0.1}              gaze -1..1 (the camera sees a face, or the brain looks around)
##   {"t":"voice", "speaking":true, "level":0.4}  the mouth
##   {"t":"react", "name":"shake"}                shake / heavy_shake / boat / thump / face_up / loud / poke
##   {"t":"tool", "tool":"device_weather", "result":{…}, "hold":true}   a tool's result on its scene
##                                                (director.play_tool); hold: it waits on its last picture
##   {"t":"release"}                              …until this: the AI finished talking about it
##   {"t":"scene", "id":"party"}                  a skit; {"t":"stop", "id":"party"} ends it if it still plays
##                                                (no id: whatever plays). The same id again with "data" while
##                                                it plays: new data for it (the "working" card), no restart
##   {"t":"hear", "level":0.4}                    the user's voice while they talk (the "listening" meter)
##   {"t":"tasks", "items":[{id, tool, args, question, state}]}   the jobs the user still waits to hear about
##                                                (running / ready / telling) as pills in the lower right corner
##   {"t":"say", "text":"…", "secs":3}            speech bubble
##   {"t":"effect", "name":"confetti"}            one effect (scene_player effect names)
##   {"t":"fidget", "name":"hop"}                 hop / spin / tilt / wiggle / peek / cheeky / nod ("" = any)
##   {"t":"eyelids", "open":0, "secs":1.5}        the screen's eyelids (the robot's sleep): 1 none … 0 shut;
##   {"t":"eyelids", "drowsy":30}                 …or heavy-eyed for 30 s (eyelids.gd). While they show, any tap
##                                                anywhere is a "tap" (zone "screen" off the pet): it wakes the robot
## Events: hello (attached: send everything), ready (warmed up), touch {kind, zone}, scene {id, state}, back
##
## Without the plugin (desktop) it reads the same commands from --app-sim=<file.json>:
## [{"at": seconds, "cmd": {…}}, …], and prints the events it would send.

const PLUGIN := "JarvisAvatar"
const EMOTES := preload("res://scripts/emotes.gd")
const TASK_HUD := preload("res://scripts/task_hud.gd")
const EYELIDS := preload("res://scripts/eyelids.gd")
const DRAG_PX := 28.0            # canvas px a finger moves before a touch is a drag
const STROKE_PX := 60.0          # a stroke down the head / up under the chin
const DOUBLE_TAP := 0.28         # seconds a tap waits for a second one (double tap = tickle)
const LONG_PRESS := 0.7
const EMOTE_EVERY := 2.4         # seconds between the symbols of a lasting emote (zzz while asleep …)
const LOOK_HOLD := 2.5           # seconds an app gaze holds before the pet looks around by itself
const TILT_LEAN := 0.55          # share of the phone's roll the body leans (the rest is the spring's wobble)

var stage: Node3D                # main.gd: set_background(), MAIN_BACKGROUND
var pet: Node3D
var director: Node
var camera: Camera3D
var plugin: Object = null
var tasks: CanvasLayer                 # task_hud.gd: the jobs the user waits to hear about
var eyelids: CanvasLayer               # eyelids.gd: the screen's eyelids while the robot falls asleep

var _state := {}                 # the app's last state
var _applied_eyes := ""
var _emote := ""
var _emote_left := 0.0
var _look := Vector2.ZERO
var _look_age := 99.0
var _touch := {}                 # the finger that is down: start, pos, t, zone, moved
var _tap_left := 0.0             # a tap waiting for its double
var _tap_zone := ""
var _tap_pos := Vector2.ZERO
var _sim: Array = []             # desktop simulation: [{at, cmd}]
var _sim_time := 0.0


func setup(st: Node3D, p: Node3D, d: Node, cam: Camera3D, sim_path := "") -> void:
	stage = st
	pet = p
	director = d
	camera = cam
	tasks = TASK_HUD.new()
	tasks.director = director
	st.add_child(tasks)
	eyelids = EYELIDS.new()
	st.add_child(eyelids)
	director.started.connect(func(id: String): _send("scene", {"id": id, "state": "started"}))
	director.finished.connect(_on_scene_finished)
	if Engine.has_singleton(PLUGIN):
		plugin = Engine.get_singleton(PLUGIN)
		plugin.connect("command", _on_command)
	elif sim_path != "":
		var list = JSON.parse_string(FileAccess.get_file_as_string(sim_path))
		if list is Array:
			_sim = list
			_sim.sort_custom(func(a, b): return float(a.get("at", 0.0)) < float(b.get("at", 0.0)))
	_send("hello")


## After the warm-up: the first scene or reaction won't hitch any more.
func warmed_up() -> void:
	_send("ready")


static func wanted() -> bool:
	return Engine.has_singleton(PLUGIN)


var last_event := []                   # [name, data] of the last event sent (tests)


func _send(name: String, data := {}) -> void:
	last_event = [name, data]
	var json := JSON.stringify(data)
	if plugin:
		plugin.call("sendEvent", name, json)
	else:
		print("EVENT %s %s" % [name, json])


func _on_command(json: String) -> void:
	var c = JSON.parse_string(json)
	if c is Dictionary:
		handle(c)


func handle(c: Dictionary) -> void:
	match str(c.get("t", "")):
		"state":
			_state = c
			if director.playing == "":         # a scene owns the pet; its end puts this state back
				_apply_state()
		"look":
			_look = Vector2(float(c.get("x", 0.0)), float(c.get("y", 0.0)))
			_look_age = 0.0
		"voice":
			pet.speaking = bool(c.get("speaking", false))
			pet.voice_level = clampf(float(c.get("level", 0.0)), 0.0, 1.0)
		"react":
			_react(str(c.get("name", "")))
		"tool":
			var extra = c.get("extra", {})
			director.play_tool(str(c.get("tool", "")), c.get("result"), extra if extra is Dictionary else {},
				bool(c.get("hold", false)))
		"release":
			director.release()
		"scene":
			var id := str(c.get("id", ""))
			var data = c.get("data", {})
			if not data is Dictionary:
				data = {}
			if id != "" and director.playing == id and not data.is_empty():
				director.update(data)
			else:
				director.play(id, data)
		"hear":
			director.hear(float(c.get("level", 0.0)))
		"tasks":
			var items = c.get("items", [])
			tasks.set_tasks(items if items is Array else [])
		"stop":                                # a tool's scene that took over from the skit keeps playing
			var id := str(c.get("id", ""))
			if id == "" or director.playing == id:
				director.stop()
		"say":
			director.say(str(c.get("text", "")), float(c.get("secs", 3.0)), str(c.get("at", "auto")))
		"effect":
			director.effect(str(c.get("name", "")), c)
		"fidget":
			if not pet.scene_active:
				pet.fidget(str(c.get("name", "")))
		"eyelids":
			if c.has("drowsy"):
				eyelids.drowsy(float(c.get("drowsy", 30.0)))
			else:
				eyelids.set_open(float(c.get("open", 1.0)), float(c.get("secs", 0.6)))
		_:
			push_warning("app command not understood: %s" % [c])


# ---------------------------------------------------------------- state

func _apply_state() -> void:
	var s := _state
	var emotion := str(s.get("emotion", "normal"))
	if not pet.EMOTIONS.has(emotion):
		emotion = "normal"
	var changed: bool = emotion != pet.emotion
	if changed:
		pet.set_emotion(emotion)
	var eyes := str(s.get("eyes", ""))
	if not pet.EYES.has(eyes):
		eyes = ""
	if eyes != _applied_eyes or (changed and eyes != ""):
		if eyes == "":
			pet.reset_eyes()
		else:
			pet.set_eyes(eyes)
		_applied_eyes = eyes
	# outfit: what the state lists, nothing else (one item per slot -- the later one wins)
	var want: Array = s.get("items", []) if s.get("items") is Array else []
	var known: Dictionary = pet.meta["accessories"]
	for slot in pet.worn.keys():
		var item: String = pet.worn[slot]
		if not want.has(item):
			pet.take_off(item)
	for item in want:
		if known.has(item) and not pet.worn.values().has(item):
			pet.wear(item)
	var bg := str(s.get("background", ""))
	stage.set_background(bg if stage.BACKGROUNDS.has(bg) else stage.MAIN_BACKGROUND)
	var emote := str(s.get("emote", ""))
	if emote != _emote:
		_emote = emote
		_emote_left = 0.3


func _on_scene_finished(id: String) -> void:
	_send("scene", {"id": id, "state": "finished"})
	# the scene put the pet back as it was before it; the app's state may have moved on meanwhile
	_applied_eyes = ""
	# a scene that picks up where this one left the pet ("keep_pose": getting up from sleep) keeps its stage too
	if not _state.is_empty() and not director.continuing:
		_apply_state()


func _tick_emote(delta: float) -> void:
	if _emote == "" or director.playing != "":
		return
	_emote_left -= delta
	if _emote_left > 0.0:
		return
	_emote_left = EMOTE_EVERY
	match _emote:
		"dizzy":
			EMOTES.dizzy_stars(pet.head_node(), Vector3(0, 0.55, 0), EMOTE_EVERY + 0.2)
		"battery_low":
			EMOTES.battery(pet, Vector3(0, 2.55, 0), 0.05, 0.05, 1.6, 0.4)
		"rain_cloud":
			EMOTES.rain_cloud(pet, Vector3(0, 2.75, 0), EMOTE_EVERY + 0.3)
		"zzz":
			director.emote("zzz", 2, 2.0, 1.0)
		_:
			if EMOTES.SYMBOLS.has(_emote):
				director.emote(_emote, 2, 1.4, 0.9)


# ---------------------------------------------------------------- reactions

func _react(name: String) -> void:
	match name:
		"shake":                                   # shaken: a wobble and stars
			pet.boing(-0.25)
			_wobble(0.2, 5, 0.09)
			director.effect("dizzy_stars", {"dur": 2.4})
		"heavy_shake":                             # shaken hard: the whole screen rattles
			pet.boing(-0.35)
			_wobble(0.32, 7, 0.07)
			director.shake(0.12, 0.6)
			director.emote("anger", 1, 1.2, 1.2)
		"boat":                                    # rocked like a boat: slow, seasick swaying
			_wobble(0.26, 4, 0.45)
			director.emote("sweat", 1, 1.6)
		"thump":                                   # a thump on the table: it jumps
			pet.hop(0.35, 0.55)
			director.shake(0.08, 0.35)
			director.emote("exclaim", 1, 1.0, 1.2)
		"face_up":
			pet.hop(0.18, 0.5)
		"loud":
			pet.boing(-0.25)
			director.emote("exclaim", 1, 0.9)
		"poke":
			pet.bump("")
		_:
			push_warning("app reaction not understood: %s" % name)


## Rock the body side to side `count` times, `deg`-ish radians, `step` seconds per swing, then settle.
func _wobble(amount: float, count: int, step: float) -> void:
	var t := pet.create_tween()
	for k in count:
		var side := 1.0 if k % 2 == 0 else -1.0
		t.tween_property(pet, "_lean", amount * side * (1.0 - float(k) / float(count + 1)), step).set_trans(Tween.TRANS_SINE)
	t.tween_property(pet, "_lean", 0.0, 0.35).set_trans(Tween.TRANS_ELASTIC).set_ease(Tween.EASE_OUT)


# ---------------------------------------------------------------- touch

func input(event: InputEvent) -> void:
	if event is InputEventScreenTouch:
		if event.index != 0:
			return
		if event.pressed:
			var zone := zone_at(event.position)
			_touch = {"start": event.position, "pos": event.position, "t": _now(), "zone": zone, "moved": false}
			if zone != "none":
				pet.boing(-0.08)                   # the finger lands: a tiny squash right away
		elif not _touch.is_empty():
			_release(event.position)
			_touch = {}
	elif event is InputEventScreenDrag:
		if event.index != 0 or _touch.is_empty():
			return
		_touch["pos"] = event.position
		if (event.position - (_touch["start"] as Vector2)).length() > DRAG_PX:
			_touch["moved"] = true
		_look_at_finger(event.position)


func _release(pos: Vector2) -> void:
	var zone: String = _touch["zone"]
	var d: Vector2 = pos - (_touch["start"] as Vector2)
	if eyelids and eyelids.open_amount() < 0.98:      # eyes heavy or shut: any touch anywhere wakes it (the app's call)
		_tap_left = 0.0
		_send("touch", {"kind": "tap", "zone": "screen" if zone == "none" else zone})
		return
	if _touch["moved"]:
		var kind := "drag"
		if zone == "forehead" and d.y > STROKE_PX:
			kind = "stroke"                        # stroked down the head
			pet.fidget("tilt")
		elif zone == "chin" and d.y < -STROKE_PX:
			kind = "scratch"                       # scratched up under the chin
			pet.fidget("wiggle")
		_send("touch", {"kind": kind, "zone": zone})
		return
	if zone == "none":
		return
	if _now() - float(_touch["t"]) > LONG_PRESS:
		_send("touch", {"kind": "hold", "zone": zone})
		return
	if _tap_left > 0.0 and _tap_pos.distance_to(pos) < 110.0:
		_tap_left = 0.0                            # the second tap: a double (tickle)
		pet.fidget("wiggle")
		_send("touch", {"kind": "double", "zone": zone})
		return
	if _tap_left > 0.0:                            # a tap elsewhere: the waiting one was a single tap
		_tap_left = 0.0
		_send("touch", {"kind": "tap", "zone": _tap_zone})
	pet.bump(zone)
	_tap_left = DOUBLE_TAP
	_tap_zone = zone
	_tap_pos = pos


## What a screen point touches: forehead / left_cheek / right_cheek / chin / face (the middle of the
## screen-face), body, or none. Left and right are the viewer's.
func zone_at(p: Vector2) -> String:
	var head: Node3D = pet.head_node()
	var face: Vector3 = head.global_transform * pet.screen_front_local()
	if camera.is_position_behind(face):
		return "none"
	var c := camera.unproject_position(face)
	var half: Array = pet.meta["screen"]["half_size"]
	var right := camera.unproject_position(face + head.global_basis.x.normalized() * float(half[0]))
	var up := camera.unproject_position(face + head.global_basis.y.normalized() * float(half[1]))
	var rx := maxf(absf(right.x - c.x), 1.0) * 1.3      # the head is a little wider than its screen
	var ry := maxf(absf(c.y - up.y), 1.0) * 1.45
	var n := Vector2((p.x - c.x) / rx, (p.y - c.y) / ry)
	if absf(n.x) <= 1.15 and n.y >= -1.25 and n.y <= 1.15:
		if n.y < -0.5:
			return "forehead"
		if n.y > 0.55:
			return "chin"
		if n.x < -0.5:
			return "left_cheek"
		if n.x > 0.5:
			return "right_cheek"
		return "face"
	var feet := camera.unproject_position(pet.global_position)
	if p.y > c.y and p.y < feet.y + ry * 0.3 and absf(p.x - c.x) < rx * 1.1:
		return "body"
	return "none"


func _look_at_finger(p: Vector2) -> void:
	var s := get_viewport().get_visible_rect().size
	pet.look(Vector2(p.x / s.x * 2.0 - 1.0, -(p.y / (s.y * 0.6) * 2.0 - 1.0)))
	_look_age = 0.0


func _now() -> float:
	return Time.get_ticks_msec() / 1000.0


# ---------------------------------------------------------------- per frame

func _process(delta: float) -> void:
	if not _sim.is_empty():
		_sim_time += delta
		while not _sim.is_empty() and float(_sim[0].get("at", 0.0)) <= _sim_time:
			var step: Dictionary = _sim.pop_front()
			if step.get("cmd") is Dictionary:
				handle(step["cmd"])
			elif step.has("touch"):                # {"touch": [x, y], "kind": tap/double} -> as a finger would
				_sim_touch(step)
	if _tap_left > 0.0:
		_tap_left -= delta
		if _tap_left <= 0.0:
			_send("touch", {"kind": "tap", "zone": _tap_zone})
	_tick_emote(delta)
	# gaze: the finger, then the app's gaze, then looking around by itself
	_look_age += delta
	if _touch.is_empty() and not pet.scene_active:
		if _look_age < LOOK_HOLD:
			pet.look(_look)
		else:
			var t := _now()
			pet.look(Vector2(sin(t * 0.37) * 0.6, sin(t * 0.23) * 0.3))
	# the phone's tilt: the body leans to stay upright in the room (with a wobble). Godot's gravity is
	# Android's negated: upright (0, -9.8, 0); turned clockwise by θ it is 9.8·(sin θ, -cos θ, 0), so
	# atan2(x, -y) = θ, and +θ about Z turns the body counter-clockwise on screen -- back to upright.
	var g := Input.get_gravity()
	if g.length() > 3.0 and absf(g.z) < 8.8:
		pet.sway = clampf(atan2(g.x, -g.y), -0.8, 0.8) * TILT_LEAN
	else:
		pet.sway = 0.0


func _sim_touch(step: Dictionary) -> void:
	var at: Array = step["touch"]
	var pos := Vector2(float(at[0]), float(at[1]))
	var times := 2 if str(step.get("kind", "tap")) == "double" else 1
	for i in times:
		var down := InputEventScreenTouch.new()
		down.position = pos
		down.pressed = true
		input(down)
		var up := InputEventScreenTouch.new()
		up.position = pos
		up.pressed = false
		input(up)
