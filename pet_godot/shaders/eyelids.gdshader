// The screen's eyelids for the robot's sleep (the app's Avatar mode, PetSelfCare): dark lids come down from the top
// and up from the bottom, their edges arched like an eye's (the opening widest in the middle), until they meet.
// open 1 = no lids … 0 = shut.
shader_type canvas_item;

uniform float open : hint_range(0.0, 1.0) = 1.0;
uniform vec3 lid_color = vec3(0.012, 0.004, 0.008);

void fragment() {
	float shut = 1.0 - open;
	float dx = (UV.x - 0.5) * 2.0;                        // -1 … 1 across the screen
	// how far each lid reaches in from its edge of the screen: further at the sides (the eye's corners); shut,
	// they overlap a little in the middle so no seam is left
	float reach = shut * (0.52 + 0.22 * dx * dx);
	float y = min(UV.y, 1.0 - UV.y);                      // distance from the nearer edge, top or bottom
	float lid = 1.0 - smoothstep(reach - 0.012, reach + 0.012, y);
	// the lids' shadow just inside the opening, deeper as they close
	float shade = (1.0 - smoothstep(reach, reach + 0.15, y)) * 0.5 * shut;
	COLOR = vec4(lid_color, clamp(max(lid, shade), 0.0, 1.0));
}
