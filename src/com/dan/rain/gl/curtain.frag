// curtain.frag — Regenvorhänge: Vom Rand des Tropfenkastens bis zur Szene (höchstens 20 km) läuft der Strahl durch die
// Karte der Vorhänge (höchstens bis zu ihrem Rand), bis er die Wolkenuntergrenze erreicht. Regen löscht aus (0,25 · R^0,63 je km) und streut das
// Licht von Himmel und Sonne zurück; senkrechte Schleier, die mit dem Wind ziehen, geben ihm Struktur.
// Ausgabe vormultipliziert über die Szene.
#include "rain_common.glsl"
in vec2 vUv;
out vec4 frag;
uniform mat4 uInvVP;
uniform vec3 uEye;
uniform sampler2D uSceneDepth;
uniform int uReversed;
uniform float uNear, uFar;
uniform float uStart;         // m: hier beginnt der Vorhang (davor zeichnen die Streifen)
uniform int uSteps;
uniform float uTime;
uniform vec3 uSunDir, uSunColor, uMoonDir, uMoonColor, uSkyTop, uSkyHorizon;
uniform float uStrength;

float hg(float c, float g) { return (1.0 - g * g) / (12.566 * pow(1.0 + g * g - 2.0 * g * c, 1.5)); }

float vn(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = fract(sin(dot(i, vec2(127.1, 311.7))) * 43758.55), b = fract(sin(dot(i + vec2(1, 0), vec2(127.1, 311.7))) * 43758.55);
    float c = fract(sin(dot(i + vec2(0, 1), vec2(127.1, 311.7))) * 43758.55), d = fract(sin(dot(i + vec2(1, 1), vec2(127.1, 311.7))) * 43758.55);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float sceneDist(vec3 dir) {
    float d = texelFetch(uSceneDepth, ivec2(gl_FragCoord.xy), 0).r;
    float z;
    if (uReversed == 1) z = d <= 0.0 ? 1e9 : uNear / d;
    else { float n = d * 2.0 - 1.0; z = d >= 1.0 ? 1e9 : 2.0 * uNear * uFar / (uFar + uNear - n * (uFar - uNear)); }
    return z;   // Abstand entlang der Blickachse; für die Strahllänge reicht das bis auf den Bildrand
}

void main() {
    if (uRainC.z <= 0.0) discard;
    vec4 p0 = uInvVP * vec4(vUv * 2.0 - 1.0, uReversed == 1 ? 1.0 : -1.0, 1.0);
    vec4 p1 = uInvVP * vec4(vUv * 2.0 - 1.0, uReversed == 1 ? 0.0001 : 1.0, 1.0);
    vec3 a = p0.xyz / p0.w, b = p1.xyz / p1.w;
    vec3 dir = normalize(b - a);
    // nicht über die Karte hinaus (halbe Kante 8 km): dahinter wüsste niemand, ob es regnet
    float tEnd = min(sceneDist(dir), uRainMap.w > 0.5 ? 0.49 * uRainMap.z * uRainMap.w : 20000.0);
    if (dir.y > 1e-4) tEnd = min(tEnd, (uCloudBase - uEye.y) / dir.y);
    if (uEye.y > uCloudBase && dir.y >= 0.0) discard;
    float tStart = uStart;
    if (tEnd <= tStart) discard;
    // Schritte wachsen mit dem Abstand (nah fein, fern grob)
    float ratio = tEnd / tStart;
    vec2 wind = normalize(uRainC.xy + vec2(1e-4, 0.0));
    vec2 side = vec2(-wind.y, wind.x);
    float tau = 0.0;
    vec3 acc = vec3(0.0);
    float T = 1.0;
    float cs = dot(dir, uSunDir), cm = dot(dir, uMoonDir);
    // im Schauer ist es dunkler als am hellen Horizont dahinter: die Wolke darüber nimmt das Licht von oben
    vec3 light = mix(uSkyTop, uSkyHorizon, 0.15) * 0.7 + uSunColor * (hg(cs, 0.6) * 12.566 * 0.05 + 0.01)
               + uMoonColor * (hg(cm, 0.6) * 12.566 * 0.06 + 0.015);
    float tPrev = tStart;
    for (int s = 1; s <= 64; s++) {
        if (s > uSteps) break;
        float t = tStart * pow(ratio, float(s) / float(uSteps));
        float tm = 0.5 * (t + tPrev), dt = t - tPrev;
        tPrev = t;
        vec3 p = uEye + dir * tm;
        float local = uRainC.z * rainCurtain(p.xz);
        if (local <= 0.001) continue;
        // senkrechte Schleier: quer zum Wind gestreift, fallen langsam, ziehen mit dem Wind
        vec2 q = p.xz - uRainC.xy * uTime;
        float streak = 0.55 + 0.9 * vn(vec2(dot(q, side) / 35.0, p.y / 420.0 + uTime * 0.05)) * (0.6 + 0.4 * vn(vec2(dot(q, side) / 9.0, dot(q, wind) / 120.0)));
        // nach oben, zur Wolke hin, dichter; am Wasser leicht weniger (der Regen ist durch)
        float h = clamp(p.y / max(uCloudBase, 1.0), 0.0, 1.0);
        float sigma = 0.25e-3 * pow(local, 0.63) * streak * (0.85 + 0.3 * h);
        float dT = exp(-sigma * dt);
        acc += T * (1.0 - dT) * light;
        T *= dT;
        if (T < 0.01) break;
    }
    float alpha = (1.0 - T) * uStrength;
    frag = vec4(acc * uStrength, alpha);
}
