extends SceneTree
## Headless check of the scene director, the way an AI brain would drive it:
##   Godot --headless --path pet_godot --script res://tools/scene_test.gd
## Every scene file loads and has what list() needs; a scene can be cut off by another; a
## generated scene (play_json) with an unknown verb still plays; afterwards the pet is back home
## (position, turn, flips, outfit, emotion). Prints PASS / FAIL lines and quits with 0 / 1.

var _fails := 0


func _check(ok: bool, what: String) -> void:
	print(("PASS " if ok else "FAIL ") + what)
	if not ok:
		_fails += 1


func _wait(sec: float) -> void:
	await create_timer(sec).timeout


func _initialize() -> void:
	var main: Node = (load("res://main.tscn") as PackedScene).instantiate()
	root.add_child(main)
	await _wait(0.5)
	var director: Node = main.director
	var pet: Node3D = main.pet

	var scenes: Array = director.list()
	_check(scenes.size() >= 12, "scene files loaded: %d" % scenes.size())
	for s in scenes:                     # skits last up to 10 s, info scenes (they have "needs") up to 20 s
		var longest := 10.0 if s["needs"] == "" else 20.0
		_check(s["mood"] != "" and s["description"] != "" and s["tags"].size() > 0
			and float(s["duration"]) <= longest, "catalogue entry %s" % s["id"])

	# cut a scene off with another one, then let that one finish
	director.play("hero")
	await _wait(2.0)
	_check(pet.flying and pet.worn.get("cape", "") == "cape", "hero is flying with a cape at 2 s")
	director.play("dizzy")
	await _wait(0.2)
	_check(not pet.flying and not pet.worn.has("cape"), "switching scenes takes the hero gear off")
	await _wait(10.5)
	_check(director.playing == "", "dizzy finished")
	await _wait(1.0)
	_home(pet, "after dizzy")

	# a scene written by an AI, with a verb the director doesn't know
	var generated := {
		"id": "ai_test", "duration": 2.0,
		"beats": [
			{"t": 0.0, "do": "caption", "text": "ทดสอบ"},
			{"t": 0.0, "do": "dance_the_macarena"},
			{"t": 0.1, "do": "wear", "item": "crown"},
			{"t": 0.1, "do": "move", "by": [0.4, 0.0, 0.5], "dur": 0.5},
			{"t": 0.2, "do": "roll", "by": 360, "dur": 0.8},
			{"t": 0.3, "do": "eyes", "name": "dizzy", "dur": 0.5},
			{"t": 0.6, "do": "effect", "name": "rain_cloud", "dur": 1.0},
		],
	}
	director.play_json(JSON.stringify(generated))
	await _wait(0.9)
	_check(director.playing == "ai_test", "generated scene is playing")
	_check(pet.worn.get("hat", "") == "crown", "generated scene put the crown on")
	_check(pet.position.distance_to(Vector3(0.4, 0.0, 0.5)) < 0.05, "move by [0.4, 0, 0.5] got there")
	await _wait(2.0)
	_check(director.playing == "", "generated scene finished")
	await _wait(1.0)
	_home(pet, "after the generated scene")
	_check(not pet.worn.has("hat"), "crown taken off again")

	# info scenes with the data the app would send: filling, conditions, panels
	var weather: Dictionary = main._sample_for("weather_report")
	_check(weather.has("temp") and weather.has("weather_code"), "weather sample loaded")
	director.play("weather_report", weather)
	await _wait(0.1)
	_check(director._fill("{temp:0}°C") == "%d°C" % roundi(float(weather["temp"])), "fills {temp:0}")
	_check(director._fill("{temp}") is float, "a whole reference keeps its type")
	_check(director._cond("situation == %s" % director._data["situation"]), "situation condition")
	_check(director._cond("temp > -100 and !no_such_field"), "and / ! conditions")
	_check(not director._cond("kind in [nothing, nope] or temp < -100"), "in / or conditions")
	await _wait(1.6)
	_check(director._panels.has("wx") and director._panels["wx"].kind == "weather", "weather panel is up")
	director.stop()
	await _wait(0.6)
	_check(director._panels.is_empty(), "panels closed when the scene stops")
	var stock: Dictionary = main._sample_for("stock_balance")
	director.play("stock_balance", stock)
	await _wait(0.1)
	var d: Dictionary = director._data
	_check(absf(float(d["liabilitiesPct"]) + float(d["equityPct"]) - 100.0) < 1.0, "liabilities + equity = assets")
	_check(director._fill("{totalAssets:money}").begins_with("$"), "money format")
	await _wait(7.0)
	_check(director._panels.has("main") and director._panels["main"].kind == "pie", "stock scene moved on to the pie")
	await _wait(10.0)
	_check(director.playing == "" and not pet.worn.has("hat"), "stock scene finished, fedora off")

	# tool results -> the scene made for that tool (what the app's bridge will call)
	_check(director.scene_for_tool("trading_price") == "price_report", "trading_price -> price_report")
	_check(director.scene_for_tool("device_weather") == "weather_report", "device_weather -> weather_report")
	_check(director.scene_for_tool("some_new_tool") == "answer_card", "an unknown tool falls back to the text card")
	for tool in director.tool_scenes:
		if tool != "*":
			_check(director.scenes.has(director.tool_scenes[tool]["scene"]), "%s has its scene" % tool)
	var price_row := {"symbol": "BTCUSDT", "price": "83806.8000", "change": "143.5661", "change_pct": "0.17%",
		"prev_close": "83663.2339", "high_52w": "N/A", "low_52w": "N/A", "currency": "USD", "direction": "UP"}
	_check(director.play_tool("trading_price", price_row) == "price_report", "play_tool picks price_report")
	await _wait(0.1)
	_check(director._fill("{price:price}") == "$83,806.80", "text numbers format as prices")
	_check(director._fill("{change_pct:pct+}") == "+0.17%", "percent text keeps its two decimals")
	_check(director._cond("move >= 0 and move < 3"), "conditions read numbers out of text")
	var rows := [{"symbol": "ARKUSDT", "change": "47.3"}, {"symbol": "ARKTRY", "change": "46.8"},
		{"symbol": "MOVRUSDT", "change": "29.4"}, {"symbol": "ABCUSDT.P", "change": "25.0"}, {"symbol": "XYZUSDT", "change": "12.0"}]
	director.play_tool("trading_top_gainers", rows)
	await _wait(0.1)
	var movers: Array = director._data.get("movers", [])
	_check(movers.size() == 3 and movers[0]["label"] == "ARK", "movers: one bar per coin, USDT pairs")
	director.play_tool("summarize_text", "# สรุป\n- **ข้อ 1**")
	await _wait(0.1)
	_check(director.playing == "answer_card" and str(director._data.get("markdown", "")).begins_with("# สรุป"), "text results go on the markdown card")
	director.stop()
	await _wait(0.8)

	# app mode: the app's commands through app_bridge.gd (what the Android plugin delivers)
	var app: Node = load("res://scripts/app_bridge.gd").new()
	main.add_child(app)
	app.setup(main, pet, director, main.camera)
	app.handle({"t": "state", "emotion": "happy", "items": ["crown", "sunglasses"], "background": "night", "emote": "heart"})
	await _wait(0.5)
	_check(pet.emotion == "happy" and pet.worn.get("hat", "") == "crown" and pet.worn.get("face", "") == "sunglasses",
		"state: emotion and gear")
	_check(int(main._bg_mat.get_shader_parameter("theme")) == main.BACKGROUNDS["night"][0], "state: backdrop")
	app.handle({"t": "state", "emotion": "normal", "items": ["crown"], "eyes": "dizzy"})
	await _wait(0.3)
	_check(pet.emotion == "normal" and not pet.worn.has("face") and app._applied_eyes == "dizzy", "state: gear off, dizzy eyes")
	_check(int(main._bg_mat.get_shader_parameter("theme")) == main.BACKGROUNDS[main.MAIN_BACKGROUND][0], "state: no backdrop = black")
	app.handle({"t": "voice", "speaking": true, "level": 0.6})
	_check(pet.speaking and is_equal_approx(pet.voice_level, 0.6), "voice: the mouth follows")
	app.handle({"t": "scene", "id": "party"})
	await _wait(0.3)
	app.handle({"t": "state", "emotion": "sad", "items": []})
	await _wait(0.2)
	_check(director.playing == "party" and pet.emotion != "sad", "a state waits while a scene plays")
	director.stop()
	await _wait(0.3)
	_check(pet.emotion == "sad" and not pet.worn.has("hat"), "the waiting state comes back after the scene")
	app.handle({"t": "scene", "id": "party"})
	await _wait(0.2)
	app.handle({"t": "tool", "tool": "summarize_text", "result": "# สรุป"})       # a tool's scene takes over
	await _wait(0.2)
	app.handle({"t": "stop", "id": "party"})                                   # the app's skit ended
	_check(director.playing == "answer_card", "stopping the skit leaves the tool's scene playing")
	app.handle({"t": "stop", "id": "answer_card"})
	_check(director.playing == "", "a stop with the scene's id ends it")
	await _wait(0.3)
	# a tool scene held while the app's AI talks about it: it waits on its last picture until "release"
	app.handle({"t": "tool", "tool": "trading_price", "result": price_row, "hold": true})
	await _wait(9.5)                                   # longer than the scene (9 s)
	_check(director.playing == "price_report" and director.holding() and director._panels.has("quote"),
		"a held tool scene keeps its card up past its length")
	app.handle({"t": "release"})
	await _wait(1.8)                                   # the arm goes down, the scene ends (hold 7.6 → 9.0)
	_check(director.playing == "", "after release the held scene finishes")
	app.handle({"t": "tool", "tool": "trading_price", "result": price_row})
	await _wait(9.5)
	_check(director.playing == "", "without hold a tool scene ends by itself")
	await _wait(0.6)
	var face: Vector3 = pet.head_node().global_transform * pet.screen_front_local()
	_check(app.zone_at(main.camera.unproject_position(face)) == "face", "touch zone: the middle of the screen-face")
	_check(app.zone_at(Vector2(5, 5)) == "none", "touch zone: the corner is not the pet")
	app.handle({"t": "say", "text": "ทดสอบกล่องคำพูดยาวๆ ที่ต้องขึ้นบรรทัดใหม่ในจอแนวตั้งของมือถือ"})
	await _wait(0.3)
	_check(director._bubble.visible and director._bubble.size.y < 260.0, "a bubble outside a scene fits its text")
	var head: Node3D = pet._head
	var up_before: Vector3 = head.transform.basis.y
	app.handle({"t": "fidget", "name": "nod"})
	await _wait(0.12)
	_check(pet._head_nod > 8.0, "nod: the face tips down")
	await _wait(1.4)
	_check(absf(pet._head_nod) < 0.5 and head.transform.basis.y.angle_to(up_before) < 0.1, "nod: the head is back where it was")
	Input.set_gravity(Vector3(sin(0.3), -cos(0.3), 0.0) * 9.8)      # the phone turned clockwise by 0.3 rad
	await _wait(0.1)
	_check(pet.sway > 0.1, "tilt: the body turns back towards upright (counter-clockwise on screen)")
	Input.set_gravity(Vector3(0.0, 0.0, -9.8))                       # lying flat on the table
	await _wait(0.1)
	_check(is_zero_approx(pet.sway), "tilt: lying flat, no lean")
	Input.set_gravity(Vector3.ZERO)
	app.queue_free()

	print("RESULT ", "ok" if _fails == 0 else "%d failed" % _fails)
	quit(1 if _fails else 0)


func _home(pet: Node3D, when: String) -> void:
	_check(pet.position.length() < 0.02, "position home " + when)
	_check(absf(wrapf(pet.rotation.y, -PI, PI)) < 0.02, "facing the camera " + when)
	_check(absf(pet.body_roll) < 0.02 and absf(pet.body_pitch) < 0.02, "upright " + when)
	_check(pet.emotion == "normal" and pet.suit_follows_emotion, "normal emotion, suit follows " + when)
	_check(not pet.scene_active, "scene_active off " + when)
