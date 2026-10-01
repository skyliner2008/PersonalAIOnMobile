// Visible flashlight beam: bright at the lens (mesh top, +Y), fading along the beam and at the sides.
shader_type spatial;
render_mode unshaded, blend_add, depth_draw_never, cull_disabled, shadows_disabled;

uniform vec3 color : source_color = vec3(1.0, 0.93, 0.72);
uniform float strength = 1.1;
uniform float half_height = 1.3;

varying float along;

void vertex() {
	along = clamp((half_height - VERTEX.y) / (2.0 * half_height), 0.0, 1.0);   // 0 at the lens .. 1 far end
}

void fragment() {
	float side = abs(dot(normalize(NORMAL), normalize(VIEW)));                  // soft edges
	float a = strength * (1.0 - along) * smoothstep(0.05, 0.8, side) * 0.6;
	ALBEDO = color * a;
	ALPHA = a;
}
