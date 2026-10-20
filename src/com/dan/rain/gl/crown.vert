// crown.vert — Krönchen: Wo ein großer Tropfen aufs Wasser schlägt, springen Spritzer im Kranz hoch, in der Mitte ein
// Säulchen. Die Zellen liegen fest in der Welt um das Auge; die Höhe der Fläche kommt aus der Tiefe der Szene
// (der Strahl vom Auge durch den Aufschlagpunkt), so landen Krönchen auf Wellen, Platte und Steinen gleichermaßen.
#include "rain_common.glsl"
uniform mat4 uVP, uInvVP;
uniform vec3 uEye, uFwd;
uniform float uPixel;
uniform float uCell;          // Zellgröße in m
uniform int uCells;           // Zellen je Seite
uniform int uPer;             // Spritzer je Krönchen
uniform float uPeriod;        // s zwischen zwei Einschlägen einer Zelle
uniform float uTime;
uniform float uLevel;         // Wasserspiegel
uniform float uAlpha;
uniform float uActive;       // Anteil der Zellen mit Krönchen bei voller Rate
uniform sampler2D uSceneDepth;
uniform int uReversed;
uniform vec2 uViewport;
out vec2 vUv;
out float vAlpha;
out float vDepth;
out vec3 vWorld;

void hide() { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); vAlpha = 0.0; vUv = vec2(0.0); vDepth = 0.0; vWorld = vec3(0.0); }

void main() {
    int cell = gl_InstanceID / uPer, k = gl_InstanceID % uPer;
    int cx = cell % uCells, cz = cell / uCells;
    vec2 base = floor(uEye.xz / uCell) * uCell - float(uCells / 2) * uCell + vec2(cx, cz) * uCell;
    ivec2 ic = ivec2(floor(base / uCell + 0.5));
    uint cs = rainHash(uint(ic.x * 73856093) ^ uint(ic.y * 19349663) ^ uRainSeed);
    float phase = float(rainHash(cs) >> 8u) / 16777216.0 * uPeriod;
    float e = floor((uTime + phase) / uPeriod);
    float age = uTime + phase - e * uPeriod;
    uint se = rainHash(cs ^ uint(int(e)) * 2654435761u);
    float ra = float(rainHash(se) >> 8u) / 16777216.0;
    vec2 at = base + vec2(float(rainHash(se + 1u) >> 8u), float(rainHash(se + 2u) >> 8u)) / 16777216.0 * uCell;
    float life = 0.28;
    if (age > life || ra > uActive * rainCurtain(at)) { hide(); return; }
    // Fläche unter dem Aufschlag: Strahl vom Auge durch den Punkt auf dem Wasserspiegel, Tiefe aus der Szene
    vec4 c0 = uVP * vec4(at.x, uLevel, at.y, 1.0);
    if (c0.w <= 0.0) { hide(); return; }
    vec3 ndc = c0.xyz / c0.w;
    if (any(greaterThan(abs(ndc.xy), vec2(1.0)))) { hide(); return; }
    ivec2 px = ivec2((ndc.xy * 0.5 + 0.5) * uViewport);
    float dz = texelFetch(uSceneDepth, clamp(px, ivec2(0), ivec2(uViewport) - 1), 0).r;
    vec4 w = uInvVP * vec4(ndc.xy, uReversed == 1 ? dz : dz * 2.0 - 1.0, 1.0);
    vec3 hit = w.xyz / w.w;
    if (abs(hit.y - uLevel) > 6.0 || length(hit - uEye) > 60.0 || rainSheltered(hit)) { hide(); return; }
    float d = 1.0 + 3.0 * float(rainHash(se + 3u) >> 8u) / 16777216.0;
    float ang = 6.2831853 * (float(k) + 0.3 * float(rainHash(se + uint(k) + 7u) >> 8u) / 16777216.0) / float(max(1, uPer - 1));
    vec3 v = k == 0 ? vec3(0.0, 0.9 + 0.35 * d, 0.0) : vec3(cos(ang) * (0.5 + 0.15 * d), 0.6 + 0.25 * d, sin(ang) * (0.5 + 0.15 * d));
    vec3 pos = hit + v * age + vec3(0.0, -4.9 * age * age, 0.0);
    float dist = length(pos - uEye);
    int c = gl_VertexID % 6;
    vec2 q = c == 0 ? vec2(-1, -1) : c == 1 ? vec2(1, -1) : c == 2 ? vec2(1, 1) : c == 3 ? vec2(-1, -1) : c == 4 ? vec2(1, 1) : vec2(-1, 1);
    vUv = q;
    vec3 toEye = normalize(uEye - pos);
    vec3 right = normalize(cross(toEye, vec3(0.0, 1.0, 0.0)) + vec3(1e-5, 0.0, 0.0));
    vec3 up = cross(right, toEye);
    float real = (k == 0 ? 2.5 : 1.0) * d * 0.001;
    float size = max(real, uPixel * dist * 1.2);
    vec3 wp = pos + (right * q.x + up * q.y * (k == 0 ? 2.2 : 1.0)) * size * 0.5;
    vAlpha = uAlpha * clamp(real / size * 4.0, 0.15, 1.0) * (1.0 - smoothstep(life * 0.6, life, age)) * smoothstep(0.5, 2.0, dist);
    vDepth = dot(wp - uEye, uFwd);
    vWorld = wp;
    gl_Position = uVP * vec4(wp, 1.0);
}
