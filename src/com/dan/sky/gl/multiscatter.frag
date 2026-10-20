// multiscatter.frag — Tabelle der Mehrfachstreuung Ψ (MS_N²) nach Hillaire 2020, Abschnitt 5.5: je Texel 8 × 8
// Richtungen über die Kugel, isotrop gestreut, Ψ = L₂ / (1 − f_ms). Wie Scattering.buildMultiScattering.
#include "sky_common.glsl"
out vec4 frag;
void main() {
    vec2 x = (gl_FragCoord.xy - 0.5) / float(MS_N - 1);
    float muS = -1.0 + 2.0 * x.x;
    float r = R_BOTTOM + max(1.0, min(R_TOP - R_BOTTOM - 1.0, x.y * (R_TOP - R_BOTTOM)));
    vec3 sun = vec3(sqrt(max(0.0, 1.0 - muS * muS)), muS, 0.0);
    vec3 l2 = vec3(0.0), fs = vec3(0.0);
    const int D = 8;
    for (int a = 0; a < D; a++)
        for (int b = 0; b < D; b++) {
            float th = 2.0 * PI * (float(a) + 0.5) / float(D), ph = acos(1.0 - 2.0 * (float(b) + 0.5) / float(D));
            vec3 v = vec3(cos(th) * sin(ph), cos(ph), sin(th) * sin(ph));
            vec3 L, f, tp;
            integrate(r, v, sun, vec3(1.0), sun, vec3(0.0), 20, false, false, L, f, tp);
            l2 += L;
            fs += f;
        }
    float nd = float(D * D);
    frag = vec4(l2 / nd / (1.0 - min(fs / nd, vec3(0.99))), 1.0);
}
