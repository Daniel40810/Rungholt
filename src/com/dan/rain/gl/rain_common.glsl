// rain_common.glsl — ein Tropfen aus Nummer, Zeit und Wind, Zeile für Zeile wie Rain.Snapshot.drop() (com.dan.rain).
uniform vec4 uRainA;      // Auge x, y, z, halbe Kastenbreite
uniform vec4 uRainB;      // Drift x, Drift z, Fallzeit (im Kreis), Λ
uniform vec4 uRainC;      // Wind x, Wind z, Rate (mm/h), halbe Kastenhöhe
uniform vec4 uRainMap;    // Ecke x, Ecke z, Zellgröße, Zellen je Seite (0 = überall gleich)
uniform float uCloudBase;
uniform uint uRainSeed;
uniform sampler2D uRainMapTex;   // R32F, Vorhang 0..1 je Zelle
uniform vec4 uRainShelter[4];    // Unterstände: x, z, Halbmesser, Oberkante
uniform int uRainShelterN;

bool rainSheltered(vec3 p) {
    for (int i = 0; i < 4; i++) {
        if (i >= uRainShelterN) break;
        vec4 s = uRainShelter[i];
        vec2 d = p.xz - s.xy;
        if (dot(d, d) < s.z * s.z && p.y < s.w) return true;
    }
    return false;
}

const float RAIN_DMIN = 0.5, RAIN_DMAX = 6.0;

uint rainHash(uint x) {
    x ^= x >> 16u;
    x *= 0x7feb352du;
    x ^= x >> 15u;
    x *= 0x846ca68bu;
    x ^= x >> 16u;
    return x;
}

float rainRnd(uint seed, int i, int j) {
    return float(rainHash(rainHash(uint(i * 16 + j)) ^ seed) >> 8u) * (1.0 / 16777216.0);
}

float rainTerminal(float d) { return max(0.5, 9.65 - 10.3 * exp(-0.6 * d)); }

float rainWrap(float v, float h) { return v - 2.0 * h * floor((v + h) / (2.0 * h)); }

float rainAt(int i, int j) {
    int n = int(uRainMap.w + 0.5);
    return texelFetch(uRainMapTex, ivec2(clamp(i, 0, n - 1), clamp(j, 0, n - 1)), 0).r;
}

// Vorhang 0..1 am Ort, bilinear zwischen den Zellmitten (wie Snapshot.curtainAt)
float rainCurtain(vec2 xz) {
    if (uRainMap.w < 0.5) return 1.0;
    vec2 g = (xz - uRainMap.xy) / uRainMap.z - 0.5;
    vec2 f = floor(g);
    ivec2 i = ivec2(f);
    vec2 t = g - f;
    float a = rainAt(i.x, i.y), b = rainAt(i.x + 1, i.y), c = rainAt(i.x, i.y + 1), d = rainAt(i.x + 1, i.y + 1);
    float ab = a + (b - a) * t.x;
    return ab + ((c + (d - c) * t.x) - ab) * t.y;
}

float rainActive(float localRate) { return localRate <= 0.0 ? 0.0 : min(1.0, sqrt(localRate / 40.0)); }

void rainDrop(int i, out vec3 pos, out vec3 vel, out float d, out bool live) {
    float r1 = rainRnd(uRainSeed, i, 1), r3 = rainRnd(uRainSeed, i, 3), r4 = rainRnd(uRainSeed, i, 4);
    float r5 = rainRnd(uRainSeed, i, 5), r6 = rainRnd(uRainSeed, i, 6);
    float lam = uRainB.w;
    d = RAIN_DMIN - log(1.0 - r1 * (1.0 - exp(-lam * (RAIN_DMAX - RAIN_DMIN)))) / lam;
    float vt = rainTerminal(d);
    float h = uRainA.w, hy = uRainC.w;
    float x = (r3 * 2.0 - 1.0) * h + uRainB.x, z = (r5 * 2.0 - 1.0) * h + uRainB.y, y = (r4 * 2.0 - 1.0) * hy - vt * uRainB.z;
    x = uRainA.x + rainWrap(x - uRainA.x, h);
    z = uRainA.z + rainWrap(z - uRainA.z, h);
    y = uRainA.y + rainWrap(y - uRainA.y, hy);
    pos = vec3(x, y, z);
    vel = vec3(uRainC.x, -vt, uRainC.y);
    live = r6 < rainActive(uRainC.z * rainCurtain(pos.xz)) && y < uCloudBase && !rainSheltered(pos);
}
