// transmittance.frag — Tabelle der Durchlässigkeit (TRANS_W × TRANS_H): von Höhe r in Richtung mu bis zur Oberkante,
// 64 quadratisch wachsende Schritte wie Scattering.transmittanceExact. Ausgabe linear, RGBA32F.
#include "sky_common.glsl"
out vec4 frag;
void main() {
    vec2 x = (gl_FragCoord.xy - 0.5) / vec2(TRANS_W - 1, TRANS_H - 1);
    vec2 rm = transRMu(x);
    float r = rm.x, mu = rm.y, d = distToTop(r, mu);
    vec3 od = vec3(0.0);
    const int N = 64;
    for (int k = 0; k < N; k++) {
        float a = float(k) / float(N), b = float(k + 1) / float(N);
        float t0 = d * a * a, t1 = d * b * b, t = 0.5 * (t0 + t1);
        float h = sqrt(r * r + t * t + 2.0 * r * mu * t) - R_BOTTOM;
        od += extinction(h) * (t1 - t0);
    }
    frag = vec4(exp(-od), 1.0);
}
