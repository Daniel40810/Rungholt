// stage.frag — die flache Bühne von Phase 1 (Platzhalter bis zum Gelände aus den Höhendaten in Phase 2, der Tide aus
// com.dan.tide in Phase 4 und dem nassen Sand aus com.dan.mudflat in Phase 5). Gleiche Formen wie scene/Buehne.java,
// alle [G] und nur zur Ausrichtung; y = 0 ist NHN:
//   das Watt um NHN −0,5 m mit flachen Wellen, die Norderhever als Rinne von Südwest nach Nordost bis −18 m,
//   ein Priel im Süden bis −8 m, Südfall als Hallig auf +1,8 m mit der Warft auf +5,8 m,
//   Pellworm und Nordstrand als Köge auf +0,8 m hinter Deichen von +8 m.
// Der Sichtstrahl läuft in wachsenden Schritten über das Höhenfeld und wird halbiert, bis er den Grund trifft; das Meer
// ist eine Ebene auf dem Pegel (uLevel), außer hinter den Deichen. Unter flachem Wasser scheint der Grund durch. Am
// Wassersaum ist der Sand nass, dunkel und spiegelt (Vorschau auf mudflat). Marken: Raster 100 m und 1 km, die Kirche
// [G] als Grundriss 40 × 15 m, der Ring um die Warft von Südfall, ein Nordstrich am Ursprung. Die Tiefe wird aus dem
// Schnittpunkt geschrieben (umgekehrte Tiefe).
#include "common.glsl"
in vec2 vUV;
out vec4 frag;
uniform vec2 uWindTo;      // wohin der Wind weht (x, z), Einheitsvektor
uniform float uWindSpeed;  // m/s
uniform int uMarks;        // 1 = Marken zeigen
uniform vec4 uHever;       // Norderhever: Punkt x, z auf der Mittellinie, Richtung x, z
uniform vec2 uHeverSize;   // halbe Breite, Sohle
uniform vec3 uPriel;       // Priel: Mittellinie z, halbe Breite, Sohle
uniform vec4 uHallig;      // Südfall: Mitte x, z, Halbachsen a, b
uniform vec4 uWarft;       // Warft: Mitte x, z, Halbmesser, Höhe
uniform float uHalligH;
uniform vec4 uPellworm, uNordstrand;  // Mitte x, z, Halbachsen a, b
uniform vec3 uDeich;       // Koog, Deichkrone, Rand (in Einheiten der Ellipse)
uniform vec2 uChurch;      // Länge und Breite der Kirche (m), Mitte am Ursprung, Achse Ost-West

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), u.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), u.x), u.y);
}
float fbm(vec2 p) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) { s += a * vnoise(p); p = p * 2.03 + 17.1; a *= 0.5; }
    return s;
}
float segDist(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}
float line(float d, float w, float px) { return 1.0 - smoothstep(w, w + px * 1.5, d); }

// ---------------------------------------------------------------- das Höhenfeld wie Buehne.height()
float wattH(vec2 p) {
    return -0.5 + 0.35 * sin(p.x / 1300.0 + 0.7) * cos(p.y / 1700.0 - 0.3) + 0.2 * sin((p.x + p.y) / 650.0) + 0.08 * sin(p.x / 210.0 - p.y / 330.0);
}
float heverDist(vec2 p) { vec2 q = p - uHever.xy; return abs(q.x * uHever.w - q.y * uHever.z); }
float ell(vec4 e, vec2 p) { vec2 q = (p - e.xy) / e.zw; return dot(q, q); }
bool diked(vec2 p) { return ell(uPellworm, p) < 1.0 || ell(uNordstrand, p) < 1.0; }

float height(vec2 p) {
    float h = wattH(p);
    h = min(h, h + (uHeverSize.y - h) * (1.0 - smoothstep(0.55, 1.0, heverDist(p) / uHeverSize.x)));
    h = min(h, h + (uPriel.z - h) * (1.0 - smoothstep(0.3, 1.0, abs(p.y - uPriel.x) / uPriel.y)));
    float he = ell(uHallig, p);
    h = max(h, h + (uHalligH - h) * (1.0 - smoothstep(0.85, 1.15, he)));
    float wr = length(p - uWarft.xy);
    if (wr < uWarft.z * 2.2) h = max(h, uHalligH + (uWarft.w - uHalligH) * (1.0 - smoothstep(uWarft.z, uWarft.z * 2.2, wr)));
    for (int i = 0; i < 2; i++) {
        float q = ell(i == 0 ? uPellworm : uNordstrand, p);
        if (q < 1.0 - uDeich.z) h = uDeich.x;
        else if (q < 1.0 + uDeich.z) h = max(q < 1.0 ? uDeich.x : h, uDeich.y * (1.0 - smoothstep(0.0, uDeich.z, abs(q - 1.0))));
    }
    return h;
}

vec3 normalAt(vec2 p, float e) {
    float hx = height(p + vec2(e, 0.0)) - height(p - vec2(e, 0.0));
    float hz = height(p + vec2(0.0, e)) - height(p - vec2(0.0, e));
    return normalize(vec3(-hx, 2.0 * e, -hz));
}

// Sichtstrahl gegen das Höhenfeld: wachsende Schritte, dann halbieren. −1 ohne Treffer.
float marchGround(vec3 ro, vec3 rd) {
    const float TMAX = 26000.0;
    float t = 0.5, prevT = 0.0;
    float prevD = ro.y - height(ro.xz);
    if (prevD < 0.0) return 0.0;
    for (int i = 0; i < 180; i++) {
        vec3 p = ro + rd * t;
        float d = p.y - height(p.xz);
        if (d < 0.0) {
            float a = prevT, b = t;
            for (int k = 0; k < 7; k++) {
                float m = 0.5 * (a + b);
                vec3 q = ro + rd * m;
                if (q.y - height(q.xz) < 0.0) b = m; else a = m;
            }
            return 0.5 * (a + b);
        }
        prevT = t;
        prevD = d;
        t += max(0.5, max(d * 0.6, t * 0.012));
        if (t > TMAX || (rd.y > 0.0 && p.y > 12.0)) break;
    }
    return -1.0;
}

// Farbe des Grunds: Land (Koog, Deich, Salzwiese), trockenes und nasses Watt, Schlick am Priel
vec3 groundColor(vec3 p, vec3 n, vec3 dir, float px, out float wetOut) {
    vec2 xz = p.xz;
    float h = p.y;
    bool land = diked(xz) || ell(uPellworm, xz) < 1.0 + uDeich.z || ell(uNordstrand, xz) < 1.0 + uDeich.z || h > 1.2;
    vec3 amb = skyColor(vec3(0.0, 1.0, 0.0)) * (0.55 + 0.45 * n.y);
    vec3 sun = sunLight() * max(dot(n, uSun), 0.0);
    wetOut = 0.0;
    if (land) {
        float g = fbm(xz * 0.004);
        vec3 alb = mix(vec3(0.07, 0.10, 0.04), vec3(0.11, 0.13, 0.05), g);         // Weide, Salzwiese
        if (h > 3.0) alb = mix(alb, vec3(0.09, 0.13, 0.05), 0.6);                    // Deich, Warft: Gras
        alb = mix(alb, vec3(0.13, 0.11, 0.07), smoothstep(0.62, 0.8, fbm(xz * 0.02 + 3.0)) * 0.5);
        return alb * (sun + amb * 0.9);
    }
    // Watt: Sand hell auf den Höhen, Schlick dunkler in den Senken und am Priel
    float mud = (1.0 - smoothstep(-1.4, -0.4, h)) * 0.8 + 0.2 * fbm(xz * 0.01);
    vec3 sand = vec3(0.21, 0.18, 0.135), schlick = vec3(0.10, 0.088, 0.07);
    vec3 alb = mix(sand, schlick, clamp(mud, 0.0, 1.0));
    // Rippel als Helligkeit quer zur Strömung (Vorschau, mudflat bringt sie in Phase 5)
    float rip = sin(dot(xz, vec2(0.72, 0.69)) * 0.9 + 2.0 * fbm(xz * 0.05)) * 0.5 + 0.5;
    alb *= 0.92 + 0.08 * rip * (1.0 - smoothstep(0.05, 0.3, px));
    // nass: dicht über dem Pegel und in den Senken; dunkler und spiegelnd
    float above = h - uLevel;
    float wet = clamp(1.0 - smoothstep(0.0, 0.9, above) + 0.35 * (1.0 - smoothstep(-1.2, -0.2, h)), 0.0, 1.0);
    wet = max(wet, smoothstep(0.62, 0.72, fbm(xz * 0.03 + 9.0)) * 0.7);              // Pfützen [G]
    wetOut = wet;
    alb *= mix(1.0, 0.55, wet);
    vec3 col = alb * (sun + amb * 0.9);
    vec3 r = reflect(dir, vec3(0.0, 1.0, 0.0));
    float F = 0.02 + 0.98 * pow(1.0 - max(-dir.y, 0.0), 5.0);
    col = mix(col, skyColor(vec3(r.x, max(r.y, 0.0), r.z)), F * wet * 0.85);
    col += sunLight() * 4.0 * pow(max(dot(r, uSun), 0.0), 300.0) * F * wet;
    return col;
}

void main() {
    vec3 dir = viewRay(vUV);
    float tG = marchGround(uCam, dir);
    float tW = (dir.y < -1e-5 && uCam.y > uLevel) ? (uCam.y - uLevel) / -dir.y : -1.0;
    vec3 pW = uCam + dir * max(tW, 0.0);
    bool waterHit = tW > 0.0 && (tG < 0.0 || tW < tG) && !diked(pW.xz);
    if (!waterHit && tG < 0.0) discard;

    float t = waterHit ? tW : tG;
    vec3 p = uCam + dir * t;
    vec2 xz = p.xz;
    float px = t * 0.0012 / max(0.06, abs(dir.y));
    vec3 col;

    if (waterHit) {
        // das Meer: kurze Wellen in Windrichtung, Fresnel, Spiegelung des Himmels, Sonnenglitzer;
        // unter flachem Wasser scheint der Grund durch (die Tiefe kennt erst tide genau)
        float ground = height(xz);
        float depth = max(0.0, uLevel - ground);
        vec2 dx = vec2(0.0);
        float L = 6.0 + uWindSpeed * 0.8;
        float calm = smoothstep(0.0, 1.5, depth);                                      // auf dem Watt wenig Seegang
        for (int i = 0; i < 6; i++) {
            float k = 6.2831853 / L, ang = sin(float(i) * 2.39996) * 1.1;
            vec2 wd = mat2(cos(ang), -sin(ang), sin(ang), cos(ang)) * uWindTo;
            float A = L * 0.006 * clamp(uWindSpeed / 8.0, 0.05, 1.2) * (0.25 + 0.75 * calm);
            float fade = 1.0 - smoothstep(L * 0.08, L * 0.35, px);
            float ph = k * dot(wd, xz) - sqrt(9.81 * k) * uTime + float(i) * 2.17;
            dx += wd * (A * k * cos(ph)) * fade;
            L *= 0.7;
        }
        vec3 n = normalize(vec3(-dx.x, 1.0, -dx.y));
        float F = 0.02 + 0.98 * pow(1.0 - max(dot(n, -dir), 0.0), 5.0);
        vec3 r = reflect(dir, n);
        r.y = abs(r.y);
        vec3 lit = skyColor(vec3(0.0, 1.0, 0.0)) * 1.2 + sunLight() * max(uSun.y, 0.0) * 0.35;
        vec3 deep = vec3(0.03, 0.09, 0.09) * lit;                                     // Nordsee: grüngrau [G]
        float wg;
        vec3 under = groundColor(vec3(xz.x, ground, xz.y), vec3(0.0, 1.0, 0.0), dir, px, wg) * vec3(0.75, 0.85, 0.8);
        float through = exp(-depth * 1.6 / max(0.15, -dir.y + 0.15));                  // trübes Wasser: 1 m sieht man kaum
        vec3 body = mix(deep, under, through);
        col = mix(body, skyColor(r), F);
        col += sunLight() * 6.0 * pow(max(dot(r, uSun), 0.0), 500.0) * F;
        // Saum: Schaum, wo das Wasser auf dem Watt dünn wird
        float foam = (1.0 - smoothstep(0.0, 0.06, depth)) * smoothstep(0.4, 0.7, fbm(xz * 0.4 + uTime * 0.2));
        col = mix(col, vec3(0.85, 0.88, 0.86) * lit, foam * 0.35);
    } else {
        vec3 n = normalAt(xz, max(1.0, px * 2.0));
        float wg;
        col = groundColor(p, n, dir, px, wg);
    }

    if (uMarks == 1) {
        vec3 ink = skyColor(vec3(0.0, 1.0, 0.0)) * 0.9 + sunLight() * max(uSun.y, 0.0) * 0.4;
        vec2 g100 = abs(fract(xz / 100.0 + 0.5) - 0.5) * 100.0, g1k = abs(fract(xz / 1000.0 + 0.5) - 0.5) * 1000.0;
        float grid = max(line(min(g100.x, g100.y), 0.3, px) * 0.15 * (1.0 - smoothstep(800.0, 2500.0, t)),
                         line(min(g1k.x, g1k.y), 1.2, px) * 0.2);
        grid *= (1.0 - smoothstep(6000.0, 16000.0, t));
        col = mix(col, ink * vec3(0.55, 0.85, 1.0), grid);
        // die Kirche [G]: Grundriss 40 × 15 m, Achse Ost-West; der Ring um die Warft von Südfall; der Nordstrich
        vec2 c = abs(xz) - uChurch * 0.5;
        float church = line(abs(max(c.x, c.y)), 0.6, px);
        float ring = line(abs(length(xz - uWarft.xy) - 90.0), 1.2, px);
        float north = line(segDist(xz, vec2(0.0, -12.0), vec2(0.0, -90.0)), 0.8, px);
        col = mix(col, vec3(1.0, 0.45, 0.2) * (0.4 + 0.6 * sunLight().r) + 0.05, max(church, max(ring, north)));
    }

    col = aerial(col, dir, t);
    frag = vec4(col, 1.0);
    gl_FragDepth = depthOf(p);
}
