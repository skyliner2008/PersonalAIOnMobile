extends Node
## Plays short scripted scenes (8-10 s skits) on the pet. A scene is data -- res://scenes/<id>.json,
## schema in scenes/SCHEMA.md -- so an AI brain can pick one by mood / tags or write a new one:
##   play(id, data)  play_json(text, data)  play_data(dict, data)  stop()  list() -> [{id, title, mood, tags, ...}]
##
## Beats run at their time `t`; movement is free in 3D (any position, 360 degree turns and flips,
## towards the camera and away from it). When the scene ends the pet goes home and gets its outfit,
## emotion and suit colour back.
##
## Info scenes take data (the app's weather / stock models): "{temp:0}" in any text is filled from it,
## a beat with "if": "rainy" only runs when that holds, and `panel` beats show hologram cards
## (weather, balance sheet, pie, bars, numbers, markdown) built by holo_panel.gd.

signal started(id: String)
signal finished(id: String)

const EMOTES := preload("res://scripts/emotes.gd")
const FX := preload("res://scripts/effects.gd")
const HOLO := preload("res://scripts/holo_panel.gd")
const SCENE_DIR := "res://scenes"
const BUBBLE_MAX := 440.0               # widest speech-bubble line (canvas px, the screen is 720 wide)
const MAX_HOLD := 60.0                  # seconds a held scene waits for release() before it goes on by itself
## where panels go (`"at"` in a panel beat); the pet steps back to the lower left to present them
## (PRESENTER_SPOT) and its speech bubble moves beside its head
const PANEL_AT := {
	"top": Vector3(0.0, 2.0, 1.1), "upper": Vector3(0.0, 1.75, 1.3), "middle": Vector3(0.0, 1.2, 1.5),
	"bottom": Vector3(0.0, 0.2, 1.8), "left": Vector3(-0.5, 1.4, 1.5), "right": Vector3(0.5, 1.4, 1.5),
	"bottom_right": Vector3(0.45, -0.05, 1.6),
}
const EASES := {
	"linear": Tween.TRANS_LINEAR, "sine": Tween.TRANS_SINE, "quad": Tween.TRANS_QUAD, "cubic": Tween.TRANS_CUBIC,
	"back": Tween.TRANS_BACK, "bounce": Tween.TRANS_BOUNCE, "elastic": Tween.TRANS_ELASTIC, "expo": Tween.TRANS_EXPO,
}

var pet: Node3D
var camera: Camera3D
var stage: Node                        # has set_background(name)
var scenes := {}                       # id -> Dictionary
var tool_scenes := {}                  # app tool name -> {scene, extra}; "*" = any other tool (scenes/tool_scenes.json)
var playing := ""

var _time := 0.0
var _duration := 0.0
var _hold_wanted := false              # play_tool(…, hold): wait at the scene's "hold" beat until release()
var _hold_left := 0.0                  # > 0: waiting there (the scene's clock is stopped)
var _beats: Array = []
var _next := 0
var _tweens: Array = []
var _restore_tweens: Array = []        # the walk home after a scene (cut short if a new one starts)
var _worn_before := {}
var _face_travel_left := 0.0
var _face_back := true                 # turn back to the camera when the travel ends
var _prev_pos := Vector3.ZERO
var _shake_left := 0.0
var _shake_amount := 0.0
var _cam_home := Vector3.ZERO
var _fov_home := 40.0
var _data := {}                        # the scene's data (after holo_panel.derive): fills "{...}" and "if"
var _panels := {}                      # panel id -> holo panel node
var _ref := RegEx.create_from_string("\\{([A-Za-z_][\\w.]*)(?::([\\w+]+))?\\}")
var _cmp := RegEx.create_from_string("^\\s*([A-Za-z_][\\w.]*)\\s*(==|!=|>=|<=|>|<| in )\\s*(.+?)\\s*$")

var _layer := CanvasLayer.new()
var _caption := Label.new()
var _bubble := PanelContainer.new()
var _bubble_text := Label.new()
var _bubble_left := 0.0
var _bubble_side := "top"
var _caption_left := 0.0


func setup(p: Node3D, cam: Camera3D, st: Node) -> void:
	pet = p
	camera = cam
	stage = st
	sync_camera_home()


## The stage changed the camera (window resized / rotated): scenes shake and zoom around this, and go back to it.
func sync_camera_home() -> void:
	if playing == "":
		_cam_home = camera.position
		_fov_home = camera.fov


func _ready() -> void:
	_load_scenes()
	_layer.layer = 2
	add_child(_layer)
	var font := SystemFont.new()
	font.font_names = PackedStringArray(["Leelawadee UI", "Tahoma", "Noto Sans Thai", "Segoe UI"])
	font.font_weight = 800
	# meme caption: big white text with a heavy black outline at the top of the screen
	_caption.set_anchors_preset(Control.PRESET_TOP_WIDE)
	_caption.offset_top = 40
	_caption.offset_left = 24
	_caption.offset_right = -24
	_caption.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_caption.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_caption.add_theme_font_override("font", font)
	_caption.add_theme_font_size_override("font_size", 52)
	_caption.add_theme_color_override("font_color", Color.WHITE)
	_caption.add_theme_color_override("font_outline_color", Color.BLACK)
	_caption.add_theme_constant_override("outline_size", 18)
	_caption.visible = false
	_layer.add_child(_caption)
	# speech bubble that follows the pet's head
	var style := StyleBoxFlat.new()
	style.bg_color = Color(1, 1, 1, 0.96)
	style.set_corner_radius_all(26)
	style.content_margin_left = 22
	style.content_margin_right = 22
	style.content_margin_top = 10
	style.content_margin_bottom = 12
	style.shadow_color = Color(0, 0, 0, 0.25)
	style.shadow_size = 6
	_bubble.add_theme_stylebox_override("panel", style)
	_bubble_text.add_theme_font_override("font", font)
	_bubble_text.add_theme_font_size_override("font_size", 34)
	_bubble_text.add_theme_color_override("font_color", Color(0.1, 0.12, 0.18))
	_bubble.add_child(_bubble_text)
	_bubble.visible = false
	_layer.add_child(_bubble)


func _load_scenes() -> void:
	var dir := DirAccess.open(SCENE_DIR)
	if dir == null:
		return
	for f in dir.get_files():
		if f.ends_with(".json"):
			var data = JSON.parse_string(FileAccess.get_file_as_string(SCENE_DIR + "/" + f))
			if data is Dictionary and data.has("id"):
				scenes[data["id"]] = data
			elif f == "tool_scenes.json" and data is Dictionary:
				tool_scenes = data.get("tools", {})


## Show a tool's result with the scene made for that tool (tool_scenes.json), as the app's AI calls it:
## `result` is what the tool returned -- a map (JSON object), a list of rows, or plain text (which goes
## on a markdown card). `extra` is laid on top (e.g. {"advice": "…"} written by the AI). Returns the scene id.
## `hold`: the app's AI is about to talk about it -- the scene waits on its last picture (its "hold" beat)
## until release(), so it stays up as long as the AI talks.
func play_tool(tool: String, result, extra := {}, hold := false) -> String:
	var id := scene_for_tool(tool)
	play(id, tool_data(tool, result, extra))
	_hold_wanted = hold and playing == id
	return id


## The AI finished talking about this scene: go on from its "hold" beat to the end (or don't wait at the
## beat if it hasn't come yet).
func release() -> void:
	_hold_wanted = false
	_hold_left = 0.0


## Waiting at a "hold" beat for release().
func holding() -> bool:
	return _hold_left > 0.0


func scene_for_tool(tool: String) -> String:
	return tool_scenes.get(tool, tool_scenes.get("*", {})).get("scene", "answer_card")


## A tool's result as scene data: a map as it is, a list of rows as {"items": [...]}, text as a markdown card.
func tool_data(tool: String, result, extra := {}) -> Dictionary:
	var entry: Dictionary = tool_scenes.get(tool, tool_scenes.get("*", {}))
	var data := {}
	if result is Dictionary:
		data = result.duplicate(true)
	elif result is Array:
		data = {"items": result}
	else:
		data = {"markdown": str(result), "title": entry.get("title", "")}
	data.merge(entry.get("extra", {}), false)
	data.merge(extra, true)
	data["tool"] = tool
	return data


## The tools whose results a scene shows (the other way round from scene_for_tool).
func tools_for_scene(id: String) -> Array:
	var out := []
	for tool in tool_scenes:
		if tool != "*" and tool_scenes[tool].get("scene", "") == id:
			out.append(tool)
	return out


## The catalogue. `needs` names the data an info scene expects ("weather", "stock", "answer"), empty for skits.
func list() -> Array:
	var out := []
	for id in scenes:
		var s: Dictionary = scenes[id]
		out.append({"id": id, "title": s.get("title", id), "mood": s.get("mood", ""), "tags": s.get("tags", []),
			"description": s.get("description", ""), "duration": s.get("duration", 9.0), "needs": s.get("needs", "")})
	out.sort_custom(func(a, b): return a["id"] < b["id"])
	return out


## Seconds since the current scene started.
func elapsed() -> float:
	return _time


func play(id: String, data := {}) -> void:
	if scenes.has(id):
		play_data(scenes[id], data)


## `text` is a scene (it may carry its own "data"); `data` overrides that.
func play_json(text: String, data := {}) -> void:
	var scene = JSON.parse_string(text)
	if scene is Dictionary:
		play_data(scene, data)


func play_data(scene: Dictionary, data := {}) -> void:
	if playing != "":
		stop()
	_data = HOLO.derive(data if not data.is_empty() else scene.get("data", {}))
	var ready_scene := scene
	if not _restore_tweens.is_empty():   # a new scene cuts the walk home short: start from home
		for t in _restore_tweens:
			if is_instance_valid(t):
				t.kill()
		_restore_tweens.clear()
		pet.position = Vector3.ZERO
		pet.rotation = Vector3.ZERO
		pet.body_roll = 0.0
		pet.body_pitch = 0.0
		camera.fov = _fov_home
	playing = ready_scene.get("id", "custom")
	_duration = float(ready_scene.get("duration", 9.0))
	_beats = (ready_scene.get("beats", []) as Array).duplicate()
	_beats.sort_custom(func(a, b): return float(a.get("t", 0.0)) < float(b.get("t", 0.0)))
	_next = 0
	_time = 0.0
	_hold_wanted = false
	_hold_left = 0.0
	_worn_before = pet.worn.duplicate()
	pet.scene_active = true
	_prev_pos = pet.global_position
	started.emit(playing)


func stop() -> void:
	if playing == "":
		return
	var id := playing
	playing = ""
	_hold_wanted = false
	_hold_left = 0.0
	for t in _tweens:
		if is_instance_valid(t):
			t.kill()
	_tweens.clear()
	_caption.visible = false
	_bubble.visible = false
	_shake_left = 0.0
	camera.position = _cam_home
	for pid in _panels:
		if is_instance_valid(_panels[pid]):
			_panels[pid].close()
	_panels.clear()
	pet.present("L", false)
	pet.present("R", false)
	_restore_pet()
	finished.emit(id)


func _restore_pet() -> void:
	if pet.flying:
		pet.set_fly(false)
	for slot in pet.worn.keys():
		var item: String = pet.worn[slot]
		if _worn_before.get(slot, "") != item:
			pet.take_off(item)
	for slot in _worn_before:
		if pet.worn.get(slot, "") != _worn_before[slot]:
			pet.wear(_worn_before[slot])
	pet.suit_follows_emotion = true
	pet.set_emotion("normal")
	pet.look(Vector2.ZERO)
	pet.lean(0.0, 0.4)
	pet.tilt_head(0.0, 0.4)
	pet.body_roll = wrapf(pet.body_roll, -PI, PI)
	pet.body_pitch = wrapf(pet.body_pitch, -PI, PI)
	var r := pet.rotation
	r.y = wrapf(r.y, -PI, PI)                        # take the short way home after 360s
	r.x = wrapf(r.x, -PI, PI)
	r.z = wrapf(r.z, -PI, PI)
	pet.rotation = r
	var t := pet.create_tween().set_parallel(true)
	t.tween_property(pet, "body_roll", 0.0, 0.5).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	t.tween_property(pet, "body_pitch", 0.0, 0.5).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	t.tween_property(pet, "position", Vector3.ZERO, 0.6).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	t.tween_property(pet, "rotation", Vector3.ZERO, 0.6).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	t.tween_property(camera, "fov", _fov_home, 0.5)
	_restore_tweens = [t]
	pet.scene_active = false


func _process(delta: float) -> void:
	_update_overlays(delta)
	if playing == "":
		return
	if _hold_left > 0.0:                     # held: the scene's clock waits on its last picture
		_hold_left -= delta
		if _hold_left <= 0.0:
			release()
	else:
		_time += delta
		while _next < _beats.size() and float(_beats[_next].get("t", 0.0)) <= _time:
			var beat: Dictionary = _beats[_next]
			_next += 1
			if not beat.has("if") or _cond(beat["if"]):
				_run(_fill(beat))
			if playing == "":                # a beat may end the scene
				return
			if _hold_left > 0.0:             # a "hold" beat: the rest waits
				break
	# face the way we are going (orbit / move with "face": "travel"), then back to the camera
	var pos := pet.global_position
	if _face_travel_left > 0.0:
		_face_travel_left -= delta
		var v := pos - _prev_pos
		v.y = 0.0
		if v.length() > 0.002:
			var yaw := atan2(v.x, v.z)
			pet.rotation.y = lerp_angle(pet.rotation.y, yaw, 1.0 - exp(-delta * 10.0))
		if _face_travel_left <= 0.0 and _face_back:
			_face_camera()
	_prev_pos = pos
	if _time >= _duration:
		stop()


## Camera shake and the caption / bubble timers; they also run between scenes (the app's reactions and
## bubbles use them), the bubble follows the head.
func _update_overlays(delta: float) -> void:
	if _shake_left > 0.0:
		_shake_left -= delta
		var k := _shake_amount * clampf(_shake_left * 4.0, 0.0, 1.0)
		camera.position = _cam_home + Vector3(randf_range(-k, k), randf_range(-k, k), 0.0)
		if _shake_left <= 0.0:
			camera.position = _cam_home
	if _caption_left > 0.0:
		_caption_left -= delta
		if _caption_left <= 0.0:
			_caption.visible = false
	if _bubble_left > 0.0:
		_bubble_left -= delta
		var top: Vector3 = pet.head_top()
		if camera.is_position_behind(top):
			_bubble.visible = false
		else:
			var p := camera.unproject_position(top)
			var view := get_viewport().get_visible_rect().size
			var at := Vector2(p.x - _bubble.size.x * 0.5, p.y - _bubble.size.y - 24.0)       # above the head
			if _bubble_side == "right":                  # beside the head (panels take the space above)
				at = Vector2(p.x + 70.0, p.y + 30.0)
			elif _bubble_side == "left":
				at = Vector2(p.x - 70.0 - _bubble.size.x, p.y + 30.0)
			_bubble.position = Vector2(clampf(at.x, 8.0, view.x - _bubble.size.x - 8.0),
				clampf(at.y, 8.0, view.y - _bubble.size.y - 8.0))
		if _bubble_left <= 0.0:
			_bubble.visible = false


# ---------------------------------------------------------------- beats

func _tween() -> Tween:
	var t := create_tween()
	_tweens.append(t)
	return t


## `t` is a PropertyTweener or MethodTweener (both have set_trans / set_ease, but no shared typed base).
## Names: linear sine quad cubic back bounce elastic expo, optionally with _in / _out / _in_out
## ("quad_in" speeds up into a landing). Without a suffix back/bounce/elastic/expo ease out, the rest in-out.
func _ease(t, name: String) -> void:
	var base := name
	var dir := -1
	for suffix in [["_in_out", Tween.EASE_IN_OUT], ["_out", Tween.EASE_OUT], ["_in", Tween.EASE_IN]]:
		if name.ends_with(suffix[0]):
			base = name.trim_suffix(suffix[0])
			dir = suffix[1]
			break
	if dir < 0:
		dir = Tween.EASE_OUT if base in ["back", "bounce", "elastic", "expo"] else Tween.EASE_IN_OUT
	t.set_trans(EASES.get(base, Tween.TRANS_SINE))
	t.set_ease(dir)


## Turn to face the camera the short way (after 360s the pet may be at any multiple of a full turn).
func _face_camera(dur := 0.35) -> void:
	var y := pet.rotation.y
	_tween().tween_property(pet, "rotation:y", y - wrapf(y, -PI, PI), dur).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)


func _start_shake(amount: float, dur: float) -> void:
	_shake_left = dur
	_shake_amount = amount


func _face_travel(dur: float, b: Dictionary) -> void:
	_face_travel_left = dur
	_face_back = b.get("then", "camera") != "keep"


func _vec(a) -> Vector3:
	return Vector3(float(a[0]), float(a[1]), float(a[2]))


func _world() -> Node3D:
	var p := pet.get_parent()
	return p as Node3D if p is Node3D else pet


func _run(b: Dictionary) -> void:
	var dur := float(b.get("dur", 0.6))
	match b.get("do", ""):
		"caption":
			_caption.text = b.get("text", "")
			_caption.visible = true
			_caption.pivot_offset = _caption.size * 0.5
			_caption.scale = Vector2(0.6, 0.6)
			_tween().tween_property(_caption, "scale", Vector2.ONE, 0.25).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
			_caption_left = float(b.get("dur", 3.0))
		"say":                              # "at": top / right / left; auto = right while a panel is up
			say(str(b.get("text", "")), float(b.get("dur", 1.8)), str(b.get("at", "auto")))
		"emotion":
			pet.set_emotion(b.get("name", "normal"))
		"eyes":
			pet.set_eyes(b.get("name", "normal"), float(b.get("dur", 0.0)))
		"suit":
			var c: String = b.get("color", "auto")
			if c == "auto":
				pet.suit_follows_emotion = true
				pet.set_emotion(pet.emotion)
			else:
				pet.suit_follows_emotion = false
				pet.set_suit_color(Color(c))
		"move":
			var to := pet.position + _vec(b["by"]) if b.has("by") else _vec(b.get("to", [0.0, 0.0, 0.0]))
			_ease(_tween().tween_property(pet, "position", to, dur), b.get("ease", "sine"))
			if b.get("face", "") == "travel":
				_face_travel(dur, b)
			elif b.get("face", "") == "camera":
				_face_camera()
		"orbit":
			var c: Array = b.get("center", [0.0, 0.0])
			var r := float(b.get("radius", 1.2))
			var y := float(b.get("y", pet.position.y))
			var a0 := deg_to_rad(float(b.get("from", 0.0)))
			var a1 := deg_to_rad(float(b.get("to", 360.0)))
			var put := func(a: float): pet.position = Vector3(float(c[0]) + sin(a) * r, y, float(c[1]) + cos(a) * r)
			_ease(_tween().tween_method(put, a0, a1, dur), b.get("ease", "sine"))
			if b.get("face", "travel") == "travel":
				_face_travel(dur, b)
		"turn":
			var now := pet.rotation.y
			var target := now + deg_to_rad(float(b.get("by", 0.0)))
			if not b.has("by"):             # absolute yaw (0 = facing the camera), reached the short way
				target = now + wrapf(deg_to_rad(float(b.get("yaw", 0.0))) - now, -PI, PI)
			_ease(_tween().tween_property(pet, "rotation:y", target, dur), b.get("ease", "back"))
		"roll", "pitch":                   # flips turn about the body centre (pet.body_roll / body_pitch)
			var prop := "body_roll" if b["do"] == "roll" else "body_pitch"
			var now: float = pet.get(prop)
			var target := now + deg_to_rad(float(b.get("by", 0.0))) if b.has("by") else deg_to_rad(float(b.get("deg", 0.0)))
			_ease(_tween().tween_property(pet, prop, target, dur), b.get("ease", "sine"))
		"vibrate":
			var base := pet.position
			var amount := float(b.get("amount", 0.05))
			var shake := func(k: float): pet.position = base + Vector3(sin(k * 90.0) * amount * (1.0 - k), 0.0, 0.0)
			_tween().tween_method(shake, 0.0, 1.0, dur)
		"hop":
			pet.hop(float(b.get("height", 0.25)), dur)
		"squash":
			pet.boing(float(b.get("amount", -0.2)))
		"lean":
			pet.lean(float(b.get("deg", 12.0)), dur)
		"tilt_head":
			pet.tilt_head(float(b.get("deg", 15.0)), dur)
		"fidget":
			pet.fidget(b.get("name", ""))
		"look":
			pet.look(Vector2(float(b.get("x", 0.0)), float(b.get("y", 0.0))))
		"wear":
			pet.wear(b["item"])
		"take_off":
			pet.take_off(b["item"])
		"tool":
			pet.use_tool(b.get("side", "R"))
		"fly":
			pet.set_fly(bool(b.get("on", true)))
		"emote":
			var at: Vector3 = pet.head_top() if b.get("at", "head") == "head" else pet.global_position + Vector3(0, 1.0, 0)
			EMOTES.spawn(_world(), at, b.get("kind", "sparkle"), int(b.get("count", 3)), float(b.get("spread", 0.45)),
				float(b.get("rise", 0.7)), float(b.get("dur", 1.3)), float(b.get("size", 1.0)))
		"effect":
			_effect(b)
		"shake":
			_start_shake(float(b.get("amount", 0.06)), dur)
		"zoom":                             # fov as on a 9:16 screen (40 = normal), scaled to the real camera
			_ease(_tween().tween_property(camera, "fov", _fov_home * float(b.get("fov", 40.0)) / 40.0, dur), b.get("ease", "sine"))
		"background":
			if stage and stage.has_method("set_background"):
				stage.set_background(b.get("name", "black"))
		"panel":
			_open_panel(b)
		"panel_close":
			var pid := str(b.get("id", "all"))
			for key in _panels.keys():
				if pid == "all" or key == pid:
					if is_instance_valid(_panels[key]):
						_panels[key].close()
					_panels.erase(key)
		"present":                          # raise an arm towards a panel, like showing it off
			pet.present(b.get("side", "R"), bool(b.get("on", true)))
		"hold":                             # everything is shown: wait here while the app's AI talks
			if _hold_wanted:
				_hold_left = MAX_HOLD
		_:
			push_warning("scene beat not understood: %s" % [b])


## Speech bubble over the pet's head for `secs` (in a scene or on its own). `side`: top / right / left,
## auto = right while a panel is up. Long lines wrap instead of running off a portrait screen.
func say(text: String, secs := 1.8, side := "auto") -> void:
	_bubble_text.text = text
	var wide := _bubble_text.get_theme_font("font").get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, 34).x > BUBBLE_MAX
	_bubble_text.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART if wide else TextServer.AUTOWRAP_OFF
	_bubble_text.custom_minimum_size = Vector2(BUBBLE_MAX if wide else 0.0, 0.0)
	# wrap at the final width now: a label that was never laid out wraps at width 0 (a letter per line),
	# and the bubble would keep that tall cached minimum size
	_bubble_text.size = Vector2(BUBBLE_MAX if wide else 0.0, 0.0)
	_bubble_text.update_minimum_size()
	_bubble_side = side
	if _bubble_side == "auto":
		_bubble_side = "right" if not _panels.is_empty() else "top"
	_bubble.visible = true
	_bubble.reset_size()
	_bubble.pivot_offset = Vector2(0.0 if _bubble_side == "right" else _bubble.size.x * 0.5, _bubble.size.y)
	_bubble.scale = Vector2(0.3, 0.3)
	create_tween().tween_property(_bubble, "scale", Vector2.ONE, 0.25).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	_bubble_left = secs


## One effect from the `effect` beat (confetti, dizzy_stars, rain, rocket_at_viewer …) outside a scene.
func effect(name: String, params := {}) -> void:
	var b := params.duplicate()
	b["name"] = name
	_effect(b)


## Comic symbols over the head (heart, question, exclaim, zzz, note, sparkle, star, dots, bolt, sweat, anger).
func emote(kind: String, count := 3, dur := 1.3, size := 1.0) -> void:
	EMOTES.spawn(_world(), pet.head_top(), kind, count, 0.45, 0.7, dur, size)


## Camera shake outside a scene (a bump, a thump on the table).
func shake(amount := 0.06, dur := 0.4) -> void:
	_start_shake(amount, dur)


## A hologram panel: fields of the beat laid over the scene data; "beam": "L"/"R" projects it from that hand.
func _open_panel(b: Dictionary) -> void:
	var pid := str(b.get("id", "panel"))
	if _panels.has(pid) and is_instance_valid(_panels[pid]):
		_panels[pid].close()
	var p := _data.duplicate(true)
	p.merge(b, true)
	var panel := HOLO.new()
	panel.name = "Panel_%s" % pid
	_world().add_child(panel)
	var at = b.get("pos")
	panel.position = _vec(at) if at is Array else PANEL_AT.get(str(b.get("at", "top")), PANEL_AT["top"])
	panel.setup(str(b.get("type", "text")), p)
	panel.open(float(b.get("reveal", 0.55)))
	_panels[pid] = panel
	if b.has("beam"):
		_beam(pet.muzzle_position(str(b["beam"])), panel.position + Vector3(0, -panel.size.y * 0.5, 0))


## The projector beam from the hand to the bottom edge of a panel as it opens.
func _beam(from: Vector3, to: Vector3) -> void:
	var m := MeshInstance3D.new()
	var cyl := CylinderMesh.new()
	var length := from.distance_to(to)
	cyl.height = length
	cyl.top_radius = 0.7
	cyl.bottom_radius = 0.03
	cyl.cap_top = false
	cyl.cap_bottom = false
	m.mesh = cyl
	var mat := StandardMaterial3D.new()
	mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	mat.blend_mode = BaseMaterial3D.BLEND_MODE_ADD
	mat.cull_mode = BaseMaterial3D.CULL_DISABLED
	mat.albedo_color = Color(0.35, 0.85, 1.0, 0.22)
	m.material_override = mat
	_world().add_child(m)
	m.global_position = (from + to) * 0.5
	m.global_basis = FX.basis_along(to - from)
	var t := m.create_tween()
	t.tween_property(mat, "albedo_color:a", 0.0, 0.9).set_delay(0.35)
	t.tween_callback(m.queue_free)


# ---------------------------------------------------------------- data binding

## Fills "{path}" / "{path:format}" in every string of a beat from the scene data. A string that is just
## one reference keeps the value's own type (a number stays a number, a list stays a list).
func _fill(v):
	if v is String:
		var m := _ref.search(v)
		if m == null:
			return v
		if m.get_start() == 0 and m.get_end() == v.length() and m.get_string(2) == "":
			var whole = _lookup(m.get_string(1))
			return "" if whole == null else whole
		var out := ""
		var pos := 0
		for r in _ref.search_all(v):
			out += v.substr(pos, r.get_start() - pos) + _format(_lookup(r.get_string(1)), r.get_string(2))
			pos = r.get_end()
		return out + v.substr(pos)
	if v is Array:
		return v.map(_fill)
	if v is Dictionary:
		var d := {}
		for k in v:
			d[k] = v[k] if k == "if" else _fill(v[k])
		return d
	return v


func _lookup(path: String):
	if _data.has(path):                  # keys with dots in them ("MACD.macd", "Recommend.All")
		return _data[path]
	var cur = _data
	for key in path.split("."):
		if cur is Dictionary and cur.has(key):
			cur = cur[key]
		elif cur is Array and key.is_valid_int() and int(key) < cur.size():
			cur = cur[int(key)]
		else:
			return null
	return cur


## Formats: 0 / 1 / 2 (decimals), int, money ($619.0B), price ($4,085.20), group (4,085.20),
## pct (12.3%), pct+ (+12.3%), abs. Numbers inside text values ("4085.2000", "+0.31%") count as numbers.
func _format(v, fmt: String) -> String:
	if v == null:
		return "–"
	var n = HOLO.num(v)
	if n == null or (fmt == "" and not (v is float or v is int)):
		return str(v)
	var x := float(n)
	match fmt:
		"price":
			return str(_data.get("currencySymbol", "")) + HOLO.group(x)
		"group":
			return HOLO.group(x)
		"0", "int":
			return str(roundi(x))
		"1":
			return "%.1f" % x
		"2":
			return "%.2f" % x
		"money":
			return HOLO.money(x, str(_data.get("currencySymbol", "")))
		"pct":
			return HOLO.pct(x)
		"pct+":
			return HOLO.pct(x, true)
		"abs":
			return str(roundi(absf(x))) if absf(x - roundf(x)) < 1e-6 else "%.1f" % absf(x)
	return str(roundi(x)) if absf(x - roundf(x)) < 1e-6 else "%.1f" % x


## Conditions: "rainy", "!rainy", "temp >= 33", "kind == thunder", "kind in [rain, drizzle]",
## joined with " and " / " or " (and binds tighter). Plain data, never executed as code.
func _cond(expr) -> bool:
	if expr is bool:
		return expr
	for alt in str(expr).split(" or "):
		var all := true
		for part in alt.split(" and "):
			if not _cond_one(part.strip_edges()):
				all = false
				break
		if all:
			return true
	return false


func _cond_one(s: String) -> bool:
	if s.begins_with("!"):
		return not _cond_one(s.substr(1).strip_edges())
	var m := _cmp.search(s)
	if m == null:
		return _truthy(_lookup(s))
	var left = _lookup(m.get_string(1))
	var op := m.get_string(2).strip_edges()
	var raw := m.get_string(3).strip_edges()
	if op == "in":
		for item in raw.trim_prefix("[").trim_suffix("]").split(","):
			if str(left) == item.strip_edges().trim_prefix("'").trim_suffix("'").trim_prefix("\"").trim_suffix("\""):
				return true
		return false
	var right_text := raw.trim_prefix("'").trim_suffix("'").trim_prefix("\"").trim_suffix("\"")
	if left == null:
		return op == "!=" and right_text != "null"
	var left_num = HOLO.num(left) if not left is bool else null
	if left_num != null and right_text.is_valid_float():
		var a := float(left_num)
		var b := right_text.to_float()
		match op:
			"==": return is_equal_approx(a, b)
			"!=": return not is_equal_approx(a, b)
			">": return a > b
			"<": return a < b
			">=": return a >= b
			"<=": return a <= b
	var ls := str(left).to_lower() if not left is bool else ("true" if left else "false")
	match op:
		"==": return ls == right_text.to_lower()
		"!=": return ls != right_text.to_lower()
	return false


func _truthy(v) -> bool:
	if v == null:
		return false
	if v is bool:
		return v
	if v is float or v is int:
		return v != 0
	if v is String or v is Array or v is Dictionary:
		return not v.is_empty()
	return true


func _effect(b: Dictionary) -> void:
	var dur := float(b.get("dur", 2.0))
	match b.get("name", ""):
		"confetti":
			EMOTES.confetti(_world(), pet.global_position + Vector3(0, 1.4, 0.3))
		"bulb":                            # high enough that a speech bubble fits under it
			EMOTES.bulb(_world(), pet.head_top() + Vector3(0, 0.72, 0), float(b.get("dur", 1.6)))
		"snot_bubble":
			EMOTES.snot_bubble(pet.head_node(), pet.screen_front_local() + Vector3(0.12, -0.12, 0.08), dur)
		"big_heart":
			EMOTES.big_heart(_world(), pet.head_top(), camera.global_position + Vector3(0, -0.4, -1.2))
		"dizzy_stars":
			EMOTES.dizzy_stars(pet.head_node(), Vector3(0, 0.55, 0), dur)
		"rain_cloud":
			EMOTES.rain_cloud(pet, Vector3(0, 2.75, 0), dur)
		"battery":                         # from / to: fill 0..1, hold: seconds it stays after filling
			EMOTES.battery(pet, Vector3(0, 2.55, 0), float(b.get("from", 0.05)), float(b.get("to", b.get("from", 0.05))),
				dur, float(b.get("hold", 0.6)))
		"rain", "snow":                    # precipitation over the whole stage
			EMOTES.stage_weather(_world(), b["name"] == "snow", dur, float(b.get("amount", 1.0)))
		"flash":
			FX.explosion(_world(), pet.global_position + Vector3(0, 1.0, 0.8))
		"rocket_at_viewer":                # straight at the lens; bursts just in front of it (screen flash)
			var from: Vector3 = pet.muzzle_position(b.get("side", "R"))
			var target := camera.global_position - camera.global_basis.z * 1.1
			var speed := float(b.get("speed", 5.5))
			var flight := from.distance_to(target) / speed
			FX.rocket(_world(), from, target - from, false, speed, flight)
			pet.boing(-0.15)
			_tween().tween_callback(_start_shake.bind(0.14, 0.45)).set_delay(flight)
		_:
			push_warning("scene effect not understood: %s" % [b])
