// scene.frag — die Szene der Vorführung, ohne Dreiecke: Himmel mit Sonne, die See mit kleinen Wellen, ein rot-weißer
// Turm von 30 m mit Laterne und sechs Pfähle in 60 m bis 3 km Abstand (daran sieht man, wie weit die Sicht reicht).
// Schreibt Farbe (linear, vorbelichtet) und Tiefe (klassisch, −1..1).
in vec2 vUV;
out vec4 frag;
uniform mat4 uInvVP, uVP;
uniform vec3 uCam, uSunDir, uSunCol, uSkyTop, uSkyHor;
uniform float uTime, uLamp;

const float PI = 3.14159265;
const vec3 TOWER = vec3(0.0, 0.0, -420.0);
const float TR = 4.0, TH = 30.0;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x), f.y);
}

float hitCyl(vec3 o, vec3 d, vec3 c, float r, float h, out vec3 n) {
    vec3 q = o - c;
    float a = dot(d.xz, d.xz), b = dot(q.xz, d.xz), cc = dot(q.xz, q.xz) - r * r;
    float disc = b * b - a * cc;
    n = vec3(0.0);
    if (disc < 0.0 || a < 1e-9) return -1.0;
    float t = (-b - sqrt(disc)) / a;
    if (t < 0.0) return -1.0;
    vec3 p = q + d * t;
    if (p.y < -3.0 || p.y > h) return -1.0;
    n = normalize(vec3(p.x, 0.0, p.z));
    return t;
}

vec3 skyCol(vec3 d) {
    vec3 c = mix(uSkyHor, uSkyTop, pow(clamp(d.y, 0.0, 1.0), 0.45));
    float s = max(dot(d, uSunDir), 0.0);
    c += uSunCol * (pow(s, 900.0) * 400.0 + pow(s, 12.0) * 0.08);
    return c;
}

void main() {
    vec4 a = uInvVP * vec4(vUV * 2.0 - 1.0, -1.0, 1.0), b = uInvVP * vec4(vUV * 2.0 - 1.0, 1.0, 1.0);
    vec3 o = uCam, d = normalize(b.xyz / b.w - a.xyz / a.w);
    float best = 1e9;
    vec3 col = skyCol(d), n;
    float tw = d.y < -1e-5 ? -o.y / d.y : -1.0;
    if (tw > 0.0) {
        best = tw;
        vec3 p = o + d * tw;
        vec2 g = vec2(vnoise(p.xz * 0.35 + uTime * 0.4) - vnoise(p.xz * 0.35 + vec2(3.1, 0.0) + uTime * 0.4),
                      vnoise(p.xz * 0.35 + vec2(0.0, 5.7) - uTime * 0.3) - vnoise(p.xz * 0.35 + vec2(1.9, 7.3) - uTime * 0.3));
        float fade = exp(-tw / 260.0);
        vec3 nn = normalize(vec3(g.x * 0.18 * fade, 1.0, g.y * 0.18 * fade));
        vec3 r = reflect(d, nn);
        float f = 0.02 + 0.98 * pow(1.0 - max(dot(-d, nn), 0.0), 5.0);
        col = skyCol(r) * f + vec3(0.01, 0.025, 0.03) * (uSkyTop + uSkyHor);
    }
    // Turm
    float t = hitCyl(o, d, TOWER, TR, TH, n);
    if (t > 0.0 && t < best) {
        best = t;
        vec3 p = o + d * t;
        float y = p.y;
        vec3 alb = y < 8.0 ? vec3(0.06) : (mod(floor((y - 8.0) / 4.0), 2.0) < 1.0 ? vec3(0.75) : vec3(0.6, 0.06, 0.04));
        if (y > 25.0) alb = vec3(0.1);
        col = alb * (uSunCol * max(dot(n, uSunDir), 0.0) * 0.32 + mix(uSkyHor, uSkyTop, 0.5) * 1.0);
        if (y > 25.5 && y < 28.0) col += vec3(1.0, 0.85, 0.6) * uLamp;
    }
    // sechs Pfähle (Peilpfähle) in wachsendem Abstand
    for (int i = 0; i < 6; i++) {
        float dist = 60.0 * pow(1.95, float(i));
        vec3 c = vec3(-70.0 + 25.0 * float(i), 0.0, -dist);
        t = hitCyl(o, d, c, 0.6 + 0.15 * float(i), 6.0 + float(i), n);
        if (t > 0.0 && t < best) {
            best = t;
            vec3 p = o + d * t;
            vec3 alb = mod(floor(p.y / 1.5), 2.0) < 1.0 ? vec3(0.04) : vec3(0.7, 0.55, 0.05);
            col = alb * (uSunCol * max(dot(n, uSunDir), 0.0) * 0.32 + mix(uSkyHor, uSkyTop, 0.5));
        }
    }
    frag = vec4(col, 1.0);
    if (best < 1e8) {
        vec4 c = uVP * vec4(o + d * best, 1.0);
        gl_FragDepth = c.z / c.w * 0.5 + 0.5;
    } else gl_FragDepth = 1.0;
}
