// streak.vert — Regentropfen als Streifen ohne Eckpuffer: Instanz = Tropfen, sechs Ecken je Streifen. Der Streifen ist
// so lang, wie der Tropfen in der Belichtungszeit fällt, und mindestens ein Bildpunkt breit; wie viel vom Bildpunkt
// der Tropfen wirklich deckt, geht in die Deckkraft.
#include "rain_common.glsl"
uniform mat4 uVP;
uniform vec3 uEye;
uniform vec3 uFwd;
uniform float uShutter;      // Belichtungszeit in s
uniform float uPixel;        // Breite eines Bildpunkts in m auf 1 m Abstand
uniform float uAlpha;        // Grunddeckkraft (aus der Rate)
out vec2 vUv;
out float vAlpha;
out float vDepth;
out vec3 vWorld;

void main() {
    vec3 pos, vel;
    float d;
    bool live;
    rainDrop(gl_InstanceID, pos, vel, d, live);
    float dist = length(pos - uEye);
    // zum Rand des Kastens hin ausblenden (sonst sieht man seine Kante), dicht vor dem Auge auch
    float edge = max(abs(pos.x - uRainA.x), abs(pos.z - uRainA.z)) / uRainA.w;
    float fade = (1.0 - smoothstep(0.65, 0.98, edge)) * (1.0 - smoothstep(0.7, 0.98, abs(pos.y - uRainA.y) / uRainC.w)) * smoothstep(0.25, 1.2, dist);
    if (!live || fade <= 0.0) { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); vAlpha = 0.0; vUv = vec2(0.0); vDepth = 0.0; vWorld = vec3(0.0); return; }
    int c = gl_VertexID % 6;
    vec2 q = c == 0 ? vec2(-1, -1) : c == 1 ? vec2(1, -1) : c == 2 ? vec2(1, 1) : c == 3 ? vec2(-1, -1) : c == 4 ? vec2(1, 1) : vec2(-1, 1);
    vUv = q;
    vec3 seg = vel * uShutter;
    float len = length(seg);
    vec3 axis = seg / max(len, 1e-5);
    vec3 toEye = normalize(uEye - pos);
    vec3 right = normalize(cross(axis, toEye) + vec3(1e-5, 0.0, 0.0));
    float real = d * 0.001;
    float width = max(real, uPixel * dist * 1.1);
    float cover = real / width;
    // der Streifen endet am Tropfen und zieht sich gegen die Bewegung
    vec3 mid = pos - seg * 0.5;
    vec3 wp = mid + right * (q.x * width * 0.5) + axis * (q.y * (len * 0.5 + width * 0.5));
    // ein Tropfen deckt nur einen Teil des Bildpunkts, leuchtet dort aber hell (er bündelt den Himmel)
    vAlpha = uAlpha * fade * clamp(cover * 25.0, 0.3, 1.0) * clamp(width / max(width, len) * 6.0, 0.45, 1.0);
    vDepth = dot(wp - uEye, uFwd);
    vWorld = wp;
    gl_Position = uVP * vec4(wp, 1.0);
}
