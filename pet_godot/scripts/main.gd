extends Node3D
## Pet Robot Lab: the design table for the 3D pet. Builds the stage (camera, lights, environment,
## backdrop), the pet, and a tabbed panel to try emotions, headgear, body gear, hand tools,
## backgrounds and actions. The mouse (or a finger) is the pet's gaze; a click pokes it.
##
## Command line (after `--`), used to check the lab from scripts:
##   --shot=<file.png>            save a screenshot after --wait seconds and quit
##   --state=<emotion>,<item+item>,<background>[,<action+action>]
##        e.g. --state=happy,crown+cannon_R,night,fire_R      actions: fire_L / fire_R / fly /
##        fidget_<name> / poke   (run 0.6 s in, so the shot can catch the effect)
##   --wait=<seconds>             default 1.5
##   --hide-ui                    no button panel (clean shots)
##   --turn=<degrees>             turn the pet (e.g. 180 to check back gear)
##   --scene=<id>                 play a scene from scenes/<id>.json (with --quit-at-end: quit when it ends,
##                                e.g. under --write-movie to record it)
##   --frames=<dir> [--every=1]   while the scene plays, save a frame every N seconds (scene time) to <dir>
##   --data=<file.json>           data for an info scene (default: its tool's saved result in data_samples/)
##   --tool=<name> [--data=…]     show a tool's result the way the app will: director.play_tool(name, result)
##   --stop-at=<seconds>          stop the scene then (looping scenes such as listening never end by themselves)
##   --tab=<n>                    open the n-th tab of the panel (0 = อารมณ์ … 7 = ข้อมูล)
##   --app-sim=<file.json>        app mode on the desktop: the app's commands from a file (app_bridge.gd)
##   --sprites=<dir>              render the robot's faces for the app's overlay / drive screen and quit
##
## Inside the app (the Android plugin "JarvisAvatar" is there) the lab panel is off and app_bridge.gd
## takes the app's commands: emotion, gaze, voice, reactions, tool results, scenes.

const PetCharacter := preload("res://scripts/pet_character.gd")
const ScenePlayer := preload("res://scripts/scene_player.gd")
const AppBridge := preload("res://scripts/app_bridge.gd")
const InfoSources := preload("res://scripts/info_sources.gd")
const FX := preload("res://scripts/effects.gd")
const EMOTES := preload("res://scripts/emotes.gd")
const HOLO := preload("res://scripts/holo_panel.gd")
const BG_SHADER := preload("res://shaders/backgrounds.gdshader")
## saved tool results (what the app's tool returned), one file per tool name; answer_card.json for the text card
const SAMPLE_PATH := "res://data_samples/%s.json"
## the ข้อมูล tab: live tool results (same sources as the app) and samples
const INFO_TOOLS := {
	"device_weather": "อากาศกรุงเทพ", "trading_fundamental_analysis": "งบดุล MSFT", "trading_price": "ราคา BTC",
	"trading_technical_analysis": "เทคนิคอล BTC", "trading_top_gainers": "คริปโตขึ้นแรง", "trading_top_losers": "คริปโตลงแรง",
	"trading_fear_greed": "Fear & Greed", "trading_crypto_overview": "ภาพรวมคริปโต",
}

const BACKGROUNDS := {
	"black": [12, "ดำ (หลัก)"], "studio": [0, "สตูดิโอ"], "night": [1, "กลางคืน"], "sunny": [2, "แดดจ้า"],
	"cyber": [3, "ไซเบอร์"], "sakura": [4, "ซากุระ"], "space": [5, "อวกาศ"],
	"underwater": [6, "ใต้ทะเล"], "city": [7, "เมืองยามค่ำ"], "sunset": [8, "ทะเลยามเย็น"],
	"snow": [9, "หิมะ"], "candy": [10, "แคนดี้"], "hangar": [11, "ห้องแล็บ"],
}
const MAIN_BACKGROUND := "black"      # what the pet stands on when nothing else is asked for
const DARK_BACKGROUNDS := ["black", "night", "cyber", "space", "city", "underwater", "hangar"]
## horizontal fov that matches the 40° vertical view at 9:16 (2·atan(tan 20° · 9/16))
const PORTRAIT_HFOV := 23.14
const EMOTION_LABELS := {
	"normal": "ปกติ", "happy": "ดีใจ", "sad": "เศร้า", "wink": "ขยิบ/โบกมือ", "love": "รัก",
	"surprised": "ตกใจ", "sleepy": "ง่วง", "angry": "โกรธ", "talk": "พูด",
}
const ITEM_LABELS := {
	"crown": "มงกุฎ", "party_hat": "หมวกปาร์ตี้", "wizard_hat": "หมวกพ่อมด", "chef_hat": "หมวกเชฟ",
	"sunglasses": "แว่นดำ", "headphones": "หูฟัง", "backpack": "เป้",
	"propeller_cap": "หมวกใบพัด", "cat_ears": "หูแมว", "halo": "วงแหวนนางฟ้า", "devil_horns": "เขาปีศาจ",
	"viking_helmet": "หมวกไวกิ้ง", "astronaut_helmet": "หมวกอวกาศ", "fedora": "หมวกเฟโดร่า",
	"vr_headset": "แว่น VR", "jetpack": "เจ็ตแพ็ก", "cape": "ผ้าคลุมฮีโร่",
}
const TOOL_LABELS := {"cannon": "ปืนใหญ่จรวด", "laser": "ปืนเลเซอร์", "flashlight": "ไฟฉาย", "shield": "โล่พลังงาน"}

var pet: Node3D
var camera: Camera3D
var director: Node                   # plays the JSON scenes (scene_player.gd)
var info: Node                       # live weather / stock data for info scenes (info_sources.gd)
var app: Node = null                 # app_bridge.gd inside the app (or --app-sim), null in the lab
var _playlist: Array = []
var _bg := ColorRect.new()
var _bg_mat := ShaderMaterial.new()
var _env := Environment.new()
var _idle_time := 0.0
var _args := {}
var _perf_left := 2.0
var _frame_dir := ""
var _frame_every := 1.0
var _frame_next := 0.0
var _frame_k := 0


func _ready() -> void:
	_parse_args()
	_setup_environment()
	_setup_backdrop()
	_setup_camera_and_lights()
	pet = PetCharacter.new()
	add_child(pet)
	director = ScenePlayer.new()
	add_child(director)
	director.setup(pet, camera, self)
	director.finished.connect(_on_scene_finished)
	info = InfoSources.new()
	add_child(info)
	if AppBridge.wanted() or _args.has("app-sim"):
		app = AppBridge.new()
		app.name = "AppBridge"
		add_child(app)
	elif not _args.has("hide-ui"):
		_setup_ui()
	get_viewport().size_changed.connect(_on_resize)
	_on_resize()
	set_background(MAIN_BACKGROUND)
	if app:
		app.setup(self, pet, director, camera, _args.get("app-sim", ""))
	if _args.has("sprites"):
		_render_sprites(_args["sprites"])
		return
	if OS.has_feature("mobile") or _args.has("warmup"):
		_warm_up()
	elif app:
		app.warmed_up()
	if _args.has("state"):
		_apply_state(_args["state"])
	if _args.has("turn"):                # look at the pet from another side (checks)
		pet.rotation_degrees.y = float(_args["turn"])
	if _args.has("shot"):
		_take_shot_later(_args["shot"], float(_args.get("wait", "1.5")))
	if _args.has("frames"):
		_frame_dir = _args["frames"]
		_frame_every = float(_args.get("every", "1.0"))
		_frame_next = _frame_every * 0.5
		DirAccess.make_dir_recursive_absolute(_frame_dir)
	if _args.has("scene"):
		await get_tree().create_timer(0.4).timeout
		var data := _load_json(_args["data"]) if _args.has("data") else _sample_for(_args["scene"])
		director.play(_args["scene"], data)
	elif _args.has("tool"):
		await get_tree().create_timer(0.4).timeout
		var result = JSON.parse_string(FileAccess.get_file_as_string(_args["data"])) if _args.has("data") else _sample_result(_args["tool"])
		director.play_tool(_args["tool"], result)
	if _args.has("stop-at") and (_args.has("scene") or _args.has("tool")):
		await get_tree().create_timer(float(_args["stop-at"])).timeout
		director.stop()


func _load_json(path: String) -> Dictionary:
	var d = JSON.parse_string(FileAccess.get_file_as_string(path))
	return d if d is Dictionary else {}


## A saved tool result (a map, a list of rows or text), or null.
func _sample_result(tool: String):
	var path := SAMPLE_PATH % tool
	return JSON.parse_string(FileAccess.get_file_as_string(path)) if FileAccess.file_exists(path) else null


## Data for an info scene from a saved result of a tool it shows (as play_tool would pass it); {} for skits.
func _sample_for(id: String) -> Dictionary:
	for tool in director.tools_for_scene(id):
		var r = _sample_result(tool)
		if r != null:
			return director.tool_data(tool, r)
	var own = _sample_result(id)
	return own if own is Dictionary else {}


## Build every effect once at start, tiny and hidden behind the pet, so its first real use in a
## scene doesn't hitch on a phone (particles, lights, comic-symbol glyphs, panel meshes and text).
func _warm_up() -> void:
	var holder := Node3D.new()
	add_child(holder)
	holder.position = Vector3(0.0, 1.0, -1.2)
	holder.scale = Vector3.ONE * 0.01
	var at := holder.global_position
	FX.explosion(holder, at)
	FX.laser(holder, at, Vector3.UP)
	FX.rocket(holder, at, Vector3.UP, false, 0.1, 0.3)
	EMOTES.confetti(holder, at, 8)
	EMOTES.bulb(holder, at, 0.3)
	EMOTES.rain_cloud(holder, Vector3.ZERO, 5.0)          # freed with the holder before it pops
	EMOTES.stage_weather(holder, false, 0.6, 0.1)
	EMOTES.battery(holder, Vector3.ZERO, 0.1, 0.9, 0.3, 0.1)
	EMOTES.snot_bubble(holder, Vector3.ZERO, 5.0)
	EMOTES.dizzy_stars(holder, Vector3.ZERO, 5.0)
	EMOTES.big_heart(holder, at, at + Vector3(0.0, 0.0, 0.01))
	FX.flashlight(holder)
	FX.shield(holder)
	for kind in EMOTES.SYMBOLS:
		EMOTES.spawn(holder, at, kind, 1, 0.0, 0.0, 0.3, 1.0)
	var samples := {
		"weather": {"location": "กรุงเทพฯ", "temp": 30, "weather_code": 61, "is_day": 1, "max": 33, "min": 26},
		"balance": {"symbol": "X", "totalAssets": 10, "totalLiabilities": 4, "totalEquity": 6, "totalDebt": 2, "cashAndEquivalents": 1},
		"price": {"symbol": "X", "price": "100", "change": "1", "change_pct": "1%", "high_52w": "120", "low_52w": "80"},
		"gauge": {"value": 50, "zones": [{"to": 50, "color": "#FF5A6E", "label": "ต่ำ"}, {"to": 100, "color": "#3DDC97"}]},
		"pie": {"slices": [{"label": "ก", "value": 1}, {"label": "ข", "value": 2}]},
		"bars": {"bars": [{"label": "ก", "value": 1}, {"label": "ข", "value": -1}]},
		"kpi": {"items": [{"label": "ก", "value": "1", "good": true}]},
		"text": {"text": "# หัวข้อ\n- **ตัวหนา** *เอียง* `โค้ด` +1.2% -3.4%\n> อ้างอิง ภาษาไทย ๐๑๒๓"},
	}
	for kind in samples:
		var panel := HOLO.new()
		holder.add_child(panel)
		panel.setup(kind, HOLO.derive(samples[kind]))
		panel.open(0.2)
	# Pre-warm shaders for 0.3s so pipeline compilation completes quickly without freezing UI
	await get_tree().create_timer(0.3).timeout
	if app:
		app.warmed_up()
	# Keep holder briefly in background to let any remaining particle pipelines finish
	await get_tree().create_timer(0.7).timeout
	if is_instance_valid(holder):
		holder.queue_free()


func _parse_args() -> void:
	for a in OS.get_cmdline_user_args():
		var kv := a.trim_prefix("--").split("=", true, 1)
		_args[kv[0]] = kv[1] if kv.size() > 1 else ""


func _setup_environment() -> void:
	var sky := Sky.new()
	var sky_mat := ProceduralSkyMaterial.new()
	sky_mat.sky_top_color = Color(0.78, 0.82, 0.9)
	sky_mat.sky_horizon_color = Color(0.93, 0.94, 0.96)
	sky_mat.ground_horizon_color = Color(0.85, 0.86, 0.88)
	sky_mat.ground_bottom_color = Color(0.45, 0.47, 0.52)
	sky.sky_material = sky_mat
	_env.sky = sky
	_env.background_mode = Environment.BG_CANVAS        # the CanvasLayer backdrop shows behind 3D
	_env.background_canvas_max_layer = -1
	_env.ambient_light_source = Environment.AMBIENT_SOURCE_SKY
	_env.ambient_light_energy = 0.75
	_env.reflected_light_source = Environment.REFLECTION_SOURCE_SKY
	_env.tonemap_mode = Environment.TONE_MAPPER_AGX
	_env.tonemap_exposure = 1.0
	_env.glow_enabled = true
	_env.glow_intensity = 0.7
	_env.glow_hdr_threshold = 1.1
	_env.glow_bloom = 0.02
	var world := WorldEnvironment.new()
	world.environment = _env
	add_child(world)


func _setup_backdrop() -> void:
	var layer := CanvasLayer.new()
	layer.layer = -1
	add_child(layer)
	_bg_mat.shader = BG_SHADER
	_bg.material = _bg_mat
	_bg.set_anchors_preset(Control.PRESET_FULL_RECT)
	_bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
	layer.add_child(_bg)


func _setup_camera_and_lights() -> void:
	camera = Camera3D.new()
	camera.fov = 40.0
	camera.position = Vector3(0.0, 1.35, 6.8)
	add_child(camera)
	camera.look_at(Vector3(0.0, 0.95, 0.0))

	var key := DirectionalLight3D.new()
	key.rotation_degrees = Vector3(-38.0, -32.0, 0.0)
	key.light_energy = 1.5
	key.shadow_enabled = true
	# one shadow map over the stage only (the default spreads it over 100 m in splits: on a phone's
	# 2048 px map that left blocky dark patches on the arms and body)
	key.directional_shadow_mode = DirectionalLight3D.SHADOW_ORTHOGONAL
	key.directional_shadow_max_distance = 14.0
	add_child(key)
	var fill := DirectionalLight3D.new()
	fill.rotation_degrees = Vector3(-8.0, 48.0, 0.0)
	fill.light_energy = 0.35
	fill.light_color = Color(0.9, 0.95, 1.0)
	add_child(fill)
	var rim := DirectionalLight3D.new()
	rim.rotation_degrees = Vector3(-22.0, 158.0, 0.0)
	rim.light_energy = 1.1
	add_child(rim)


func _on_resize() -> void:
	var s := get_viewport().get_visible_rect().size
	_bg_mat.set_shader_parameter("aspect", s.x / maxf(s.y, 1.0))
	if s.y >= s.x:
		# portrait: hold the width, so a taller phone (19.5:9) shows more above and below the pet
		# but never less at the sides; at 9:16 this is the same view as a 40° vertical fov
		camera.keep_aspect = Camera3D.KEEP_WIDTH
		camera.fov = PORTRAIT_HFOV
	else:
		# landscape: pull the camera back a little so the pet keeps its size
		camera.keep_aspect = Camera3D.KEEP_HEIGHT
		camera.fov = 32.0
	if director:
		director.sync_camera_home()


# ---------------------------------------------------------------- UI

func _setup_ui() -> void:
	var ui_layer := CanvasLayer.new()
	ui_layer.layer = 1
	add_child(ui_layer)
	var font := SystemFont.new()
	font.font_names = PackedStringArray(["Leelawadee UI", "Tahoma", "Noto Sans Thai", "Segoe UI"])
	var theme := Theme.new()
	theme.default_font = font
	theme.default_font_size = 28

	var panel := PanelContainer.new()
	panel.theme = theme
	panel.set_anchors_preset(Control.PRESET_BOTTOM_WIDE)
	panel.offset_top = -330
	var style := StyleBoxFlat.new()
	style.bg_color = Color(0.07, 0.09, 0.14, 0.8)
	style.corner_radius_top_left = 22
	style.corner_radius_top_right = 22
	style.content_margin_left = 12
	style.content_margin_right = 12
	style.content_margin_top = 8
	style.content_margin_bottom = 12
	panel.add_theme_stylebox_override("panel", style)
	ui_layer.add_child(panel)

	var tabs := TabContainer.new()
	panel.add_child(tabs)
	var head := {}
	var body := {}
	for name in pet.items():
		var inf: Dictionary = pet.info(name)
		if inf.get("kind", "") == "tool":
			continue
		if inf["follows"] == "PR_HeadPivot":
			head[name] = ITEM_LABELS.get(name, name)
		else:
			body[name] = ITEM_LABELS.get(name, name)
	_tab(tabs, "อารมณ์", EMOTION_LABELS, func(k): pet.set_emotion(k))
	_tab(tabs, "หัว", head, func(k): pet.toggle(k))
	_tab(tabs, "ตัว", body, func(k): pet.toggle(k))
	var hands := {}
	for tool in TOOL_LABELS:
		for side in ["L", "R"]:
			hands["%s_%s" % [tool, side]] = ("ซ้าย · " if side == "L" else "ขวา · ") + TOOL_LABELS[tool]
	_tab(tabs, "มือ", hands, func(k): pet.toggle(k))
	var bgs := {}
	for k in BACKGROUNDS:
		bgs[k] = BACKGROUNDS[k][1]
	_tab(tabs, "ฉาก", bgs, func(k): set_background(k))
	var actions := {
		"fire_L": "ใช้มือซ้าย", "fire_R": "ใช้มือขวา", "fly": "บิน / ลง",
		"fidget": "ท่ากวน (สุ่ม)", "suit_auto": "สีชุดตามอารมณ์", "suit_random": "สุ่มสีชุด",
	}
	_tab(tabs, "แอ็กชัน", actions, func(k): _action(k))
	var skits := {"__all": "▶ เล่นทุกฉาก"}
	for s in director.list():
		skits[s["id"]] = s["title"]
	_tab(tabs, "ละคร", skits, func(k): _play_scene(k))
	var infos := {}
	for tool in INFO_TOOLS:
		infos["live:" + tool] = INFO_TOOLS[tool] + " (สด)"
	infos["sample:device_battery_status"] = "แบตมือถือ (ตัวอย่าง)"
	infos["sample:answer_card"] = "การ์ดข้อความ (ตัวอย่าง)"
	_tab(tabs, "ข้อมูล", infos, func(k): _play_info(k))
	if _args.has("tab"):                 # open a tab from the command line (screenshots of the panel)
		tabs.current_tab = clampi(int(_args["tab"]), 0, tabs.get_tab_count() - 1)


func _tab(tabs: TabContainer, title: String, items: Dictionary, on_press: Callable) -> void:
	var scroll := ScrollContainer.new()
	scroll.name = title
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	tabs.add_child(scroll)
	var flow := HFlowContainer.new()
	flow.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	flow.add_theme_constant_override("h_separation", 6)
	flow.add_theme_constant_override("v_separation", 6)
	scroll.add_child(flow)
	for key in items:
		var b := Button.new()
		b.text = items[key]
		b.custom_minimum_size = Vector2(0, 58)
		b.focus_mode = Control.FOCUS_NONE
		b.pressed.connect(on_press.bind(key))
		flow.add_child(b)


func _action(name: String) -> void:
	match name:
		"fire_L":
			pet.use_tool("L")
		"fire_R":
			pet.use_tool("R")
		"fly":
			pet.toggle_fly()
		"fidget":
			pet.fidget()
		"poke":
			pet.poke()
		"suit_auto":
			pet.suit_follows_emotion = true
			pet.set_emotion(pet.emotion)
		"suit_random":
			pet.suit_follows_emotion = false
			pet.set_suit_color(Color.from_hsv(randf(), 0.45, 0.95))
		_:
			if name.begins_with("fidget_"):
				pet.fidget(name.trim_prefix("fidget_"))


func _play_scene(id: String) -> void:
	if id == "__all":
		_playlist = director.list().map(func(s): return s["id"])
		_play_next()
	else:
		_playlist.clear()
		director.play(id, _sample_for(id))


func _play_next() -> void:
	if _playlist.size() > 0:
		var id: String = _playlist.pop_front()
		director.play(id, _sample_for(id))


## A tool's result on its scene, as the app will do it: "live:<tool>" fetches it now from the same source
## the app's tool uses (the saved sample if that fails), "sample:<tool>" uses the saved result.
func _play_info(key: String) -> void:
	_playlist.clear()
	var tool := key.get_slice(":", 1)
	if key.begins_with("live:"):
		var r = await info.fetch(tool)
		if (r is Dictionary or r is Array) and not r.is_empty():
			director.play_tool(tool, r)
			return
	var sample = _sample_result(tool)
	if sample != null:
		director.play_tool(tool, sample)


func _on_scene_finished(_id: String) -> void:
	set_background(MAIN_BACKGROUND)
	if _args.has("quit-at-end"):
		await get_tree().create_timer(0.7).timeout
		get_tree().quit()
		return
	if _playlist.size() > 0:
		await get_tree().create_timer(1.0).timeout
		_play_next()


func set_background(name: String) -> void:
	_bg_mat.set_shader_parameter("theme", BACKGROUNDS[name][0])
	# dark scenes get a dimmer ambient so the white plastic doesn't glow
	_env.ambient_light_energy = 0.45 if name in DARK_BACKGROUNDS else 0.75


# ---------------------------------------------------------------- input

func _unhandled_input(event: InputEvent) -> void:
	if app:
		app.input(event)
		return
	var s := get_viewport().get_visible_rect().size
	if event is InputEventMouseMotion or event is InputEventScreenDrag:
		var p: Vector2 = event.position
		pet.look(Vector2(p.x / s.x * 2.0 - 1.0, -(p.y / (s.y * 0.6) * 2.0 - 1.0)))
		_idle_time = 0.0
	elif event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
		pet.poke()


## Android's back button inside the app: the app closes Avatar mode (quit_on_go_back is off, the
## engine must not quit under the app).
func _notification(what: int) -> void:
	if what == NOTIFICATION_WM_GO_BACK_REQUEST and app:
		app._send("back")


func _process(delta: float) -> void:
	_idle_time += delta
	if app == null and _idle_time > 3.0 and not pet.scene_active:  # nobody is looking: let the gaze wander
		var t := Time.get_ticks_msec() / 1000.0
		pet.look(Vector2(sin(t * 0.37) * 0.6, sin(t * 0.23) * 0.3))
	# on a phone (or with --perf): one line of numbers every 2 s for logcat / the console
	_perf_left -= delta
	if _perf_left <= 0.0 and (OS.has_feature("mobile") or _args.has("perf")):
		_perf_left = 2.0
		print("PERF fps=%d frame_ms=%.1f draw_calls=%d objects=%d vram_mb=%.0f mem_mb=%.0f scene=%s" % [
			Performance.get_monitor(Performance.TIME_FPS), Performance.get_monitor(Performance.TIME_PROCESS) * 1000.0,
			Performance.get_monitor(Performance.RENDER_TOTAL_DRAW_CALLS_IN_FRAME),
			Performance.get_monitor(Performance.RENDER_TOTAL_OBJECTS_IN_FRAME),
			Performance.get_monitor(Performance.RENDER_VIDEO_MEM_USED) / 1048576.0,
			Performance.get_monitor(Performance.MEMORY_STATIC) / 1048576.0,
			director.playing if director.playing != "" else "-"])
	if _frame_dir != "" and director.playing != "" and director.elapsed() >= _frame_next:
		_save_frame("%s/%s_%02d.png" % [_frame_dir, director.playing, _frame_k], director.elapsed())
		_frame_k += 1
		_frame_next += _frame_every


func _save_frame(path: String, at: float) -> void:
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png(path)
	print("FRAME %.2f %s" % [at, path])


# ---------------------------------------------------------------- sprites for the app

## Faces the app shows where the 3D engine doesn't run (the floating overlay bubble, the Drive screen):
## the robot's head and shoulders on a transparent background, rendered off-screen at SPRITE_RENDER px
## and saved as SPRITE_PX webp -- robot_<face>.webp and robot_<face>_blink.webp for every emotion and
## eye expression, robot_talk_1..3.webp with the mouth more and more open. Then quits.
const SPRITE_RENDER := 768
const SPRITE_PX := 256
const BOOT_SPLASH_PX := 760        # shown at its own size, centred (about the live robot's size on a 1440 px phone)
const SPRITE_FACES := ["normal", "happy", "sad", "wink", "love", "surprised", "sleepy", "angry", "talk",
	"dizzy", "cry", "squint"]


func _render_sprites(dir: String) -> void:
	DirAccess.make_dir_recursive_absolute(dir)
	var sub := SubViewport.new()
	sub.size = Vector2i(SPRITE_RENDER, SPRITE_RENDER)
	sub.transparent_bg = true
	sub.msaa_3d = Viewport.MSAA_4X
	sub.world_3d = get_viewport().world_3d
	sub.render_target_update_mode = SubViewport.UPDATE_ALWAYS
	add_child(sub)
	var cam := Camera3D.new()
	var env: Environment = _env.duplicate()
	env.background_mode = Environment.BG_CLEAR_COLOR
	env.background_color = Color(0, 0, 0, 0)
	env.glow_enabled = false                       # glow smears colour into the transparent edge
	env.ambient_light_energy = 0.6
	cam.environment = env
	sub.add_child(cam)
	await get_tree().create_timer(0.3).timeout
	cam.fov = 27.0                                  # wide enough for arms spread out (happy, wink)
	pet.scene_active = true                          # no idle fidget (a spin) in the middle of a face
	pet.look(Vector2.ZERO)
	pet.blink_override = 0.0
	for name in SPRITE_FACES:
		if pet.EMOTIONS.has(name):
			pet.set_emotion(name)
		else:
			pet.set_emotion("normal")
			pet.set_eyes(name)
		pet.mouth_override = 0.0
		await get_tree().create_timer(0.9).timeout
		pet.blink_override = 0.0
		await _save_sprite(sub, "%s/robot_%s.webp" % [dir, name])
		pet.blink_override = 1.0
		await _save_sprite(sub, "%s/robot_%s_blink.webp" % [dir, name])
		pet.blink_override = 0.0
	pet.set_emotion("talk")
	await get_tree().create_timer(0.9).timeout
	for k in 3:
		pet.mouth_override = [0.3, 0.65, 1.0][k]
		await _save_sprite(sub, "%s/robot_talk_%d.webp" % [dir, k + 1])
	# the engine's boot splash (project.godot): the same face while the app's engine starts
	pet.mouth_override = 0.0
	pet.set_emotion("normal")
	await get_tree().create_timer(0.9).timeout
	await _save_sprite(sub, ProjectSettings.globalize_path("res://assets/boot_splash.png"), BOOT_SPLASH_PX)
	print("SPRITES ", dir)
	get_tree().quit()


## Head and shoulders, straight on and a little from above, aimed at the face as it is now (the idle
## animation moves the robot), so the face sits in the same spot on every sprite.
func _save_sprite(sub: SubViewport, path: String, px := SPRITE_PX) -> void:
	var cam := sub.get_child(0) as Camera3D
	var face: Vector3 = pet.head_node().global_transform * pet.screen_front_local()
	cam.position = face + Vector3(0.0, 0.35, 5.2)
	cam.look_at(face + Vector3(0.0, -0.3, 0.0))
	await RenderingServer.frame_post_draw
	await RenderingServer.frame_post_draw
	var img := sub.get_texture().get_image()
	img.resize(px, px, Image.INTERPOLATE_LANCZOS)
	if path.ends_with(".png"):
		img.save_png(path)
	else:
		img.save_webp(path, true, 0.9)
	print("SPRITE ", path)


# ---------------------------------------------------------------- scripted shots

func _apply_state(state: String) -> void:
	var parts := state.split(",")
	if parts.size() > 0 and parts[0] != "":
		pet.set_emotion(parts[0])
	if parts.size() > 1 and parts[1] != "":
		for a in parts[1].split("+"):
			pet.wear(a)
	if parts.size() > 2 and parts[2] != "":
		set_background(parts[2])
	if parts.size() > 3 and parts[3] != "":
		await get_tree().create_timer(0.6).timeout
		for a in parts[3].split("+"):
			_action(a)


func _take_shot_later(path: String, wait: float) -> void:
	await get_tree().create_timer(wait).timeout
	await RenderingServer.frame_post_draw
	var img := get_viewport().get_texture().get_image()
	img.save_png(path)
	print("SHOT ", path)
	if _args.has("debug"):
		print("DBG window ", get_window().size, " visible ", get_viewport().get_visible_rect().size)
		print("DBG float scale ", pet._float.scale, " squash ", pet._squash, " robot ", pet._robot.transform,
			" pet ", pet.transform, " cam ", camera.global_position, " fov ", camera.fov)
		for side in ["L", "R"]:
			var m: Node3D = pet._muzzle(side)
			print("DBG arm ", side, " q ", pet._arms[side].quaternion, " aim_w ", pet.get(pet._aim_prop(side)),
				" muzzle ", m.global_position if m else null, " dir ", pet._aim_dir(side, m) if m else null)
	get_tree().quit()
