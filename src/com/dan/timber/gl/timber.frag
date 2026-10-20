// timber.frag — Werkstoffe des Holzbaus aus Rauschen. u der Flächenkoordinate läuft mit der Faser (Holz), den Halmen
// (Reet: vom First zur Traufe) oder den Ruten (Flechtwerk), alles in Metern.
//   Holz (Pfosten, Bohlen, Tür, Deck): Eiche, neu braun, alt silbergrau; Maserung entlang der Faser, Jahresringe, Beil-
//     spuren als flache Mulden quer zur Faser, Risse; Bohlen 24 cm breit mit dunklen Fugen.
//   Lehm: auf Flechtwerk verstrichen, ocker bis grau, Flecken, Schwundrisse (Netz aus Zellen), unten Spritzwasser.
//   Flechtwerk: waagerechte Ruten um senkrechte Staken geflochten.
//   Reet: Halme in Lagen (Bindung alle 32 cm), golden neu, grau alt, Moos auf der Nordseite; an der Traufe Halmenden.
//   Soden: Grasnarbe. Dunkel: Öffnungen. Boden: gestampfter Lehm mit Gras (für Vorführungen).
// Licht: Sonne und Mond (Schattenkarte von oben), Himmel mit Verdeckung, Nässe, Dunst. Mit uTonemap gleich ins Bild.
in vec3 vWorld;
in vec3 vNormal;
in vec2 vUV;
flat in int vMat;
in float vAO;
in float vLocalY;
flat in vec2 vState;
flat in float vBurn;
in vec3 vLocal;
uniform vec4 uSoot;          // Ruß: halbe Länge des Typs, Höhe der Rauchlöcher, Firsthöhe, an (0 = ohne Rauchloch, 0,5 = nur ein Giebel)
out vec4 frag;
uniform vec3 uCam;
uniform vec3 uSunDir, uSunColor, uMoonDir, uMoonColor, uSkyColor, uFogColor;
uniform float uSkyWeight, uFogDist, uWet, uExposure;
uniform int uTonemap;
uniform sampler2D uSky;      // Rundumbild des Himmels für den Dunst (uSkyOn), sonst uFogColor
uniform int uSkyOn;
uniform sampler2D uShadow;
uniform vec4 uShadowRect;
uniform sampler2DShadow uSunShadow;     // Schattenkarte der Sonne (Tiefe mit Vergleich)
uniform mat4 uSunShadowM;
uniform int uSunShadowOn;
uniform float uSunShadowBias;
uniform int uPointN;            // seit Haithabu Phase 10: Punktlichter (Feuer, Fackeln)
uniform vec4 uPointPos[8];      // Ort, Größe der Quelle (m)
uniform vec4 uPointRgb[8];      // Lichtstärke RGB (Einheit von uSunColor · m²)
uniform vec4 uSnowT;          // seit Haithabu Phase 12: Schneehöhe auf den Dächern (m), auf dem Boden (m), Nässe 0..1, Reif 0..1
uniform vec2 uWater;          // seit Haithabu Phase 8: Wasserspiegel (m), an (1): Nässe, Algenlinie und Schleim an Pfählen und Bohlen
// seit Haithabu Phase 11: ein begehbarer Raum (Interior.Room#pack). Flächen innen: Sonne und Mond nur durch die
// Öffnungen, Himmel nur durch die Öffnungen (Raumwinkel), Rückstrahlung, Lichter des Raums, Rauchschicht dämpft.
uniform vec4 uRoomA;          // Ort x, y (Boden), z, Richtung (rad)
uniform vec4 uRoomB;          // halbe Länge, halbe Breite innen, Wandhöhe, Firsthöhe innen
uniform vec4 uRoomC;          // Neigung der Dachunterseite (tan), Zahl der Öffnungen, an
uniform vec4 uRoomO[16];      // je Öffnung: Seite, a0, a1, Dreieck; y0, y1, Fläche, 0
uniform vec4 uRoomSmoke;      // Unterkante der Rauchschicht (m), Auslöschung oben, unten (1/m), Übergang (m)
uniform vec3 uRoomBounce;     // Rückstrahlung im Raum (Bestrahlung)
uniform int uRoomLightN;
uniform vec4 uRoomLightPos[4];
uniform vec4 uRoomLightRgb[4];

// Schatten der Sonne: 3 × 3 Proben mit Vergleich (weicher Rand), Versatz nach der Neigung zur Sonne
float sunShadow(vec3 n) {
    if (uSunShadowOn == 0) return 1.0;
    vec4 q = uSunShadowM * vec4(vWorld + n * 0.04, 1.0);
    vec3 c = q.xyz / q.w * 0.5 + 0.5;
    if (c.x < 0.0 || c.y < 0.0 || c.x > 1.0 || c.y > 1.0 || c.z > 1.0) return 1.0;
    float bias = uSunShadowBias * (1.0 + 2.0 * (1.0 - abs(dot(n, uSunDir))));
    vec2 texel = 1.0 / vec2(textureSize(uSunShadow, 0));
    float s = 0.0;
    for (int y = -1; y <= 1; y++)
        for (int x = -1; x <= 1; x++) s += texture(uSunShadow, vec3(c.xy + vec2(x, y) * texel * 1.2, c.z - bias));
    return s / 9.0;
}

const int POST = 0, PLANK = 1, DAUB = 2, THATCH = 3, SOD = 4, DOOR = 5, WATTLE = 6, DARK = 7, DECK = 8, GROUND = 9, FISH = 10, STONE = 11, CLOTH = 12, SOAPSTONE = 13;
const int FLOOR = 14, FUR = 15, IRON = 16, WOOL = 17, HEARTHM = 18, BRICK = 19, CHURCH = 20, SHINGLE = 21;

// ---------------------------------------------------------------- Raum (Phase 11), wie Interior.Room
vec3 roomLocal(vec3 w) {
    vec3 d = w - uRoomA.xyz;
    float s = sin(uRoomA.w), c = cos(uRoomA.w);
    return vec3(d.x * s - d.z * c, d.y, d.x * c + d.z * s);
}
vec3 roomDir(vec3 d) {
    float s = sin(uRoomA.w), c = cos(uRoomA.w);
    return vec3(d.x * s - d.z * c, d.y, d.x * c + d.z * s);
}
float roomRoofY(float z) { return uRoomB.w - abs(z) * uRoomC.x; }
// Liegt der örtliche Punkt im Raum? m > 0 schrumpft, m < 0 erweitert (Flächen genau auf der Hülle zählen innen)
bool roomInside(vec3 p, float m) {
    return abs(p.x) < uRoomB.x - m && abs(p.z) < uRoomB.y - m && p.y > -0.3 && p.y < roomRoofY(p.z) - m;
}
// Gehört die Fläche zum Raum? Innen, dazu die Wandkronen und Rähme unter dem Dach (bis durch die Wand), aber nicht
// die Außenseiten der Wände (Normale nach außen) und nichts über der Dachunterseite
bool roomSurface(vec3 p, vec3 n) {
    if (roomInside(p, -0.04)) return true;
    float wx = uRoomB.x + 0.19, wz = uRoomB.y + 0.19;
    if (abs(p.x) > wx || abs(p.z) > wz || p.y < -0.3 || p.y > roomRoofY(min(abs(p.z), uRoomB.y)) + 0.04) return false;
    if (abs(p.z) > uRoomB.y + 0.08 && n.z * sign(p.z) > 0.5) return false;
    if (abs(p.x) > uRoomB.x + 0.08 && n.x * sign(p.x) > 0.5) return false;
    return p.y > uRoomB.z - 0.35;
}
// Austritt aus der Hülle: Seite (0 +x, 1 −x, 2 +z, 3 −z, 4/5 Dach, 6 Boden), entlang, Höhe, t
vec4 roomExit(vec3 p, vec3 d) {
    float best = 1e9, face = -1.0;
    if (d.x > 1e-6) { float t = (uRoomB.x - p.x) / d.x; if (t > -1e-3 && t < best) { best = t; face = 0.0; } }
    if (d.x < -1e-6) { float t = (-uRoomB.x - p.x) / d.x; if (t > -1e-3 && t < best) { best = t; face = 1.0; } }
    if (d.z > 1e-6) { float t = (uRoomB.y - p.z) / d.z; if (t > -1e-3 && t < best && p.y + d.y * t <= uRoomB.z) { best = t; face = 2.0; } }
    if (d.z < -1e-6) { float t = (-uRoomB.y - p.z) / d.z; if (t > -1e-3 && t < best && p.y + d.y * t <= uRoomB.z) { best = t; face = 3.0; } }
    for (int k = 0; k < 2; k++) {
        float s = k == 0 ? 1.0 : -1.0;
        float den = d.y + s * uRoomC.x * d.z;
        if (den > 1e-6) {
            float t = (uRoomB.w - p.y - s * uRoomC.x * p.z) / den;
            if (t > 0.0 && t < best && (p.z + d.z * t) * s >= -1e-4) { best = t; face = 4.0 + float(k); }
        }
    }
    if (d.y < -1e-6) { float t = -p.y / d.y; if (t < best) { best = t; face = 6.0; } }
    vec3 q = p + d * best;
    return vec4(face, face < 1.5 ? q.z : q.x, q.y, best);
}
// Geht der Strahl durch eine Öffnung ins Freie? 1 ja, 0 nein
float roomOpen(vec3 p, vec3 d) {
    vec4 e = roomExit(p, d);
    int n = int(uRoomC.y + 0.5);
    for (int i = 0; i < 8; i++) {
        if (i >= n) break;
        vec4 a = uRoomO[i * 2], b = uRoomO[i * 2 + 1];
        if (abs(a.x - e.x) > 0.5 || e.z < b.x || e.z > b.y) continue;
        float c = 0.5 * (a.y + a.z), hw = 0.5 * (a.z - a.y);
        if (a.w > 0.5) hw *= (b.y - e.z) / max(1e-3, b.y - b.x);
        if (abs(e.y - c) <= hw) return 1.0;
    }
    return 0.0;
}
// Mitte und Normale nach außen einer Öffnung (örtlich)
void roomOpening(int i, out vec3 c, out vec3 n) {
    vec4 a = uRoomO[i * 2], b = uRoomO[i * 2 + 1];
    float along = 0.5 * (a.y + a.z), y = a.w > 0.5 ? b.x + (b.y - b.x) / 3.0 : 0.5 * (b.x + b.y);
    int f = int(a.x + 0.5);
    if (f == 0) { c = vec3(uRoomB.x, y, along); n = vec3(1, 0, 0); }
    else if (f == 1) { c = vec3(-uRoomB.x, y, along); n = vec3(-1, 0, 0); }
    else if (f == 2) { c = vec3(along, y, uRoomB.y); n = vec3(0, 0, 1); }
    else { c = vec3(along, y, -uRoomB.y); n = vec3(0, 0, -1); }
}
// Optische Dicke des Rauchs auf der Strecke p → q (örtlich): oben dicht, unten dünn, weicher Übergang
float roomTau(vec3 p, vec3 q) {
    float L = length(q - p);
    if (L < 1e-4) return 0.0;
    float y0 = min(p.y, q.y), y1 = max(p.y, q.y), zi = uRoomSmoke.x, w = uRoomSmoke.w;
    float frac;
    if (y1 - y0 < 1e-3) frac = smoothstep(zi - w, zi + w, y0);
    else {
        // Anteil der Strecke über der Unterkante, mit dem Übergang verschmiert
        float a = clamp((y1 - (zi - w)) / (y1 - y0), 0.0, 1.0), b = clamp((y1 - (zi + w)) / (y1 - y0), 0.0, 1.0);
        frac = 0.5 * (a + b);
    }
    return L * (uRoomSmoke.z + (uRoomSmoke.y - uRoomSmoke.z) * frac);
}

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float hash1(float n) { return fract(sin(n * 91.3458) * 47453.5453); }
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x), f.y);
}
float fbm(vec2 p) { return 0.5 * vnoise(p) + 0.27 * vnoise(p * 2.03 + 3.1) + 0.15 * vnoise(p * 4.1 - 1.7) + 0.08 * vnoise(p * 8.3 + 7.7); }

// Zellen: Abstand zur Grenze zweier Zellen (für Schwundrisse)
float cellEdge(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    float d1 = 9.0, d2 = 9.0;
    for (int y = -1; y <= 1; y++)
        for (int x = -1; x <= 1; x++) {
            vec2 g = vec2(x, y), o = vec2(hash(i + g), hash(i + g + 17.3));
            float d = length(g + o - f);
            if (d < d1) { d2 = d1; d1 = d; } else if (d < d2) d2 = d;
        }
    return d2 - d1;
}

// Holz: Grundfarbe nach Alter, Maserung entlang u, Beilspuren, Risse. Gibt Farbe; bump verschattet leicht.
vec3 wood(vec2 uv, float age, float rnd, float tone, out float bump) {
    vec3 fresh = mix(vec3(0.30, 0.21, 0.13), vec3(0.36, 0.26, 0.16), tone);
    vec3 old = mix(vec3(0.20, 0.19, 0.17), vec3(0.27, 0.26, 0.24), tone);
    vec3 c = mix(fresh, old, smoothstep(0.15, 0.85, age));
    float grain = fbm(vec2(uv.x * 0.7, uv.y * 22.0 + rnd * 40.0));
    float rings = 0.5 + 0.5 * sin(uv.y * 70.0 + grain * 9.0 + rnd * 20.0);
    c *= 0.82 + 0.22 * grain + 0.08 * rings;
    // Beilspuren: flache Mulden quer zur Faser, je Hieb 12 bis 18 cm
    float row = floor(uv.y * 6.0), cut = fract(uv.x * 6.5 + hash1(row + rnd * 31.0));
    bump = 0.94 + 0.10 * smoothstep(0.0, 0.25, cut) * (1.0 - smoothstep(0.75, 1.0, cut));
    // Risse entlang der Faser
    float crack = smoothstep(0.985, 1.0, vnoise(vec2(uv.x * 1.2, uv.y * 40.0 + rnd * 9.0))) * (0.4 + 0.6 * age);
    c *= 1.0 - 0.6 * crack;
    return c;
}

vec3 albedo(out float bumpOut, out float wetDark) {
    float age = vState.x, rnd = vState.y;
    vec2 uv = vUV;
    bumpOut = 1.0;
    wetDark = 1.0;
    if (vMat == POST || vMat == DECK) {
        float b;
        vec3 c = wood(uv, age, rnd, hash(floor(vWorld.xz * 0.37) + rnd), b);
        if (vMat == DECK) c *= vec3(0.62, 0.58, 0.52) * mix(1.0, 0.7, fbm(vWorld.xz * 0.8));   // abgetreten, schmutzig, vergraut
        bumpOut = b;
        return c;
    }
    if (vMat == PLANK || vMat == DOOR) {
        // Spaltbohlen, Faser senkrecht (u nach oben), v quer
        float pw = vMat == DOOR ? 0.19 : 0.24, k = floor(uv.y / pw), f = fract(uv.y / pw);
        float tone = hash1(k * 3.1 + rnd * 17.0);
        float b;
        vec3 c = wood(vec2(uv.x + tone * 5.0, uv.y), age, rnd + k * 0.13, tone, b);
        float seam = smoothstep(0.0, 0.035, f) * (1.0 - smoothstep(0.965, 1.0, f));
        c *= 0.35 + 0.65 * seam;
        if (vMat == DOOR) {
            c *= 0.72;
            // zwei Querriegel
            float r = min(abs(uv.x - 0.35), abs(uv.x - 1.45));
            c = mix(c * 0.8, c, smoothstep(0.06, 0.08, r));
        }
        // unten feucht und dunkel
        c *= mix(0.7, 1.0, smoothstep(0.0, 0.6, vLocalY + 0.1));
        bumpOut = b;
        return c;
    }
    if (vMat == BRICK) {
        // seit Rungholt Phase 9: Läuferverband 24 × 7,5 cm, helle Fugen, Steine einzeln getönt; Sprossenfenster
        vec2 p = vec2(uv.x, vLocalY);
        float row = floor(p.y / 0.075), off = mod(row, 2.0) * 0.12;
        vec2 b = vec2(fract((p.x + off) / 0.24), fract(p.y / 0.075));
        float joint = smoothstep(0.0, 0.05, b.x) * (1.0 - smoothstep(0.95, 1.0, b.x)) * smoothstep(0.0, 0.14, b.y) * (1.0 - smoothstep(0.86, 1.0, b.y));
        float tone = hash1(floor((p.x + off) / 0.24) * 7.1 + row * 3.3 + rnd * 11.0);
        vec3 c = mix(vec3(0.28, 0.075, 0.045), vec3(0.42, 0.14, 0.075), tone) * (0.9 + 0.15 * vnoise(p * 30.0));
        c = mix(c, vec3(0.20, 0.10, 0.07), smoothstep(0.85, 1.0, tone) * 0.6);   // Klinker dunkler
        c = mix(vec3(0.40, 0.38, 0.34), c, joint);
        float wx = fract(p.x / 2.4) * 2.4;
        if (wx > 0.7 && wx < 1.7 && p.y > 0.95 && p.y < 1.85) {
            float fx = wx - 0.7, fy = p.y - 0.95;
            bool frame = fx < 0.07 || fx > 0.93 || fy < 0.07 || fy > 0.83 || abs(fx - 0.5) < 0.025 || abs(fy - 0.45) < 0.025;
            c = frame ? vec3(0.72, 0.72, 0.69) : vec3(0.015, 0.02, 0.025) + 0.05 * vec3(0.3, 0.35, 0.4) * smoothstep(0.3, 0.9, fy);
            bumpOut = 1.0;
            return c;
        }
        c *= mix(0.65, 1.0, smoothstep(0.0, 0.5, vLocalY + 0.1));
        return c;
    }
    if (vMat == DAUB) {
        vec2 p = vec2(uv.x, vWorld.y) ;
        float blot = fbm(p * 1.3 + rnd * 7.0), fine = vnoise(p * 9.0);
        // Lehm ockergelb wie an den Museumshäusern (templates/Museumsdorf4, 9), alt fleckig und blasser
        vec3 c = mix(vec3(0.50, 0.34, 0.15), vec3(0.58, 0.42, 0.21), blot);
        c = mix(c, vec3(0.44, 0.35, 0.24), smoothstep(0.3, 1.0, age) * 0.5);
        c *= 0.9 + 0.12 * fine;
        // Ruten des Flechtwerks scheinen schwach durch
        c *= 0.96 + 0.04 * sin(p.y * 3.14159 / 0.12);
        // Schwundrisse: fein, nur stellenweise, mit dem Alter mehr
        float e = cellEdge(p * vec2(5.5, 6.5) + rnd * 13.0);
        float where = smoothstep(0.45 - 0.25 * age, 0.75, fbm(p * 0.9 + rnd * 3.0));
        c *= mix(1.0, mix(0.6, 1.0, smoothstep(0.0, 0.025, e)), where);
        // abgeplatzter Putz: dunkles Flechtwerk kommt durch
        float chip = smoothstep(0.78, 0.82, fbm(p * 2.3 + 9.0)) * smoothstep(0.3, 1.0, age);
        c = mix(c, vec3(0.20, 0.15, 0.10) * (0.8 + 0.4 * sin(p.y * 3.14159 / 0.06)), chip);
        // Spritzwasser und Schmutz unten
        c *= mix(0.62, 1.0, smoothstep(0.0, 0.7, vLocalY + 0.15));
        return c;
    }
    if (vMat == WATTLE) {
        // Ruten waagerecht (v nach oben, Abstand 4,5 cm), um Staken alle 30 cm gewunden
        float row = floor(uv.y / 0.045), f = fract(uv.y / 0.045);
        float weave = sin((uv.x / 0.30 + row) * 3.14159);
        float rod = sqrt(max(0.0, 1.0 - pow(2.0 * f - 1.0, 2.0)));
        vec3 bark = mix(vec3(0.27, 0.20, 0.13), vec3(0.34, 0.27, 0.18), hash1(row + rnd * 11.0));
        bark = mix(bark, vec3(0.24, 0.23, 0.21), smoothstep(0.2, 0.9, age));
        bumpOut = 0.45 + 0.45 * rod * (0.75 + 0.25 * weave);
        return bark * (0.85 + 0.2 * vnoise(uv * vec2(3.0, 40.0)));
    }
    if (vMat == CHURCH) {
        // seit Rungholt Phase 11: Klosterformat 28 × 9 cm, Wendischer Verband (zwei Läufer, ein Binder), dunkle
        // Brandfarben; Rundbogenfenster im Schiff und Chor, Schallöffnungen im Turm
        vec2 p = vec2(uv.x, vLocalY);
        float row = floor(p.y / 0.105);
        float u3 = (p.x + mod(row, 2.0) * 0.21) / 0.70;                 // zwei Läufer (je 28 cm) und ein Binder (14 cm)
        float f3 = fract(u3) * 0.70, idx3 = floor(u3);
        float bx = f3 < 0.28 ? f3 / 0.28 : f3 < 0.56 ? (f3 - 0.28) / 0.28 : (f3 - 0.56) / 0.14;
        float id = idx3 * 3.0 + (f3 < 0.28 ? 0.0 : f3 < 0.56 ? 1.0 : 2.0);
        float by = fract(p.y / 0.105);
        float joint = smoothstep(0.0, 0.05, bx) * (1.0 - smoothstep(0.95, 1.0, bx)) * smoothstep(0.0, 0.13, by) * (1.0 - smoothstep(0.87, 1.0, by));
        float tone = hash1(id * 5.3 + row * 2.7 + rnd * 13.0);
        vec3 c = mix(vec3(0.22, 0.065, 0.04), vec3(0.36, 0.12, 0.065), tone) * (0.88 + 0.18 * vnoise(p * 24.0));
        c = mix(c, vec3(0.13, 0.07, 0.06), smoothstep(0.78, 1.0, tone) * 0.7);    // Sinterbrand fast schwarz
        c = mix(vec3(0.36, 0.34, 0.30), c, joint);
        c *= 0.9 + 0.15 * fbm(vWorld.xz * 0.3 + vWorld.y * 0.2);          // Flecken, Flechten
        // Rundbogenfenster: 1,1 m breit, Sohlbank 3,2 m, Bogen oben bei 6,6 m; alle 4,2 m (nicht im Turm oben)
        float wx = mod(p.x - 1.5, 4.2);
        if (p.y > 3.2 && p.y < 6.6 && wx < 1.1 && p.x > 1.0) {
            float cx = wx - 0.55, top = 6.05 + sqrt(max(0.0, 0.3025 - cx * cx));
            if (p.y < top) {
                float edge = min(min(wx, 1.1 - wx), min(p.y - 3.2, top - p.y));
                c = edge < 0.12 ? c * 0.55 : vec3(0.012, 0.014, 0.016) + vec3(0.03, 0.035, 0.03) * vnoise(p * vec2(9.0, 5.0));   // Laibung dunkel, Glas blind
                bumpOut = 1.0;
                return c;
            }
        }
        // Schallöffnungen oben im Turm: zwei je Seite, 0,9 × 2,4 m mit Bogen
        float sx = mod(p.x - 1.2, 3.0);
        if (p.y > 17.5 && p.y < 20.3 && sx < 0.9) {
            float cx = sx - 0.45, top = 19.85 + sqrt(max(0.0, 0.2025 - cx * cx));
            if (p.y < top) { bumpOut = 1.0; return vec3(0.01, 0.01, 0.012); }
        }
        c *= mix(0.62, 1.0, smoothstep(0.0, 0.9, vLocalY + 0.1));     // unten Spritzwasser und Moos
        bumpOut = mix(0.7, 1.0, joint);
        return c;
    }
    if (vMat == SHINGLE) {
        // seit Rungholt Phase 11: Eichenschindeln 12 cm breit in Reihen zu 25 cm, versetzt; silbergrau verwittert
        vec2 p = vec2(uv.x, vLocalY);
        float row = floor(p.y / 0.25), off = mod(row, 2.0) * 0.06;
        float sx = fract((p.x + off) / 0.12), sy = fract(p.y / 0.25);
        float tone = hash1(floor((p.x + off) / 0.12) * 3.1 + row * 7.7 + rnd);
        vec3 c = mix(vec3(0.20, 0.19, 0.17), vec3(0.32, 0.30, 0.27), tone) * (0.85 + 0.2 * vnoise(p * vec2(4.0, 30.0)));
        c *= mix(0.55, 1.0, smoothstep(0.0, 0.08, sx) * (1.0 - smoothstep(0.92, 1.0, sx)));
        c *= mix(0.65, 1.0, smoothstep(0.0, 0.25, sy));                   // Schatten der Reihe darüber
        bumpOut = 0.9;
        wetDark = 0.75;
        return c;
    }
    if (vMat == THATCH) {
        // Von außen sieht man die gekappten Halmenden: dichte Borsten, entlang der Neigung gekämmt (u vom First zur
        // Traufe), dazu flache Wellen der Lagen, Regenschlieren abwärts, Flickstellen in anderer Farbe.
        float d = length(uCam - vWorld);
        float fineFade = 1.0 - smoothstep(8.0, 40.0, d);
        float stip = vnoise(vec2(uv.x * 26.0, uv.y * 70.0 + rnd * 50.0));
        float comb = vnoise(vec2(uv.x * 2.2, uv.y * 38.0 + rnd * 20.0));
        float brush = mix(0.5, stip * 0.55 + comb * 0.45, fineFade * 0.9 + 0.1);
        float course = fract(uv.x / 0.34 + 0.25 * vnoise(vec2(uv.y * 0.4, 3.0 + rnd)));
        float layer = 0.86 + 0.14 * smoothstep(0.0, 0.5, course);
        float streak = vnoise(vec2(uv.x * 0.35, uv.y * 3.5 + rnd * 9.0));
        float patchN = fbm(vec2(uv.x * 0.28, uv.y * 0.22) + rnd * 7.0);
        // verwittertes Reet ist dunkel graubraun (templates/Museumsdorf1 bis 11), frisches goldbraun
        vec3 fresh = vec3(0.34, 0.25, 0.13), grey = vec3(0.17, 0.145, 0.11), repair = vec3(0.36, 0.28, 0.15);
        float a = smoothstep(0.0, 1.0, age + 0.3 * (patchN - 0.5));
        vec3 c = mix(fresh, grey, a);
        c = mix(c, repair, smoothstep(0.72, 0.76, patchN) * 0.7);
        c *= (0.62 + 0.62 * brush) * layer * (0.88 + 0.2 * streak);
        // zur Traufe hin dunkler (länger nass)
        c *= mix(1.0, 0.82, smoothstep(2.5, 6.0, uv.x));
        // Moos: auf der Nordseite (Normale nach −z), alt, in Flecken
        float north = smoothstep(0.0, 0.6, -normalize(vNormal).z) * smoothstep(0.2, 0.8, normalize(vNormal).y + 0.3);
        float moss = smoothstep(0.55, 0.75, fbm(vWorld.xz * 0.45 + vWorld.y * 0.3 + rnd * 3.0)) * north * smoothstep(0.35, 0.9, age);
        c = mix(c, vec3(0.12, 0.16, 0.06) * (0.8 + 0.4 * brush), moss * 0.85);
        // Unterseite und Traufkante: Halmenden als Punkte
        if (normalize(vNormal).y < 0.2) c *= 0.75 + 0.35 * vnoise(uv * vec2(60.0, 60.0));
        wetDark = 0.6;
        return c;
    }
    if (vMat == SOD) {
        float g = fbm(vWorld.xz * 2.0 + vWorld.y + rnd * 5.0);
        vec3 c = mix(vec3(0.10, 0.13, 0.05), vec3(0.20, 0.19, 0.08), g);
        return mix(c, vec3(0.16, 0.12, 0.08), smoothstep(0.6, 0.8, vnoise(vWorld.xz * 5.0)) * 0.6);
    }
    if (vMat == DARK) return vec3(0.012, 0.010, 0.008);
    if (vMat == FISH) {
        // gespaltener Dorsch: außen graubraune Haut, innen helles, gelbliches Fleisch, Flossen dunkel
        float k = fbm(vec2(uv.x * 9.0, uv.y * 4.0) + rnd * 7.0);
        vec3 c = mix(vec3(0.16, 0.14, 0.10), vec3(0.34, 0.29, 0.19), smoothstep(0.35, 0.7, k));
        bumpOut = 0.92 + 0.08 * k;
        wetDark = 0.8;
        return c;
    }
    if (vMat == STONE) {
        // Mühlstein aus Basaltlava aus der Eifel: dunkel, porig
        float k = fbm(vec2(uv.x * 6.0, uv.y * 6.0) + vWorld.xz), pore = smoothstep(0.62, 0.7, vnoise(uv * 38.0));
        bumpOut = 0.85 + 0.15 * k;
        wetDark = 0.6;
        return vec3(0.075, 0.072, 0.07) * (0.8 + 0.4 * k) * (1.0 - 0.45 * pore);
    }
    if (vMat == SOAPSTONE) {
        // Topf aus norwegischem Speckstein: grau-grünlich, glatt, außen verrußt
        float k = fbm(uv * 5.0 + vWorld.xz);
        bumpOut = 0.95 + 0.05 * k;
        vec3 c = mix(vec3(0.13, 0.14, 0.125), vec3(0.2, 0.205, 0.185), k);
        return mix(c, vec3(0.03, 0.028, 0.026), smoothstep(0.35, 0.0, uv.y) * 0.7);
    }
    if (vMat == CLOTH) {
        // Ballen aus Wolltuch und Fell: grob gewebt, beige bis braun, mit Schnüren
        float weave = 0.5 + 0.5 * sin(uv.x * 140.0) * sin(uv.y * 140.0);
        vec3 c = mix(vec3(0.27, 0.22, 0.15), vec3(0.16, 0.12, 0.08), step(0.55, hash(floor(vWorld.xz * 0.7) + 3.0))) * (0.88 + 0.12 * weave) * (0.85 + 0.3 * fbm(uv * 3.0 + rnd));
        float cord = smoothstep(0.03, 0.0, abs(fract(uv.x * 0.9 + 0.25) - 0.5) - 0.47);
        c = mix(c, vec3(0.12, 0.1, 0.07), cord);
        wetDark = 0.55;
        return c;
    }
    if (vMat == FLOOR) {
        // gestampfter Lehm, dunkel vom Ruß, Binsen und Stroh gestreut, zum Herd hin Asche
        vec2 p = vLocal.xz;
        float g = fbm(p * 1.3 + rnd * 4.0), f = vnoise(p * 9.0);
        vec3 c = mix(vec3(0.085, 0.068, 0.05), vec3(0.13, 0.105, 0.075), g) * (0.85 + 0.25 * f);
        // Binsen: kurze, helle Striche in zufälliger Richtung
        float ang = hash(floor(p * 3.0)) * 6.2832;
        vec2 q = mat2(cos(ang), -sin(ang), sin(ang), cos(ang)) * fract(p * 3.0 - 0.5);
        float rush = smoothstep(0.035, 0.0, abs(q.y)) * step(abs(q.x), 0.45) * step(0.45, hash(floor(p * 3.0) + 7.0));
        c = mix(c, vec3(0.22, 0.18, 0.09), rush * 0.7 * smoothstep(1.6, 2.8, length(p)));
        // Asche um den Herd
        float ash = 1.0 - smoothstep(1.2, 2.3, length(p * vec2(0.55, 1.0)));
        c = mix(c, vec3(0.16, 0.15, 0.14), ash * 0.55 * (0.6 + 0.4 * g));
        return c;
    }
    if (vMat == HEARTHM) {
        // Herdstelle und Webgewichte: gebrannter Lehm, rötlich, mit grauer Asche und schwarzer Kohle
        float g = fbm(vLocal.xz * 3.0 + vLocal.y * 2.0), k = vnoise(vLocal.xz * 14.0);
        vec3 c = mix(vec3(0.22, 0.12, 0.07), vec3(0.30, 0.19, 0.11), g);
        c = mix(c, vec3(0.25, 0.24, 0.22), smoothstep(0.45, 0.7, g) * 0.8);
        c = mix(c, vec3(0.02, 0.018, 0.016), smoothstep(0.6, 0.85, k) * 0.7);
        return c;
    }
    if (vMat == FUR) {
        // Fell: zottig, Schaf hell, Ziege braun, Rind gefleckt
        float kind = hash(floor(vLocal.xz * 0.9) + rnd * 3.0);
        vec3 base = kind < 0.4 ? vec3(0.42, 0.38, 0.30) : kind < 0.75 ? vec3(0.20, 0.14, 0.09) : vec3(0.30, 0.22, 0.16);
        float hair = vnoise(vec2(uv.x * 60.0, uv.y * 18.0) + vLocal.xz * 7.0), clump = fbm(uv * 6.0 + vLocal.xz);
        bumpOut = 0.7 + 0.3 * hair;
        wetDark = 0.6;
        return base * (0.6 + 0.5 * clump) * (0.8 + 0.3 * hair);
    }
    if (vMat == IRON) {
        float k = fbm(uv * 8.0 + vLocal.xz * 3.0);
        bumpOut = 0.9 + 0.1 * k;
        return mix(vec3(0.035, 0.032, 0.03), vec3(0.09, 0.07, 0.055), k);
    }
    if (vMat == WOOL) {
        // Kettfäden senkrecht, Gewebe oben dichter; Naturweiß, Grau, ein Band in Krapprot
        float thread = 0.75 + 0.25 * sin(uv.x * 420.0);
        float band = smoothstep(0.02, 0.0, abs(fract(uv.y * 1.6) - 0.5) - 0.12);
        vec3 c = mix(vec3(0.45, 0.42, 0.35), vec3(0.30, 0.28, 0.25), hash1(floor(uv.x * 40.0)) * 0.5);
        c = mix(c, vec3(0.38, 0.08, 0.05), band * step(uv.y, 0.7));
        return c * thread;
    }
    // Boden
    float g = fbm(vWorld.xz * 0.12), m = fbm(vWorld.xz * 1.7);
    vec3 earth = vec3(0.17, 0.14, 0.10) * (0.85 + 0.3 * m), grass = vec3(0.07, 0.10, 0.035) * (0.8 + 0.4 * m);
    return mix(earth, grass, smoothstep(0.42, 0.62, g));
}

vec3 aces(vec3 x) {
    const float a = 2.51, b = 0.03, c = 2.43, d = 0.59, e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    vec3 n = normalize(vNormal);
    float bump, wetDark;
    // Rückstrahlung neben dem Gelände der Szene (Gras rund 0,1): verwittertes Holz, Lehm und Reet dunkler als frisch (Befund Phase 5)
    vec3 alb = albedo(bump, wetDark) * 0.5;
    // Phase 11: liegt die Fläche im begehbaren Raum? (dort kein Regen, kein Wasser)
    vec3 rl = vec3(0.0);
    bool inRoom = false;
    if (uRoomC.z > 0.5) { rl = roomLocal(vWorld); inRoom = roomSurface(rl, roomDir(normalize(vNormal))); }
    float wet = uWet * (vMat == DARK || inRoom ? 0.0 : 1.0);
    alb *= mix(1.0, wetDark * 0.75, wet);
    // Pfähle und Bohlen am Wasser: nass bis über den Spiegel, eine grünbraune Algenlinie, darunter Schleim (seit Haithabu Phase 8)
    if (uWater.y > 0.5 && !inRoom && (vMat == POST || vMat == PLANK || vMat == DECK || vMat == WATTLE || vMat == STONE)) {
        float hw = vWorld.y - uWater.x + 0.08 * (fbm(vWorld.xz * 2.3 + vWorld.y) - 0.5);
        float damp = 1.0 - smoothstep(0.05, 0.7, hw);
        alb *= mix(1.0, 0.55, damp);
        float algae = smoothstep(-0.7, -0.15, hw) * (1.0 - smoothstep(0.02, 0.22, hw)) * (0.6 + 0.4 * fbm(vWorld.xz * 6.0 + vWorld.y * 3.0));
        alb = mix(alb, vec3(0.03, 0.045, 0.018), algae * 0.85);
        alb = mix(alb, vec3(0.022, 0.03, 0.022), (1.0 - smoothstep(-0.9, -0.3, hw)) * 0.8);
        wet = max(wet, damp);
    }
    // Ruß um die Rauchlöcher und am First (alte Häuser mehr), fleckig ausgefranst
    float soot = 0.0;
    if (uSoot.w > 0.0 && vMat != DARK) {
        // Hof um die Rauchlöcher (Giebel bei ±halber Länge, Grubenhaus nur hinten), nach oben weiter als nach unten
        for (int k = 0; k < 2; k++) {
            if (k == 1 && uSoot.w < 0.75) break;
            float gx = k == 0 ? -uSoot.x : uSoot.x;
            vec3 d = vLocal - vec3(gx, uSoot.y, 0.0);
            d.y = d.y > 0.0 ? d.y * 0.55 : d.y * 1.4;
            soot = max(soot, exp(-dot(d, d) / 2.6));
        }
        // am First zieht der Rauch durch das Reet
        if (vMat == THATCH || vMat == SOD) soot = max(soot, 0.55 * smoothstep(uSoot.z - 2.4, uSoot.z + 0.1, vLocal.y));
    }
    soot *= (0.45 + 0.55 * vState.x) * (0.7 + 0.6 * fbm(vWorld.xz * 1.3 + vWorld.y * 0.7));
    alb = mix(alb, vec3(0.018, 0.015, 0.012), clamp(soot, 0.0, 0.9));
    // seit Haithabu Phase 15: verkohlt (fleckig, von unten nach oben und vom Dach her), eingestürzt ganz schwarz
    if (vBurn > 0.001) {
        float ch = min(vBurn, 1.0);
        float spot = fbm(vWorld.xz * 0.9 + vWorld.y * 1.7);
        float reach = vBurn > 1.0 ? 1.0 : clamp(ch * 1.6 - 0.3 + 0.5 * spot, 0.0, 1.0);
        alb = mix(alb, vec3(0.012, 0.010, 0.009), reach * (0.75 + 0.25 * spot));
    }
    // Phase 12: Schnee auf allem, was nach oben zeigt (Reet und Soden bis rund 55° Neigung); über geheizten Häusern
    // taut er um die Rauchlöcher und am First (warme Luft und Rauch ziehen durch das Reet); Reif auf dem Rest
    float snowC = 0.0;
    if ((uSnowT.x > 0.002 || uSnowT.y > 0.002) && !inRoom && vMat != DARK) {
        bool roof = vMat == THATCH || vMat == SOD || vMat == SHINGLE;
        float depth = roof ? uSnowT.x : uSnowT.y * (vMat == DECK ? 0.6 : 1.0);
        float k = roof ? smoothstep(0.40, 0.58, n.y) : smoothstep(0.62, 0.88, n.y);
        if (roof && uSoot.w > 0.0) {
            float warm = 0.0;
            for (int j = 0; j < 2; j++) {
                if (j == 1 && uSoot.w < 0.75) break;
                vec3 d = vLocal - vec3(j == 0 ? -uSoot.x : uSoot.x, uSoot.y, 0.0);
                d.y = d.y > 0.0 ? d.y * 0.6 : d.y * 1.4;
                warm = max(warm, exp(-dot(d, d) / 1.4));
            }
            warm = max(warm, 0.75 * smoothstep(uSoot.z - 0.9, uSoot.z, vLocal.y));
            depth *= 1.0 - clamp(warm * 1.2 * (0.6 + 0.4 * vState.x), 0.0, 1.0);
        }
        float nz = fbm(vWorld.xz * 0.9 + vWorld.y * 0.6);
        snowC = k * smoothstep(0.002, 0.025, depth * (0.6 + 0.8 * nz)) * smoothstep(0.25, 0.6, vAO);
        vec3 snowAlb = vec3(0.255, 0.258, 0.27) * mix(1.0, 0.72, uSnowT.z) * (0.95 + 0.08 * nz);
        alb = mix(alb, snowAlb, snowC);
        bump = mix(bump, 1.0, snowC * 0.85);
        wet *= 1.0 - snowC;
    }
    if (uSnowT.w > 0.0 && !inRoom && vMat != DARK) alb = mix(alb, vec3(0.20, 0.205, 0.215), uSnowT.w * 0.4 * (1.0 - snowC) * smoothstep(0.2, 0.7, n.y));
    float shade = 1.0;
    if (uShadowRect.w > 0.0) shade = textureLod(uShadow, (vWorld.xz - uShadowRect.xy) / uShadowRect.z, 0.0).r;
    shade *= sunShadow(n);
    float ao = vAO * bump;
    vec3 v = normalize(uCam - vWorld);
    float ndl = max(dot(n, uSunDir), 0.0) * smoothstep(-0.02, 0.04, uSunDir.y);
    float ndm = max(dot(n, uMoonDir), 0.0) * smoothstep(-0.02, 0.04, uMoonDir.y);
    // direktes Licht unter der Traufe gedämpft (Verdeckung wirkt halb auch auf die Sonne)
    float direct = mix(1.0, vAO, 0.55);
    vec3 col;
    if (inRoom) {
        vec3 nl = roomDir(n), pl = rl + nl * 0.03;
        // Sonne und Mond nur durch die Öffnungen, gedämpft vom Rauch auf dem Weg dorthin
        vec3 sl = roomDir(uSunDir), ml = roomDir(uMoonDir);
        vec4 es = roomExit(pl, sl), em = roomExit(pl, ml);
        float sunIn = ndl > 0.0 ? roomOpen(pl, sl) * exp(-roomTau(pl, pl + sl * es.w)) : 0.0;
        float moonIn = ndm > 0.0 ? roomOpen(pl, ml) * exp(-roomTau(pl, pl + ml * em.w)) : 0.0;
        col = alb * (uSunColor * ndl * sunIn * shade + uMoonColor * ndm * moonIn) * direct;
        // Himmel durch die Öffnungen: Raumwinkel jeder Öffnung, halb Himmel, halb Boden und Nachbarn
        vec3 sky = vec3(0.0);
        int no = int(uRoomC.y + 0.5);
        for (int i = 0; i < 8; i++) {
            if (i >= no) break;
            vec3 c, on;
            roomOpening(i, c, on);
            vec3 d = c - pl;
            float d2 = dot(d, d), dl = sqrt(d2), A = uRoomO[i * 2 + 1].z;
            float co = dot(d, on) / dl, cp = dot(d, nl) / dl;
            if (co <= 0.0 || cp <= 0.0) continue;
            float tri = uRoomO[i * 2].w > 0.5 ? 0.55 : 0.5;
            sky += vec3(A * co * cp / (3.14159 * max(d2, A)) * tri * exp(-roomTau(pl, c)));
        }
        col += alb * (uSkyColor * uSkyWeight * sky + uRoomBounce) * ao;
        // Lichter des Raums (Herd, Lampen), durch den Rauch gedämpft
        for (int i = 0; i < 4; i++) {
            if (i >= uRoomLightN) break;
            vec3 tl = uRoomLightPos[i].xyz - vWorld;
            float d2 = dot(tl, tl) + uRoomLightPos[i].w * uRoomLightPos[i].w;
            float tau = roomTau(pl, roomLocal(uRoomLightPos[i].xyz));
            col += alb * uRoomLightRgb[i].rgb * (max(dot(n, tl * inversesqrt(max(d2, 1e-4))), 0.0) * direct + 0.15 * ao) / d2 * exp(-tau);
        }
    } else {
    col = alb * ((uSunColor * ndl + uMoonColor * ndm) * shade * direct + uSkyColor * uSkyWeight * (0.55 + 0.45 * n.y) * ao);
    // Feuerschein: Punktlichter mit 1/d², ohne Schatten; unter der Traufe gedämpft wie die Sonne
    for (int i = 0; i < 8; i++) {
        if (i >= uPointN) break;
        vec3 tl = uPointPos[i].xyz - vWorld;
        float d2 = dot(tl, tl) + uPointPos[i].w * uPointPos[i].w;
        col += alb * uPointRgb[i].rgb * (max(dot(n, tl * inversesqrt(max(d2, 1e-4))), 0.0) * direct + 0.15 * ao) / d2;
    }
    }
    if (inRoom) wet = 0.0;
    // nasser Glanz
    if (wet > 0.0) {
        vec3 h = normalize(uSunDir + v);
        col += uSunColor * shade * pow(max(dot(n, h), 0.0), 60.0) * 0.12 * wet * ndl;
    }
    float d = length(uCam - vWorld);
    vec3 fogC = uFogColor;
    if (uSkyOn == 1) {
        vec3 hd = normalize(vec3(vWorld.x - uCam.x, 0.0, vWorld.z - uCam.z) + vec3(0.0, 1e-4, 0.0));
        fogC = textureLod(uSky, vec2(atan(hd.x, -hd.z) / 6.28318530718 + 0.5, 0.5), 0.0).rgb;
    }
    col = mix(col, fogC, 1.0 - exp(-d / max(1.0, uFogDist)));
    if (uTonemap == 1) col = pow(aces(col * uExposure), vec3(1.0 / 2.2));
    frag = vec4(col, 1.0);
}
