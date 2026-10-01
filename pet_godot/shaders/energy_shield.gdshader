// Energy shield lens in front of the hand: glowing rim, hex cells, a pulse running across.
shader_type spatial;
render_mode unshaded, blend_add, depth_draw_never, cull_disabled, shadows_disabled;

uniform vec3 color : source_color = vec3(0.25, 0.9, 1.0);
uniform float strength = 1.0;

float hex(vec2 p) {
	p *= vec2(1.0, 1.1547);
	p.x += mod(floor(p.y), 2.0) * 0.5;
	vec2 f = abs(fract(p) - 0.5);
	return max(f.x * 1.5 + f.y, f.y * 2.0);
}

void fragment() {
	float rim = pow(1.0 - abs(dot(normalize(NORMAL), normalize(VIEW))), 2.2);
	float cells = smoothstep(0.88, 0.97, hex(UV * vec2(22.0, 11.0)));
	float pulse = 0.5 + 0.5 * sin(TIME * 4.0 - UV.y * 18.0);
	float a = strength * (0.12 + rim * 0.9 + cells * 0.35 * pulse);
	ALBEDO = color * a;
	ALPHA = clamp(a, 0.0, 1.0);
}
