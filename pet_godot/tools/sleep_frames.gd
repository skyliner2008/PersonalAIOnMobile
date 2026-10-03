extends SceneTree
## Frames of the robot falling asleep and waking up in the app (PetSelfCare, sped up): drowsy, the screen's eyelids
## sagging, shut, then opening as it gets up. Renders (not headless):
##   Godot --path pet_godot --script res://tools/sleep_frames.gd -- --out=<dir> [--every=0.5]
## Saves <dir>/sleep_NN.png with the time in the file name's order.

const TIMELINE := [
	[0.3, {"t": "scene", "id": "drowsy"}],
	[6.0, {"t": "eyelids", "drowsy": 8.0}],
	[14.5, {"t": "scene", "id": "sleep"}],
	[14.5, {"t": "eyelids", "open": 0.0, "secs": 1.5}],
	[19.0, {"t": "eyelids", "open": 1.0, "secs": 2.2}],
	[19.0, {"t": "scene", "id": "wake_up"}],
]
const LENGTH := 25.0


func _initialize() -> void:
	var args := {}
	for a in OS.get_cmdline_user_args():
		var kv := a.trim_prefix("--").split("=", true, 1)
		args[kv[0]] = kv[1] if kv.size() > 1 else ""
	var out: String = args.get("out", "user://sleep_frames")
	var every := float(args.get("every", "0.5"))
	DirAccess.make_dir_recursive_absolute(out)
	var main: Node = (load("res://main.tscn") as PackedScene).instantiate()
	root.add_child(main)
	await create_timer(0.6).timeout
	var app: Node = load("res://scripts/app_bridge.gd").new()
	main.add_child(app)
	app.setup(main, main.pet, main.director, main.camera)
	app.handle({"t": "state", "emotion": "normal", "items": []})
	var t := 0.0
	var next := 0
	var shot := 0.0
	var k := 0
	while t < LENGTH:
		while next < TIMELINE.size() and float(TIMELINE[next][0]) <= t:
			app.handle(TIMELINE[next][1])
			next += 1
		if t >= shot:
			main._save_frame("%s/sleep_%02d.png" % [out, k], t)
			k += 1
			shot += every
		await process_frame
		t += main.get_process_delta_time()
	quit(0)
