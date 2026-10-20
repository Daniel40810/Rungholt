// scene.frag — die Szene der Vorführung, ohne Dreiecke: Himmel mit Sonne, See mit kleinen Wellen, ein rot-weißer
// Turm (Halbmesser uTower.x, Höhe uTower.y) mit Galerie (Ring in uTower.z). Schreibt Farbe und Tiefe (−1..1).
in vec2 vUV;
out vec4 frag;
uniform mat4 uInvVP, uVP;
uniform vec3 uCam, uSunDir, uSunCol, uSkyTop, uSkyHor;
uniform vec4 uTower;          // Halbmesser, Höhe, Galerie-Halbmesser, Galerie-Höhe
uniform float uTime;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x), f.y);
}
vec3 skyCol(vec3 d) {
    vec3 c = mix(uSkyHor, uSkyTop, pow(clamp(d.y, 0.0, 1.0), 0.45));
    float s = max(dot(d, uSunDir), 0.0);
    return c + uSunCol * (pow(s, 1200.0) * 300.0 + pow(s, 14.0) * 0.06);
}
void main() {
    vec4 a = uInvVP * vec4(vUV * 2.0 - 1.0, -1.0, 1.0), b = uInvVP * vec4(vUV * 2.0 - 1.0, 1.0, 1.0);
    vec3 o = uCam, d = normalize(b.xyz / b.w - a.xyz / a.w);
    vec3 col = skyCol(d);
    float best = 1e9;
    float tw = d.y < -1e-5 ? -o.y / d.y : -1.0;
    if (tw > 0.0) {
        best = tw;
        vec3 p = o + d * tw;
        float fade = exp(-tw / 220.0);
        vec2 g = vec2(vnoise(p.xz * 0.4 + uTime * 0.5) - vnoise(p.xz * 0.4 + vec2(3.1, 0.0) + uTime * 0.5),
                      vnoise(p.xz * 0.4 + vec2(0.0, 5.7) - uTime * 0.4) - vnoise(p.xz * 0.4 + vec2(1.9, 7.3) - uTime * 0.4)) * fade;
        vec3 n = normalize(vec3(g.x * 0.25, 1.0, g.y * 0.25));
        vec3 r = reflect(d, n);
        r.y = abs(r.y);
        float f = 0.02 + 0.98 * pow(1.0 - max(dot(-d, n), 0.0), 5.0);
        col = mix(vec3(0.012, 0.035, 0.04) * (uSkyTop + uSkyHor), skyCol(r), f);
    }
    // Turm
    float A = dot(d.xz, d.xz), B = dot(o.xz, d.xz), C = dot(o.xz, o.xz) - uTower.x * uTower.x, disc = B * B - A * C;
    if (disc > 0.0 && A > 1e-9) {
        float t = (-B - sqrt(disc)) / A;
        vec3 p = o + d * t;
        if (t > 0.0 && t < best && p.y > -1.0 && p.y < uTower.y) {
            best = t;
            vec3 n = normalize(vec3(p.x, 0.0, p.z));
            vec3 alb = p.y < 8.0 ? vec3(0.05) : (mod(floor((p.y - 8.0) / 4.0), 2.0) < 1.0 ? vec3(0.72) : vec3(0.55, 0.05, 0.035));
            if (p.y > uTower.w + 0.5) alb = vec3(0.08);
            col = alb * (uSunCol * max(dot(n, uSunDir), 0.0) / 3.14159 + mix(uSkyHor, uSkyTop, 0.5));
        }
    }
    // Galerie: Scheibe in uTower.w, Halbmesser uTower.z
    if (abs(d.y) > 1e-5) {
        float t = (uTower.w - o.y) / d.y;
        vec3 p = o + d * t;
        if (t > 0.0 && t < best && length(p.xz) < uTower.z && length(p.xz) > uTower.x - 0.1) {
            best = t;
            col = vec3(0.6) * (uSunCol * max(sign(d.y) * -1.0, 0.0) * max(uSunDir.y, 0.0) / 3.14159 + mix(uSkyHor, uSkyTop, 0.5) * 0.8);
        }
    }
    frag = vec4(col, 1.0);
    if (best < 1e8) {
        vec4 c = uVP * vec4(o + d * best, 1.0);
        gl_FragDepth = c.z / c.w * 0.5 + 0.5;
    } else gl_FragDepth = 1.0;
}
