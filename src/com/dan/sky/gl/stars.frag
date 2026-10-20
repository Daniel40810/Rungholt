// stars.frag — Kern und Hof eines Lichtpunkts; Summe über den Punkt = Bestrahlung. Mischung: addierend.
flat in vec3 vP;
flat in float vSize;
out vec4 frag;
const float PI = 3.14159265359, S0 = 0.65, S1 = 2.4, HALO = 0.06;
void main() {
    vec2 r = (gl_PointCoord - 0.5) * vSize;
    float r2 = dot(r, r);
    float w = (1.0 - HALO) / (2.0 * PI * S0 * S0) * exp(-r2 / (2.0 * S0 * S0)) + HALO / (2.0 * PI * S1 * S1) * exp(-r2 / (2.0 * S1 * S1));
    frag = vec4(vP * w, 1.0);
}
