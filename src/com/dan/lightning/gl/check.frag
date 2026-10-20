// check.frag — Selbsttest: Beleuchtungsstärke aus lightning.glsl an 64 Orten mit Normalen (je Bildpunkt einer).
#include "lightning.glsl"
out vec4 frag;
uniform vec4 uP[64];
uniform vec4 uN[64];
void main() {
    int i = int(gl_FragCoord.x) + int(gl_FragCoord.y) * 8;
    frag = vec4(ltgIrradiance(uP[i].xyz, uN[i].xyz), 1.0);
}
