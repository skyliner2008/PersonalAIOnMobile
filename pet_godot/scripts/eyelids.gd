extends CanvasLayer
## The screen's eyelids (app_bridge "eyelids"): the robot's sleep in the app's Avatar mode (PetSelfCare). From 90 s
## of quiet they sag and blink, at 120 s the app shuts them (and dims the phone), and when it wakes they open slowly.
##   set_open(x, secs)   1 = no lids … 0 = shut, over secs
##   drowsy(secs)        heavy blinks over secs, each a little more shut, ending nearly shut
## Taps go through (the bridge wakes the robot with any tap while they show).

const SHADER := preload("res://shaders/eyelids.gdshader")

var _rect := ColorRect.new()
var _mat := ShaderMaterial.new()
var _tween: Tween
var _open := 1.0


func _ready() -> void:
	layer = 20                                  # over the scenes' captions, bubbles and the task list
	_mat.shader = SHADER
	_rect.material = _mat
	_rect.color = Color(1, 1, 1, 1)
	_rect.set_anchors_preset(Control.PRESET_FULL_RECT)
	_rect.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_rect)
	_apply(1.0)


## 1 = no lids … 0 = shut.
func open_amount() -> float:
	return _open


func _apply(v: float) -> void:
	_open = clampf(v, 0.0, 1.0)
	_mat.set_shader_parameter("open", _open)
	_rect.visible = _open < 0.999


func set_open(target: float, secs := 0.6) -> void:
	_kill()
	if secs <= 0.0:
		_apply(target)
		return
	_tween = create_tween()
	_tween.tween_method(_apply, _open, clampf(target, 0.0, 1.0), secs).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)


## Heavy-eyed for `secs`: each blink shuts a little more and stays shut a little longer, and the eyes open less
## after it — the way eyes fight sleep. Ends nearly shut (the app shuts them).
func drowsy(secs := 30.0) -> void:
	_kill()
	_tween = create_tween()
	var cycles := maxi(3, int(secs / 4.5))
	var each := secs / float(cycles)
	var from := _open
	for k in cycles:
		var f := float(k) / float(cycles - 1)
		var top := lerpf(0.8, 0.2, f)           # how far they get open again
		var dip := lerpf(0.32, 0.03, f)         # how far each heavy blink shuts
		var down := lerpf(0.8, 1.5, f)
		var hold := lerpf(0.15, 1.0, f)
		var up := lerpf(0.45, 0.9, f)
		_tween.tween_method(_apply, from, dip, down).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)
		_tween.tween_interval(hold)
		_tween.tween_method(_apply, dip, top, up).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)
		_tween.tween_interval(maxf(each - down - hold - up, 0.1))
		from = top


func _kill() -> void:
	if _tween and _tween.is_valid():
		_tween.kill()
	_tween = null
