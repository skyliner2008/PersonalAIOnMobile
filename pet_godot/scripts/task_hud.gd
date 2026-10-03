extends CanvasLayer
## The jobs the AI took on in Avatar mode that the user still waits to hear about (the app's AvatarTasks), as
## pills in the lower right corner:
##   running  handed to the assistant, still at work  (amber dot, pulsing) "กำลังทำ"
##   ready    done, waiting for a quiet moment to be told  (green) "เสร็จ รอเล่า"
##   telling  the AI is telling it now  (cyan, pulsing) "กำลังเล่า"
## The label is the user's own question; without one, the tool's task name (tool_scenes.json "task").
## Questions asked in a row are answered late and out of order, and their "รับทราบ" is cut by the next question —
## the pills show every job from the moment it is taken on until it has been told.

const MAX_SHOWN := 3
const LABEL_MAX := 24
const STATE_TEXT := {"running": "กำลังทำ", "ready": "เสร็จ รอเล่า", "telling": "กำลังเล่า"}
const ASK_TEXT := {"ready": "รอตอบ", "telling": "กำลังตอบ"}   # a question the app asks the AI again
const STATE_COLOR := {"running": Color("FFC247"), "ready": Color("3DDC97"), "telling": Color("5FD9E6")}

var director: Node                    # scene_player.gd: its tool_scenes name each tool's task
var _box := VBoxContainer.new()
var _pills := {}                      # task id -> {panel, dot, label, state_label, state}
var _more: Label
var _font: SystemFont
var _t := 0.0


func _ready() -> void:
	layer = 2
	_font = SystemFont.new()
	_font.font_names = PackedStringArray(["Leelawadee UI", "Noto Sans Thai", "Tahoma", "Segoe UI", "sans-serif"])
	_font.font_weight = 600
	_box.anchor_left = 1.0
	_box.anchor_right = 1.0
	_box.anchor_top = 1.0
	_box.anchor_bottom = 1.0
	_box.grow_horizontal = Control.GROW_DIRECTION_BEGIN
	_box.grow_vertical = Control.GROW_DIRECTION_BEGIN
	_box.offset_right = -16
	_box.offset_bottom = -28
	_box.alignment = BoxContainer.ALIGNMENT_END
	_box.add_theme_constant_override("separation", 8)
	_box.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_box)
	_more = Label.new()
	_more.add_theme_font_override("font", _font)
	_more.add_theme_font_size_override("font_size", 22)
	_more.add_theme_color_override("font_color", Color(0.75, 0.88, 0.97, 0.9))
	_more.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	_more.visible = false
	_box.add_child(_more)


## The app's list, oldest first: [{id, tool?, args, question, state}]. An empty list hides the pills.
func set_tasks(items: Array) -> void:
	var keep := {}
	var shown := 0
	for it in items:
		if not it is Dictionary or shown == MAX_SHOWN:
			continue
		var id := str(it.get("id", ""))
		if id == "":
			continue
		keep[id] = true
		shown += 1
		var pill: Dictionary = _pills.get(id, {})
		if pill.is_empty():
			pill = _make_pill()
			_pills[id] = pill
			_box.add_child(pill["panel"])
			_box.move_child(_more, _box.get_child_count() - 1)
			_slide_in(pill["panel"])
		_fill(pill, it)
	for id in _pills.keys():
		if not keep.has(id):
			_fade_out(_pills[id]["panel"])
			_pills.erase(id)
	var hidden := items.size() - shown
	_more.visible = hidden > 0
	_more.text = "+%d งาน" % hidden


## How many pills are up (tests).
func count() -> int:
	return _pills.size()


func label_for(item: Dictionary) -> String:
	var q := str(item.get("question", "")).strip_edges()
	if q == "":
		var tool := str(item.get("tool", ""))
		var scenes: Dictionary = director.tool_scenes if director else {}
		q = str(scenes.get(tool, {}).get("task", tool)).trim_prefix("กำลัง")
		var args = item.get("args", {})
		if args is Dictionary and str(args.get("symbol", "")) != "":
			q += " " + str(args["symbol"])
	return q.left(LABEL_MAX) + ("…" if q.length() > LABEL_MAX else "")


func _make_pill() -> Dictionary:
	var panel := PanelContainer.new()
	panel.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var style := StyleBoxFlat.new()
	style.bg_color = Color(0.03, 0.1, 0.16, 0.84)
	style.set_corner_radius_all(22)
	style.set_border_width_all(2)
	style.content_margin_left = 14
	style.content_margin_right = 16
	style.content_margin_top = 7
	style.content_margin_bottom = 8
	panel.add_theme_stylebox_override("panel", style)
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 10)
	row.mouse_filter = Control.MOUSE_FILTER_IGNORE
	panel.add_child(row)
	var dot := Panel.new()
	dot.custom_minimum_size = Vector2(14, 14)
	dot.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	var ds := StyleBoxFlat.new()
	ds.set_corner_radius_all(7)
	dot.add_theme_stylebox_override("panel", ds)
	row.add_child(dot)
	var label := Label.new()
	label.add_theme_font_override("font", _font)
	label.add_theme_font_size_override("font_size", 25)
	label.add_theme_color_override("font_color", Color(0.94, 0.97, 1.0))
	row.add_child(label)
	var state := Label.new()
	state.add_theme_font_override("font", _font)
	state.add_theme_font_size_override("font_size", 21)
	row.add_child(state)
	return {"panel": panel, "style": style, "dot": dot, "dot_style": ds, "label": label, "state_label": state, "state": ""}


func _fill(pill: Dictionary, item: Dictionary) -> void:
	var state := str(item.get("state", "running"))
	var color: Color = STATE_COLOR.get(state, STATE_COLOR["running"])
	(pill["label"] as Label).text = label_for(item)
	var words: Dictionary = ASK_TEXT if str(item.get("tool", "")) == "" else STATE_TEXT
	(pill["state_label"] as Label).text = str(words.get(state, STATE_TEXT.get(state, state)))
	(pill["state_label"] as Label).add_theme_color_override("font_color", color.lerp(Color.WHITE, 0.25))
	(pill["dot_style"] as StyleBoxFlat).bg_color = color
	(pill["style"] as StyleBoxFlat).border_color = Color(color.r, color.g, color.b, 0.75)
	if pill["state"] != "" and pill["state"] != state:          # a change of state: a small pop
		var p: Control = pill["panel"]
		p.pivot_offset = p.size * 0.5
		p.scale = Vector2(1.08, 1.08)
		p.create_tween().tween_property(p, "scale", Vector2.ONE, 0.25).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	pill["state"] = state


func _slide_in(panel: Control) -> void:
	panel.modulate.a = 0.0
	var t := panel.create_tween()
	t.tween_property(panel, "modulate:a", 1.0, 0.25)


func _fade_out(panel: Control) -> void:
	var t := panel.create_tween()
	t.tween_property(panel, "modulate:a", 0.0, 0.3)
	t.tween_callback(panel.queue_free)


func _process(delta: float) -> void:
	_t += delta
	for id in _pills:
		var pill: Dictionary = _pills[id]
		var pulsing: bool = pill["state"] in ["running", "telling"]
		(pill["dot"] as Control).modulate.a = 0.45 + 0.55 * (0.5 + 0.5 * sin(_t * 5.0)) if pulsing else 1.0
