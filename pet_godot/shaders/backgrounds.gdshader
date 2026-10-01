// Full-screen backdrops behind the 3D pet (drawn on a CanvasLayer below the 3D world).
// 0 studio (like the model sheet), 1 night, 2 sunny, 3 cyber grid, 4 sakura, 5 space, 6 underwater,
// 7 city, 8 sunset, 9 snow, 10 candy, 11 hangar, 12 black (the main background)
shader_type canvas_item;

uniform int theme = 0;
uniform float aspect = 0.5625;        // width / height of the screen

// sin-free hash (Dave Hoskins): the sin(...) * 43758 kind turns into blocks at mobile precision
float hash(vec2 p) {
	vec3 p3 = fract(vec3(p.xyx) * 0.1031);
	p3 += dot(p3, p3.yzx + 33.33);
	return fract((p3.x + p3.y) * p3.z);
}

float noise(vec2 p) {
	vec2 i = floor(p);
	vec2 f = fract(p);
	f = f * f * (3.0 - 2.0 * f);
	return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
	           mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float stars(vec2 uv, float density, float t) {
	vec2 g = uv * density;
	vec2 id = floor(g);
	vec2 f = fract(g) - 0.5;
	float h = hash(id);
	vec2 o = (vec2(hash(id + 1.3), hash(id + 2.1)) - 0.5) * 0.6;
	float twinkle = 0.55 + 0.45 * sin(t * (1.0 + h * 3.0) + h * 20.0);
	return step(0.9, h) * smoothstep(0.09, 0.0, length(f - o)) * twinkle;
}

void fragment() {
	vec2 uv = UV;
	vec2 sq = vec2(uv.x * aspect, uv.y);     // square units: x scaled by the aspect ratio
	float t = TIME;
	vec3 c;
	if (theme == 0) {
		float r = length((uv - vec2(0.5, 0.42)) * vec2(1.0, 0.75));
		c = mix(vec3(0.955, 0.96, 0.968), vec3(0.80, 0.815, 0.845), smoothstep(0.08, 0.85, r));
	} else if (theme == 1) {
		c = mix(vec3(0.02, 0.03, 0.10), vec3(0.09, 0.12, 0.30), uv.y);
		c += vec3(0.9, 0.95, 1.0) * stars(sq, 34.0, t);
		vec2 m = sq - vec2(0.78 * aspect, 0.16);
		float moon = smoothstep(0.075, 0.07, length(m)) - smoothstep(0.075, 0.07, length(m - vec2(0.03, -0.02)));
		c = mix(c, vec3(1.0, 0.96, 0.82), clamp(moon, 0.0, 1.0));
		c += vec3(0.5, 0.55, 0.8) * exp(-length(m) * 9.0) * 0.25;
	} else if (theme == 2) {
		c = mix(vec3(0.40, 0.72, 1.0), vec3(1.0, 0.93, 0.72), smoothstep(0.0, 1.0, uv.y));
		vec2 sp = sq - vec2(0.8 * aspect, 0.12);
		float a = atan(sp.y, sp.x);
		float rays = 0.5 + 0.5 * sin(a * 14.0 + t * 0.5);
		c += vec3(1.0, 0.85, 0.4) * rays * exp(-length(sp) * 3.0) * 0.35;
		c = mix(c, vec3(1.0, 0.92, 0.55), smoothstep(0.1, 0.095, length(sp)));
		float cloud = smoothstep(0.55, 0.75, noise(vec2(sq.x * 4.0 + t * 0.05, sq.y * 9.0)));
		c = mix(c, vec3(1.0), cloud * smoothstep(0.55, 0.2, uv.y) * 0.7);
	} else if (theme == 3) {
		c = mix(vec3(0.05, 0.0, 0.12), vec3(0.22, 0.02, 0.32), uv.y);
		float horizon = 0.62;
		c += vec3(1.0, 0.3, 0.7) * exp(-abs(uv.y - horizon) * 18.0) * 0.5;
		if (uv.y > horizon) {
			float z = 1.0 / (uv.y - horizon + 0.02);
			vec2 g = vec2((uv.x - 0.5) * z * 0.9, z * 0.35 + t * 0.6);
			vec2 gl = abs(fract(g) - 0.5);
			float line = smoothstep(0.44, 0.5, max(gl.x, gl.y));
			c += vec3(0.1, 0.9, 1.0) * line * smoothstep(horizon, 1.0, uv.y) * 1.3;
		}
		c += vec3(0.6, 0.8, 1.0) * stars(sq, 30.0, t) * step(uv.y, horizon);
	} else if (theme == 4) {
		c = mix(vec3(1.0, 0.88, 0.93), vec3(0.97, 0.70, 0.82), uv.y);
		for (int i = 0; i < 14; i++) {
			float fi = float(i);
			float sx = fract(hash(vec2(fi, 1.0)) + t * (0.015 + 0.02 * hash(vec2(fi, 3.0))));
			float sy = fract(hash(vec2(fi, 2.0)) + t * (0.05 + 0.04 * hash(vec2(fi, 4.0))));
			vec2 pp = vec2(sx * aspect + 0.05 * sin(t + fi), sy) - sq;
			float ang = t * (1.0 + hash(vec2(fi, 5.0))) + fi;
			pp = mat2(vec2(cos(ang), sin(ang)), vec2(-sin(ang), cos(ang))) * pp;
			float petal = smoothstep(1.0, 0.8, length(pp / vec2(0.022, 0.012)));
			c = mix(c, vec3(1.0, 0.62, 0.78), petal * 0.9);
		}
	} else if (theme == 5) {
		float n = noise(sq * 3.0 + t * 0.02) * 0.6 + noise(sq * 7.0 - t * 0.03) * 0.4;
		c = mix(vec3(0.02, 0.01, 0.06), vec3(0.32, 0.10, 0.45), n * n);
		c += vec3(0.08, 0.28, 0.55) * pow(noise(sq * 2.0 + 5.0), 3.0);
		c += vec3(1.0) * stars(sq, 45.0, t);
	} else if (theme == 6) {                     // underwater: caustics, light shafts, bubbles
		c = mix(vec3(0.02, 0.42, 0.55), vec3(0.0, 0.07, 0.2), uv.y);
		float ca = noise(sq * 7.0 + vec2(t * 0.3, t * 0.2)) * noise(sq * 11.0 - vec2(t * 0.25, -t * 0.12));
		c += vec3(0.25, 0.75, 0.8) * smoothstep(0.22, 0.55, ca) * (1.0 - uv.y) * 0.7;
		c += vec3(0.4, 0.8, 0.9) * pow(max(0.0, sin((uv.x + uv.y * 0.35) * 16.0 + t * 0.4)), 10.0) * (1.0 - uv.y) * 0.25;
		for (int i = 0; i < 12; i++) {
			float fi = float(i);
			vec2 b = vec2(hash(vec2(fi, 1.0)) * aspect + 0.03 * sin(t * 2.0 + fi),
			              fract(hash(vec2(fi, 5.0)) - t * (0.05 + 0.05 * hash(vec2(fi, 9.0)))));
			float r = 0.012 + 0.02 * hash(vec2(fi, 7.0));
			float d = length(sq - b);
			c += vec3(0.7, 0.95, 1.0) * (smoothstep(r, r * 0.75, d) - smoothstep(r * 0.75, r * 0.45, d)) * 0.7;
		}
	} else if (theme == 7) {                     // city at night: dusk sky, two skyline layers, lit windows
		c = mix(vec3(0.03, 0.03, 0.12), vec3(0.38, 0.18, 0.42), smoothstep(0.05, 0.62, uv.y));
		c += vec3(0.9) * stars(sq, 30.0, t) * step(uv.y, 0.35);
		for (int layer = 0; layer < 2; layer++) {
			float fl = float(layer);
			float w = 9.0 + fl * 6.0;
			float bx = floor(sq.x * w + fl * 3.7);
			float top = 0.5 + fl * 0.12 + 0.18 * hash(vec2(bx, 3.0 + fl));
			if (uv.y > top) {
				vec3 wall = mix(vec3(0.12, 0.1, 0.22), vec3(0.04, 0.04, 0.1), fl);
				vec2 cell = vec2(fract(sq.x * w + fl * 3.7) * 4.0, (uv.y - top) * 60.0);
				vec2 id = floor(cell);
				vec2 f = fract(cell);
				float lit = step(0.55, hash(id + bx * 7.0 + fl)) * step(0.25, f.x) * step(f.x, 0.75) * step(0.3, f.y) * step(f.y, 0.8);
				lit *= 0.75 + 0.25 * sin(t * 0.7 + hash(id + bx) * 30.0);
				c = wall + vec3(1.0, 0.82, 0.45) * lit * (0.8 - 0.3 * fl);
			}
		}
	} else if (theme == 8) {                     // sunset over the sea
		float horizon = 0.6;
		c = mix(vec3(0.28, 0.16, 0.45), vec3(1.0, 0.55, 0.3), smoothstep(0.0, horizon, uv.y));
		vec2 sp = sq - vec2(0.5 * aspect, horizon - 0.05);
		c = mix(c, vec3(1.0, 0.85, 0.5), smoothstep(0.1, 0.095, length(sp)));
		c += vec3(1.0, 0.6, 0.3) * exp(-length(sp) * 5.0) * 0.4;
		if (uv.y > horizon) {
			float depth = (uv.y - horizon) / (1.0 - horizon);
			c = mix(vec3(0.85, 0.42, 0.35), vec3(0.12, 0.1, 0.3), depth);
			float glint = pow(max(0.0, sin(uv.y * 160.0 / (depth + 0.2) + t * 1.5 + noise(sq * 20.0) * 4.0)), 6.0);
			c += vec3(1.0, 0.75, 0.4) * glint * exp(-abs(sq.x - 0.5 * aspect) * 8.0) * (1.0 - depth) * 0.8;
		}
	} else if (theme == 9) {                     // snowy hills, falling snow
		c = mix(vec3(0.72, 0.8, 0.9), vec3(0.88, 0.92, 0.97), uv.y);
		float hill1 = 0.62 + 0.05 * sin(sq.x * 5.0 + 1.0);
		float hill2 = 0.72 + 0.04 * sin(sq.x * 8.0 + 2.5);
		c = mix(c, vec3(0.86, 0.9, 0.96), step(hill1, uv.y));
		c = mix(c, vec3(0.97, 0.98, 1.0), step(hill2, uv.y));
		for (int i = 0; i < 40; i++) {
			float fi = float(i);
			vec2 f = vec2(fract(hash(vec2(fi, 2.0)) + 0.04 * sin(t + fi)) * aspect,
			              fract(hash(vec2(fi, 4.0)) + t * (0.04 + 0.05 * hash(vec2(fi, 6.0)))));
			c = mix(c, vec3(1.0), smoothstep(0.008, 0.004, length(sq - f)));
		}
	} else if (theme == 10) {                    // candy: pastel stripes and sprinkles
		float st = fract((sq.x + uv.y) * 5.0 + t * 0.05);
		vec3 a = vec3(1.0, 0.8, 0.88);
		vec3 b = vec3(0.78, 0.95, 0.88);
		vec3 d = vec3(1.0, 0.95, 0.72);
		c = st < 0.33 ? a : (st < 0.66 ? b : d);
		for (int i = 0; i < 18; i++) {
			float fi = float(i);
			vec2 p = vec2(hash(vec2(fi, 11.0)) * aspect, fract(hash(vec2(fi, 13.0)) + t * 0.03));
			float ang = fi + t;
			vec2 q = mat2(vec2(cos(ang), sin(ang)), vec2(-sin(ang), cos(ang))) * (sq - p);
			float sprinkle = smoothstep(1.0, 0.8, length(q / vec2(0.02, 0.006)));
			vec3 col = vec3(hash(vec2(fi, 17.0)), hash(vec2(fi, 19.0)), hash(vec2(fi, 23.0))) * 0.6 + 0.35;
			c = mix(c, col, sprinkle);
		}
	} else {                                     // hangar / lab: panels, glowing floor grid, holo rings
		c = mix(vec3(0.07, 0.1, 0.16), vec3(0.14, 0.18, 0.26), uv.y);
		float panel = step(0.96, fract(sq.x * 6.0)) * step(uv.y, 0.62);
		c += vec3(0.35, 0.7, 1.0) * panel * (0.35 + 0.15 * sin(t * 2.0 + sq.x * 20.0));
		float horizon = 0.62;
		if (uv.y > horizon) {
			float z = 1.0 / (uv.y - horizon + 0.02);
			vec2 g = vec2((uv.x - 0.5) * z * 0.9, z * 0.35);
			vec2 gl = abs(fract(g) - 0.5);
			c += vec3(0.3, 0.75, 1.0) * smoothstep(0.46, 0.5, max(gl.x, gl.y)) * smoothstep(horizon, 1.0, uv.y) * 0.8;
		}
		vec2 hp = sq - vec2(0.5 * aspect, 0.3);
		float ring = abs(length(hp) - 0.28 - 0.01 * sin(t * 2.0));
		c += vec3(0.3, 0.8, 1.0) * smoothstep(0.006, 0.0, ring) * (0.5 + 0.5 * sin(atan(hp.y, hp.x) * 12.0 + t * 2.0));
	}
	if (theme == 12) {                      // black: the main background (the app's dark look)
		c = vec3(0.0);
	}
	COLOR = vec4(c, 1.0);
}
