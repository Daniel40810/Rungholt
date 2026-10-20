// slope.frag — Steigung der Ringkarte (dh/dx, dh/dz) aus Mittendifferenzen, kachelbar; das Meer legt sie auf seine Normale.
in vec2 vUv;
out vec4 frag;
uniform sampler2D uHeight;
uniform float uTexel;    // m je Texel
void main() {
    ivec2 n = textureSize(uHeight, 0);
    ivec2 p = ivec2(gl_FragCoord.xy);
    float l = texelFetch(uHeight, ivec2((p.x - 1 + n.x) % n.x, p.y), 0).r, r = texelFetch(uHeight, ivec2((p.x + 1) % n.x, p.y), 0).r;
    float d = texelFetch(uHeight, ivec2(p.x, (p.y - 1 + n.y) % n.y), 0).r, u = texelFetch(uHeight, ivec2(p.x, (p.y + 1) % n.y), 0).r;
    frag = vec4((r - l) / (2.0 * uTexel), (u - d) / (2.0 * uTexel), 0.0, 1.0);
}
