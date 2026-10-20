// hearth.vert — Flammen, Funken und Ruß als Tafeln ohne Eckpuffer. Instanz = (Strahler, Nummer), sechs Ecken je Tafel.
// Strahler im Speicherpuffer: a = Fuß x, y, z, Breite; b = Höhe, Stärke, Zufall (Bits), Art.
// Modus 0: Flammenzungen (senkrecht, zur Kamera gedreht) und Glut (flach am Boden); 1: Funken als Striche; 2: Ruß.
struct HearthFlame { vec4 a; vec4 b; };
layout(std430, binding = 0) readonly buffer HearthFlames { HearthFlame flames[]; };
uniform mat4 uVP;
uniform vec3 uEye;
uniform int uMode, uPer;
uniform float uTime;
uniform vec3 uWind;              // Richtung x, z (wohin), Geschwindigkeit m/s
out vec2 vUv;
out float vDepth;
out vec3 vWorld;
flat out float vI;
flat out float vRand;
flat out int vKind;
flat out float vAge;

uint hearthHash(uint x) { x ^= x >> 16u; x *= 0x7feb352du; x ^= x >> 15u; x *= 0x846ca68bu; x ^= x >> 16u; return x; }
float hearthRnd(uint seed, int k) { return float(hearthHash(hearthHash(uint(k)) ^ seed) >> 8u) * (1.0 / 16777216.0); }

// Wie viele Zungen, Funken, Rußballen je Art (Feuerstelle, Fackel, Esse, Haus, Glut)
int countFor(int mode, int kind) {
    if (mode == 0) return kind == 0 ? 4 : kind == 1 ? 2 : kind == 2 ? 3 : kind == 3 ? 3 : 1;
    if (mode == 1) return kind == 0 ? 16 : kind == 1 ? 4 : kind == 2 ? 30 : kind == 3 ? 16 : 3;
    return kind == 3 ? 8 : 0;
}

void hide() { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); vUv = vec2(0.0); vDepth = 0.0; vWorld = vec3(0.0); vI = 0.0; }

void main() {
    int inst = gl_InstanceID, fi = inst / uPer, j = inst % uPer;
    HearthFlame F = flames[fi];
    int kind = int(F.b.w + 0.5);
    uint seed = floatBitsToUint(F.b.z);
    vKind = kind;
    vRand = hearthRnd(seed, j * 7 + uMode * 1000);
    vAge = 0.0;
    if (j >= countFor(uMode, kind) || F.b.y <= 0.001) { hide(); return; }
    int c = gl_VertexID % 6;
    vec2 q = c == 0 ? vec2(-1, -1) : c == 1 ? vec2(1, -1) : c == 2 ? vec2(1, 1) : c == 3 ? vec2(-1, -1) : c == 4 ? vec2(1, 1) : vec2(-1, 1);
    vec3 base = F.a.xyz;
    float w = F.a.w, h = F.b.x;
    vec3 wp;
    vec2 wind = uWind.xy * uWind.z;
    if (uMode == 0) {
        if (kind == 4) {
            // Glut: flach liegende Tafel
            wp = base + vec3(q.x * w, 0.05 + 0.02 * float(j), q.y * w * 0.6);
            vUv = q;
            vI = F.b.y;
        } else {
            // Zunge: versetzt um den Fuß, im Wind geneigt, zur Kamera gedreht (nur um die Senkrechte)
            float ang = vRand * 6.2831, off = (kind == 1 ? 0.0 : 0.35) * w * sqrt(hearthRnd(seed, j * 7 + 3));
            vec3 foot = base + vec3(cos(ang) * off, 0.0, sin(ang) * off);
            float hh = h * (0.7 + 0.5 * hearthRnd(seed, j * 7 + 5)) * (j == 0 ? 1.1 : 0.85);
            vec3 toEye = uEye - foot;
            toEye.y = 0.0;
            vec3 right = normalize(cross(vec3(0.0, 1.0, 0.0), normalize(toEye + vec3(1e-4, 0.0, 0.0))));
            float y01 = q.y * 0.5 + 0.5;
            vec3 lean = vec3(wind.x, 0.0, wind.y) * 0.12 * y01 * y01 * hh;
            wp = foot + right * q.x * w * (0.55 + 0.25 * hearthRnd(seed, j * 7 + 6)) + vec3(0.0, (y01 * 1.15 - 0.08) * hh, 0.0) + lean;
            vUv = vec2(q.x, y01);
            vI = F.b.y;
        }
    } else if (uMode == 1) {
        // Funke: steigt mit dem Auftrieb, kühlt ab, treibt mit dem Wind; Strich entlang der Bewegung
        float life = kind == 3 ? 4.5 : kind == 2 ? 1.6 : 2.4;
        float ph = fract(uTime / life + hearthRnd(seed, j * 7 + 1));
        float a = ph * life;
        vAge = ph;
        float k = 1.1, v0 = (kind == 3 ? 6.0 : kind == 2 ? 3.0 : 3.5) * (0.6 + 0.8 * hearthRnd(seed, j * 7 + 2)) * max(0.6, h / 1.2);
        float rise = v0 / k * (1.0 - exp(-k * a)) - 0.25 * a * a * ph;
        float spread = (hearthRnd(seed, j * 7 + 3) - 0.5) * 2.0, spread2 = (hearthRnd(seed, j * 7 + 4) - 0.5) * 2.0;
        vec3 p = base + vec3(spread * w * 0.6, h * 0.3, spread2 * w * 0.6)
               + vec3(wind.x, 0.0, wind.y) * (a - (1.0 - exp(-k * a)) / k)
               + vec3(spread, 0.0, spread2) * 0.6 * a + vec3(0.0, rise, 0.0);
        vec3 vel = vec3(wind.x, 0.0, wind.y) * (1.0 - exp(-k * a)) + vec3(0.0, v0 * exp(-k * a), 0.0) + vec3(spread, 0.0, spread2) * 0.6;
        float len = length(vel) * 0.025 + 0.015;
        vec3 axis = normalize(vel + vec3(0.0, 1e-4, 0.0));
        vec3 toEye = normalize(uEye - p);
        vec3 side = normalize(cross(axis, toEye) + vec3(1e-5, 0.0, 0.0)) * 0.012;
        wp = p + side * q.x + axis * q.y * len;
        vUv = q;
        vI = F.b.y * (1.0 - ph) * (1.0 - ph);
    } else {
        // Ruß über dem Hausbrand: dunkle Ballen, die steigen, wachsen und verblassen
        float life = 9.0;
        float ph = fract(uTime / life + hearthRnd(seed, j * 7 + 1));
        float a = ph * life;
        vAge = ph;
        float r = 0.8 + 0.55 * a;
        vec3 p = base + vec3(0.0, h * 0.8 + 2.2 * a, 0.0) + vec3(wind.x, 0.0, wind.y) * a * 0.8
               + vec3(hearthRnd(seed, j * 7 + 3) - 0.5, 0.0, hearthRnd(seed, j * 7 + 4) - 0.5) * (w + a);
        vec3 toEye = normalize(uEye - p);
        vec3 right = normalize(cross(toEye, vec3(0.0, 1.0, 0.0)) + vec3(1e-5, 0.0, 0.0));
        vec3 up = cross(right, toEye);
        wp = p + (right * q.x + up * q.y) * r;
        vUv = q;
        vI = F.b.y * smoothstep(0.0, 0.12, ph) * (1.0 - ph);
    }
    vWorld = wp;
    vDepth = length(wp - uEye);
    gl_Position = uVP * vec4(wp, 1.0);
}
