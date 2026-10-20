// terrain_height.glsl — Höhe des Geländes für Vertex- und Fragment-Shader (Phase 2, aus Glacier und Haithabu).
// Höhentextur R16 (normiert, 2048² zu 8 m, mit Mip-Stufen), Höhe über NHN = Wert · uHMap.x + uHMap.y, bilinear wie
// Terrain.height in Java, damit Kamera und Bild dieselbe Fläche sehen. In der Ferne aus gröberen Mip-Stufen (stetig
// nach der Entfernung, damit Nachbarknoten dieselbe Fläche sehen). Außerhalb des Rasters sinkt der Grund vom Rand aus
// in 1,5 km auf den Meeresboden der Außensände (uOutside) [G]; dort steht das Meer (com.dan.ocean, bedOutside).
uniform sampler2D uHeight;
uniform usampler2D uSource;    // Art je Zelle: 0 Watt, 1 Vorland, 2 Deich/Warft, 3 Koog, 4 ohne Messwert
// Phase 10: das Gelände von 1362 (Land1362) mit eigener Höhe und Art (dazu 5 Marsch, 6 Warft, 7 Seedeich, 8 Sielzug);
// uEra blendet 0 = heute … 1 = 1362 (Höhe gemischt, Art als Anteil gemischt)
uniform sampler2D uHeight1362;
uniform usampler2D uSource1362;
uniform float uEra;
uniform vec3 uGrid;            // X0, Z0 (Nordwestecke), Zellgröße
uniform float uGridN;          // Zellen je Seite
uniform vec2 uHMap;            // Spanne, kleinste Höhe
uniform vec3 uLodRange;        // Sichtweite der feinsten Stufe (m), Beginn des Gleitens (Anteil), Sichtweite in Knotengrößen
uniform float uOutside;        // Grund außerhalb des Rasters (m über NHN)

vec2 gridUV(vec2 w) { return (w - uGrid.xy) / (uGrid.z * uGridN); }

// Mip-Stufe nach der Entfernung: ein Texel etwa so groß wie der Punktabstand dort
float heightLevel(float dist) { return clamp(log2(max(dist, 1.0) / (64.0 * uLodRange.z * uGrid.z)), 0.0, 9.0); }

// Abstand außerhalb des Rasters (m, 0 innen)
float outsideDist(vec2 w) {
    vec2 lo = uGrid.xy, hi = uGrid.xy + uGrid.z * uGridN;
    vec2 d = max(lo - w, w - hi);
    return length(max(d, vec2(0.0)));
}

float heightAt(vec2 w, float lvl) {
    float h0 = textureLod(uHeight, gridUV(w), lvl).r, h1 = uEra > 0.0 ? textureLod(uHeight1362, gridUV(w), lvl).r : h0;
    float h = mix(h0, h1, uEra) * uHMap.x + uHMap.y;
    float o = outsideDist(w);
    if (o > 0.0) h = mix(h, uOutside, smoothstep(0.0, 1500.0, o));
    return h;
}

float terrainHeight(vec2 w, float dist) { return heightAt(w, heightLevel(dist)); }

uint artAt(vec2 w) {
    if (outsideDist(w) > 0.0) return 0u;
    ivec2 c = clamp(ivec2(floor((w - uGrid.xy) / uGrid.z)), ivec2(0), ivec2(int(uGridN) - 1));
    return uEra >= 0.5 ? texelFetch(uSource1362, c, 0).r : texelFetch(uSource, c, 0).r;
}

// Art weich: Anteil der Nachbarzellen (2 × 2, bilinear) mit dieser Art – keine Treppen an Deichfuß und Koog
float artShare(vec2 w, uint a) {
    vec2 u = (w - uGrid.xy) / uGrid.z - 0.5;
    ivec2 i = ivec2(floor(u));
    vec2 f = u - vec2(i);
    float s = 0.0;
    for (int b = 0; b <= 1; b++)
        for (int c = 0; c <= 1; c++) {
            ivec2 q = clamp(i + ivec2(c, b), ivec2(0), ivec2(int(uGridN) - 1));
            float wt = (c == 0 ? 1.0 - f.x : f.x) * (b == 0 ? 1.0 - f.y : f.y);
            float now = texelFetch(uSource, q, 0).r == a ? 1.0 : 0.0, old = uEra > 0.0 && texelFetch(uSource1362, q, 0).r == a ? 1.0 : 0.0;
            s += wt * mix(now, old, uEra);
        }
    return outsideDist(w) > 0.0 ? (a == 0u ? 1.0 : 0.0) : s;
}

// Phase 13: die Spuren von Rungholt im Watt von heute (scene.Spuren): RGBA8, 2048² zu 3,5 m um die Kirche; R Klei
// (Rest des Warftkörpers, Höhe bis uSpurRect… · 0,25 m), G Kulturschicht, B Magnetik (0,5 = 0 nT), A Messfläche.
// uSpuren: an (0..1), Magnetik (0..1), Fundstellen (0/1), Höhe des Kleirückens bei voller Spur (m)
uniform sampler2D uSpurTex;
uniform vec4 uSpurRect;        // x0, z0, Kantenlänge (m), an
uniform vec4 uSpuren;

vec4 spurAt(vec2 w, float lvl) {
    if (uSpurRect.w <= 0.0) return vec4(0.0, 0.0, 0.5, 0.0);
    vec2 uv = (w - uSpurRect.xy) / uSpurRect.z;
    if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) return vec4(0.0, 0.0, 0.5, 0.0);
    return textureLod(uSpurTex, uv, lvl);
}

// Höhe des Kleirückens über dem Watt (m), wie Spuren.height in Java (bilinear, Stufe 0); nur heute
float spurHeight(vec2 w) { return uSpuren.x * (1.0 - uEra) * uSpuren.w * spurAt(w, 0.0).r; }

// Art von 1362 an der Stelle (für die Gräben der Fennen unter dem Watt)
uint art1362At(vec2 w) {
    if (outsideDist(w) > 0.0) return 0u;
    ivec2 c = clamp(ivec2(floor((w - uGrid.xy) / uGrid.z)), ivec2(0), ivec2(int(uGridN) - 1));
    return texelFetch(uSource1362, c, 0).r;
}
