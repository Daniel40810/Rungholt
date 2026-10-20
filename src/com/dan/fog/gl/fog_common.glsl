// fog_common.glsl — das Feld des Seenebels, Zeile für Zeile wie com.dan.fog.Fog.density (gleicher Ganzzahl-Hash).
// uFogA: σ (1/m), Schwelle, mittlere Obergrenze (m), Weichheit · uFogB: Bankgröße (m), Schwaden, Versatz x, z (m)
// uFogC: Wasserspiegel, Bedeckung, Untergrenze (m über dem Wasser), — · uFogSeed
uniform vec4 uFogA, uFogB, uFogC;
uniform int uFogSeed;

uint fogHash(int x, int z, int salt) {
    uint h = (uint(x) & 255u) * 0x8da6b343u ^ (uint(z) & 255u) * 0xd8163841u ^ uint(salt) * 0xcb1ab31fu;
    h ^= h >> 16;
    h *= 0x7feb352du;
    h ^= h >> 15;
    h *= 0x846ca68bu;
    h ^= h >> 16;
    return h;
}

float fogH01(int x, int z, int salt) { return float(fogHash(x, z, salt) >> 8) * (1.0 / 16777216.0); }

float fogNoise(vec2 p, int salt) {
    vec2 i0 = floor(p);
    ivec2 i = ivec2(i0);
    vec2 f = p - i0;
    f = f * f * (3.0 - 2.0 * f);
    float a = fogH01(i.x, i.y, salt), b = fogH01(i.x + 1, i.y, salt), c = fogH01(i.x, i.y + 1, salt), d = fogH01(i.x + 1, i.y + 1, salt);
    return a + (b - a) * f.x + (c - a) * f.y + (a - b - c + d) * f.x * f.y;
}

// smoothstep, der auch mit a > b fällt (GLSL lässt das offen)
float fogSmooth(float a, float b, float x) {
    float t = clamp((x - a) / (b - a), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

const float FOG_EDGE = 0.035;

// Bank 0..1 und Obergrenze am Ort (m über dem Wasser)
vec2 fogBank(vec2 xz) {
    vec2 q = (xz + uFogB.zw) / uFogB.x;
    float wx = fogNoise(q + vec2(13.0, 0.0), uFogSeed + 1) - 0.5, wz = fogNoise(q + vec2(0.0, 29.0), uFogSeed + 2) - 0.5;
    float n = 0.55 * fogNoise(q + vec2(wx, wz) * 0.8, uFogSeed)
            + 0.30 * fogNoise(q * 2.0 + vec2(5.0, 9.0), uFogSeed + 3)
            + 0.15 * fogNoise(q * 4.0 + vec2(11.0, 3.0), uFogSeed + 4);
    float bk = uFogC.y >= 0.999 ? 1.0 : fogSmooth(uFogA.y - FOG_EDGE, uFogA.y + FOG_EDGE, n);
    float tl = uFogA.z * (0.75 + 0.5 * fogNoise(q * 3.0 + vec2(7.0, 1.0), uFogSeed + 6));
    return vec2(bk, tl);
}

// Dichte aus Bank und Obergrenze (bt = fogBank oder die Karte davon) am Ort p
float fogDensityBT(vec3 p, vec2 bt) {
    if (uFogA.x <= 1e-7 || bt.x <= 0.0) return 0.0;
    float h = p.y - uFogC.x;
    if (h < -2.0) return 0.0;
    float soft = bt.y * uFogA.w;
    float hv = fogSmooth(bt.y + soft * 0.5, bt.y - soft * 0.5, h);
    if (uFogC.z > 0.0) hv *= fogSmooth(uFogC.z - 2.0 * max(1.5, 0.3 * uFogC.z), uFogC.z, h);
    if (hv <= 0.0) return 0.0;
    float w = 1.0;
    if (uFogB.y > 0.0) {
        float q = fogNoise(vec2((p.x + uFogB.z * 1.15) / 70.0 + h / 23.0, (p.z + uFogB.w * 1.15) / 70.0 - h / 31.0), uFogSeed + 5);
        w = 1.0 + uFogB.y * (q - 0.5) * 1.6;
    }
    return uFogA.x * bt.x * hv * max(0.0, w);
}

float fogDensity(vec3 p) {
    float h = p.y - uFogC.x;
    if (uFogA.x <= 1e-7 || h < -2.0 || h > uFogA.z * 1.25 * (1.0 + uFogA.w * 0.5)) return 0.0;
    return fogDensityBT(p, fogBank(p.xz));
}
