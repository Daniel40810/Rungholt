// glow.frag — weicher Lichtpunkt, additiv
in vec3 vCol;
out vec4 frag;
void main() {
    vec2 p = gl_PointCoord * 2.0 - 1.0;
    float r2 = dot(p, p);
    float k = exp(-r2 * 6.0) + 0.6 * exp(-r2 * 60.0);
    frag = vec4(vCol * k, 0.0);
}
