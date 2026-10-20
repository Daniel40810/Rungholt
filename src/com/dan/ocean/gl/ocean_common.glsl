// ocean_common.glsl — gemeinsame Werte der Oberflächen-Shader: Kaskaden, Dünung, Ringwellen, Rauschen, Himmel.
// Formeln wie in com.dan.ocean (Swell, Ripples, Ocean.streaks/surfRing), damit Bild und Abfragen übereinstimmen.
uniform mat4 uVP;
uniform vec3 uCam;
uniform vec2 uCenter;          // Mittelpunkt des Ringgitters (auf 0,25 m gerastert)
uniform float uTime;           // Zeit der See modulo Wiederholzeit
uniform float uLevel;          // Wasserstand
uniform int uCascades;
uniform float uLength[4];
uniform sampler2DArray uDisp;  // Dx, Dy, Dz, Schaum
uniform sampler2DArray uDeriv; // ∂h/∂x, ∂h/∂z, ∂Dx/∂x, ∂Dz/∂z
uniform vec4 uSwellA[4];       // Richtung x, z, k, ω
uniform vec4 uSwellB[4];       // A, Q, φ₀, –
uniform int uReversed;
uniform sampler2D uBed;        // seit Haithabu: Höhe des Grunds, optional
uniform vec4 uBedRect;         // x, z der Nordwestecke, Kantenlänge (m), an (1) / aus (0)
uniform vec3 uBedMap;          // Höhe = Wert · x + y; z: Trübung je m
uniform vec2 uBedOutside;      // seit Rungholt: Grund außerhalb des Rechtecks (x, m), an (y = 1); aus: dort kein Wasser

// Tiefe des Wassers über dem Grund am Ort p (groß, wenn kein Grund gesetzt; −1, wenn außerhalb des Rechtecks)
float bedDepth(vec2 p) {
    if (uBedRect.w <= 0.0) return 1.0e4;
    vec2 uv = (p - uBedRect.xy) / uBedRect.z;
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return uBedOutside.y > 0.5 ? uLevel - uBedOutside.x : -1.0;
    return uLevel - (textureLod(uBed, uv, 0.0).r * uBedMap.x + uBedMap.y);
}
const float EARTH_R = 6371000.0;

// seit Glacier Phase 8: eine lange Welle von außen (Flachwasser, zwei Gitter), optional. Je Texel der Wasserstand über
// dem Ruhespiegel; trockene Zellen tragen LONG_DRY und zählen beim Abtasten nicht mit.
uniform sampler2D uLongH0, uLongH1;
uniform vec4 uLongR0, uLongR1;   // x0, z0 der Ecke, Zelle (m), an (1) / aus (0)
const float LONG_DRY = -29.0;

// x: Höhe (m), y: Gewicht nasser Nachbarn (0 = trocken), z: 1 im Gitter, sonst 0
vec3 longLevel(sampler2D t, vec4 r, vec2 p) {
    ivec2 n = textureSize(t, 0);
    vec2 u = (p - r.xy) / r.z - 0.5;
    if (u.x < -0.5 || u.y < -0.5 || u.x > float(n.x) - 0.5 || u.y > float(n.y) - 0.5) return vec3(0.0);
    vec2 i = floor(u), f = u - i;
    float s = 0.0, w = 0.0;
    for (int b = 0; b <= 1; b++)
        for (int a = 0; a <= 1; a++) {
            ivec2 c = clamp(ivec2(i) + ivec2(a, b), ivec2(0), n - 1);
            float e = texelFetch(t, c, 0).r;
            if (e <= LONG_DRY) continue;
            float ww = (a == 0 ? 1.0 - f.x : f.x) * (b == 0 ? 1.0 - f.y : f.y);
            s += ww * e;
            w += ww;
        }
    return vec3(w > 1.0e-4 ? s / w : 0.0, w, 1.0);
}

vec3 longWave(vec2 p) {
    if (uLongR0.w > 0.0) {
        vec3 a = longLevel(uLongH0, uLongR0, p);
        if (a.z > 0.0) return a;
    }
    if (uLongR1.w > 0.0) return longLevel(uLongH1, uLongR1, p);
    return vec3(0.0);
}

// seit Rungholt Phase 6: Strömung als Textur (RG: m/s nach Osten und Süden) über {x0, z0, Zelle, an}; die Fläche
// kräuselt sich darin, Schaum zieht in Streifen mit, das Wasser wird trüb vom Schwebstoff (uFlowSilt: Farbe, Stärke)
uniform sampler2D uFlow;
uniform vec4 uFlowRect;
uniform vec4 uFlowSilt;
// seit Rungholt Phase 6: gegen das Kachelmuster (Befund Status_Phase4: Rauten auf dem Wasser im Watt) schwanken die
// beiden größten Kaskaden und die Dünung mit flachem Rauschen in der Stärke (0 = aus, 1 = voll)
uniform float uTileBreak;
// seit Rungholt Phase 6: weicher Rand der langen Welle (0 = wie bisher: halbe Zelle über die letzte nasse hinaus, dann 1 m
// unter den Grund)
uniform float uLongSoft;

vec2 flowAt(vec2 p) {
    if (uFlowRect.w <= 0.0) return vec2(0.0);
    vec2 uv = (p - uFlowRect.xy) / (uFlowRect.z * vec2(textureSize(uFlow, 0)));
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return vec2(0.0);
    return textureLod(uFlow, uv, 0.0).rg;
}

// Dünung am Ruhepunkt p: Auslenkung (xyz) und Steigungen dy/dx0, dy/dz0, Jacobi-Anteil
void swell(vec2 p, out vec3 d, out vec3 slope) {
    d = vec3(0.0);
    slope = vec3(0.0);
    for (int i = 0; i < 4; i++) {
        float a = uSwellB[i].x;
        if (a == 0.0) continue;
        vec2 dir = uSwellA[i].xy;
        float k = uSwellA[i].z, w = uSwellA[i].w, q = uSwellB[i].y;
        float ph = k * dot(dir, p) - w * uTime + uSwellB[i].z;
        float s = sin(ph), c = cos(ph);
        d += vec3(-q * a * dir.x * s, a * c, -q * a * dir.y * s);
        slope += vec3(-a * k * dir.x * s, -a * k * dir.y * s, -q * a * k * c);
    }
}

float hash21(vec2 p) { return fract(sin(p.x * 127.1 + p.y * 311.7) * 43758.5453); }

float vnoise(vec2 p) {
    vec2 i = floor(p), f = p - i;
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i), b = hash21(i + vec2(1.0, 0.0)), c = hash21(i + vec2(0.0, 1.0)), d = hash21(i + vec2(1.0, 1.0));
    return a + (b - a) * f.x + (c - a) * f.y + (a - b - c + d) * f.x * f.y;
}

// Stärke einer Kaskade am Ort (gegen das Kachelmuster): 1 ohne uTileBreak; sonst für die beiden größten 0,45 … 1,55
// nach Rauschen über gut dreieinhalb Kachellängen, im Mittel 1
float cascadeGain(vec2 p, int c) {
    if (uTileBreak <= 0.0 || c > 1) return 1.0;
    float n = vnoise(p / (uLength[c] * 3.7) + float(c) * 13.1) * 0.65 + vnoise(p / (uLength[c] * 1.3) + float(c) * 5.3) * 0.35;
    return mix(1.0, 0.45 + 1.1 * n, uTileBreak);
}
float swellGain(vec2 p) {
    if (uTileBreak <= 0.0) return 1.0;
    return mix(1.0, 0.5 + vnoise(p / 430.0 + 3.7), uTileBreak);
}
