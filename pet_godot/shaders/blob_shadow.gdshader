// Soft round contact shadow under the floating pet (mobile-friendly, no shadow maps needed).
shader_type spatial;
render_mode unshaded, blend_mix, depth_draw_never, cull_disabled, shadows_disabled;

uniform float strength = 0.35;

void fragment() {
	float r = length(UV * 2.0 - 1.0);
	float a = 1.0 - smoothstep(0.0, 1.0, r);
	ALBEDO = vec3(0.02, 0.03, 0.06);
	ALPHA = a * a * strength;
}
