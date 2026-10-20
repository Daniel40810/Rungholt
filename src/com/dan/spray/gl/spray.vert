// spray.vert — Tropfen und Nebel als Bildtafeln, ohne Eckpuffer: Instanz = Tropfen, sechs Ecken je Tafel.
// Spritzwasser und Fahnen als Streifen entlang der Bewegung (Bewegungsunschärfe), Nebel als weiche, wachsende Bälle.
// Tropfen, die in einen Körper fliegen würden, gleiten an seiner Wand entlang.
#include "spray_common.glsl"
uniform mat4 uVP;
uniform vec3 uEye, uFwd;
uniform int uFirst, uPer;
uniform float uShutter;          // Belichtungszeit für die Streifen in s
uniform float uPixScale;         // Bildpunkte je m in 1 m Abstand
uniform float uLodPix;           // Durchmesser, ab dem große Bälle ausgedünnt werden (0 = aus)
uniform vec4 uBodyA[8];          // x, z, y0, y1
uniform vec2 uBodyR[8];          // r0, r1
uniform int uBodies;
out vec2 vUv;
out float vAlpha;
out float vDepth;
out vec3 vWorld;
flat out int vKind;
flat out float vRand;
flat out float vAge;
flat out float vKeep;            // Anteil der Bälle dieser Größe, die gezeichnet werden (für die Schwelle im Fragment)

void main() {
    int inst = gl_InstanceID, pi = uFirst + inst / uPer, i = inst % uPer;
    SprayParcel P = parcels[pi];
    vec3 pos, vel;
    float size;
    bool alive;
    sprayParticle(P, i, 0.0, pos, vel, size, alive);
    int kind = int(P.f.w + 0.5);
    vKind = kind;
    uint seed = floatBitsToUint(P.d.x);
    vRand = sprayRnd(seed, i, 10);
    vAge = P.a.w;
    vKeep = 1.0;
    if (!alive) { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); vAlpha = 0.0; vUv = vec2(0.0); vDepth = 0.0; vWorld = vec3(0.0); return; }
    // an Wänden entlang gleiten
    for (int b = 0; b < 8; b++) {
        if (b >= uBodies) break;
        vec4 A = uBodyA[b];
        if (pos.y < A.z || pos.y > A.w) continue;
        float r = mix(uBodyR[b].x, uBodyR[b].y, clamp((pos.y - A.z) / (A.w - A.z), 0.0, 1.0)) + 0.05;
        vec2 d = pos.xz - A.xy;
        float l = length(d);
        if (l < r) pos.xz = A.xy + (l > 1e-4 ? d / l : vec2(1.0, 0.0)) * r;
    }
    int c = gl_VertexID % 6;
    vec2 q = c == 0 ? vec2(-1, -1) : c == 1 ? vec2(1, -1) : c == 2 ? vec2(1, 1) : c == 3 ? vec2(-1, -1) : c == 4 ? vec2(1, 1) : vec2(-1, 1);
    vUv = q;
    vec3 toEye = normalize(uEye - pos);
    vec3 right, up;
    float age = max(0.0, P.a.w), life = P.b.w;
    if (kind == 1 || kind == 3) {
        right = normalize(cross(toEye, vec3(0.0, 1.0, 0.0)) + vec3(1e-5, 0.0, 0.0));
        up = cross(right, toEye);
        right *= size;
        up *= size;
        vAlpha = kind == 3 ? smoothstep(0.0, 0.35, age) * (1.0 - smoothstep(life * 0.35, life, age))
                           : smoothstep(0.0, 1.2, age) * (1.0 - smoothstep(life * 0.5, life, age));
        // große Nebelbälle im Bild zufällig ausdünnen, die übrigen decken entsprechend mehr (gleiche mittlere Trübung);
        // Weißwasser deckt bis 45 %, dort höchstens auf die Hälfte (sonst ginge die Rechnung über 100 %)
        if (uLodPix > 0.0) {
            float pix = 2.0 * size * uPixScale / max(0.5, dot(pos - uEye, uFwd));
            float keep = clamp((uLodPix * uLodPix) / max(1.0, pix * pix), kind == 1 ? 0.125 : 0.5, 1.0);
            if (sprayRnd(seed, i, 11) > keep) { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); vAlpha = 0.0; vUv = vec2(0.0); vDepth = 0.0; vWorld = vec3(0.0); return; }
            vAlpha /= keep;
            vKeep = keep;
        }
    } else {
        // Streifen: lange Achse entlang der Geschwindigkeit, quer dazu die Tropfengröße
        vec3 v = vel * uShutter;
        float len = length(v);
        vec3 axis = len > 1e-4 ? v / len : vec3(0.0, 1.0, 0.0);
        right = normalize(cross(axis, toEye) + vec3(0.0, 1e-5, 0.0)) * size * 0.5;
        up = axis * (len * 0.5 + size * 0.5);
        pos += v * 0.5;
        float a0 = kind == 0 ? 0.75 : 0.5;
        vAlpha = a0 * (1.0 - smoothstep(life * 0.7, life, age)) * clamp(size / max(size, len * 0.3), 0.25, 1.0);
    }
    vec3 wp = pos + right * q.x + up * q.y;
    vDepth = dot(wp - uEye, uFwd);
    vWorld = wp;
    gl_Position = uVP * vec4(wp, 1.0);
}
