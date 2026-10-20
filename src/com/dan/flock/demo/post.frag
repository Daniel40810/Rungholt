// post.frag — Filmkurve (ACES-Näherung) und Gamma für die Vorführung.
in vec2 vUV;
out vec4 frag;
uniform sampler2D uHdr;
void main() {
    vec3 x = texture(uHdr, vUV).rgb;
    vec3 c = clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
    frag = vec4(pow(c, vec3(1.0 / 2.2)), 1.0);
}
