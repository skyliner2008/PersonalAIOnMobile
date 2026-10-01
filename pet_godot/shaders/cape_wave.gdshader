// Cape cloth: waves in the wind, more towards the hem (UV.y 0 at the shoulders .. 1 at the hem).
// `lift` (0..1) streams it back and up while the pet flies.
shader_type spatial;
render_mode cull_disabled;

uniform vec3 color : source_color = vec3(0.85, 0.2, 0.23);
uniform float speed = 3.2;
uniform float amount = 0.07;
uniform float lift = 0.0;

void vertex() {
	float v = UV.y;
	float w = sin(TIME * speed + v * 5.0 + UV.x * 3.0) * 0.5 + sin(TIME * speed * 1.7 + v * 9.0) * 0.25;
	VERTEX.z -= (w * amount + lift * 0.45 * v) * v;          // backwards (model -Z is behind the pet)
	VERTEX.y += lift * 0.35 * v * v;
}

void fragment() {
	ALBEDO = color * (0.85 + 0.15 * (1.0 - UV.y));
	ROUGHNESS = 0.75;
}
