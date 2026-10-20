// weathering.glsl — Verwitterung für beliebige Flächen (com.dan.weathering): nass, Rost, Algen, Salz, Moos.
// Zeile für Zeile wie die Masken in Weathering.java. Zum Einbinden in einen eigenen Fragment-Shader (ohne #version);
// WeatheringGl.bind() setzt die Uniforms und die Nässekarte. Eingänge je Fläche:
//   p      Ort in m (y = Höhe, gleiche Höhe wie der Wasserstand)
//   N      Normale (Einheitsvektor, x Ost, y oben, z Süd)
//   st     Flächenkoordinaten in m (s waagerecht entlang der Fläche, t senkrecht)
//   below  Abstand unter der nächsten Kante darüber in m (Rostläufer)
//   sus    Anfälligkeit für Rost 0..1 (0 frische Farbe, 1 blanker Stahl)
//   mapUV  Ort in der Nässekarte (u rundum 0..1, v Höhe 0..1); mapUV.x < 0 heißt: keine Karte
uniform vec4 uWthWater;    // Wasserstand, MHW, MNW, Auflauf
uniform vec4 uWthWet;      // Spritzhöhe, nasse Linie, Regennässe, Spritzstärke
uniform vec4 uWthAmount;   // Rost, Algen, Salz, Moos
uniform vec2 uWthRainFrom; // waagerechte Richtung, aus der der Regen kommt
uniform int uWthSeed;
uniform sampler2D uWthMap; // Nässekarte (R)
uniform int uWthHasMap;

uint wthHash(uint x) {
    x ^= x >> 16u;
    x *= 0x7feb352du;
    x ^= x >> 15u;
    x *= 0x846ca68bu;
    x ^= x >> 16u;
    return x;
}

float wthRand(int cx, int cy, int seed) {
    uint h = wthHash(uint(cx) * 0x9E3779B1u ^ wthHash(uint(cy + seed)));
    return float(h >> 8u) * (1.0 / 16777216.0);
}

float wthNoise(vec2 q, int seed) {
    vec2 i = floor(q), f = q - i;
    vec2 u = f * f * (3.0 - 2.0 * f);
    int cx = int(i.x), cy = int(i.y);
    float a = wthRand(cx, cy, seed), b = wthRand(cx + 1, cy, seed), c = wthRand(cx, cy + 1, seed), d = wthRand(cx + 1, cy + 1, seed);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float wthFbm(vec2 q, int seed) {
    float s = 0.0, a = 0.5, n = 0.0;
    for (int i = 0; i < 4; i++) {
        s += a * wthNoise(q, seed + i * 101);
        n += a;
        q = vec2(1.6 * q.x - 1.2 * q.y + 17.1, 1.2 * q.x + 1.6 * q.y + 3.7);
        a *= 0.5;
    }
    return s / n;
}

// Algen: Bedeckung und Anteil grüner Schleimalgen
vec2 wthAlgae(float y, vec2 st) {
    float mhw = uWthWater.y, mlw = uWthWater.z;
    float z = (y - mlw) / max(0.2, mhw - mlw);
    float ragged = (wthFbm(vec2(st.x * 0.9, st.y * 2.5), uWthSeed + 11) - 0.5) * 0.35;
    float band = smoothstep(-0.45, -0.15, z) * (1.0 - smoothstep(0.55, 0.85, z + ragged));
    float spot = smoothstep(0.25, 0.6, wthFbm(vec2(st.x * 1.7 + 13.1, st.y * 1.7), uWthSeed + 23));
    float cov = uWthAmount.y * band * mix(0.55, 1.0, spot);
    float green = smoothstep(-0.3, -0.08, z) * (1.0 - smoothstep(0.05, 0.35, z + ragged * 0.5));
    return vec2(cov, green);
}

float wthRust(vec2 st, float below, float sus) {
    float len = mix(0.2, 1.6, wthNoise(vec2(st.x * 1.3, 7.7), uWthSeed + 31));
    float stripes = smoothstep(0.55, 0.85, wthNoise(vec2(st.x * 7.0, st.y * 0.35), uWthSeed + 37));
    float streak = exp(-max(below, 0.0) / len) * stripes;
    float pn = wthFbm(vec2(st.x * 2.2 + 31.0, st.y * 1.6), uWthSeed + 41);
    float spot = smoothstep(0.70 - 0.14 * sus, 0.86 - 0.14 * sus, pn);
    return uWthAmount.x * sus * clamp(max(streak * 0.85, spot), 0.0, 1.0);
}

float wthSalt(float y, float ny, vec2 st) {
    float level = uWthWater.x, base = level + uWthWater.w, top = level + uWthWet.x;
    float zone = smoothstep(base - 0.2, base + 0.6, y) * (1.0 - smoothstep(top + 0.5, top + 3.0, y));
    return uWthAmount.z * zone * (1.0 - 0.75 * smoothstep(0.5, 0.9, ny)) * smoothstep(0.52, 0.82, wthFbm(vec2(st.x * 4.0 + 5.0, st.y * 4.0), uWthSeed + 53));
}

float wthMoss(float ny, float y, vec2 st) {
    float up = smoothstep(0.35, 0.8, ny);
    float above = smoothstep(uWthWater.y + 0.5, uWthWater.y + 1.5, y);
    return uWthAmount.w * up * above * smoothstep(0.45, 0.7, wthFbm(vec2(st.x * 3.1 + 17.0, st.y * 3.1), uWthSeed + 59));
}

float wthMapAt(vec2 mapUV) {
    if (uWthHasMap == 0 || mapUV.x < 0.0) return 0.0;
    return textureLod(uWthMap, vec2(fract(mapUV.x), clamp(mapUV.y, 0.0, 1.0)), 0.0).r;
}

float wthWetness(float y, vec3 N, vec2 st, float mapVal) {
    float level = uWthWater.x;
    if (y < level) return 1.0;
    float jitter = (wthNoise(vec2(st.x * 2.1, st.y * 0.7), uWthSeed + 61) - 0.5) * 0.3;
    float top = level + uWthWater.w;
    float surge = 1.0 - smoothstep(top - 0.15, top + 0.25, y + jitter);
    float line = 1.0 - smoothstep(uWthWet.y - 0.2, uWthWet.y + 0.2, y + jitter);
    float sprayZone = smoothstep(top - 0.3, top + 0.3, y) * (1.0 - smoothstep(level + uWthWet.x - 0.5, level + uWthWet.x + 0.5, y));
    float spray = uWthWet.w * sprayZone * smoothstep(0.45, 0.8, wthNoise(vec2(st.x * 1.5, st.y * 1.5 + 9.0), uWthSeed + 67));
    float face = max(0.0, N.x * uWthRainFrom.x + N.z * uWthRainFrom.y);
    float rain = uWthWet.z * clamp(0.35 + 0.65 * face + 0.4 * max(N.y, 0.0), 0.0, 1.0);
    return clamp(max(max(surge, line), max(max(spray, rain), mapVal)), 0.0, 1.0);
}

// Legt alles über die Grundfarbe (linear) und die Rauheit; gibt die Nässe zurück (für einen Wasserfilm im Glanz).
// masks (optional für Prüfung und Anzeige): Rost, Algen, Salz, Moos.
float wthApply(inout vec3 albedo, inout float rough, float porosity, vec3 p, vec3 N, vec2 st, float below, float sus, vec2 mapUV,
               out vec4 masks) {
    float wet = wthWetness(p.y, N, st, wthMapAt(mapUV));
    float ru = wthRust(st, below, sus);
    vec2 al = wthAlgae(p.y, st);
    float sa = wthSalt(p.y, N.y, st) * (1.0 - wet);
    float mo = wthMoss(N.y, p.y, st);
    float tone = wthNoise(st * 3.0, uWthSeed + 71);
    vec3 rustCol = mix(vec3(0.22, 0.075, 0.03), vec3(0.09, 0.04, 0.02), tone);
    albedo = mix(albedo, rustCol, ru);
    albedo = mix(albedo, vec3(0.62, 0.61, 0.58), sa * 0.8);
    vec3 algCol = mix(vec3(0.05, 0.04, 0.025), vec3(0.05, 0.17, 0.025), al.y);
    albedo = mix(albedo, algCol, al.x);
    albedo = mix(albedo, vec3(0.10, 0.16, 0.05), mo);
    float por = clamp(porosity + 0.5 * ru + 0.4 * al.x, 0.0, 1.0);
    albedo *= 1.0 - wet * por * 0.55;
    rough = mix(rough, max(0.07, rough * 0.35), wet * 0.9);
    rough = mix(rough, max(rough, 0.75), ru * 0.8 * (1.0 - wet));
    masks = vec4(ru, al.x, sa, mo);
    return wet;
}
