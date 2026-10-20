// fog.frag — der Blick durch den Seenebel bis zur Szene (Tiefe als Kopie) oder zur größten Entfernung. Nur das Stück
// des Blicks in der Schicht wird abgetastet, dichter nahe dem Auge. Licht: Sonne und Mond mit Vorwärtsstreuung
// (Henyey-Greenstein) und einem Anteil vielfach gestreuten Lichts, gedämpft durch den Nebel über dem Punkt; dazu der
// Himmel. Ausgabe: (Streulicht, Durchlässigkeit) für die Mischung ONE, SRC_ALPHA: Bild = Streulicht + Bild · T.
#include "fog_common.glsl"
uniform mat4 uInvVP;
uniform vec3 uCam;
uniform sampler2D uDepth;
uniform int uReversed;
uniform sampler2D uMap;
uniform vec4 uMapRect;          // x0, z0, Kantenlänge, 1/Kantenlänge
uniform int uSteps;
uniform float uMaxDist;
uniform vec2 uViewport;
uniform vec3 uSunDir, uSunCol, uMoonDir, uMoonCol, uAmbTop, uAmbHor;
uniform float uJitter;          // < 0: Rauschen je Bildpunkt, sonst fester Versatz 0..1
uniform int uFrame;
out vec4 frag;

float hgF(float c, float g) { return (1.0 - g * g) / (12.566 * pow(1.0 + g * g - 2.0 * g * c, 1.5)); }

void main() {
    vec2 uv = gl_FragCoord.xy / uViewport;
    float d = texelFetch(uDepth, ivec2(gl_FragCoord.xy), 0).r;
    bool sky = uReversed == 1 ? d <= 0.0 : d >= 1.0;
    float zN = sky ? (uReversed == 1 ? 1e-6 : 0.999999) : (uReversed == 1 ? d : d * 2.0 - 1.0);
    vec4 w = uInvVP * vec4(uv * 2.0 - 1.0, zN, 1.0);
    vec3 P = w.xyz / w.w, dir = normalize(P - uCam);
    float tEnd = sky ? uMaxDist : min(length(P - uCam), uMaxDist);
    float y0 = uFogC.x - 2.0, y1 = uFogC.x + uFogA.z * 1.25 * (1.0 + uFogA.w * 0.5);
    float t0 = 0.0, t1 = tEnd;
    if (abs(dir.y) > 1e-5) {
        float ta = (y0 - uCam.y) / dir.y, tb = (y1 - uCam.y) / dir.y;
        t0 = max(0.0, min(ta, tb));
        t1 = min(tEnd, max(ta, tb));
    } else if (uCam.y < y0 || uCam.y > y1) {
        t1 = -1.0;
    }
    if (t1 <= t0 || uFogA.x <= 1e-7) { frag = vec4(0.0, 0.0, 0.0, 1.0); return; }
    float j = uJitter >= 0.0 ? uJitter : fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))) + float(uFrame & 63) * 0.6180339);
    float cs = dot(dir, uSunDir), cm = dot(dir, uMoonDir);
    vec3 sunL = uSunCol * (hgF(cs, 0.75) * 12.566 * 0.3 + 0.12);
    vec3 moonL = uMoonCol * (hgF(cm, 0.75) * 12.566 * 0.3 + 0.12);
    float sunUp = max(0.08, uSunDir.y), moonUp = max(0.08, uMoonDir.y);
    float T = 1.0;
    vec3 S = vec3(0.0);
    int n = max(1, uSteps);
    float span = t1 - t0;
    for (int i = 0; i < n; i++) {
        float ua = float(i) / float(n), ub = float(i + 1) / float(n);
        float ta = t0 + span * pow(ua, 1.6), tb = t0 + span * pow(ub, 1.6);
        float tm = ta + (tb - ta) * j, dt = tb - ta;
        vec3 p = uCam + dir * tm;
        vec2 m = (p.xz - uMapRect.xy) * uMapRect.w;
        vec2 bt = (m.x > 0.0 && m.y > 0.0 && m.x < 1.0 && m.y < 1.0) ? texture(uMap, m).rg : fogBank(p.xz);
        float dens = fogDensityBT(p, bt);
        if (dens <= 0.0) continue;
        float tr = exp(-dens * dt);
        float h = p.y - uFogC.x, above = max(0.0, bt.y - h);
        // Licht von oben durch den Nebel darüber (vielfach gestreut kommt mehr an als der gerade Weg: Faktor 0,35)
        float sT = exp(-dens * above / sunUp * 0.35), mT = exp(-dens * above / moonUp * 0.35);
        vec3 amb = mix(uAmbHor, uAmbTop, 0.35 + 0.4 * clamp(h / max(1.0, bt.y), 0.0, 1.0)) * (0.75 + 0.25 * exp(-dens * above * 0.3));
        vec3 L = sunL * sT + moonL * mT + amb;
        S += T * (1.0 - tr) * L;
        T *= tr;
        if (T < 0.003) { T = 0.0; break; }
    }
    frag = vec4(S, T);
}
