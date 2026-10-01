// Hologram info card: tinted glass with rounded corners, a glowing rim, faint scanlines and a sheen.
// `reveal` (0..1) materialises it from the bottom up behind a bright scan line. An optional `content`
// texture (a SubViewport: markdown text, legends) is drawn on the glass.
shader_type spatial;
render_mode unshaded, blend_mix, depth_draw_never, cull_disabled, shadows_disabled;

uniform vec2 size = vec2(2.0, 1.2);                 // card size in metres (corners and rim stay round)
uniform float radius = 0.09;
uniform vec4 tint : source_color = vec4(0.035, 0.1, 0.18, 0.94);
uniform vec3 rim_color : source_color = vec3(0.38, 0.88, 1.0);
uniform float reveal = 1.0;
uniform float fade = 1.0;
uniform sampler2D content : source_color, filter_linear;
uniform bool has_content = false;

float sd_round_box(vec2 p, vec2 b, float r) {
	vec2 q = abs(p) - b + r;
	return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void fragment() {
	vec2 p = vec2(UV.x - 0.5, 0.5 - UV.y) * size;       // metres from the centre, y up
	float d = sd_round_box(p, size * 0.5, radius);
	float inside = 1.0 - smoothstep(-0.003, 0.003, d);
	float rim = 1.0 - smoothstep(0.004, 0.012, abs(d + 0.012));
	float halo = exp(-abs(d + 0.012) * 70.0) * 0.5;
	float height = 1.0 - UV.y;                               // 0 bottom .. 1 top
	float shown = 1.0 - smoothstep(reveal - 0.01, reveal, height);
	float front = exp(-abs(height - reveal) * 90.0) * (1.0 - step(0.999, reveal));
	float scan = 0.9 + 0.1 * sin(p.y * 260.0 - TIME * 4.0);
	float sheen = smoothstep(0.08, 0.0, abs(fract((p.x + p.y * 0.6) * 0.35 - TIME * 0.12) - 0.5) - 0.42) * 0.12;
	vec3 col = tint.rgb * scan * (0.85 + 0.3 * height) + rim_color * sheen;
	float a = tint.a;
	if (has_content) {
		vec4 c = texture(content, UV);
		col = mix(col, c.rgb, c.a);
		a = max(a, c.a);
	}
	col += rim_color * (rim * 1.4 + halo);
	col += rim_color * front * 2.5;
	ALBEDO = col;
	ALPHA = clamp(inside * shown * max(a, rim) + front * inside, 0.0, 1.0) * fade;
}
