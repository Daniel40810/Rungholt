// fog_map.frag — Karte der Bänke um das Auge: R = Bank 0..1, G = Obergrenze (m). Spart im Blick je Schritt sechs
// Rauschwerte; die Schwaden rechnet fog.frag weiter genau.
#include "fog_common.glsl"
uniform vec4 uMapRect;   // x0, z0, Kantenlänge (m), Texel
out vec4 frag;
void main() {
    vec2 xz = uMapRect.xy + gl_FragCoord.xy * uMapRect.w;
    frag = vec4(fogBank(xz), 0.0, 1.0);
}
