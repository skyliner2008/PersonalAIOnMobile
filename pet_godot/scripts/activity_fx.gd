extends Node3D
## What the pet is busy with between answers in Avatar mode, drawn round it for as long as the scene
## that started it plays (scene_player.gd frees it when the scene ends):
##   listen  the user is talking: sound waves leave both sides of the head and a voice meter under the
##           pet follows the user's voice (`level`: 0..1 from the app's microphone, < 0 = unknown:
##           a talking rhythm instead)
##   think   the AI is working out its answer: three lights circle the head on a tilted ring
## Built from primitives (mobile-friendly), parented to the pet so they move with it.

const FX := preload("res://scripts/effects.gd")
const LISTEN := Color(0.37, 0.9, 1.0)
const THINK_A := Color(0.66, 0.48, 1.0)
const THINK_B := Color(0.37, 0.9, 1.0)
const BARS := 15
const BAR_GAP := 0.105
const RINGS_PER_SIDE := 3
const RING_EVERY := 0.55             # seconds between waves leaving one side of the head

var kind := "listen"
var level_fn: Callable               # -> float 0..1 (the user's voice), < 0 unknown
var head_at := Vector3(0, 1.55, 0)   # the head centre in the pet's space

var _t := 0.0
var _fade := 0.0                     # 0 -> 1 as it appears, back to 0 as it goes
var _closing := false
var _level := 0.0
var _bars: Array[MeshInstance3D] = []
var _bar_mats: Array[StandardMaterial3D] = []
var _rings: Array = []               # [{node, mat, side, age}]
var _ring_clock := 0.0
var _orbit: Node3D
var _orbs: Array[MeshInstance3D] = []
var _halo: MeshInstance3D
var _halo_mat: StandardMaterial3D


func setup(k: String, head_centre: Vector3, level := Callable()) -> void:
	kind = k
	head_at = head_centre
	level_fn = level
	if kind == "think":
		_build_think()
	else:
		_build_listen()


## Fade out, then free.
func close() -> void:
	_closing = true


# ---------------------------------------------------------------- listen

func _build_listen() -> void:
	var meter := Node3D.new()
	meter.position = Vector3(0, -0.12, 0.55)
	add_child(meter)
	for i in BARS:
		var m := MeshInstance3D.new()
		var box := BoxMesh.new()
		box.size = Vector3(0.055, 1.0, 0.03)
		m.mesh = box
		var mat := FX.glow_material(LISTEN, 2.2, 0.85)
		m.material_override = mat
		m.position = Vector3((i - (BARS - 1) * 0.5) * BAR_GAP, 0, 0)
		m.scale = Vector3(1, 0.04, 1)
		meter.add_child(m)
		_bars.append(m)
		_bar_mats.append(mat)


func _spawn_ring(side: float) -> void:
	var m := MeshInstance3D.new()
	var torus := TorusMesh.new()
	torus.inner_radius = 0.19
	torus.outer_radius = 0.232
	torus.rings = 24
	torus.ring_segments = 6
	m.mesh = torus
	var mat := FX.glow_material(LISTEN, 3.4, 0.0)
	m.material_override = mat
	m.rotation = Vector3(0, 0, PI * 0.5)        # face sideways, like ")))" leaving the head
	m.position = head_at + Vector3(side * 0.42, 0, 0)
	add_child(m)
	_rings.append({"node": m, "mat": mat, "side": side, "age": 0.0})


func _tick_listen(delta: float) -> void:
	var want := -1.0
	if level_fn.is_valid():
		want = float(level_fn.call())
	if want < 0.0:                               # no level from the app: a talking rhythm
		want = 0.35 + 0.3 * sin(_t * 7.0) * sin(_t * 2.3 + 1.0)
	want = clampf(want * 1.6, 0.0, 1.0)          # mic levels sit low; speech fills most of the meter
	_level = lerpf(_level, want, 1.0 - exp(-delta * (14.0 if want > _level else 5.0)))
	for i in BARS:
		var x := absf(i - (BARS - 1) * 0.5) / ((BARS - 1) * 0.5)   # 0 in the middle, 1 at the ends
		var shape := 1.0 - x * x * 0.75
		var wobble := 0.55 + 0.45 * sin(_t * (6.0 + i * 0.9) + i * 1.7)
		var h := 0.04 + 0.42 * _level * shape * wobble + 0.03 * sin(_t * 2.0 + i * 0.6)
		var bar := _bars[i]
		bar.scale.y = lerpf(bar.scale.y, maxf(h, 0.03), 1.0 - exp(-delta * 18.0))
		_bar_mats[i].albedo_color.a = (0.45 + 0.5 * _level) * _fade
	# waves leave each side of the head, more often and brighter when the voice is loud
	_ring_clock -= delta * (0.6 + _level * 1.4)
	if _ring_clock <= 0.0 and not _closing:
		_ring_clock = RING_EVERY
		_spawn_ring(-1.0)
		_spawn_ring(1.0)
	for r in _rings.duplicate():
		r["age"] += delta
		var age: float = r["age"]
		var life := 1.2
		var node: MeshInstance3D = r["node"]
		var k := age / life
		node.position = head_at + Vector3(r["side"] * (0.42 + k * 0.45), 0, 0)
		node.scale = Vector3.ONE * (0.55 + k * 0.9)
		(r["mat"] as StandardMaterial3D).albedo_color.a = (1.0 - k) * (0.55 + 0.45 * _level) * _fade
		if age >= life:
			node.queue_free()
			_rings.erase(r)


# ---------------------------------------------------------------- think

func _build_think() -> void:
	_orbit = Node3D.new()
	_orbit.position = head_at + Vector3(0, 0.66, 0)
	_orbit.rotation = Vector3(0.42, 0, 0.12)     # a tilted ring, seen from a little above
	add_child(_orbit)
	_halo = MeshInstance3D.new()
	var torus := TorusMesh.new()
	torus.inner_radius = 0.5
	torus.outer_radius = 0.525
	torus.rings = 48
	torus.ring_segments = 6
	_halo.mesh = torus
	_halo_mat = FX.glow_material(THINK_A, 2.4, 0.0)
	_halo.material_override = _halo_mat
	_orbit.add_child(_halo)
	for i in 3:
		var m := MeshInstance3D.new()
		var s := SphereMesh.new()
		s.radius = 0.075
		s.height = 0.15
		s.radial_segments = 16
		s.rings = 8
		m.mesh = s
		m.material_override = FX.glow_material(THINK_B if i == 0 else THINK_A, 3.2, 0.0)
		_orbit.add_child(m)
		_orbs.append(m)


func _tick_think(_delta: float) -> void:
	for i in _orbs.size():
		# bunched up like a loading spinner: the three chase each other round the ring
		var a := _t * 3.1 - i * 0.55
		var orb := _orbs[i]
		orb.position = Vector3(cos(a) * 0.512, 0, sin(a) * 0.512)
		var pulse := 0.85 + 0.25 * sin(_t * 5.0 - i)
		orb.scale = Vector3.ONE * pulse * (1.0 - i * 0.18)
		(orb.material_override as StandardMaterial3D).albedo_color.a = (1.0 - i * 0.22) * _fade
	_halo_mat.albedo_color.a = (0.5 + 0.15 * sin(_t * 2.0)) * _fade
	_orbit.position.y = head_at.y + 0.66 + sin(_t * 1.6) * 0.03


# ---------------------------------------------------------------- per frame

func _process(delta: float) -> void:
	_t += delta
	_fade = move_toward(_fade, 0.0 if _closing else 1.0, delta / (0.25 if _closing else 0.35))
	if kind == "think":
		_tick_think(delta)
	else:
		_tick_listen(delta)
	if _closing and _fade <= 0.0:
		queue_free()
