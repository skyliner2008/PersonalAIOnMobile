extends RefCounted
## Effects for the pet's gear, built from primitives and particles (no extra assets, mobile-friendly).
## Every spawn function adds its nodes under `world` (so shots do not move with the pet) and frees
## them when done. Persistent effects (flashlight beam, shield, jet flames) are returned so the
## caller can move them with the hand / nozzle every frame and remove them.

const CONE_SHADER := preload("res://shaders/light_cone.gdshader")
const SHIELD_SHADER := preload("res://shaders/energy_shield.gdshader")


static func glow_material(color: Color, energy := 3.0, alpha := 1.0) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	m.albedo_color = Color(color.r, color.g, color.b, alpha)
	m.emission_enabled = true
	m.emission = color
	m.emission_energy_multiplier = energy
	if alpha < 1.0:
		m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
		m.blend_mode = BaseMaterial3D.BLEND_MODE_ADD
	return m


static func particle_material() -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	m.blend_mode = BaseMaterial3D.BLEND_MODE_ADD
	m.billboard_mode = BaseMaterial3D.BILLBOARD_PARTICLES
	m.vertex_color_use_as_albedo = true
	m.albedo_texture = soft_dot()
	return m


## A round, soft-edged dot for particles (plain quads read as square pixels).
static func soft_dot() -> GradientTexture2D:
	var g := Gradient.new()
	g.set_color(0, Color(1, 1, 1, 1))
	g.set_color(1, Color(1, 1, 1, 0))
	var tex := GradientTexture2D.new()
	tex.gradient = g
	tex.fill = GradientTexture2D.FILL_RADIAL
	tex.fill_from = Vector2(0.5, 0.5)
	tex.fill_to = Vector2(1.0, 0.5)
	tex.width = 64
	tex.height = 64
	return tex


static func fire_gradient() -> Gradient:
	var g := Gradient.new()
	g.set_color(0, Color(1.0, 0.95, 0.6, 1.0))
	g.set_color(1, Color(1.0, 0.25, 0.05, 0.0))
	g.add_point(0.35, Color(1.0, 0.55, 0.15, 0.85))
	return g


## A basis whose +Y runs along `dir` (cylinders and capsules are built along Y).
static func basis_along(dir: Vector3) -> Basis:
	var y := dir.normalized()
	var helper := Vector3.UP if absf(y.dot(Vector3.UP)) < 0.95 else Vector3.RIGHT
	var x := helper.cross(y).normalized()
	return Basis(x, y, x.cross(y).normalized())           # right-handed (a mirrored basis flips faces)


static func flame_emitter(size := 1.0) -> CPUParticles3D:
	var p := CPUParticles3D.new()
	var quad := QuadMesh.new()
	quad.size = Vector2(0.2, 0.2) * size
	quad.material = particle_material()
	p.mesh = quad
	p.amount = 28
	p.lifetime = 0.35
	p.direction = Vector3(0, -1, 0)
	p.spread = 12.0
	p.gravity = Vector3.ZERO
	p.initial_velocity_min = 2.0 * size
	p.initial_velocity_max = 3.2 * size
	p.scale_amount_min = 0.6
	p.scale_amount_max = 1.2
	p.color_ramp = fire_gradient()
	p.local_coords = false
	return p


# ---------------------------------------------------------------- rockets

## `arc`: pop up out of the cannon and curve in over the pet (stays on a portrait screen);
## otherwise fly straight along `dir` at `speed` (e.g. at the camera) for `life` seconds.
static func rocket(world: Node3D, from: Vector3, dir: Vector3, arc := true, speed := 3.5, life_max := 0.95) -> void:
	var r := Node3D.new()
	world.add_child(r)
	r.global_position = from
	var body := MeshInstance3D.new()
	var cap := CapsuleMesh.new()
	cap.radius = 0.08
	cap.height = 0.38
	body.mesh = cap
	var white := StandardMaterial3D.new()
	white.albedo_color = Color(0.95, 0.96, 0.98)
	white.roughness = 0.5
	body.material_override = white
	r.add_child(body)
	var nose := MeshInstance3D.new()
	var cone := CylinderMesh.new()
	cone.top_radius = 0.0
	cone.bottom_radius = 0.081
	cone.height = 0.14
	nose.mesh = cone
	var red := StandardMaterial3D.new()
	red.albedo_color = Color(0.9, 0.2, 0.22)
	nose.material_override = red
	nose.position = Vector3(0, 0.25, 0)
	r.add_child(nose)
	var trail := flame_emitter(1.2)
	trail.position = Vector3(0, -0.2, 0)
	r.add_child(trail)
	var smoke := flame_emitter(1.0)
	smoke.lifetime = 0.9
	smoke.amount = 20
	smoke.initial_velocity_min = 0.2
	smoke.initial_velocity_max = 0.5
	var sg := Gradient.new()
	sg.set_color(0, Color(0.85, 0.85, 0.9, 0.5))
	sg.set_color(1, Color(0.7, 0.7, 0.75, 0.0))
	smoke.color_ramp = sg
	(smoke.mesh as QuadMesh).material.blend_mode = BaseMaterial3D.BLEND_MODE_MIX
	smoke.position = Vector3(0, -0.22, 0)
	r.add_child(smoke)

	# pop up out of the cannon and arc in over the pet, bursting where a portrait screen still shows it
	var velocity := dir.normalized() * 0.5 + Vector3(-0.4 * signf(from.x), 2.4, 0.7) if arc else dir.normalized() * speed
	var life := 0.0
	var tree := world.get_tree()
	r.global_basis = basis_along(velocity)
	while life < life_max and is_instance_valid(r):
		var dt := world.get_process_delta_time()
		await tree.process_frame
		life += dt
		if arc:
			velocity += Vector3(0, -2.2, 0) * dt + Vector3(randf_range(-1, 1), randf_range(-1, 1), 0) * 1.5 * dt
		else:
			velocity += Vector3(randf_range(-1, 1), randf_range(-1, 1), 0) * 0.8 * dt
		r.global_position += velocity * dt
		r.global_basis = basis_along(velocity)
	if is_instance_valid(r):
		explosion(world, r.global_position)
		r.queue_free()


static func explosion(world: Node3D, at: Vector3) -> void:
	var boom := Node3D.new()
	world.add_child(boom)
	boom.global_position = at
	var ball := MeshInstance3D.new()
	var sphere := SphereMesh.new()
	sphere.radius = 0.35
	sphere.height = 0.7
	ball.mesh = sphere
	var mat := glow_material(Color(1.0, 0.6, 0.2), 4.0, 0.9)
	ball.material_override = mat
	boom.add_child(ball)
	var light := OmniLight3D.new()
	light.light_color = Color(1.0, 0.7, 0.35)
	light.light_energy = 6.0
	light.omni_range = 3.0
	boom.add_child(light)
	var burst := flame_emitter(1.4)
	burst.one_shot = true
	burst.explosiveness = 1.0
	burst.amount = 40
	burst.lifetime = 0.6
	burst.direction = Vector3.UP
	burst.spread = 180.0
	burst.initial_velocity_min = 1.0
	burst.initial_velocity_max = 2.6
	boom.add_child(burst)
	burst.emitting = true
	var t := boom.create_tween().set_parallel(true)
	t.tween_property(ball, "scale", Vector3.ONE * 2.8, 0.4).from(Vector3.ONE * 0.3).set_trans(Tween.TRANS_EXPO).set_ease(Tween.EASE_OUT)
	t.tween_property(mat, "albedo_color:a", 0.0, 0.35)
	t.tween_property(light, "light_energy", 0.0, 0.35)
	t.chain().tween_interval(0.5)
	t.chain().tween_callback(boom.queue_free)


# ---------------------------------------------------------------- laser

static func laser(world: Node3D, from: Vector3, dir: Vector3, color := Color(0.25, 0.95, 1.0)) -> void:
	var length := 3.0
	var beam := Node3D.new()
	world.add_child(beam)
	beam.global_position = from + dir.normalized() * length * 0.5
	beam.global_basis = basis_along(dir)
	for layer in [[0.07, 0.35, color], [0.025, 1.0, Color(1, 1, 1)]]:
		var m := MeshInstance3D.new()
		var cyl := CylinderMesh.new()
		cyl.top_radius = layer[0]
		cyl.bottom_radius = layer[0]
		cyl.height = length
		m.mesh = cyl
		m.material_override = glow_material(layer[2], 5.0, layer[1])
		beam.add_child(m)
	var flare := MeshInstance3D.new()
	var s := SphereMesh.new()
	s.radius = 0.12
	s.height = 0.24
	flare.mesh = s
	flare.material_override = glow_material(color, 6.0, 0.8)
	flare.position = Vector3(0, -length * 0.5, 0)
	beam.add_child(flare)
	var t := beam.create_tween()
	t.tween_property(beam, "scale", Vector3(1.0, 1.0, 1.0), 0.08).from(Vector3(0.1, 1.0, 0.1))
	t.tween_interval(0.5)
	t.tween_property(beam, "scale", Vector3(0.05, 1.0, 0.05), 0.25).set_ease(Tween.EASE_IN)
	t.tween_callback(beam.queue_free)


# ---------------------------------------------------------------- flashlight / shield / jets

## A spotlight plus a soft visible cone; the caller aims it every frame with aim_beam().
static func flashlight(world: Node3D) -> Node3D:
	var root := Node3D.new()
	world.add_child(root)
	var spot := SpotLight3D.new()
	spot.name = "Spot"
	spot.light_color = Color(1.0, 0.95, 0.8)
	spot.light_energy = 5.0
	spot.spot_range = 7.0
	spot.spot_angle = 22.0
	root.add_child(spot)
	var cone := MeshInstance3D.new()
	cone.name = "Cone"
	var cyl := CylinderMesh.new()
	cyl.top_radius = 0.06
	cyl.bottom_radius = 0.8
	cyl.height = 2.6
	cyl.cap_top = false
	cyl.cap_bottom = false
	cone.mesh = cyl
	var mat := ShaderMaterial.new()
	mat.shader = CONE_SHADER
	cone.material_override = mat
	root.add_child(cone)
	return root


static func aim_beam(root: Node3D, from: Vector3, dir: Vector3) -> void:
	var d := dir.normalized()
	var spot := root.get_node("Spot") as SpotLight3D
	spot.global_position = from
	spot.look_at(from + d, Vector3.UP if absf(d.y) < 0.95 else Vector3.RIGHT)
	var cone := root.get_node("Cone") as MeshInstance3D
	cone.global_basis = basis_along(-d)                 # the narrow top sits at the lens
	cone.global_position = from + d * 1.3


static func shield(world: Node3D) -> MeshInstance3D:
	var m := MeshInstance3D.new()
	var s := SphereMesh.new()
	s.radius = 0.62
	s.height = 1.24
	m.mesh = s
	var mat := ShaderMaterial.new()
	mat.shader = SHIELD_SHADER
	m.material_override = mat
	world.add_child(m)
	m.scale = Vector3(0.05, 0.05, 0.05)
	m.create_tween().tween_property(m, "scale", Vector3(1.0, 1.0, 0.28), 0.35).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	return m


static func aim_shield(m: MeshInstance3D, from: Vector3, dir: Vector3) -> void:
	var d := dir.normalized()
	var s := m.scale
	var z := d
	var x := (Vector3.UP if absf(z.dot(Vector3.UP)) < 0.95 else Vector3.RIGHT).cross(z).normalized()
	m.global_basis = Basis(x, z.cross(x), z)
	m.scale = s
	m.global_position = from + d * 0.12
