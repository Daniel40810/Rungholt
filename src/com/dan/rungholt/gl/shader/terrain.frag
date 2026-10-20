// terrain.frag — das Gelände heute (Phase 2). Eingaben: terrain.vert (vWorld, vNormal, vDist) und die Art je Zelle
// (terrain_height.glsl: 0 Watt, 1 Vorland, 2 Deich/Warft, 3 Koog, 4 ohne Messwert). Was wo liegt [G]:
//   Watt: Sand hell auf den Höhen, Mischwatt und Schlick dunkler in den Senken und an den Prielkanten (steil);
//   Phase 5: das Material aus com.dan.mudflat (mudflat.glsl): Nässe, Sediment und Richtung der letzten Strömung je
//   Zelle (WetField), daraus Farbe, Rippel mit Wasser in den Tälern, Wasserfilm, Pfützen, Schill, Wattwurmhaufen.
//   Vorland: Salzwiese (Andel, Queller) graugrün, Hallig und Lahnungsfelder.
//   Deich und Warft: kurzes, sattes Deichgras, Schafweide.
//   Koog: Weiden und Äcker in Schlägen, Gräben dunkler; ohne Messwert gleich, etwas blasser.
// Licht (Phase 3): mit com.dan.sky Sonne und Mond vorbelichtet, Himmelslicht als Bestrahlung/π (uSkyAmb), Spiegelung
// aus dem Rundumbild, Wolkenschatten aus der Karte um das Auge, Blitzlicht, nach Regen dunkler und nasser; ohne das
// Package der Platzhalter-Himmel. Marken: Raster 1 km, der Grundriss der Kirche [G] am Ursprung, der Ring um die Warft
// von Südfall, der Nordstrich; alle Linien höchstens so breit wie ihr Bildpunkt erlaubt (Befund Status_Phase2: im
// streifenden Blick wurde eine Rasterlinie, auf der die Kamera stand, zu einem breiten blauen Band).
#include "common.glsl"
#include "terrain_height.glsl"
#include "/com/dan/mudflat/gl/mudflat.glsl"
#include "/com/dan/mudflat/gl/mudflat_relief.glsl"
in vec3 vWorld;
in vec3 vNormal;
in float vDist;
in vec2 vSmooth;          // Phase 6: geglättete Höhe und ihre Neigung (für die Rinnsale)
out vec4 frag;
uniform int uMarks;
uniform vec2 uWarftPos;    // Warft von Südfall (x, z) aus dem Import
uniform vec2 uChurch;      // Länge und Breite der Kirche (m), Mitte am Ursprung, Achse Ost-West
uniform sampler2D uCloudShadow;
uniform vec4 uCloudRect;   // x, z der Ecke, Kantenlänge, an
uniform vec3 uFlashDir, uFlashRgb;
uniform float uWet;        // Regen der letzten Zeit 0..1
uniform sampler2D uFlood;  // Phase 4: Wasserspiegel je Zelle über dem Pegel (com.dan.tide), trocken ≤ −29
uniform vec4 uFloodRect;   // x0, z0 der Ecke, Zelle (m), an
uniform vec4 uSeason;
// Phase 10: Wege von 1362 als Linienzug (x, z), höchstens 24 Punkte; Länge 0 = keine
uniform vec2 uRoads[24];
uniform int uRoadN;
uniform float uInnerWater;  // Spiegel der Sielzüge und Gräben (m NHN)
uniform vec2 uCanals[24];   // Sielzüge als Linienzüge (1e9 trennt), halbe Breite uCanalHalf
uniform int uCanalN;
uniform float uCanalHalf;      // Phase 9 (com.dan.season): Schnee (m), Queller rot 0..1, Salzwiese im Winter 0..1, Reif 0..1

// Der Wasserspiegel am Ort aus der Überflutung: Mittel der nassen Nachbarn (wie die lange Welle im Meer); x: Spiegel
// (m NHN), y: Anteil nasser Nachbarn. Ohne Textur der Pegel.
vec2 floodLevel(vec2 p) {
    if (uFloodRect.w <= 0.0) return vec2(uLevel, 1.0);
    ivec2 n = textureSize(uFlood, 0);
    vec2 u = (p - uFloodRect.xy) / uFloodRect.z - 0.5;
    if (u.x < -0.5 || u.y < -0.5 || u.x > float(n.x) - 0.5 || u.y > float(n.y) - 0.5) return vec2(uLevel, 1.0);
    vec2 i = floor(u), f = u - i;
    float s = 0.0, w = 0.0;
    for (int b = 0; b <= 1; b++)
        for (int a = 0; a <= 1; a++) {
            float e = texelFetch(uFlood, clamp(ivec2(i) + ivec2(a, b), ivec2(0), n - 1), 0).r;
            if (e <= -29.0) continue;
            float ww = (a == 0 ? 1.0 - f.x : f.x) * (b == 0 ? 1.0 - f.y : f.y);
            s += ww * e;
            w += ww;
        }
    return w > 1.0e-4 ? vec2(uLevel + s / w, w) : vec2(-100.0, 0.0);
}

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
// Linie der Breite w (m) bei Bildpunktgröße px (m): wird der Bildpunkt breiter als die Linie, verblasst sie mit
// w/px statt breiter zu werden (sonst wird sie im streifenden Blick zum Band).
float line(float d, float w, float px) { return (1.0 - smoothstep(w, w + px * 1.5, d)) * clamp(2.0 * w / max(px, 1e-4), 0.0, 1.0); }

// Phase 13: Brunnen (Sodenbrunnen) und Fundstellen aus scene.Spuren, Grundriss der Kirche (Maße aus timber.Church)
uniform vec2 uWells[48];
uniform int uWellN;
uniform vec3 uFinds[16];       // x, z, Art (0 Busch, 1 Museum, 2 Messung 2023)
uniform int uFindN;
uniform vec4 uChurchX;         // örtlich längs der Achse: Westende, Turm|Schiff, Schiff|Chor, Chor|Apsis (m)
uniform vec4 uChurchW;         // halbe Breite Turm, Schiff, Chor; Halbmesser der Apsis (m)
uniform vec2 uChurchAxis;      // Richtung der Achse nach Osten (sin h, −cos h)

float boxSd(vec2 q, vec2 c, vec2 hs) { vec2 d = abs(q - c) - hs; return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0); }
// Abstand zum Grundriss der Kirche am Ursprung (innen negativ), wie Spuren.churchSdf
float churchSd(vec2 p) {
    vec2 q = vec2(dot(p, uChurchAxis), -p.x * uChurchAxis.y + p.y * uChurchAxis.x);
    float s = boxSd(q, vec2((uChurchX.x + uChurchX.y) * 0.5, 0.0), vec2((uChurchX.y - uChurchX.x) * 0.5, uChurchW.x));
    s = min(s, boxSd(q, vec2((uChurchX.y + uChurchX.z) * 0.5, 0.0), vec2((uChurchX.z - uChurchX.y) * 0.5, uChurchW.y)));
    s = min(s, boxSd(q, vec2((uChurchX.z + uChurchX.w) * 0.5, 0.0), vec2((uChurchX.w - uChurchX.z) * 0.5, uChurchW.z)));
    if (q.x >= uChurchX.w) s = min(s, length(q - vec2(uChurchX.w, 0.0)) - uChurchW.w);
    return s;
}

void main() {
    vec3 n = normalize(vNormal);
    vec3 p = vWorld;
    vec2 xz = p.xz;
    vec3 V = normalize(uCam - p);
    float px = vDist * 0.0012 / max(0.08, V.y);
    float h = p.y;
    float cloud = uCloudRect.w > 0.0 ? textureLod(uCloudShadow, (xz - uCloudRect.xy) / uCloudRect.z, 0.0).r : 1.0;
    vec3 amb = (uSkyMode == 1 ? uSkyAmb : skyColor(vec3(0.0, 1.0, 0.0))) * (0.55 + 0.45 * n.y);
    // mit com.dan.sky ist das Licht physikalisch: Leuchtdichte = Albedo/π · Bestrahlung (uSkyAmb ist schon Bestrahlung/π)
    float piK = uSkyMode == 1 ? 0.31831 : 1.0;
    vec3 sunL = (sunLight() * cloud * max(dot(n, uSun), 0.0) + moonLight() * cloud * max(dot(n, uMoonDir), 0.0)
              + uFlashRgb * max(dot(n, uFlashDir), 0.0)) * piK;

    float sWatt = artShare(xz, 0u), sVor = artShare(xz, 1u), sHoch = artShare(xz, 2u), sKoog = artShare(xz, 3u) + artShare(xz, 4u);
    // Phase 10: 1362 (Land1362): Marsch, Warft, Seedeich, Sielzug
    float sMar = 0.0, sWar = 0.0, sDei = 0.0, sSiel = 0.0;
    if (uEra > 0.0) { sMar = artShare(xz, 5u); sWar = artShare(xz, 6u); sDei = artShare(xz, 7u); sSiel = artShare(xz, 8u); }
    float rand = artShare(xz, 4u);

    // Watt (Phase 5: com.dan.mudflat). Der Spiegel am Ort aus der Überflutung (Phase 4); trocken: weit darunter
    vec2 fl = floodLevel(xz);
    float above = fl.y > 0.0 ? h - fl.x : max(h - uLevel, 1.0);
    // Phase 6: die Rinnsale je Bildpunkt (das Gitter trägt nur ihre Tiefe): Maske, Neigung der Rinne, Richtung bergab;
    // die Feinheit aus den Ableitungen des Orts (Befund Status_Phase5: Ringe der Rippel um das Auge)
    float pxD = max(px, max(length(dFdx(xz)), length(dFdy(xz))));
    float runnel = 0.0;
    vec3 nw = n;
    if (sWatt > 0.001 && vDist < 420.0 && uMudRelief.w > 0.0) {
        float where = smoothstep(-1.8, -1.2, vSmooth.x) * (1.0 - smoothstep(0.04, 0.09, vSmooth.y)) * (1.0 - smoothstep(250.0, 420.0, vDist));
        if (where > 0.0) {
            vec2 r0 = mudRunnels(xz), rx = mudRunnels(xz + vec2(0.25, 0.0)), rz = mudRunnels(xz + vec2(0.0, 0.25));
            float d0 = r0.x + 0.45 * r0.y;
            vec2 g = -uMudRelief.y * where * vec2(rx.x + 0.45 * rx.y - d0, rz.x + 0.45 * rz.y - d0) / 0.25;
            nw = normalize(n + vec3(-g.x, 0.0, -g.y) * (1.0 - smoothstep(0.08, 0.3, pxD)));
            runnel = clamp(d0, 0.0, 1.0) * where;
        }
    }
    MudSurface m = sWatt > 0.001 ? mudflatR(xz, nw, above, pxD, runnel, n.xz) : MudSurface(vec3(0.0), n, 0.0, 1.0, 0.0, 0.0);
    // nach Regen: Film auch auf dem trockenen Watt
    m.film = max(m.film, 0.35 * uWet * smoothstep(0.2, 0.6, m.wetness + uWet));
    // Phase 13: die Spuren von Rungholt (scene.Spuren), nur heute und nur auf dem Watt: der Kleirücken der Warften (dunkel,
    // glatt, hält den Wasserfilm), die Kulturschicht mit Brocken aus Torf, Klei, Ziegel und Muschelkalk, die Ringe der
    // Sodenbrunnen, die Gräben der Fennen und die Sielzüge als dunkle Bänder (nur hier und da erhalten), das Fundament der
    // Kirche aus Feldstein und Backsteinbruch
    float spOn = uSpuren.x * (1.0 - uEra) * step(0.001, sWatt);
    vec4 sp = spOn > 0.001 ? spurAt(xz, 0.0) : vec4(0.0, 0.0, 0.5, 0.0);
    if (spOn > 0.001 && uSpurRect.w > 0.0) {
        float k = sp.r * spOn, cu = sp.g * spOn, wet = m.wetness;
        vec3 kleiA = mix(vec3(0.21, 0.20, 0.17), vec3(0.105, 0.10, 0.088), wet) * (0.85 + 0.3 * fbm(xz * 0.9));
        float edgeK = smoothstep(0.12, 0.3, sp.r) * (1.0 - smoothstep(0.3, 0.55, sp.r));
        m.albedo = mix(m.albedo, kleiA, 0.8 * k);
        m.albedo *= 1.0 - 0.3 * edgeK * spOn;                         // Sodenkante am Fuß
        m.normal = normalize(mix(m.normal, n, 0.75 * k));
        m.rough = mix(m.rough, 0.35, k * wet);
        m.film = max(m.film, 0.55 * k * wet * smoothstep(0.4, 0.8, fbm(xz * 0.25 + 5.0)));
        if (cu > 0.01 && vDist < 700.0) {
            vec2 cell = floor(xz / 0.5);
            vec2 c = (cell + 0.25 + 0.5 * vec2(hash(cell), hash(cell + 7.3))) * 0.5;
            float r = 0.025 + 0.07 * hash(cell + 3.1) * hash(cell + 8.8);
            float an = hash(cell + 2.2) * 6.2832;
            vec2 dq = mat2(cos(an), sin(an), -sin(an), cos(an)) * (xz - c);
            dq.x *= 0.6 + 0.8 * hash(cell + 4.4);
            float dd = length(dq) * (1.0 + 0.6 * (vnoise(xz * 30.0) - 0.5));
            float dt = (1.0 - smoothstep(r, r + max(0.015, pxD), dd)) * step(0.86, hash(cell + 5.5)) * smoothstep(0.3, 0.8, cu) * (1.0 - smoothstep(150.0, 500.0, vDist));
            float kc = hash(cell + 11.0);
            vec3 crumb = kc < 0.55 ? vec3(0.03, 0.027, 0.024) : kc < 0.8 ? vec3(0.15, 0.075, 0.05) : kc < 0.95 ? vec3(0.07, 0.068, 0.062) : vec3(0.28, 0.27, 0.25);
            crumb *= 0.75 + 0.25 * smoothstep(0.0, r, r - dd);
            m.albedo = mix(m.albedo, crumb, dt);
        }
        m.albedo = mix(m.albedo, vec3(0.035, 0.032, 0.028), 0.5 * cu * smoothstep(0.42, 0.66, fbm(xz * 0.12 + 1.3)));
        if (k > 0.05 && vDist < 900.0) {
            for (int i = 0; i < 48; i++) {
                if (i >= uWellN) break;
                float dd = length(xz - uWells[i]);
                if (dd > 2.2) continue;
                float ring = (1.0 - smoothstep(0.35, 0.35 + max(0.04, pxD), abs(dd - 1.05))) * spOn;
                float pool = (1.0 - smoothstep(0.7, 0.7 + max(0.04, pxD), dd)) * spOn;
                m.albedo = mix(m.albedo, vec3(0.03, 0.026, 0.02), ring);
                m.normal = normalize(mix(m.normal, n, pool));
                m.film = max(m.film, pool);
                m.rough = mix(m.rough, 0.05, pool);
            }
        }
        uint a62 = art1362At(xz);
        if (a62 == 5u && vDist < 2500.0) {   // Gräben der Fennen wie im Land von 1362 (Schläge von 400 m, alle 40 m)
            vec2 cl = floor(xz / 400.0);
            float ang = hash(cl + 3.7) * 3.14159;
            mat2 rot = mat2(cos(ang), -sin(ang), sin(ang), cos(ang));
            vec2 q = rot * (xz - (cl + 0.5) * 400.0);
            float dg = abs(fract(q.y / 40.0) - 0.5) * 40.0;
            vec2 ce = abs(fract(xz / 400.0) - 0.5) * 400.0;
            float keep = smoothstep(0.44, 0.58, fbm(xz / 70.0 + 2.2));
            float gr = max(line(20.0 - dg, 0.5, pxD), line(200.0 - max(ce.x, ce.y), 0.8, pxD)) * keep * spOn;
            m.albedo = mix(m.albedo, m.albedo * vec3(0.55, 0.56, 0.6), 0.6 * gr);
            m.normal = normalize(mix(m.normal, n, 0.6 * gr));
        }
        if (uCanalN > 1) {
            float dc = 1e9;
            for (int i = 1; i < 24; i++) { if (i >= uCanalN) break; if (length(uCanals[i]) < 1e8 && length(uCanals[i - 1]) < 1e8) dc = min(dc, segDist(xz, uCanals[i - 1], uCanals[i])); }
            float band = (1.0 - smoothstep(uCanalHalf - 1.0, uCanalHalf + 2.0, dc + 2.0 * (vnoise(xz * 0.08) - 0.5))) * smoothstep(0.38, 0.55, fbm(xz / 90.0 + 6.1)) * spOn;
            m.albedo = mix(m.albedo, m.albedo * vec3(0.72, 0.74, 0.78), band);
        }
        if (abs(xz.x) < 45.0 && abs(xz.y) < 45.0) {
            float cs = churchSd(xz);
            // ausgebrochen: Lücken im Band, Steine ein paar Meter verstreut
            float wall = (1.0 - smoothstep(0.75, 0.75 + max(0.05, pxD), abs(cs + 0.1 + 0.5 * (vnoise(xz * 0.8) - 0.5)))) * (0.25 + 0.75 * smoothstep(0.36, 0.58, fbm(xz * 0.3 + 3.0)));
            float strew = (1.0 - smoothstep(0.8, 4.0, abs(cs))) * step(0.72, hash(floor(xz / 0.45) + 13.0));
            vec2 sc = floor(xz / 0.45);
            float stone = step(0.35, hash(sc)) * (1.0 - smoothstep(0.12, 0.2 + max(0.0, pxD), length(fract(xz / 0.45) - 0.5 - 0.15 * (vec2(hash(sc + 1.0), hash(sc + 2.0)) - 0.5))));
            vec3 stoneC = hash(sc + 9.0) < 0.6 ? vec3(0.17, 0.165, 0.16) * (0.8 + 0.4 * hash(sc + 4.0)) : vec3(0.20, 0.08, 0.05);
            m.albedo = mix(m.albedo, vec3(0.06, 0.055, 0.05), wall * 0.7 * spOn);
            m.albedo = mix(m.albedo, stoneC, max(wall, strew) * stone * spOn);
            m.normal = normalize(mix(m.normal, n, wall * spOn));
            m.albedo = mix(m.albedo, m.albedo * 0.75, (1.0 - smoothstep(-0.5, 0.5, cs)) * 0.6 * spOn);   // innen dunkler: Estrich und Schutt
        }
    }
    // die Albedo des Pakets ist die echte (Sand trocken 0,34); im Licht der App wie die übrigen Flächen um ein Drittel gedämpft
    vec3 wattAlb = m.albedo * (uSkyMode == 1 ? 0.64 : 0.5);
    vec3 sunW = (sunLight() * cloud * max(dot(m.normal, uSun), 0.0) + moonLight() * cloud * max(dot(m.normal, uMoonDir), 0.0)
              + uFlashRgb * max(dot(m.normal, uFlashDir), 0.0)) * piK;

    // Vorland: Salzwiese; Phase 9: der Queller in den tiefen Lagen wird im Herbst rot, im Winter liegt alles braungrau
    vec3 vorAlb = mix(vec3(0.075, 0.095, 0.055), vec3(0.11, 0.11, 0.07), fbm(xz * 0.01));
    vorAlb = mix(vorAlb, vec3(0.13, 0.11, 0.08), smoothstep(0.6, 0.8, fbm(xz * 0.05 + 2.0)) * 0.4);
    float queller = smoothstep(0.45, 0.62, fbm(xz * 0.035 + 7.3)) * (1.0 - smoothstep(1.4, 2.2, h));
    vorAlb = mix(vorAlb, mix(vec3(0.15, 0.045, 0.04), vec3(0.11, 0.06, 0.04), fbm(xz * 0.2)), queller * uSeason.y * 0.85);
    vorAlb = mix(vorAlb, vec3(0.105, 0.095, 0.072) * (0.9 + 0.2 * fbm(xz * 0.04)), uSeason.z * 0.8);
    // Deich und Warft: Deichgras (im Winter matter)
    vec3 hochAlb = mix(vec3(0.075, 0.12, 0.045), vec3(0.09, 0.13, 0.05), fbm(xz * 0.03));
    hochAlb = mix(hochAlb, vec3(0.085, 0.10, 0.06), uSeason.z * 0.5);
    // Koog: Schläge aus Weide und Acker, Gräben
    vec2 field = floor(xz / vec2(180.0, 95.0) + vec2(0.37, 0.71) * floor(xz.y / 95.0));
    float kind = hash(field);
    vec3 koogAlb = kind < 0.6 ? vec3(0.08, 0.12, 0.045) : kind < 0.8 ? vec3(0.14, 0.13, 0.07) : vec3(0.12, 0.10, 0.07);
    koogAlb *= 0.9 + 0.2 * fbm(xz * 0.02);
    vec2 fr = abs(fract(xz / vec2(180.0, 95.0) + vec2(0.37, 0.71) * floor(xz.y / 95.0)) - 0.5);
    float edge = min((0.5 - fr.x) * 180.0, (0.5 - fr.y) * 95.0);
    koogAlb = mix(koogAlb, vec3(0.04, 0.05, 0.035), (1.0 - smoothstep(0.0, 2.5, edge)) * 0.6 * (1.0 - smoothstep(0.3, 2.0, px)));
    koogAlb *= mix(1.0, 0.85, rand);

    // Phase 10: die Marsch von 1362: Fennen aus Weide (in Schlägen von 400 m, je eigene Richtung), dazwischen Gräben
    // alle 40 m und an den Grenzen; die Warften mit kurzem Gras, der Seedeich mit Deichgras und Klei an der Krone
    vec3 marAlb = vec3(0.0);
    float graben = 0.0, weg = 0.0;
    if (sMar + sWar + sDei > 0.001) {
        vec2 cell = floor(xz / 400.0);
        float ang = hash(cell + 3.7) * 3.14159;
        mat2 rot = mat2(cos(ang), -sin(ang), sin(ang), cos(ang));
        vec2 q = rot * (xz - (cell + 0.5) * 400.0);
        float fid = floor(q.y / 40.0);
        float kindF = hash(cell * 7.1 + fid);
        marAlb = mix(vec3(0.075, 0.12, 0.045), vec3(0.10, 0.13, 0.055), kindF) * (0.9 + 0.2 * fbm(xz * 0.02));
        marAlb = mix(marAlb, vec3(0.13, 0.12, 0.07), step(0.86, kindF) * 0.7);   // Heuwiese, gemäht
        marAlb = mix(marAlb, vec3(0.085, 0.10, 0.06), uSeason.z * 0.5);
        float dg = abs(fract(q.y / 40.0) - 0.5) * 40.0;                     // Gräben längs, alle 40 m
        vec2 ce = abs(fract(xz / 400.0) - 0.5) * 400.0;                      // Grenzgraben am Schlag
        float dEdge = 200.0 - max(ce.x, ce.y);
        // im streifenden Blick verdecken Böschung und Gras die Gräben
        float vis = max(smoothstep(0.02, 0.2, V.y), 1.0 - smoothstep(60.0, 220.0, vDist));
        graben = max(line(20.0 - dg, 1.1, px), line(dEdge, 1.6, px)) * sMar * vis * (1.0 - smoothstep(120.0, 450.0, vDist) * (1.0 - smoothstep(0.05, 0.3, V.y)));
        // Wege
        if (uRoadN > 1) {
            float dw = 1e9;
            for (int i = 1; i < 24; i++) { if (i >= uRoadN) break; if (length(uRoads[i] - uRoads[i - 1]) > 0.0 && length(uRoads[i]) < 1e8 && length(uRoads[i - 1]) < 1e8) dw = min(dw, segDist(xz, uRoads[i - 1], uRoads[i])); }
            weg = line(dw, 1.8, px) * (sMar + sWar);
        }
    }
    float sum = max(1e-3, sWatt + sVor + sHoch + sKoog + sMar + sWar + sDei + sSiel), shW = sWatt / sum;
    // Land (Vorland, Deich, Koog; 1362 Marsch, Warft, Seedeich): nach Regen dunkler
    float sLand = sVor + sHoch + sKoog + sMar + sWar + sDei;
    // die Warften: kurz verbissenes, trockeneres Gras und ausgetretener Klei um die Höfe (heller und gelber als die Marsch)
    vec3 warAlb = mix(hochAlb * vec3(1.35, 1.2, 1.1), vec3(0.15, 0.13, 0.075), 0.35 + 0.3 * fbm(xz * 0.08));
    vec3 albL = (vorAlb * sVor + hochAlb * (sHoch + sDei) + warAlb * sWar + koogAlb * sKoog + marAlb * sMar) / max(1e-3, sLand);
    albL = mix(albL, vec3(0.16, 0.13, 0.09) * (0.85 + 0.3 * fbm(xz * 0.7)), weg * 0.9);   // Klei, ausgefahren
    albL *= 1.0 - 0.25 * uWet;
    // Phase 9: Reif und Schnee auf dem Land (der Wind räumt die Kronen der Deiche und lässt Wehen an der Leeseite;
    // hier nur Rauschen und Hang); auf dem Watt nur auf den höchsten trockenen Flächen über dem Hochwasser
    float frostL = uSeason.w * 0.55;
    albL = mix(albL, vec3(0.42, 0.44, 0.46), frostL * smoothstep(0.3, 0.9, n.y));
    float snowL = smoothstep(0.004, 0.05, uSeason.x * (0.7 + 0.6 * fbm(xz * 0.08 + 3.1))) * smoothstep(0.35, 0.8, n.y);
    albL = mix(albL, vec3(0.70, 0.72, 0.75) * (0.92 + 0.08 * fbm(xz * 1.3)), snowL);
    vec3 col = albL * (sunL + amb * 0.9);
    // Phase 10: Wasser in Sielzügen und Gräben (Binnenwasser, still): dunkel, Himmel nach Fresnel; die Sielzüge glatt
    // aus ihrem Linienzug (das Raster von 8 m gäbe Treppen)
    float canal = 0.0;
    if (uCanalN > 1 && uEra > 0.0) {
        float dc = 1e9;
        for (int i = 1; i < 24; i++) { if (i >= uCanalN) break; if (length(uCanals[i]) < 1e8 && length(uCanals[i - 1]) < 1e8) dc = min(dc, segDist(xz, uCanals[i - 1], uCanals[i])); }
        canal = (1.0 - smoothstep(uCanalHalf - max(0.3, px), uCanalHalf + max(0.3, px), dc + 1.2 * (vnoise(xz * 0.15) - 0.5))) * uEra;
    }
    float inner = clamp(canal + graben * (1.0 - weg), 0.0, 1.0) * (1.0 - smoothstep(0.004, 0.05, uSeason.x) * step(0.5, uSeason.w));
    if (inner > 0.001) {
        vec3 r = reflect(-V, vec3(0.0, 1.0, 0.0));
        float F = 0.02 + 0.98 * pow(1.0 - max(V.y, 0.0), 5.0);
        // Gräben: Böschung und Gras davor nehmen in der Ferne den Himmel weg; die Sielzüge spiegeln gedämpft (trübes, gekräuseltes Wasser)
        F *= mix(0.45, 0.35 - 0.25 * smoothstep(80.0, 400.0, vDist), step(canal, graben));
        vec3 wcol = mix(vec3(0.02, 0.025, 0.02) * (sunL + amb), skyColor(vec3(r.x, max(r.y, 0.0), r.z)) * 0.8, F);
        col = mix(col, wcol, inner);
    }
    if (shW > 0.001) {
        // das Watt: eigenes Licht mit der Normale der Rippel; der Wasserfilm spiegelt den Himmel nach Fresnel und die
        // Sonne, umso schärfer, je glatter; feuchter Sand glänzt nur schwach
        vec3 colW = wattAlb * (sunW + amb * 0.9);
        vec3 r = reflect(-V, m.normal);
        float F = 0.02 + 0.98 * pow(1.0 - max(dot(m.normal, V), 0.0), 5.0);
        vec3 refl = skyColor(vec3(r.x, max(r.y, 0.0), r.z)) * 0.8;
        colW = mix(colW, refl, clamp(F, 0.0, 1.0) * m.film);
        colW += refl * F * 0.08 * (1.0 - m.film) * (1.0 - m.rough);
        float sharp = mix(30.0, 1200.0, 1.0 - m.rough);
        colW += sunLight() * cloud * 4.0 * pow(max(dot(r, uSun), 0.0), sharp) * F * (1.0 - m.rough) * sqrt(sharp / 300.0);
        col = mix(col, colW, shW);
        // Schnee auf trockenem Watt über dem Spiegel (Hochwatt, Sandbänke), nicht wo das Wasser kam
        float snowW = smoothstep(0.004, 0.06, uSeason.x) * smoothstep(0.4, 1.2, above) * (1.0 - m.wetness) * smoothstep(0.5, 0.75, fbm(xz * 0.05 + 9.0));
        col = mix(col, vec3(0.70, 0.72, 0.75) * (sunL + amb * 0.9), snowW * shW);
    }

    // Phase 13: Magnetik als Karte über dem Watt (Draufsicht; Plus dunkel, Minus hell, wie die veröffentlichten
    // Magnetogramme) und die Fundstellen als Punkte
    float litM = dot(amb, vec3(0.33)) / (0.55 + 0.45 * n.y) + sunLight().r * cloud * max(uSun.y, 0.0) * piK;
    vec4 ovl = vec4(0.0);   // eine Karte über dem Bild: nach dem Dunst aufgetragen
    if (uSpuren.y > 0.001 && uSpurRect.w > 0.0) {
        vec4 sm2 = spurAt(xz, 0.0);
        float cov = smoothstep(0.4, 0.6, sm2.a) * uSpuren.y * (1.0 - uEra);
        float g = clamp(0.5 - (sm2.b - 0.5) * 1.6, 0.0, 1.0);
        ovl = mix(ovl, vec4(vec3(0.01 + 0.2 * g * g) * litM, 1.0), cov * 0.95);
    }
    if (uSpuren.z > 0.5 && uEra < 0.5) {
        for (int i = 0; i < 16; i++) {
            if (i >= uFindN) break;
            float dd = length(xz - uFinds[i].xy);
            float pxs = min(length(dFdx(xz)), length(dFdy(xz)));
            float rr = max(1.2, 4.0 * pxs);
            if (dd > rr * 2.6) continue;
            vec3 kc = uFinds[i].z < 0.5 ? vec3(1.0, 0.62, 0.12) : uFinds[i].z < 1.5 ? vec3(0.25, 0.6, 1.0) : vec3(1.0, 0.22, 0.18);
            float ringF = line(abs(dd - rr * 1.8), max(0.25, pxs * 0.9), pxs), dotF = 1.0 - smoothstep(rr * 0.6, rr * 0.6 + pxs * 1.5, dd);
            ovl = mix(ovl, vec4(kc * 0.5 * litM, 1.0), max(ringF, dotF));
        }
    }
    if (uMarks == 1) {
        vec3 ink = amb * 0.9 / (0.55 + 0.45 * n.y) + sunLight() * cloud * max(uSun.y, 0.0) * 0.4 * piK;
        vec2 g1k = abs(fract(xz / 1000.0 + 0.5) - 0.5) * 1000.0;
        // das Raster ist eine Orientierung von oben: gut einen Bildpunkt breit, nah am Auge (unter 150 m) ausgeblendet
        float grid = line(min(g1k.x, g1k.y), max(0.15, px * 0.8), px) * 0.12 * (1.0 - smoothstep(6000.0, 16000.0, vDist)) * smoothstep(40.0, 150.0, vDist);
        col = mix(col, ink * vec3(0.55, 0.85, 1.0), grid);
        vec2 c = abs(xz) - uChurch * 0.5;
        float church = line(abs(max(c.x, c.y)), 0.6, px);
        float ring = line(abs(length(xz - uWarftPos) - 90.0), 1.2, px);
        float north = line(segDist(xz, vec2(0.0, -12.0), vec2(0.0, -90.0)), 0.8, px);
        float lit = dot(amb, vec3(0.33)) + sunLight().r * cloud * max(uSun.y, 0.0) * piK;
        vec3 orange = vec3(1.0, 0.45, 0.2) * lit * 0.6 + 0.04 * lit;
        col = mix(col, orange, max(church, max(ring, north)));
    }
    frag = vec4(mix(aerial(col, -V, vDist), ovl.rgb, ovl.a), 1.0);
}
