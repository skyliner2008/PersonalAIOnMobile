// Live eyes on the robot's glass screen (the PR_Screen mesh, UV 0..1 over the whole glass).
// Expressions follow the model sheet's Expression Key.
shader_type spatial;
render_mode cull_back;

// 0 normal, 1 happy (^ ^), 2 sad, 3 wink, 4 love (hearts), 5 surprised, 6 sleepy, 7 angry,
// 8 dizzy (@ @, spinning), 9 cry (T T, running tears), 10 squint (> <)
uniform int expression = 0;
uniform vec3 eye_color : source_color = vec3(0.49, 0.97, 1.0);
uniform vec3 ui_color : source_color = vec3(0.12, 0.78, 0.91);
uniform float glow = 3.2;
uniform float blink = 0.0;            // 0 open .. 1 closed
uniform vec2 gaze = vec2(0.0);        // -1..1, +x = viewer's right, +y = up
uniform float aspect = 1.376;         // glass width / height
uniform float mouth = 0.0;            // talking mouth 0..1

float sd_round_box(vec2 p, vec2 b, float r) {
	vec2 q = abs(p) - b + r;
	return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float sd_segment(vec2 p, vec2 a, vec2 b) {
	vec2 pa = p - a;
	vec2 ba = b - a;
	float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
	return length(pa - ba * h);
}

// Inigo Quilez heart: tip at (0, 0), lobes near y = 1
float sd_heart(vec2 p) {
	p.x = abs(p.x);
	if (p.y + p.x > 1.0) {
		return sqrt(dot(p - vec2(0.25, 0.75), p - vec2(0.25, 0.75))) - sqrt(2.0) / 4.0;
	}
	vec2 a = p - vec2(0.0, 1.0);
	vec2 b = p - 0.5 * max(p.x + p.y, 0.0);
	return sqrt(min(dot(a, a), dot(b, b))) * sign(p.x - p.y);
}

// upper half of a ring with round ends: the ^ of a happy eye
float sd_arc(vec2 p, float r, float w) {
	if (p.y < 0.0) {
		return min(length(p - vec2(r, 0.0)), length(p - vec2(-r, 0.0))) - w;
	}
	return abs(length(p) - r) - w;
}

// Archimedean spiral, `gap` between the arms, clipped to radius r
float sd_spiral(vec2 p, float spin, float gap, float w, float r) {
	float k = (length(p) / gap - (atan(p.y, p.x) + spin) / 6.2831853);
	float d = abs(k - round(k)) * gap - w;
	return max(d, length(p) - r);
}

float eye_sd(vec2 p, int kind, float side, float t) {
	if (kind == 1) {
		return sd_arc(p + vec2(0.0, 0.05), 0.13, 0.034);
	}
	if (kind == 8) {
		return sd_spiral(p, t * 5.0 * side, 0.058, 0.016, 0.175);
	}
	if (kind == 9) {            // flat sad lid + a stream of tears down the glass
		float lid = sd_round_box(p - vec2(0.0, 0.04), vec2(0.15, 0.024), 0.024);
		lid = min(lid, sd_segment(p, vec2(-0.15 * side, 0.04), vec2(-0.2 * side, 0.1)) - 0.022);
		float wob = 0.034 + 0.01 * sin(p.y * 38.0 + t * 11.0);
		float stream = sd_round_box(p - vec2(0.07 * side, -0.25), vec2(wob, 0.23), wob);
		return min(lid, stream);
	}
	if (kind == 10) {           // > <  (the tip points at the nose)
		vec2 tip = vec2(-0.085 * side, 0.0);
		return min(sd_segment(p, tip, vec2(0.085 * side, 0.1)), sd_segment(p, tip, vec2(0.085 * side, -0.1))) - 0.03;
	}
	if (kind == 4) {
		float s = 0.33;
		return sd_heart(p / s + vec2(0.0, 0.5)) * s;
	}
	if (kind == 6) {
		return sd_round_box(p + vec2(0.0, 0.03), vec2(0.15, 0.022), 0.022);
	}
	vec2 half_size = kind == 5 ? vec2(0.2, 0.225) : vec2(0.172, 0.2);
	float open = clamp(1.0 - blink, 0.08, 1.0);
	vec2 b = vec2(half_size.x, half_size.y * open);
	float d = sd_round_box(p, b, min(b.x, b.y) * 0.96);
	if (kind == 2) {            // sad: the lid slopes down towards the outside
		d = max(d, dot(p - vec2(0.0, 0.07), normalize(vec2(0.45 * side, 1.0))));
	} else if (kind == 7) {     // angry: the lid slopes down towards the nose
		d = max(d, dot(p - vec2(0.0, 0.04), normalize(vec2(-0.6 * side, 1.0))));
	}
	return d;
}

float brackets(vec2 p) {
	vec2 c = vec2(aspect * 0.5 - 0.07, 0.5 - 0.07);
	vec2 q = abs(p);
	float len = 0.085;
	float d = min(sd_segment(q, c, c - vec2(len, 0.0)), sd_segment(q, c, c - vec2(0.0, len)));
	return d - 0.009;
}

void fragment() {
	vec2 p = vec2((UV.x - 0.5) * aspect, 0.5 - UV.y);
	vec2 look = gaze * vec2(0.07, 0.05);
	int left = expression;
	int right = expression == 3 ? 1 : expression;   // wink: right eye closes to ^
	if (expression == 3) {
		left = 0;
	}
	float d = min(eye_sd(p - vec2(-0.30, 0.02) - look, left, -1.0, TIME),
	              eye_sd(p - vec2(0.30, 0.02) - look, right, 1.0, TIME));
	if (expression == 5) {      // surprised: small round mouth
		d = min(d, abs(length(p - vec2(0.0, -0.29) - look * 0.5) - 0.045) - 0.016);
	}
	if (mouth > 0.01) {         // talking
		d = min(d, sd_round_box(p - vec2(0.0, -0.3) - look * 0.5, vec2(0.07, 0.012 + 0.04 * mouth), 0.02));
	}
	vec3 col = eye_color;
	float fill = 1.0 - smoothstep(0.0, 0.006, d);
	float halo = exp(-max(d, 0.0) * 26.0) * 0.5;
	float ui = 1.0 - smoothstep(0.0, 0.004, brackets(p));
	vec3 em = col * (fill * glow + halo * glow * 0.45) + ui_color * ui * 1.4;
	em *= 0.93 + 0.07 * sin(UV.y * 900.0);           // faint scanlines
	ALBEDO = vec3(0.0);
	ROUGHNESS = 0.16;
	SPECULAR = 0.22;
	EMISSION = em;
}
