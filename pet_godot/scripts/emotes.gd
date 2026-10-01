extends RefCounted
## The comic-book layer of the pet's scenes: floating reaction symbols (hearts, ?, !, Zzz, notes,
## sparkles, stars, dots, bolts, anger marks, sweat drops) and props (idea bulb, confetti, snot
## bubble, a big heart thrown at the viewer, dizzy stars, a rain cloud, a battery gauge).
## Everything frees itself when done.

const MARK_SHADER := preload("res://shaders/emote_mark.gdshader")

## kind -> [text, colour]  (text symbols are Label3D; kinds in MARKS are drawn by emote_mark.gdshader)
const SYMBOLS := {
	"heart": ["♥", Color(1.0, 0.35, 0.6)],
	"question": ["?", Color(1.0, 0.85, 0.25)],
	"exclaim": ["!", Color(1.0, 0.4, 0.2)],
	"zzz": ["Z", Color(0.72, 0.68, 1.0)],
	"note": ["♪", Color(0.35, 0.9, 1.0)],
	"sparkle": ["✦", Color(1.0, 0.9, 0.4)],
	"star": ["★", Color(1.0, 0.85, 0.2)],
	"dots": ["…", Color(1.0, 1.0, 1.0)],
	"bolt": ["⚡", Color(1.0, 0.9, 0.2)],
	"sweat": ["", Color(0.6, 0.85, 1.0)],
	"anger": ["", Color(0.95, 0.15, 0.2)],
}
const MARKS := {"anger": 0, "sweat": 1, "battery": 2}

static var _font: SystemFont


static func font() -> SystemFont:
	if _font == null:
		_font = SystemFont.new()
		_font.font_names = PackedStringArray(["Segoe UI Symbol", "Segoe UI Emoji", "Noto Color Emoji", "Segoe UI", "Leelawadee UI"])
		_font.font_weight = 800
	return _font


static func _symbol_node(kind: String, size: float) -> Node3D:
	if MARKS.has(kind):
		var m := MeshInstance3D.new()
		var quad := QuadMesh.new()
		quad.size = Vector2(1, 1)
		m.mesh = quad
		var mat := ShaderMaterial.new()
		mat.shader = MARK_SHADER
		mat.set_shader_parameter("kind", MARKS[kind])
		m.material_override = mat
		m.scale = Vector3.ONE * size * 0.7
		return m
	var info: Array = SYMBOLS.get(kind, SYMBOLS["sparkle"])
	var l := Label3D.new()
	l.text = info[0]
	l.font = font()
	l.font_size = 128
	l.pixel_size = 0.0035 * size
	l.modulate = info[1]
	l.outline_modulate = Color(0.08, 0.06, 0.12, 0.9)
	l.outline_size = 22
	l.billboard = BaseMaterial3D.BILLBOARD_ENABLED
	l.no_depth_test = true
	l.render_priority = 10
	l.outline_render_priority = 9
	return l


## Symbols pop up around `at`, rise and fade, one after another.
## Zzz climb a staircase and grow (z Z Z); anger marks throb in place.
static func spawn(world: Node3D, at: Vector3, kind: String, count := 3, spread := 0.45, rise := 0.7,
		dur := 1.3, size := 1.0) -> void:
	for i in count:
		var grow := size * randf_range(0.85, 1.1)
		var start := at + Vector3(randf_range(-spread, spread), randf_range(-0.05, 0.15), randf_range(-0.1, 0.25))
		var delay := i * 0.18
		var drift := Vector3(0.1 * randf_range(-1, 1), rise, 0.0)
		if kind == "zzz":
			grow = size * (0.65 + 0.3 * i)
			start = at + Vector3(0.12 + 0.2 * i, 0.05 + 0.2 * i, 0.0)
			delay = i * 0.35
			drift = Vector3(0.18, rise * 0.45, 0.0)
		elif kind == "anger":
			drift = Vector3(0.0, 0.08, 0.0)
		var n := _symbol_node(kind, grow)
		world.add_child(n)
		n.global_position = start
		var s := n.scale
		n.scale = s * 0.01
		var t := n.create_tween()
		t.tween_interval(delay)
		t.tween_property(n, "scale", s, 0.22).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
		t.parallel().tween_property(n, "global_position", start + drift, dur).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_OUT)
		if kind == "anger":                  # throb, like a pulsing vein
			var beats := maxi(2, int(dur / 0.35))
			var pulse := n.create_tween()
			pulse.tween_interval(delay + 0.22)
			for k in beats:
				pulse.tween_property(n, "scale", s * 1.28, 0.1).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_OUT)
				pulse.tween_property(n, "scale", s, 0.2).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_IN)
		t.tween_property(n, "scale", s * 0.01, 0.2).set_ease(Tween.EASE_IN)
		t.tween_callback(n.queue_free)


## A glowing light bulb over the head ("I've got it!"), with little rays.
static func bulb(world: Node3D, at: Vector3, dur := 1.6) -> void:
	var root := Node3D.new()
	world.add_child(root)
	root.global_position = at
	var glass := MeshInstance3D.new()
	var sphere := SphereMesh.new()
	sphere.radius = 0.16
	sphere.height = 0.34
	glass.mesh = sphere
	var glow := StandardMaterial3D.new()
	glow.albedo_color = Color(1.0, 0.95, 0.55)
	glow.emission_enabled = true
	glow.emission = Color(1.0, 0.85, 0.3)
	glow.emission_energy_multiplier = 4.0
	glass.material_override = glow
	root.add_child(glass)
	var base := MeshInstance3D.new()
	var cyl := CylinderMesh.new()
	cyl.top_radius = 0.075
	cyl.bottom_radius = 0.06
	cyl.height = 0.12
	base.mesh = cyl
	var grey := StandardMaterial3D.new()
	grey.albedo_color = Color(0.55, 0.58, 0.62)
	grey.metallic = 0.7
	base.material_override = grey
	base.position = Vector3(0, -0.2, 0)
	root.add_child(base)
	var light := OmniLight3D.new()
	light.light_color = Color(1.0, 0.9, 0.5)
	light.light_energy = 2.0
	light.omni_range = 1.6
	root.add_child(light)
	for k in 6:
		var ray := _symbol_node("sparkle", 0.55)
		var a := TAU * k / 6.0
		ray.position = Vector3(cos(a) * 0.3, sin(a) * 0.3, 0)
		root.add_child(ray)
	root.scale = Vector3.ONE * 0.01
	var t := root.create_tween()
	t.tween_property(root, "scale", Vector3.ONE, 0.3).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	t.tween_property(root, "global_position", at + Vector3(0, 0.12, 0), dur).set_trans(Tween.TRANS_SINE)
	t.tween_property(root, "scale", Vector3.ONE * 0.01, 0.2)
	t.tween_callback(root.queue_free)


## A burst of multicoloured paper.
static func confetti(world: Node3D, at: Vector3, amount := 90) -> void:
	var p := CPUParticles3D.new()
	world.add_child(p)
	p.global_position = at
	var quad := QuadMesh.new()
	quad.size = Vector2(0.06, 0.1)
	var mat := StandardMaterial3D.new()
	mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	mat.vertex_color_use_as_albedo = true
	mat.cull_mode = BaseMaterial3D.CULL_DISABLED
	mat.billboard_mode = BaseMaterial3D.BILLBOARD_PARTICLES
	quad.material = mat
	p.mesh = quad
	p.amount = amount
	p.one_shot = true
	p.explosiveness = 0.95
	p.lifetime = 2.2
	p.direction = Vector3.UP
	p.spread = 70.0
	p.initial_velocity_min = 2.5
	p.initial_velocity_max = 4.5
	p.gravity = Vector3(0, -3.2, 0)
	p.damping_min = 0.8
	p.damping_max = 1.6
	p.angle_min = -180.0
	p.angle_max = 180.0
	p.angular_velocity_min = -360.0
	p.angular_velocity_max = 360.0
	var colors := Gradient.new()
	colors.set_color(0, Color(1.0, 0.35, 0.55))
	colors.set_color(1, Color(0.3, 0.8, 1.0))
	colors.add_point(0.25, Color(1.0, 0.85, 0.2))
	colors.add_point(0.5, Color(0.5, 1.0, 0.55))
	colors.add_point(0.75, Color(0.75, 0.5, 1.0))
	p.color_initial_ramp = colors
	p.emitting = true
	var t := p.create_tween()
	t.tween_interval(2.6)
	t.tween_callback(p.queue_free)


## The sleeping snot bubble: grows and shrinks with each breath on the screen, then pops.
static func snot_bubble(head: Node3D, local_at: Vector3, dur := 2.0) -> void:
	var b := MeshInstance3D.new()
	var s := SphereMesh.new()
	s.radius = 0.2
	s.height = 0.4
	b.mesh = s
	var mat := StandardMaterial3D.new()
	mat.albedo_color = Color(0.7, 0.93, 1.0, 0.42)
	mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	mat.roughness = 0.04
	mat.metallic_specular = 1.0
	mat.rim_enabled = true
	mat.rim = 1.0
	mat.rim_tint = 0.2
	mat.emission_enabled = true
	mat.emission = Color(0.45, 0.8, 1.0)
	mat.emission_energy_multiplier = 0.9
	b.material_override = mat
	var shine := MeshInstance3D.new()                # the glossy highlight that sells "bubble"
	var ss := SphereMesh.new()
	ss.radius = 0.045
	ss.height = 0.07
	shine.mesh = ss
	var white := StandardMaterial3D.new()
	white.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	white.albedo_color = Color(1, 1, 1, 0.9)
	white.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	shine.material_override = white
	shine.position = Vector3(-0.08, 0.09, 0.15)
	b.add_child(shine)
	head.add_child(b)
	b.position = local_at
	b.scale = Vector3.ONE * 0.05
	var t := b.create_tween()
	for k in 3:                                   # in-out with the breathing, a little bigger each time
		t.tween_property(b, "scale", Vector3.ONE * (0.55 + 0.25 * k), dur / 6.0).set_trans(Tween.TRANS_SINE)
		t.tween_property(b, "scale", Vector3.ONE * (0.35 + 0.25 * k), dur / 6.0).set_trans(Tween.TRANS_SINE)
	t.tween_property(b, "scale", Vector3.ONE * 1.35, 0.15)
	t.tween_callback(func():
		spawn(head, b.global_position, "sparkle", 5, 0.25, 0.25, 0.4, 0.6)
		b.queue_free())


## A big heart thrown at the viewer: flies towards the camera, grows and fades.
static func big_heart(world: Node3D, from: Vector3, to: Vector3) -> void:
	var h := _symbol_node("heart", 1.0) as Label3D
	world.add_child(h)
	h.global_position = from
	h.font_size = 256
	h.outline_modulate = Color(1.0, 0.72, 0.84, 0.9)       # a soft pink rim (a dark one turns into a blob up close)
	var t := h.create_tween().set_parallel(true)
	t.tween_property(h, "global_position", to, 0.9).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_IN)
	t.tween_property(h, "pixel_size", 0.02, 0.9).from(0.004).set_trans(Tween.TRANS_EXPO).set_ease(Tween.EASE_IN)
	t.tween_property(h, "modulate:a", 0.0, 0.3).set_delay(0.6)
	t.tween_property(h, "outline_modulate:a", 0.0, 0.3).set_delay(0.6)
	t.chain().tween_callback(h.queue_free)


## Cartoon "seeing stars": a tilted ring of stars circling the head (parented to it, so it follows).
static func dizzy_stars(head: Node3D, local_at: Vector3, dur := 2.5, count := 5) -> void:
	var ring := Node3D.new()
	head.add_child(ring)
	ring.position = local_at
	ring.rotation = Vector3(0.3, 0.0, 0.0)
	for k in count:
		var s := _symbol_node("star" if k % 2 == 0 else "sparkle", 0.8)
		var a := TAU * k / count
		s.position = Vector3(cos(a) * 0.5, 0.0, sin(a) * 0.5)
		ring.add_child(s)
	ring.scale = Vector3.ONE * 0.01
	var t := ring.create_tween()
	t.tween_property(ring, "scale", Vector3.ONE, 0.25).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	t.parallel().tween_property(ring, "rotation:y", TAU * dur * 0.8, dur)
	t.tween_property(ring, "scale", Vector3.ONE * 0.01, 0.2)
	t.tween_callback(ring.queue_free)


## A little grey rain cloud that follows `parent` (the pet) and rains on its head.
static func rain_cloud(parent: Node3D, local_at: Vector3, dur := 3.0) -> void:
	var cloud := Node3D.new()
	parent.add_child(cloud)
	cloud.position = local_at
	var grey := StandardMaterial3D.new()
	grey.albedo_color = Color(0.46, 0.5, 0.58)
	grey.roughness = 0.9
	for puff in [[Vector3(0, 0.06, 0), 0.3], [Vector3(-0.3, -0.03, 0.02), 0.22], [Vector3(0.31, -0.02, 0), 0.23],
			[Vector3(-0.14, 0.15, -0.05), 0.2], [Vector3(0.15, 0.15, 0.03), 0.21], [Vector3(0, -0.08, 0.12), 0.21]]:
		var m := MeshInstance3D.new()
		var s := SphereMesh.new()
		s.radius = puff[1]
		s.height = puff[1] * 1.7
		m.mesh = s
		m.material_override = grey
		m.position = puff[0]
		cloud.add_child(m)
	var rain := CPUParticles3D.new()
	var drop := QuadMesh.new()
	drop.size = Vector2(0.035, 0.17)
	var mat := StandardMaterial3D.new()
	mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	mat.albedo_color = Color(0.45, 0.72, 1.0, 0.95)
	mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	mat.billboard_mode = BaseMaterial3D.BILLBOARD_FIXED_Y
	drop.material = mat
	rain.mesh = drop
	rain.amount = 40
	rain.lifetime = 0.45
	rain.emission_shape = CPUParticles3D.EMISSION_SHAPE_BOX
	rain.emission_box_extents = Vector3(0.36, 0.02, 0.14)
	rain.direction = Vector3.DOWN
	rain.spread = 0.0
	rain.gravity = Vector3(0, -6.0, 0)
	rain.initial_velocity_min = 1.2
	rain.initial_velocity_max = 1.6
	rain.position = Vector3(0, -0.15, 0)
	cloud.add_child(rain)
	rain.emitting = true
	cloud.scale = Vector3.ONE * 0.01
	var t := cloud.create_tween()
	t.tween_property(cloud, "scale", Vector3.ONE, 0.35).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	t.tween_interval(maxf(dur - 0.6, 0.1))
	t.tween_callback(func(): rain.emitting = false)
	t.tween_property(cloud, "scale", Vector3.ONE * 0.01, 0.25).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_IN)
	t.tween_callback(func():
		spawn(parent.get_parent() if parent.get_parent() is Node3D else parent, cloud.global_position, "sparkle", 4, 0.3, 0.2, 0.4, 0.7)
		cloud.queue_free())


## Rain (or snow) over the whole stage for `dur` seconds, in front of the backdrop and around the pet.
static func stage_weather(world: Node3D, snow: bool, dur := 6.0, amount := 1.0) -> void:
	var p := CPUParticles3D.new()
	var q := QuadMesh.new()
	q.size = Vector2(0.045, 0.045) if snow else Vector2(0.02, 0.22)
	var m := StandardMaterial3D.new()
	m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	m.albedo_color = Color(1, 1, 1, 0.9) if snow else Color(0.62, 0.8, 1.0, 0.55)
	m.billboard_mode = BaseMaterial3D.BILLBOARD_ENABLED if snow else BaseMaterial3D.BILLBOARD_FIXED_Y
	if snow:
		m.albedo_texture = preload("res://scripts/effects.gd").soft_dot()
	q.material = m
	p.mesh = q
	p.amount = int((140 if snow else 220) * amount)
	p.lifetime = 3.2 if snow else 0.75
	p.preprocess = p.lifetime
	p.emission_shape = CPUParticles3D.EMISSION_SHAPE_BOX
	p.emission_box_extents = Vector3(2.4, 0.1, 2.2)
	p.direction = Vector3(0.15, -1, 0) if not snow else Vector3.DOWN
	p.spread = 25.0 if snow else 3.0
	p.gravity = Vector3(0, -0.5 if snow else -9.0, 0)
	p.initial_velocity_min = 0.5 if snow else 5.0
	p.initial_velocity_max = 0.9 if snow else 6.5
	world.add_child(p)
	p.global_position = Vector3(0.0, 3.8, 0.4)
	p.emitting = true
	var t := p.create_tween()
	t.tween_interval(maxf(dur - p.lifetime, 0.1))
	t.tween_callback(func(): p.emitting = false)
	t.tween_interval(p.lifetime + 0.1)
	t.tween_callback(p.queue_free)


## A battery gauge over the head: the fill runs from `from` to `to` (0..1) over `dur`, then it stays
## for `hold` seconds and pops away. Below 20 % the fill blinks red.
static func battery(parent: Node3D, local_at: Vector3, from := 0.05, to := 0.05, dur := 2.0, hold := 0.6) -> void:
	var m := _symbol_node("battery", 1.0) as MeshInstance3D
	parent.add_child(m)
	m.position = local_at
	var mat := m.material_override as ShaderMaterial
	mat.set_shader_parameter("level", from)
	var s := Vector3(0.8, 0.8, 0.8)
	m.scale = s * 0.01
	var t := m.create_tween()
	t.tween_property(m, "scale", s, 0.25).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	t.tween_method(func(v: float): mat.set_shader_parameter("level", v), from, to, dur)
	t.tween_interval(hold)
	t.tween_property(m, "scale", s * 0.01, 0.2).set_ease(Tween.EASE_IN)
	t.tween_callback(m.queue_free)
