// skypano.frag — der Himmel als Panorama (Azimut von Nord über die Breite, Höhe −90°…+90° über die Höhe) für die
// Spiegelungen im Wasser. Gleiche Farbe wie sky.frag; 256 × 128 Bildpunkte, jedes Bild neu (kostet fast nichts).
// Aus Glacier und Haithabu; Phase 3 ersetzt beides durch com.dan.sky.
#include "common.glsl"
in vec2 vUV;
out vec4 frag;
void main() {
    float az = (vUV.x - 0.5) * 6.28318530718;
    float el = (vUV.y - 0.5) * 3.14159265359;
    vec3 d = vec3(sin(az) * cos(el), sin(el), -cos(az) * cos(el));
    frag = vec4(skyColor(d), 1.0);
}
