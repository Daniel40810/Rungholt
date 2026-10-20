// check.frag — Selbsttest: die Dichte an 64 Orten (uP[i]) wie Fog.density, je Bildpunkt einer.
#include "fog_common.glsl"
uniform vec4 uP[64];
out vec4 frag;
void main() {
    int i = int(gl_FragCoord.x);
    vec3 p = uP[i].xyz;
    vec2 b = fogBank(p.xz);
    frag = vec4(fogDensity(p) * 1000.0, b.x, b.y, 1.0);
}
