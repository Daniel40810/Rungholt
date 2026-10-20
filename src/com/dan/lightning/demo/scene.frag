// scene.frag — die Szene der Vorführung, ohne Dreiecke gerechnet: Nachthimmel, eine Wolkendecke mit Lücken über
// einer bewegten See und ein rot-weißer Mast. Das Licht der Blitze kommt aus lightning.glsl (Mast, See) und für die
// Wolkenunterseite aus den Wolkenlichtern (im Kanal, ungedämpft). Die See spiegelt das Rundumbild, in dem auch die
// Kanäle stehen. Mit uPano = 1: nur Himmel und Wolken ins Rundumbild. Schreibt Farbe (vorbelichtet) und Tiefe.
in vec2 vUV;
out vec4 frag;
uniform mat4 uInvVP, uVP;
uniform vec3 uCam;
uniform float uSkyL;            // Himmelsleuchtdichte am Horizont (vorbelichtet)
uniform float uBase;            // Wolkenuntergrenze (m)
uniform float uCover;           // Bedeckung 0..1
uniform float uTime;
uniform float uRough;           // Rauigkeit der See 0..1
uniform float uSigma;           // Auslöschung der Luft 1/m
uniform int uPano;
uniform sampler2D uPanoTex;
uniform vec3 uMast;             // x, z, Höhe
uniform int uClCount;
uniform vec4 uClPos[4];         // Wolkenlicht: Ort
uniform vec4 uClI[4];           // Lichtstärke (cd · Vorbelichtung)

const float PI = 3.14159265;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x), f.y);
}
float fbm(vec2 p) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) { s += a * vnoise(p); p = p * 2.03 + vec2(17.1, 3.7); a *= 0.5; }
    return s;
}

// Wolkenunterseite am Ort P (auf der Untergrenze): Bedeckung c und Leuchtdichte aus Himmel und Blitz
vec3 cloudBase(vec3 P, out float c) {
    float n = fbm(P.xz / 2600.0 + vec2(uTime * 0.002, 0.0));
    c = smoothstep(1.0 - uCover - 0.12, 1.0 - uCover + 0.12, n);
    vec3 L = vec3(uSkyL * 0.5) * c;
    for (int i = 0; i < 4; i++) {
        if (i >= uClCount) break;
        vec3 q = uClPos[i].xyz - P;
        float d2 = dot(q, q), d = sqrt(d2);
        // Licht, das in der Wolke wandert (Diffusion über gut einen Kilometer), und Licht von unten
        float inside = 3.0 * exp(-d / 1300.0) / (4.0 * PI * (d2 + 640000.0));
        float below = q.y < 0.0 ? 0.8 * (-q.y / d) / (PI * d2) : 0.0;
        L += uClI[i].rgb * (inside + below) * c * (0.75 + 0.5 * n);
    }
    return L;
}

vec3 sky(vec3 d) {
    float h = max(0.0, d.y);
    vec3 L = vec3(uSkyL) * mix(1.0, 0.25, sqrt(h)) * vec3(0.75, 0.85, 1.0);
    if (d.y > 1e-4 && uCam.y < uBase) {
        float t = (uBase - uCam.y) / d.y;
        vec3 P = uCam + d * t;
        float c;
        vec3 cl = cloudBase(P, c);
        float fade = exp(-t / 40000.0);
        L = mix(L, cl, c * fade);
    }
    return L;
}

vec2 panoUV(vec3 d) { return vec2(0.5 + atan(d.x, -d.z) / (2.0 * PI), 0.5 + asin(clamp(d.y, -1.0, 1.0)) / PI); }

float hitMast(vec3 o, vec3 d, out vec3 n) {
    vec2 oc = o.xz - uMast.xy;
    float r = 0.6;
    float a = dot(d.xz, d.xz), b = dot(oc, d.xz), c = dot(oc, oc) - r * r;
    float disc = b * b - a * c;
    n = vec3(0.0);
    if (disc < 0.0 || a < 1e-9) return -1.0;
    float t = (-b - sqrt(disc)) / a;
    if (t < 0.0) return -1.0;
    vec3 p = o + d * t;
    if (p.y < 0.0 || p.y > uMast.z) return -1.0;
    n = normalize(vec3(p.x - uMast.x, 0.0, p.z - uMast.y));
    return t;
}

void writeDepth(vec3 p) {
    vec4 c = uVP * vec4(p, 1.0);
    gl_FragDepth = clamp(c.z / c.w * 0.5 + 0.5, 0.0, 1.0);
}

void main() {
    vec3 d;
    if (uPano == 1) {
        float az = (vUV.x - 0.5) * 2.0 * PI, el = (vUV.y - 0.5) * PI;
        d = vec3(sin(az) * cos(el), sin(el), -cos(az) * cos(el));
        frag = vec4(sky(d), 1.0);
        return;
    }
    vec4 c4 = uInvVP * vec4(vUV * 2.0 - 1.0, 1.0, 1.0);
    d = normalize(c4.xyz / c4.w - uCam);
    vec3 col = sky(d);
    float tHit = 1e9;
    vec3 nm;
    float tm = hitMast(uCam, d, nm);
    float ts = d.y < -1e-5 ? uCam.y / -d.y : -1.0;
    if (tm > 0.0 && (ts < 0.0 || tm < ts)) {
        vec3 p = uCam + d * tm;
        float band = step(0.5, fract(p.y / 6.0));
        vec3 alb = mix(vec3(0.75, 0.75, 0.72), vec3(0.6, 0.12, 0.08), band);
        vec3 E = ltgIrradiance(p, nm) + vec3(uSkyL * PI * 0.6);
        col = alb * E / PI + ltgSpecular(p, nm, -d, 0.35) * 0.3;
        col = col * exp(-uSigma * tm) + uSkyL * (1.0 - exp(-uSigma * tm));
        writeDepth(p);
    } else if (ts > 0.0) {
        vec3 p = uCam + d * ts;
        // Wellen: Steigung aus Rauschen, mit der Ferne geglättet
        float s = 1.0 / (1.0 + ts / 300.0);
        vec2 q = p.xz / 7.0 + vec2(uTime * 0.25, uTime * 0.1);
        float e = 0.02;
        vec2 gr = vec2(fbm(q + vec2(e, 0.0)) - fbm(q - vec2(e, 0.0)), fbm(q + vec2(0.0, e)) - fbm(q - vec2(0.0, e))) / (2.0 * e);
        vec3 n = normalize(vec3(-gr.x * (0.25 + 0.5 * uRough) * s, 1.0, -gr.y * (0.25 + 0.5 * uRough) * s));
        vec3 r = reflect(d, n);
        r.y = abs(r.y);
        float F = 0.02 + 0.98 * pow(1.0 - max(0.0, dot(n, -d)), 5.0);
        vec3 refl = textureLod(uPanoTex, panoUV(r), 0.0).rgb;
        // der Kanal selbst und die leuchtende Wolke spiegeln sich über das Rundumbild
        col = refl * F + 0.02 * (ltgIrradiance(p, n) + vec3(uSkyL * PI * 0.3)) / PI;
        float fog = 1.0 - exp(-uSigma * ts);
        col = col * (1.0 - fog) + vec3(uSkyL) * fog;
        writeDepth(p);
    } else {
        gl_FragDepth = 1.0;
    }
    frag = vec4(col, 1.0);
}
