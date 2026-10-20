// skyview.frag — der ganze Himmel vom Auge aus als Tabelle (SKYVIEW_W × SKYVIEW_H, RGBA32F): Azimut von Nord über
// die Breite, über die Höhe nicht linear (dicht an der Kimm, nach Hillaire). Je Texel ein Strahlengang mit Sonne
// und Mond, dazu der natürliche Nachthimmel, Lichtglocken und bei Bedeckung der Schleier. Wie Sky.State.radiance().
#include "sky_common.glsl"
#include "sky_view.glsl"
out vec4 frag;
uniform vec3 uSunDir, uSunE, uMoonDir, uMoonE;
uniform float uEyeR;
uniform float uNight;          // Leuchtdichte des Nachthimmels im Zenit
uniform vec4 uDomes[4];        // Azimut (rad), Entfernung (km), Stärke, 0
uniform float uVis;            // Sicht in km
uniform float uVeil;           // Schleier 0..1
uniform vec3 uOvercast;        // Leuchtdichte des Schleiers im Zenit (Moon-Spencer)
const vec3 NIGHT_COLOR = vec3(0.86, 1.02, 1.04);

vec3 nightSky(vec3 d) {
    float y = max(0.0, d.y);
    float k = pow(R_BOTTOM / (R_BOTTOM + 90000.0), 2.0);
    float vr = 1.0 / sqrt(1.0 - k * (1.0 - y * y));
    return uNight * NIGHT_COLOR * vr * transmittance(uEyeR, y);
}

vec3 domes(vec3 d) {
    vec3 o = vec3(0.0);
    float el = asin(clamp(d.y, -1.0, 1.0));
    if (el < -0.02) return o;
    float az = azimuthOf(d);
    for (int i = 0; i < 4; i++) {
        float s = uDomes[i].z;
        if (s <= 0.0) continue;
        float dist = uDomes[i].y;
        float da = az - uDomes[i].x;
        da = abs(da - 2.0 * PI * round(da / (2.0 * PI)));
        float w = atan(12.0 / dist) + 0.05;
        float haze = exp(-dist / (1.5 * uVis)) * (1.0 + 6.0 / uVis);
        float g = s * 5.0 * uNight * haze * exp(-da * da / (w * w)) * exp(-max(0.0, el) / (0.03 + 0.12 * w));
        o += g * vec3(1.35, 0.92, 0.52);
    }
    return o;
}

void main() {
    vec3 d = skyViewDir(gl_FragCoord.xy / vec2(SKYVIEW_W, SKYVIEW_H), uEyeR);
    vec3 L, f, tp;
    integrate(uEyeR, d, uSunDir, uSunE, uMoonDir, uMoonE, 32, true, true, L, f, tp);
    vec3 clear = L + nightSky(d) + domes(d);
    vec3 ov = uOvercast * (1.0 + 2.0 * max(0.0, d.y)) / 3.0;
    frag = vec4(mix(clear, ov, uVeil), 1.0);
}
