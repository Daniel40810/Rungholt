// cloud_common.glsl — Dichte der Wolken, Zeile für Zeile wie Clouds.density (Java): Wetterkarte → Bedeckung und
// Höhe der Türme, Höhenprofil (Haufen: flache Basis, runde Kuppen; Schicht: flach), Amboss, Grundform aus
// Perlin-Worley, Fransen aus der Feinheit. Immer Stufe 0 der Texturen (textureLod): im Strahlengang gibt es keine
// brauchbaren Ableitungen, und der Prozessor rechnet auch auf Stufe 0. Raum: Szene in m, x Ost, y oben, z Süd, Höhe über der gekrümmten Erde.
const float EARTH_R = 6360000.0, SOFT = 0.12, HIGH_HEIGHT = 6500.0, HIGH_TILE = 60000.0, HIGH_TAU = 0.6, SHADOW_SOFT = 0.6;
const float PI = 3.14159265359, ANVIL_R = 0.35 / 16.0;
uniform sampler3D uBase, uDetail;
uniform sampler2D uWeather;
uniform vec4 uType1;    // Basis, Dicke, größte Dicke, schichtförmig
uniform vec4 uType2;    // Auslöschung je m, Amboss, Kachel der Karte, Kachel der Grundform
uniform vec4 uMisc;     // Kachel der Feinheit, Schwelle, Zeit · Formwandel, Schwelle der hohen Schicht
uniform float uCore;    // Dichte im Kern einer Haufenzelle
uniform vec4 uOff;      // Drift x, Drift z, hohe Schicht, Niederschlag
uniform vec4 uLens;     // seit Glacier Phase 3: Lentikularis 0..1, Richtung der Strömung (Bogenmaß, woher), –, –
const float LENS_HEIGHT = 4600.0, LENS_STEP = 320.0, LENS_WAVE = 9000.0, LENS_CELL = 12000.0, LENS_TAU = 3.0;
const int LENS_SLICES = 3;

float smooth01(float a, float b, float x) { return smoothstep(a, b, x); }
float remap(float v, float lo, float hi, float a, float b) { return a + (v - lo) / max(1e-6, hi - lo) * (b - a); }

float heightOf(vec3 p) { return length(vec3(p.x, p.y + EARTH_R, p.z)) - EARTH_R; }

float density(vec3 p, bool detail, out float tt) {
    tt = 0.0;
    float base = uType1.x, thick = uType1.y, maxThick = uType1.z, strat = uType1.w;
    float h = heightOf(p);
    if (h < base || h > base + maxThick) return 0.0;
    vec2 wp = p.xz + uOff.xy;
    vec4 w = textureLod(uWeather, wp / uType2.z, 0.0);
    float f = mix(w.r, w.g, strat), thr = uMisc.y;
    float tall = smooth01(thr, thr + 0.15 + 0.2 * strat - 0.07 * uType2.y, f);   // wie hoch der Turm an dieser Stelle wächst
    float top = base + thick + (maxThick - thick) * tall * tall;
    tt = (h - base) / (top - base);
    // Haufen werden nach oben schmaler (Kuppe statt Säule): die Schwelle steigt mit der Höhe
    float rise = (1.0 - strat) * (0.2 - 0.12 * uType2.y) * clamp(tt, 0.0, 1.0) * clamp(tt, 0.0, 1.0);
    float cov = smooth01(thr + rise, thr + rise + SOFT, f);
    float anv = 0.0;
    if (uType2.y > 0.0) {
        // Amboss: oben in der Schicht, nur über starken Türmen, ein Drittel einer Zelle über sie hinaus (größter Wert in der Nachbarschaft)
        float ta = (h - base) / maxThick;
        float band = smooth01(0.8, 0.88, ta) * (1.0 - smooth01(0.94, 0.99, ta));
        if (band > 0.0) {
            vec2 uv = wp / uType2.z;
            float fn = f;
            for (int i = 0; i < 8; i++) {
                float a = float(i) * PI / 4.0;
                vec4 q = textureLod(uWeather, uv + ANVIL_R * vec2(cos(a), sin(a)), 0.0);
                fn = max(fn, mix(q.r, q.g, strat));
            }
            anv = uType2.y * band * smooth01(thr + 0.06, thr + 0.14, fn);
        }
    }
    float prof = 0.0;
    if (tt <= 1.0) {
        // Haufen runden sich ab der Hälfte ab, Gewittertürme bleiben bis oben dicht
        float cu = smooth01(0.0, 0.06, tt) * (1.0 - smooth01(mix(0.45, 0.8, uType2.y), 1.0, tt)), st = smooth01(0.0, 0.18, tt) * (1.0 - smooth01(0.65, 1.0, tt));
        prof = cu + (st - cu) * strat;
    }
    float cc = cov * prof * mix(uCore, 1.0, max(strat, uType2.y)), c = max(cc, anv);
    float sheet = max(strat, smooth01(0.0, 0.1, anv - cc));   // Schicht oder Amboss: glatt, ohne Türmchen
    if (c <= 0.0) return 0.0;
    float ns = uType2.w;
    vec4 b = textureLod(uBase, vec3(wp.x, h - uMisc.z * 0.6, wp.y) / ns, 0.0);
    float low = b.g * 0.625 + b.b * 0.25 + b.a * 0.125;
    // Haufen: Perlin-Worley mit Worley-fbm gespreizt (Türmchen und Lücken); Schicht: gestaucht (geschlossen)
    float v = mix(clamp((b.r * 0.7 + low * 0.3 - 0.2) / 0.6, 0.0, 1.0), remap(b.r, low - 1.0, 1.0, 0.0, 1.0), sheet);
    v = remap(v, 1.0 - c, 1.0, 0.0, 1.0) * c;
    if (v <= 0.0) return 0.0;
    if (detail) {
        float ds = uMisc.x;
        vec4 d = textureLod(uDetail, vec3(wp.x, h - uMisc.z * 1.5, wp.y) / ds, 0.0);
        float hf = d.r * 0.625 + d.g * 0.25 + d.b * 0.125;
        float m = hf + (1.0 - 2.0 * hf) * clamp(tt * 4.0, 0.0, 1.0);
        v = remap(v, m * mix(0.55, 0.3, sheet), 1.0, 0.0, 1.0);
    }
    return max(0.0, v) * uType2.x;
}

float highTau(vec2 xz) {
    if (uOff.z <= 0.0) return 0.0;
    // Schleier mit Fasern: die Karte quer gestaucht (Fallstreifen), dazu die Feinheit der Karte als Streifen
    vec2 q = xz + uOff.xy * 1.5;
    vec4 w = textureLod(uWeather, vec2(q.x / (HIGH_TILE * 3.0), q.y / HIGH_TILE), 0.0);
    vec4 f = textureLod(uWeather, vec2(q.x / (HIGH_TILE * 2.0), q.y / (HIGH_TILE * 0.12)), 0.0);
    return HIGH_TAU * smooth01(uMisc.w, uMisc.w + 0.2, w.a) * (0.35 + 0.65 * f.b);
}

// Ganzzahliges Rauschen 0..1 je Kamm und Abschnitt (bitgleich mit Clouds.lensHash)
float lensHash(int k, int j) {
    uint h = uint(k) * 374761393u + uint(j) * 668265263u;
    h = (h ^ (h >> 13)) * 1274126177u;
    h ^= h >> 16;
    return float(h & 0xFFFFFFu) / 16777216.0;
}

// Lentikularis: optische Dicke der Lage i senkrecht an xz (wie Clouds.lensTau). Kämme der stehenden Welle längs des
// Winds, Abschnitte quer dazu aus einem Rauschen je Kamm als Mandeln, obere Lagen kleiner; ortsfest.
float lensTau(vec2 xz, int i) {
    if (uLens.x <= 0.0) return 0.0;
    vec2 to = vec2(-sin(uLens.y), cos(uLens.y));
    float u = dot(xz, to), v = -xz.x * to.y + xz.y * to.x;
    float cu = 0.5 + 0.5 * cos(2.0 * PI * u / LENS_WAVE);
    if (cu < 0.68) return 0.0;
    int k = int(floor(u / LENS_WAVE + 0.5));
    float q = v / LENS_CELL;
    int j = int(floor(q));
    float f = q - float(j), n = mix(lensHash(k, j), lensHash(k, j + 1), f * f * (3.0 - 2.0 * f));
    // Mandelform: breit und dick, wo das Rauschen hoch ist, spitz zu den Enden
    float thr = 1.0 - 0.55 * uLens.x + 0.07 * float(i);
    float b2 = clamp((n - thr) / 0.4, 0.0, 1.0);
    if (b2 <= 0.0) return 0.0;
    float c0 = 1.0 - (0.30 - 0.06 * float(i)) * sqrt(b2);
    float bu = clamp((cu - c0) / max(1e-6, 1.0 - c0), 0.0, 1.0);
    return LENS_TAU * sqrt(uLens.x) * pow(bu, 0.6) * sqrt(b2);
}

// Abstände (t0, t1) zwischen den Höhen h0 und h1 entlang d vom Punkt o; x < 0: kein Schnitt. Wie Clouds.shell.
vec2 shell(vec3 o, vec3 d, float h0, float h1) {
    vec3 oc = vec3(o.x, o.y + EARTH_R, o.z);
    float b = dot(oc, d), oo = dot(oc, oc);
    float r0 = EARTH_R + h0, r1 = EARTH_R + h1, r = sqrt(oo);
    float disc1 = b * b - (oo - r1 * r1);
    if (disc1 < 0.0) return vec2(-1.0);
    float far1 = -b + sqrt(disc1), near1 = -b - sqrt(disc1);
    float disc0 = b * b - (oo - r0 * r0);
    if (r < r0) return vec2(-b + sqrt(max(0.0, disc0)), far1);
    if (r <= r1) {
        if (disc0 > 0.0 && -b - sqrt(disc0) > 0.0) return vec2(0.0, -b - sqrt(disc0));
        return vec2(0.0, far1);
    }
    if (near1 < 0.0) return vec2(-1.0);
    if (disc0 > 0.0 && -b - sqrt(disc0) > 0.0) return vec2(near1, -b - sqrt(disc0));
    return vec2(near1, far1);
}

// Licht des Gestirns in einer Höhe (über die Luft gedämpft, im Erdschatten aus): Stützstellen 0, 0,5, 1, 2, 4, 7, 10, 14 km.
uniform vec3 uSunE[8];
vec3 sunAt(float h) {
    const float H[8] = float[8](0.0, 500.0, 1000.0, 2000.0, 4000.0, 7000.0, 10000.0, 14000.0);
    if (h <= 0.0) return uSunE[0];
    for (int i = 0; i < 7; i++) if (h < H[i + 1]) return mix(uSunE[i], uSunE[i + 1], (h - H[i]) / (H[i + 1] - H[i]));
    return uSunE[7];
}

// Optische Dicke des Dunsts vom Auge (Höhe y0) die Strecke t entlang d, Dunst mit 1,2 km Skalenhöhe (wie com.dan.sky)
float hazeDepth(float k0, float y0, vec3 d, float t) {
    const float H = 1200.0;
    float a = exp(-max(0.0, y0) / H);
    if (abs(d.y) < 1e-3) return k0 * a * t;
    return k0 * a * H / d.y * (1.0 - exp(-d.y * t / H));
}

float hg(float c, float g) { float g2 = g * g; return (1.0 - g2) / (4.0 * PI * pow(max(1e-4, 1.0 + g2 - 2.0 * g * c), 1.5)); }
