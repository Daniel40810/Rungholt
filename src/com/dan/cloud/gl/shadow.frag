// shadow.frag — Wolkenschatten von oben: je Texel ein Punkt auf Meereshöhe, 16 Schritte zum Gestirn durch die
// Schicht, dazu die hohe Schicht; Ergebnis der Anteil direkten Lichts. Wie Clouds.shadow (Java).
#include "cloud_common.glsl"
in vec2 vUV;
out vec4 frag;
uniform vec3 uLightDir;
uniform vec4 uRect;   // x, z der Ecke, Kantenlänge in m
void main() {
    float x = uRect.x + vUV.x * uRect.z, z = uRect.y + vUV.y * uRect.z;
    vec3 d = normalize(vec3(uLightDir.x, max(0.05, uLightDir.y), uLightDir.z));
    vec2 seg = shell(vec3(x, 0.0, z), d, uType1.x, uType1.x + uType1.z);
    float tau = 0.0, dummy;
    if (seg.x >= 0.0) {
        float t0 = seg.x, t1 = min(seg.y, seg.x + 40000.0), dt = (t1 - t0) / 16.0;
        for (int k = 0; k < 16; k++) {
            float t = t0 + (float(k) + 0.5) * dt;
            tau += density(vec3(x, 0.0, z) + d * t, false, dummy) * dt;
        }
    }
    float th = HIGH_HEIGHT / d.y;
    tau += highTau(vec2(x, z) + d.xz * th) / d.y;
    for (int i = 0; i < LENS_SLICES; i++) {
        float tl = (LENS_HEIGHT + float(i) * LENS_STEP) / d.y;
        tau += lensTau(vec2(x, z) + d.xz * tl, i) / d.y;
    }
    frag = vec4(exp(-tau * SHADOW_SOFT), 0.0, 0.0, 1.0);
}
