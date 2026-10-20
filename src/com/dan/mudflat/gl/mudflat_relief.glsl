// mudflat_relief.glsl — com.dan.mudflat (Phase 6): die Feinform des Watts unter 10 m, die kein Höhenraster trägt.
// Ein Baustein für Vertex- und Pixelshader der App (#include "/com/dan/mudflat/gl/mudflat_relief.glsl"); dieselbe
// Rechnung in Java: com.dan.mudflat.Relief (gleiche Ganzzahl-Hashes, damit Kamera und Bild denselben Boden sehen).
//
//   Prielkante mit Abbruch: wo der Grund nahe seinem geglätteten Mittel liegt und der Hang mehr als 2 % fällt, wird er
//     steiler (Höhen über dem Mittel höher, darunter tiefer, höchstens uMudRelief.x m) und bekommt oben eine Stufe von
//     0,3 m; die Kante schlängelt sich mit Rauschen (Buchten von 5 bis 15 m, abgebrochene Schollen).
//   Rinnsale: ein Netz flacher Rinnen (Abstand 20 bis 30 m, dazu feine von 7 m), in denen das Wasser bei Ebbe zuletzt
//     abläuft; Tiefe uMudRelief.y m, nicht in Prielen.
//   Wellenrücken: flache Buckel von 3 bis 5 m auf Sand (5 cm).
//
// Eingaben: xz (m), hf die Höhe aus dem Raster am Ort, hs die geglättete Höhe (rund 30 m), slope die Neigung von hs
// (Betrag). uMudRelief: x Stärke der Kante (m), y Tiefe der Rinnsale (m), z Startwert, w an (1) / aus (0).
uniform vec4 uMudRelief;

uint mrHashU(ivec2 p, uint salt) {
    uint h = uint(p.x) * 374761393u + uint(p.y) * 668265263u + salt * 2246822519u + uint(uMudRelief.z) * 3266489917u;
    h = (h ^ (h >> 13u)) * 1274126177u;
    return h ^ (h >> 16u);
}
float mrHash(ivec2 p, uint salt) { return float(mrHashU(p, salt) & 16777215u) / 16777216.0; }
float mrNoise(vec2 p, uint salt) {
    vec2 i = floor(p), f = p - i;
    vec2 u = f * f * (3.0 - 2.0 * f);
    ivec2 c = ivec2(i);
    float a = mrHash(c, salt), b = mrHash(c + ivec2(1, 0), salt), d = mrHash(c + ivec2(0, 1), salt), e = mrHash(c + ivec2(1, 1), salt);
    return mix(mix(a, b, u.x), mix(d, e, u.x), u.y);
}

// Rinnsale: 0 außerhalb, 1 in der Mitte einer Rinne; x grobe, y feine
vec2 mudRunnels(vec2 xz) {
    vec2 w = vec2(mrNoise(xz / 31.0, 7u), mrNoise(xz / 31.0 + 17.0, 7u)) - 0.5;
    float n1 = mrNoise(xz / 23.0 + w * 1.6, 3u);
    float r1 = 1.0 - smoothstep(0.0, 0.055, abs(n1 - 0.5));
    vec2 w2 = vec2(mrNoise(xz / 11.0, 9u), mrNoise(xz / 11.0 + 5.0, 9u)) - 0.5;
    float n2 = mrNoise(xz / 7.0 + w2 * 1.2, 5u);
    float r2 = (1.0 - smoothstep(0.0, 0.07, abs(n2 - 0.5))) * smoothstep(0.35, 0.65, mrNoise(xz / 40.0, 11u));
    return vec2(r1, r2);
}

// Kante und Wellenrücken (ohne Rinnsale): Änderung der Höhe in m
float mudEdge(vec2 xz, float hf, float hs, float slope) {
    if (uMudRelief.w <= 0.0) return 0.0;
    float scallop = (mrNoise(xz / 9.0, 1u) - 0.5) * 0.7 + (mrNoise(xz / 3.3, 2u) - 0.5) * 0.25;
    float e = hf - hs + scallop;
    float edge = smoothstep(0.02, 0.07, slope);
    float steep = clamp(1.6 * e, -uMudRelief.x, uMudRelief.x) * exp(-e * e / 1.6);
    float lip = 0.3 * (smoothstep(-0.06, 0.06, e - 0.15) - 0.5) * smoothstep(0.03, 0.08, slope);
    float bumps = 0.05 * (mrNoise(xz / 4.1, 4u) - 0.5) * 2.0 * (1.0 - edge);
    return (steep + lip) * edge + bumps;
}

// Tiefe der Rinnsale (m, negativ), nicht in Prielen (hs unter NHN −1,8 m) und nicht an der Kante
float mudRunnelDepth(vec2 xz, float hs, float slope) {
    if (uMudRelief.w <= 0.0) return 0.0;
    vec2 r = mudRunnels(xz);
    float where = smoothstep(-1.8, -1.2, hs) * (1.0 - smoothstep(0.04, 0.09, slope));
    return -uMudRelief.y * (r.x + 0.45 * r.y) * where;
}

float mudRelief(vec2 xz, float hf, float hs, float slope) { return mudEdge(xz, hf, hs, slope) + mudRunnelDepth(xz, hs, slope); }
