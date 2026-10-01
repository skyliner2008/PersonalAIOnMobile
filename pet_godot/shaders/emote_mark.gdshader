// Comic marks drawn on a camera-facing quad: 0 anger mark (four red arcs, the 💢),
// 1 sweat drop, 2 battery (fill = level; the fill blinks red when it is nearly empty).
shader_type spatial;
render_mode unshaded, blend_mix, depth_test_disabled, cull_disabled, shadows_disabled;

uniform int kind = 0;
uniform float alpha = 1.0;
uniform float level = 1.0;

void vertex() {
	// billboard: keep the node's position and scale, face the camera
	MODELVIEW_MATRIX = VIEW_MATRIX * mat4(INV_VIEW_MATRIX[0] * length(MODEL_MATRIX[0].xyz),
		INV_VIEW_MATRIX[1] * length(MODEL_MATRIX[1].xyz), INV_VIEW_MATRIX[2], MODEL_MATRIX[3]);
}

float sd_round_box(vec2 p, vec2 b, float r) {
	vec2 q = abs(p) - b + r;
	return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

// a thick arc of the circle (c, r) that bows towards the middle of the mark, with round caps
float arc(vec2 p, vec2 c, float r, float half_angle, float w) {
	vec2 q = p - c;
	vec2 dir = -normalize(c);
	float a = acos(clamp(dot(normalize(q), dir), -1.0, 1.0));
	if (a <= half_angle) {
		return abs(length(q) - r) - w;
	}
	float s = sign(dir.x * q.y - dir.y * q.x);
	float end = atan(dir.y, dir.x) + s * half_angle;
	return length(q - r * vec2(cos(end), sin(end))) - w;
}

float inside(float d) {
	return 1.0 - smoothstep(-0.01, 0.01, d);
}

void fragment() {
	vec2 p = UV * 2.0 - 1.0;
	vec3 col;
	float a;
	if (kind == 2) {
		// battery: dark shell, see-through inside, coloured fill, white outline around it all
		vec2 q = vec2(p.x, -p.y);
		float outer = sd_round_box(q - vec2(-0.06, 0.0), vec2(0.78, 0.42), 0.12);
		float nub = sd_round_box(q - vec2(0.8, 0.0), vec2(0.07, 0.17), 0.05);
		float shape = min(outer, nub);
		float ring = min(max(outer, -(outer + 0.11)), nub);
		float lv = clamp(level, 0.0, 1.0);
		float inner_w = 1.24 * max(lv, 0.04);
		float fill = sd_round_box(q - vec2(-0.68 + inner_w * 0.5, 0.0), vec2(inner_w * 0.5, 0.22), 0.05);
		vec3 fill_col = lv < 0.25 ? vec3(1.5, 0.22, 0.22) : (lv < 0.6 ? vec3(1.4, 1.05, 0.2) : vec3(0.4, 1.45, 0.5));
		float blink = lv < 0.2 ? step(0.5, fract(TIME * 2.2)) : 1.0;
		col = vec3(0.07, 0.08, 0.11);
		a = 0.55;
		float kf = inside(fill) * blink;
		col = mix(col, fill_col, kf);
		a = mix(a, 1.0, kf);
		float kr = inside(ring);
		col = mix(col, vec3(0.16, 0.18, 0.24), kr);
		a = mix(a, 1.0, kr);
		float body = inside(shape);
		float halo = 1.0 - smoothstep(0.04, 0.08, shape);
		col = mix(vec3(1.0), col, body);
		a = max(body * a, halo * (1.0 - body));
	} else {
		float d;
		vec3 edge = vec3(0.35, 0.02, 0.05);
		if (kind == 0) {
			d = 1e3;
			for (int i = 0; i < 4; i++) {
				float ang = 0.785398 + float(i) * 1.570796;
				d = min(d, arc(p, vec2(cos(ang), sin(ang)) * 0.9, 0.45, 0.55, 0.085));
			}
			col = vec3(0.95, 0.15, 0.2);
		} else {
			vec2 q = vec2(p.x, -p.y);                                   // y up
			float circle = length(q - vec2(0.0, -0.2)) - 0.42;
			float w = 0.42 * clamp((0.8 - q.y) / 1.0, 0.0, 1.0);        // cone narrowing to the tip
			float cone = max(abs(q.x) - w, max(q.y - 0.8, -0.2 - q.y));
			d = min(circle, cone * 0.9);
			col = mix(vec3(0.55, 0.85, 1.0), vec3(1.0), smoothstep(0.18, 0.0, length(q - vec2(-0.14, -0.1))));
			edge = vec3(0.1, 0.35, 0.6);
		}
		col = mix(edge, col, inside(d));
		a = 1.0 - smoothstep(0.03, 0.06, d);
	}
	ALBEDO = col;
	ALPHA = a * alpha;
}
