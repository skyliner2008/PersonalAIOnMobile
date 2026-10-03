extends RefCounted
## Props for the pet's own care (the app's PetSelfCare: it eats, bathes, plays and sleeps by itself), built from
## primitives and particles like the rest (no extra assets):
##   snack(pet, kind)        a snack in front of the face, eaten in three bites (donut / burger / pizza / cake /
##                           ice_cream / popcorn); returns it
##   bubbles(pet, dur)       soap foam on the head and bubbles rising round the body; returns the foam
##   shower(pet, dur)        a shower head over the pet, water pouring on it; returns it
##   splash(world, at)       water flying off as it shakes itself dry
##   beach_ball() + keepy_uppy(ball, pet, camera, bounces)   a beach ball bounced on the head, then headed at the lens
## Everything frees itself when done; the director frees what is still there when its scene stops.

const EMOTES := preload("res://scripts/emotes.gd")
const FX := preload("res://scripts/effects.gd")

## a bite every second from 1 s after the snack appears: chomps at 1, 2 and 3 s
const BITE_EVERY := 1.0
const BITES := 3
## the snack models are about 0.3 m across; on the 0.85 m screen-face they show at this scale
const SNACK_SIZE := 1.5
const BALL_RADIUS := 0.2


static func _mat(color: Color, rough := 0.6, metal := 0.0) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = color
	m.roughness = rough
	m.metallic = metal
	return m


static func _mesh(parent: Node3D, mesh: Mesh, color: Color, at := Vector3.ZERO, rot := Vector3.ZERO, rough := 0.6) -> MeshInstance3D:
	var m := MeshInstance3D.new()
	m.mesh = mesh
	m.material_override = _mat(color, rough)
	m.position = at
	m.rotation_degrees = rot
	parent.add_child(m)
	return m


static func _cyl(top: float, bottom: float, h: float, segments := 24) -> CylinderMesh:
	var c := CylinderMesh.new()
	c.top_radius = top
	c.bottom_radius = bottom
	c.height = h
	c.radial_segments = segments
	return c


static func _ball(r: float, hemi := false) -> SphereMesh:
	var s := SphereMesh.new()
	s.radius = r
	s.height = r if hemi else r * 2.0
	s.is_hemisphere = hemi
	return s


# ---------------------------------------------------------------- snacks

## The snack's model (about 0.3 m across, facing the viewer) and the colour of its crumbs.
static func _food(kind: String) -> Array:
	var root := Node3D.new()
	var crumbs := Color(0.86, 0.62, 0.32)
	match kind:
		"burger":
			_mesh(root, _cyl(0.15, 0.14, 0.06), Color(0.86, 0.6, 0.3), Vector3(0, -0.07, 0))
			_mesh(root, _cyl(0.155, 0.155, 0.05), Color(0.36, 0.2, 0.12), Vector3(0, -0.015, 0))
			var cheese := BoxMesh.new()
			cheese.size = Vector3(0.27, 0.012, 0.27)
			_mesh(root, cheese, Color(1.0, 0.78, 0.15), Vector3(0, 0.015, 0), Vector3(0, 45, 0))
			_mesh(root, _cyl(0.17, 0.17, 0.014), Color(0.35, 0.78, 0.3), Vector3(0, 0.03, 0))
			_mesh(root, _ball(0.15, true), Color(0.9, 0.62, 0.3), Vector3(0, 0.035, 0))
			for k in 7:                                   # sesame seeds
				var a := TAU * k / 7.0
				_mesh(root, _ball(0.012), Color(1.0, 0.96, 0.85), Vector3(cos(a) * 0.08, 0.15, sin(a) * 0.08))
			root.rotation_degrees = Vector3(18, 0, 0)
			crumbs = Color(0.9, 0.66, 0.34)
		"pizza":                                          # a slice: a triangle, tip down towards the mouth
			_mesh(root, _cyl(0.19, 0.19, 0.025, 3), Color(0.93, 0.72, 0.4))
			_mesh(root, _cyl(0.165, 0.165, 0.012, 3), Color(1.0, 0.8, 0.25), Vector3(0, 0.018, 0))
			for p in [Vector2(-0.03, 0.05), Vector2(0.06, -0.02), Vector2(-0.04, -0.07)]:
				_mesh(root, _cyl(0.028, 0.028, 0.012), Color(0.78, 0.15, 0.12), Vector3(p.x, 0.027, p.y))
			var crust := _mesh(root, CapsuleMesh.new(), Color(0.85, 0.6, 0.3), Vector3(0, 0.0, -0.095), Vector3(0, 0, 90))
			crust.scale = Vector3(0.07, 0.2, 0.07)
			root.rotation_degrees = Vector3(80, 0, 180)
			crumbs = Color(1.0, 0.8, 0.3)
		"cake":
			_mesh(root, _cyl(0.13, 0.13, 0.14), Color(0.98, 0.94, 0.86), Vector3(0, -0.02, 0))
			_mesh(root, _cyl(0.135, 0.135, 0.03), Color(1.0, 0.55, 0.72), Vector3(0, 0.06, 0))
			_mesh(root, _ball(0.032), Color(0.85, 0.08, 0.15), Vector3(0, 0.1, 0), Vector3.ZERO, 0.2)
			for k in 8:                                   # cream dots round the top
				var a := TAU * k / 8.0
				_mesh(root, _ball(0.022), Color(1, 1, 1), Vector3(cos(a) * 0.1, 0.08, sin(a) * 0.1))
			root.rotation_degrees = Vector3(20, 0, 0)
			crumbs = Color(1.0, 0.85, 0.85)
		"ice_cream":
			_mesh(root, _cyl(0.075, 0.0, 0.22), Color(0.88, 0.66, 0.38), Vector3(0, -0.1, 0))
			_mesh(root, _ball(0.085), Color(1.0, 0.62, 0.78), Vector3(0, 0.03, 0), Vector3.ZERO, 0.35)
			_mesh(root, _ball(0.075), Color(0.6, 0.95, 0.78), Vector3(0, 0.12, 0), Vector3.ZERO, 0.35)
			_mesh(root, _ball(0.025), Color(0.85, 0.08, 0.15), Vector3(0, 0.2, 0), Vector3.ZERO, 0.2)
			root.rotation_degrees = Vector3(10, 0, 0)
			crumbs = Color(1.0, 0.75, 0.85)
		"popcorn":
			_mesh(root, _cyl(0.12, 0.09, 0.2), Color(0.88, 0.16, 0.16), Vector3(0, -0.06, 0))
			var band := TorusMesh.new()
			band.inner_radius = 0.105
			band.outer_radius = 0.13
			_mesh(root, band, Color(1, 1, 1), Vector3(0, 0.035, 0))
			for k in 14:
				var a := TAU * k / 7.0
				var r := 0.03 + 0.06 * float(k % 2)
				_mesh(root, _ball(0.036), Color(1.0, 0.97, 0.8) if k % 3 else Color(1.0, 0.86, 0.45),
					Vector3(cos(a) * r, 0.06 + 0.035 * floorf(k / 7.0), sin(a) * r))
			root.rotation_degrees = Vector3(18, 0, 0)
			crumbs = Color(1.0, 0.95, 0.75)
		_:                                                # a donut with pink icing and sprinkles
			var dough := TorusMesh.new()
			dough.inner_radius = 0.05
			dough.outer_radius = 0.15
			_mesh(root, dough, Color(0.86, 0.6, 0.33))
			var icing := TorusMesh.new()
			icing.inner_radius = 0.06
			icing.outer_radius = 0.142
			var glaze := _mesh(root, icing, Color(1.0, 0.5, 0.72), Vector3(0, 0.022, 0), Vector3.ZERO, 0.3)
			glaze.scale = Vector3(1, 0.7, 1)
			var colours := [Color(1, 1, 0.4), Color(0.4, 0.85, 1), Color(1, 1, 1), Color(0.5, 1, 0.5), Color(0.75, 0.45, 1)]
			for k in 12:
				var a := TAU * k / 12.0 + 0.3
				var bit := BoxMesh.new()
				bit.size = Vector3(0.028, 0.008, 0.009)
				_mesh(root, bit, colours[k % colours.size()], Vector3(cos(a) * 0.1, 0.05, sin(a) * 0.1), Vector3(0, rad_to_deg(a) + 60.0 * (k % 3), 0))
			root.rotation_degrees = Vector3(75, 0, 0)
			crumbs = Color(0.9, 0.62, 0.35)
	return [root, crumbs]


## A burst of crumbs at `at` (world space), in the snack's colour.
static func crumbs(world: Node3D, at: Vector3, color: Color) -> void:
	var p := CPUParticles3D.new()
	var q := QuadMesh.new()
	q.size = Vector2(0.035, 0.035)
	var m := StandardMaterial3D.new()
	m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	m.billboard_mode = BaseMaterial3D.BILLBOARD_PARTICLES
	m.vertex_color_use_as_albedo = true
	m.albedo_texture = FX.soft_dot()
	q.material = m
	p.mesh = q
	p.amount = 16
	p.one_shot = true
	p.explosiveness = 0.95
	p.lifetime = 0.75
	p.direction = Vector3(0, 0.6, 1)
	p.spread = 70.0
	p.initial_velocity_min = 0.8
	p.initial_velocity_max = 1.8
	p.gravity = Vector3(0, -5.0, 0)
	p.scale_amount_min = 0.6
	p.scale_amount_max = 1.3
	p.color = color
	world.add_child(p)
	p.global_position = at
	p.emitting = true
	var t := p.create_tween()
	t.tween_interval(1.0)
	t.tween_callback(p.queue_free)


## A snack below the face, eaten in [BITES] bites [BITE_EVERY] apart (the first a second after it appears): each
## time it comes up to the mouth, the mouth opens and snaps shut, crumbs fly and it gets smaller; then the mouth
## chews. After the last bite it is gone in a puff of sparkles. Parented to the head, so it follows it.
static func snack(pet: Node3D, kind: String) -> Node3D:
	var made := _food(kind)
	var model: Node3D = made[0]
	var crumb_color: Color = made[1]
	var food := Node3D.new()                     # holds the model at snack size; the bites scale this
	model.scale = Vector3.ONE * SNACK_SIZE
	food.add_child(model)
	var head: Node3D = pet.head_node()
	var front: Vector3 = pet.screen_front_local()
	var rest := front + Vector3(0.0, -0.2, 0.32)  # over the lower half of the screen-face, towards the viewer
	var mouth := front + Vector3(0.0, -0.13, 0.1) # at the face's mouth (robot_eyes.gdshader draws it 0.3 below the middle)
	head.add_child(food)
	food.position = rest
	food.scale = Vector3.ONE * 0.01
	var world: Node3D = pet.get_parent() if pet.get_parent() is Node3D else pet
	var t := food.create_tween()
	t.tween_callback(func(): pet.mouth_override = 0.0)
	t.tween_property(food, "scale", Vector3.ONE, 0.3).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	t.tween_interval(BITE_EVERY - 0.45)
	for k in BITES:
		var last: bool = k == BITES - 1
		var size := 1.0 - 0.3 * float(k + 1)
		# up to the mouth as it opens wide
		t.tween_property(food, "position", mouth, 0.15).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_OUT)
		t.parallel().tween_property(pet, "mouth_override", 0.9, 0.15)
		# chomp
		t.tween_callback(func():
			pet.boing(-0.08)
			crumbs(world, food.global_position, crumb_color)
			if last:
				EMOTES.spawn(world, food.global_position, "sparkle", 5, 0.2, 0.3, 0.5, 0.7))
		t.tween_property(pet, "mouth_override", 0.0, 0.08)
		if last:
			t.parallel().tween_property(food, "scale", Vector3.ONE * 0.01, 0.12)
		else:
			t.parallel().tween_property(food, "scale", Vector3.ONE * size, 0.08)
			t.tween_property(food, "position", rest, 0.2).set_trans(Tween.TRANS_SINE)
		# chew
		for c in 2:
			t.tween_property(pet, "mouth_override", 0.35, 0.1)
			t.tween_property(pet, "mouth_override", 0.0, 0.1)
		if not last:
			t.tween_interval(BITE_EVERY - (0.15 + 0.08 + 0.2 + 0.4))
	t.tween_callback(func():
		pet.mouth_override = -1.0
		food.queue_free())
	return food


# ---------------------------------------------------------------- bath

## A soap bubble: clear, with a bright rim.
static func _bubble_texture() -> GradientTexture2D:
	var g := Gradient.new()
	g.set_color(0, Color(1, 1, 1, 0.06))
	g.set_color(1, Color(1, 1, 1, 0.0))
	g.add_point(0.72, Color(0.75, 0.92, 1.0, 0.22))
	g.add_point(0.9, Color(1, 1, 1, 0.9))
	var tex := GradientTexture2D.new()
	tex.gradient = g
	tex.fill = GradientTexture2D.FILL_RADIAL
	tex.fill_from = Vector2(0.5, 0.5)
	tex.fill_to = Vector2(1.0, 0.5)
	tex.width = 64
	tex.height = 64
	return tex


## Soap foam piled on the head for `dur` seconds and bubbles rising round the body; the foam pops away at the end.
static func bubbles(pet: Node3D, dur := 5.0) -> Node3D:
	var head: Node3D = pet.head_node()
	var foam := Node3D.new()
	head.add_child(foam)
	foam.position = Vector3(0, 0.5, 0.05)
	var white := _mat(Color(1, 1, 1), 0.85)
	white.emission_enabled = true
	white.emission = Color(0.85, 0.92, 1.0)
	white.emission_energy_multiplier = 0.25
	for puff in [[Vector3(0, 0.05, 0), 0.14], [Vector3(-0.17, 0, 0.04), 0.11], [Vector3(0.18, 0, 0.02), 0.12],
			[Vector3(-0.07, 0.13, -0.03), 0.1], [Vector3(0.09, 0.12, 0.05), 0.1], [Vector3(0.0, 0.0, 0.16), 0.09],
			[Vector3(-0.28, -0.08, 0.0), 0.08], [Vector3(0.29, -0.08, 0.03), 0.08]]:
		var m := MeshInstance3D.new()
		m.mesh = _ball(puff[1])
		m.material_override = white
		m.position = puff[0]
		foam.add_child(m)
	var rise := CPUParticles3D.new()
	var q := QuadMesh.new()
	q.size = Vector2(0.14, 0.14)
	var bm := StandardMaterial3D.new()
	bm.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	bm.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	bm.billboard_mode = BaseMaterial3D.BILLBOARD_PARTICLES
	bm.albedo_texture = _bubble_texture()
	bm.vertex_color_use_as_albedo = true
	q.material = bm
	rise.mesh = q
	rise.amount = 22
	rise.lifetime = 2.4
	rise.emission_shape = CPUParticles3D.EMISSION_SHAPE_BOX
	rise.emission_box_extents = Vector3(0.55, 0.5, 0.3)
	rise.direction = Vector3.UP
	rise.spread = 20.0
	rise.gravity = Vector3(0, 0.15, 0)
	rise.initial_velocity_min = 0.25
	rise.initial_velocity_max = 0.6
	rise.scale_amount_min = 0.5
	rise.scale_amount_max = 1.4
	var fade := Gradient.new()
	fade.set_color(0, Color(1, 1, 1, 0.0))
	fade.set_color(1, Color(1, 1, 1, 0.0))
	fade.add_point(0.15, Color(1, 1, 1, 1))
	fade.add_point(0.8, Color(1, 1, 1, 0.9))
	rise.color_ramp = fade
	pet.add_child(rise)
	rise.position = Vector3(0, 1.0, 0.15)
	rise.emitting = true
	# the bubbles go with the foam (also when the director frees it as its scene stops)
	foam.tree_exiting.connect(func():
		if is_instance_valid(rise):
			rise.queue_free())
	foam.scale = Vector3.ONE * 0.01
	var t := foam.create_tween()
	t.tween_property(foam, "scale", Vector3.ONE, 0.4).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	var wobbles := maxi(1, int((dur - 0.8) / 0.6))
	for k in wobbles:
		t.tween_property(foam, "scale", Vector3(1.08, 0.94, 1.08) if k % 2 == 0 else Vector3.ONE, 0.3).set_trans(Tween.TRANS_SINE)
		t.tween_property(foam, "scale", Vector3.ONE, 0.3).set_trans(Tween.TRANS_SINE)
	t.tween_callback(func(): rise.emitting = false)
	t.tween_property(foam, "scale", Vector3.ONE * 0.01, 0.25).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_IN)
	t.tween_callback(func():
		EMOTES.spawn(pet.get_parent() if pet.get_parent() is Node3D else pet, foam.global_position, "sparkle", 4, 0.3, 0.2, 0.4, 0.7)
		foam.visible = false)
	t.tween_interval(rise.lifetime)                    # the last bubbles rise and fade
	t.tween_callback(foam.queue_free)
	return foam


## A chrome shower head over the pet, water pouring on its head for `dur` seconds. Follows the pet.
static func shower(pet: Node3D, dur := 5.0) -> Node3D:
	var root := Node3D.new()
	pet.add_child(root)
	root.position = Vector3(0.0, 2.75, 0.12)
	var chrome := _mat(Color(0.82, 0.85, 0.9), 0.18, 0.9)
	var head := MeshInstance3D.new()
	head.mesh = _cyl(0.2, 0.13, 0.08)
	head.material_override = chrome
	head.rotation_degrees = Vector3(-12, 0, 0)
	root.add_child(head)
	var pipe := MeshInstance3D.new()
	pipe.mesh = _cyl(0.028, 0.028, 1.4)
	pipe.material_override = chrome
	pipe.position = Vector3(0, 0.72, -0.05)
	root.add_child(pipe)
	var water := CPUParticles3D.new()
	var drop := QuadMesh.new()
	drop.size = Vector2(0.03, 0.16)
	var wm := StandardMaterial3D.new()
	wm.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	wm.albedo_color = Color(0.55, 0.82, 1.0, 0.75)
	wm.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	wm.billboard_mode = BaseMaterial3D.BILLBOARD_FIXED_Y
	drop.material = wm
	water.mesh = drop
	water.amount = 70
	water.lifetime = 0.55
	water.emission_shape = CPUParticles3D.EMISSION_SHAPE_SPHERE
	water.emission_sphere_radius = 0.13
	water.direction = Vector3.DOWN
	water.spread = 9.0
	water.gravity = Vector3(0, -6.0, 0)
	water.initial_velocity_min = 1.9
	water.initial_velocity_max = 2.5
	water.position = Vector3(0, -0.06, 0)
	root.add_child(water)
	water.emitting = true
	root.scale = Vector3.ONE * 0.01
	var t := root.create_tween()
	t.tween_property(root, "scale", Vector3.ONE, 0.3).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	t.tween_interval(maxf(dur - 0.6, 0.1))
	t.tween_callback(func(): water.emitting = false)
	t.tween_interval(water.lifetime)
	t.tween_property(root, "scale", Vector3.ONE * 0.01, 0.25).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_IN)
	t.tween_callback(root.queue_free)
	return root


## Water flying off the pet as it shakes itself dry (world space, frees itself).
static func splash(world: Node3D, at: Vector3) -> void:
	var p := CPUParticles3D.new()
	var q := QuadMesh.new()
	q.size = Vector2(0.05, 0.05)
	var m := StandardMaterial3D.new()
	m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	m.billboard_mode = BaseMaterial3D.BILLBOARD_PARTICLES
	m.albedo_texture = FX.soft_dot()
	m.albedo_color = Color(0.6, 0.85, 1.0, 0.9)
	q.material = m
	p.mesh = q
	p.amount = 46
	p.one_shot = true
	p.explosiveness = 0.85
	p.lifetime = 0.9
	p.emission_shape = CPUParticles3D.EMISSION_SHAPE_SPHERE
	p.emission_sphere_radius = 0.45
	p.direction = Vector3(0, 0.4, 0.3)
	p.spread = 180.0
	p.initial_velocity_min = 1.8
	p.initial_velocity_max = 3.4
	p.gravity = Vector3(0, -6.0, 0)
	p.scale_amount_min = 0.5
	p.scale_amount_max = 1.2
	world.add_child(p)
	p.global_position = at
	p.emitting = true
	var t := p.create_tween()
	t.tween_interval(1.2)
	t.tween_callback(p.queue_free)


# ---------------------------------------------------------------- ball

## A beach ball: six coloured wedges from pole to pole (hard bands of a gradient round the sphere's U).
static func beach_ball() -> Node3D:
	var root := Node3D.new()
	var g := Gradient.new()
	g.interpolation_mode = Gradient.GRADIENT_INTERPOLATE_CONSTANT
	var bands := [Color(0.95, 0.2, 0.22), Color(1, 1, 1), Color(0.2, 0.5, 0.95), Color(1, 1, 1), Color(1.0, 0.82, 0.15), Color(1, 1, 1)]
	g.set_color(0, bands[0])
	g.set_offset(0, 0.0)
	g.set_color(1, bands[5])
	g.set_offset(1, 5.0 / 6.0)
	for k in range(1, 5):
		g.add_point(float(k) / 6.0, bands[k])
	var tex := GradientTexture2D.new()
	tex.gradient = g
	tex.width = 256
	tex.height = 4
	var m := StandardMaterial3D.new()
	m.albedo_texture = tex
	m.roughness = 0.35
	var ball := MeshInstance3D.new()
	ball.mesh = _ball(BALL_RADIUS)
	ball.material_override = m
	root.add_child(ball)
	for pole in [1.0, -1.0]:                             # white caps
		var cap := MeshInstance3D.new()
		cap.mesh = _cyl(0.045, 0.045, 0.012)
		cap.material_override = _mat(Color(1, 1, 1), 0.4)
		cap.position = Vector3(0, BALL_RADIUS * pole, 0)
		root.add_child(cap)
	return root


## Keepy-uppy: `ball` (already in the world) drops onto the pet's head and bounces off it `bounces` − 1 times
## (0.8 m up, 0.85 s apart), the last header sends it at the lens, where it bonks and drops away. Every touch is
## a squash of the pet; it follows the head wherever a hop puts it.
static func keepy_uppy(ball: Node3D, pet: Node3D, camera: Camera3D, bounces := 4) -> void:
	var height := 0.8
	var period := 0.85
	var g := 8.0 * height / (period * period)
	var up := g * period * 0.5
	var pos: Vector3 = pet.head_top() + Vector3(0.05, 1.6, 0.2)
	var vel := Vector3.ZERO
	var hits := 0
	var kicked := false
	var bonked := false
	var life := 0.0
	var world: Node3D = ball.get_parent() as Node3D
	var tree := pet.get_tree()
	ball.global_position = pos
	while is_instance_valid(ball) and life < 7.0:
		await tree.process_frame
		if not is_instance_valid(ball):
			return
		var dt := pet.get_process_delta_time()
		life += dt
		vel.y -= g * dt
		pos += vel * dt
		ball.rotate_object_local(Vector3(1, 0, 0), dt * (9.0 if kicked else 4.0))
		if not kicked:
			var top: Vector3 = pet.head_top()
			pos.x = lerpf(pos.x, top.x, 1.0 - exp(-dt * 4.0))
			pos.z = lerpf(pos.z, top.z + 0.15, 1.0 - exp(-dt * 4.0))
			if vel.y < 0.0 and pos.y <= top.y + BALL_RADIUS:
				pos.y = top.y + BALL_RADIUS
				hits += 1
				pet.boing(-0.14)
				EMOTES.spawn(world, pos, "sparkle", 2, 0.15, 0.3, 0.5, 0.6)
				_squish(ball)
				if hits >= bounces:
					vel = (camera.global_position + Vector3(0, -0.35, 0) - pos).normalized() * 5.2
					g = 0.0
					kicked = true
				else:
					vel.y = up
		elif not bonked and pos.distance_to(camera.global_position) < 1.25:
			bonked = true                                 # on the glass: squash flat, then drop away
			_squish(ball, 0.45)
			vel = Vector3(randf_range(-0.8, 0.8), 0.6, -1.2)
			g = 9.0
		elif bonked and life > 0.0 and pos.y < -1.5:
			break
		ball.global_position = pos
	if is_instance_valid(ball):
		ball.queue_free()


static func _squish(ball: Node3D, amount := 0.25) -> void:
	var t := ball.create_tween()
	t.tween_property(ball, "scale", Vector3(1.0 + amount, 1.0 - amount, 1.0 + amount), 0.06)
	t.tween_property(ball, "scale", Vector3.ONE, 0.25).set_trans(Tween.TRANS_ELASTIC).set_ease(Tween.EASE_OUT)
