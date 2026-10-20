// teich.frag — Phase 9: stilles Wasser (Himmel nach Fresnel, Kräuselung im Wind, moorbraun in der Tiefe), im Frost Eis
// mit Schnee; der Rand franst aus (Binsen und Schlick).
in vec3 vWorld, vLocal;
out vec4 frag;
uniform vec3 uCam, uSunDir, uSunCol, uSkyTop, uSkyHor;
uniform float uFogDist;
uniform vec4 uIce;     // Eis 0..1, Schnee (m), Wind (m/s), Zeit (s)
const float PI = 3.14159265;
float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), u.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), u.x), u.y);
}
void main() {
    vec2 p = vWorld.xz;
    // ausgefranster Rand: der äußere Saum fällt mit Rauschen weg
    if (vLocal.z > 0.82 + 0.16 * vnoise(p * 1.7)) discard;
    vec3 V = normalize(uCam - vWorld);
    float t = uIce.w, w = clamp(uIce.z / 8.0, 0.0, 1.0);
    vec2 g = vec2(vnoise(p * 3.0 + t * 0.5) - 0.5, vnoise(p * 3.0 + 17.0 - t * 0.4) - 0.5) * (0.04 + 0.12 * w);
    vec3 N = normalize(vec3(g.x, 1.0, g.y));
    vec3 R = reflect(-V, N);
    float F = 0.02 + 0.98 * pow(1.0 - max(dot(N, V), 0.0), 5.0);
    vec3 sky = mix(uSkyHor, uSkyTop, clamp(R.y * 1.5, 0.0, 1.0));
    vec3 body = vec3(0.025, 0.022, 0.012) * (uSunCol * max(uSunDir.y, 0.0) / PI + uSkyTop);
    vec3 c = mix(body, sky, F);
    c += uSunCol * 2.0 * pow(max(dot(R, uSunDir), 0.0), 400.0) * F;
    // Eis und Schnee
    if (uIce.x > 0.0) {
        vec3 iceAlb = mix(vec3(0.05, 0.06, 0.07), vec3(0.35, 0.37, 0.40), smoothstep(0.2, 0.9, vnoise(p * 0.6)));
        float snow = smoothstep(0.003, 0.03, uIce.y) * smoothstep(0.3, 0.7, vnoise(p * 0.4 + 5.0) + 0.3);
        iceAlb = mix(iceAlb, vec3(0.70, 0.72, 0.75), snow);
        vec3 ci = iceAlb * (uSunCol * max(uSunDir.y, 0.0) / PI + mix(uSkyHor, uSkyTop, 0.5));
        c = mix(c, ci + sky * F * 0.3, uIce.x);
    }
    float d = length(vWorld - uCam);
    frag = vec4(mix(c, uSkyHor, 1.0 - exp(-d / max(1.0, uFogDist))), 1.0);
}
