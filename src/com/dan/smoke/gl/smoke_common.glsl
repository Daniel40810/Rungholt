// smoke_common.glsl — die Rauchfahne als Formel, Zeile für Zeile wie com.dan.smoke.Plume.
// Eine Fahne sind 20 Zahlen (fünf vec4): siehe Plume.constants.
struct SmokePlume { vec4 a; vec4 b; vec4 c; vec4 d; vec4 e; };
layout(std430, binding = 0) readonly buffer SmokePlumes { SmokePlume plumes[]; };

uint smokeHash(uint x) {
    x ^= x >> 16u;
    x *= 0x7feb352du;
    x ^= x >> 15u;
    x *= 0x846ca68bu;
    x ^= x >> 16u;
    return x;
}

float smokeRnd(uint seed, int k, int ch) {
    return float(smokeHash(smokeHash(uint(k * 8 + ch)) ^ seed) >> 8u) * (1.0 / 16777216.0);
}

float smokeRise(SmokePlume p, float a) {
    float dhe = p.c.w, free = p.d.x * pow(max(a, 0.0), 2.0 / 3.0);
    return dhe * tanh(free / dhe);
}

vec2 smokeSigma(SmokePlume p, float a) {
    float dh = smokeRise(p, a);
    return vec2(sqrt(0.16 * dh * dh + 2.0 * p.d.z * a + 0.09), sqrt(0.16 * dh * dh + 2.0 * p.d.w * a + 0.09));
}

vec3 smokeCenter(SmokePlume p, float a) {
    float dh = smokeRise(p, a);
    float run = p.d.y * a * (1.0 + 0.012 * dh);
    return vec3(p.a.x + p.e.y * run, p.a.y + dh, p.a.z + p.e.z * run);
}

float smokeSmooth(float e0, float e1, float x) {
    float t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

// Ball i: Ort und Halbmesser (posR), Deckkraft, Alter, Zufall für das Aussehen
void smokeParticle(SmokePlume p, int i, out vec4 posR, out float alpha, out float age, out float rnd) {
    float da = p.b.w, life = p.b.z;
    int k = floatBitsToInt(p.e.x) - i;
    float a = (float(i) + p.c.z) * da;
    uint seed = floatBitsToUint(p.b.y);
    float r1 = smokeRnd(seed, k, 0) + smokeRnd(seed, k, 1) - 1.0;
    float r2 = smokeRnd(seed, k, 2) + smokeRnd(seed, k, 3) - 1.0;
    float r3 = smokeRnd(seed, k, 4) + smokeRnd(seed, k, 5) - 1.0;
    vec3 cen = smokeCenter(p, a);
    vec2 sg = smokeSigma(p, a);
    vec3 pos = cen + vec3(r1 * sg.x * 0.9, r2 * sg.y * 0.7, r3 * sg.x * 0.9);
    float radius = min(25.0, (0.9 + 1.1 * sg.x) * p.c.y * (0.8 + 0.4 * smokeRnd(seed, k, 6)));
    float fadeIn = smokeSmooth(0.0, 1.5 * da, a), fadeOut = 1.0 - smokeSmooth(0.6 * life, life, a);
    alpha = p.a.w * fadeIn * fadeOut * min(0.8, 2.6 / (1.0 + p.e.w * sg.x * sg.y)) * (0.7 + 0.6 * smokeRnd(seed, k, 7));
    if (i >= int(p.c.x)) alpha = 0.0;
    posR = vec4(pos, radius);
    age = a;
    rnd = smokeRnd(seed, k, 9);
}
