// spray_common.glsl — ein Tropfen aus seinem Bündel, Zeile für Zeile wie Parcel.particle() (com.dan.spray).
// Bündel liegen in einem Speicherpuffer, je sechs vec4:
//   a: x, y, z, Alter (s)        b: vx, vy, vz, Lebensdauer   c: k, Streuung Ort, Streuung Geschw., Schwerkraftanteil
//   d: seed (Bits), Größe, Wasserhöhe, Masse                  e: tx, tz (quer), nx, nz (nach außen)
//   f: Wind x, Wind z, Dauer des Wurfs, Art (0 Spritzwasser, 1 Nebel, 2 Fahne, 3 Weißwasser)
struct SprayParcel { vec4 a, b, c, d, e, f; };
layout(std430, binding = 0) readonly buffer SprayParcels { SprayParcel parcels[]; };

const float SPRAY_G = 9.81;

uint sprayHash(uint x) {
    x ^= x >> 16u;
    x *= 0x7feb352du;
    x ^= x >> 15u;
    x *= 0x846ca68bu;
    x ^= x >> 16u;
    return x;
}

float sprayRnd(uint seed, int i, int j) {
    return float(sprayHash(sprayHash(uint(i * 16 + j)) ^ seed) >> 8u) * (1.0 / 16777216.0);
}

vec3 sprayMove(vec3 p, vec3 v0, float k, float dt, vec2 wind, float grav) {
    vec3 u = vec3(wind.x, -SPRAY_G * grav / k, wind.y);
    float e = (1.0 - exp(-k * dt)) / k;
    return p + u * dt + (v0 - u) * e;
}

// Tropfen i des Bündels P, dt nach dem Stand im Puffer (meist 0): Lage, Geschwindigkeit, Größe, lebt.
void sprayParticle(SprayParcel P, int i, float extra, out vec3 pos, out vec3 vel, out float size, out bool alive) {
    int kind = int(P.f.w + 0.5);
    uint seed = floatBitsToUint(P.d.x);
    float delay = sprayRnd(seed, i, 0) * P.f.z;
    float dt = P.a.w + extra - delay;
    float r2 = sprayRnd(seed, i, 1), r3 = sprayRnd(seed, i, 2);
    bool cloudy = kind == 1 || kind == 3;
    float k = kind == 0 ? P.c.x * (0.5 + 1.5 * r2 * r2) : cloudy ? P.c.x : P.c.x * (0.6 + 0.8 * r2);
    float f = kind == 0 ? 0.35 + 0.8 * sqrt(r3) : 1.0;
    float sa = (sprayRnd(seed, i, 3) - 0.5) * 2.0 * P.c.y, sb = sprayRnd(seed, i, 4) * P.c.y * 0.3, sc = (sprayRnd(seed, i, 5) - 0.5) * P.c.y * 0.3;
    vec3 p0 = P.a.xyz + vec3(P.e.x * sa + P.e.z * sb, sc, P.e.y * sa + P.e.w * sb);
    vec3 j = (vec3(sprayRnd(seed, i, 6), sprayRnd(seed, i, 7), sprayRnd(seed, i, 8)) - 0.5) * 2.0 * P.c.z;
    vec3 v0 = P.b.xyz * f + j;
    float tt = max(dt, 0.0);
    pos = sprayMove(p0, v0, k, tt, P.f.xy, P.c.w);
    vec3 u = vec3(P.f.x, -SPRAY_G * P.c.w / k, P.f.y);
    vel = u + (v0 - u) * exp(-k * tt);
    size = P.d.y * (0.6 + 0.8 * sprayRnd(seed, i, 9)) * (kind == 1 ? 1.0 + 0.3 * tt : kind == 3 ? 1.0 + 0.35 * tt : 1.0);
    alive = dt >= 0.0 && dt <= P.b.w && (cloudy || pos.y >= P.d.z);
}
