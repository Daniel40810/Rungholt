// post.frag — Filmkurve und Gamma für die Demo.
uniform sampler2D uImg;
uniform float uExposure;
out vec4 frag;
void main() {
    vec3 c = texelFetch(uImg, ivec2(gl_FragCoord.xy), 0).rgb * uExposure;
    c = clamp((c * (2.51 * c + 0.03)) / (c * (2.43 * c + 0.59) + 0.14), 0.0, 1.0);
    frag = vec4(pow(c, vec3(1.0 / 2.2)), 1.0);
}
