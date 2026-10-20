// hearth.frag — Flamme aus wanderndem Rauschen mit Farbe nach der Temperatur (weißgelber Kern, orange, dunkelrot am
// Rand), Glut, Funken, Ruß. Flammen, Glut und Funken leuchten (additiv), Ruß deckt (vormultipliziert): der Modus sagt,
// welche Mischung gilt. Leuchtdichte in cd/m² mal uLumScale (vorbelichtet), weiche Ränder an der Szene.
in vec2 vUv;
in float vDepth;
in vec3 vWorld;
flat in float vI;
flat in float vRand;
flat in int vKind;
flat in float vAge;
out vec4 frag;
uniform int uMode;
uniform float uTime, uLumScale;
uniform vec3 uSkyTop, uSkyHorizon, uFogColor;
uniform float uFogDist;
uniform sampler2D uSceneDepth;
uniform int uHasDepth, uReversed;
uniform float uNear, uFar;

float h21(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + vec2(1, 0)), u.x), mix(h21(i + vec2(0, 1)), h21(i + vec2(1, 1)), u.x), u.y);
}
float fbm(vec2 p) { return 0.5 * vnoise(p) + 0.27 * vnoise(p * 2.03 + 3.1) + 0.15 * vnoise(p * 4.07 - 1.3) + 0.08 * vnoise(p * 8.1 + 7.7); }

float sceneDepth() {
    float d = texelFetch(uSceneDepth, ivec2(gl_FragCoord.xy), 0).r;
    if (uReversed == 1) return d <= 0.0 ? 1e9 : uNear / d;
    float z = d * 2.0 - 1.0;
    return 2.0 * uNear * uFar / (uFar + uNear - z * (uFar - uNear));
}

// Farbe einer Flamme nach der Temperatur 0..1 (linear, Leuchtdichte etwa 1 bei t = 1)
vec3 flameColor(float t) {
    vec3 red = vec3(0.55, 0.06, 0.01), orange = vec3(1.6, 0.55, 0.08), yellow = vec3(1.25, 0.95, 0.45), white = vec3(1.1, 1.0, 0.85);
    return t < 0.4 ? mix(red, orange, t / 0.4) : t < 0.75 ? mix(orange, yellow, (t - 0.4) / 0.35) : mix(yellow, white, (t - 0.75) / 0.25);
}

void main() {
    float soft = 1.0;
    if (uHasDepth == 1) soft = clamp((sceneDepth() - vDepth) / (uMode == 2 ? 1.5 : 0.25), 0.0, 1.0);
    float fog = 1.0 - exp(-max(vDepth, 0.0) / uFogDist);
    if (uMode == 0 && vKind == 4) {
        // Glut: rot glimmende Flecken, die langsam atmen
        float r2 = dot(vUv, vUv);
        float n = fbm(vUv * 3.0 + vRand * 17.0 + vec2(uTime * 0.05, 0.0));
        float g = smoothstep(1.0, 0.2, r2) * smoothstep(0.35, 0.75, n) * (0.7 + 0.3 * sin(uTime * 1.3 + vRand * 9.0));
        vec3 col = flameColor(0.25 + 0.3 * n) * g * vI * 1800.0 * uLumScale;
        frag = vec4(col * soft * (1.0 - fog), 0.0);
        return;
    }
    if (uMode == 0) {
        // Zunge: Breite nimmt nach oben ab, das Rauschen läuft nach oben und reißt die Spitze in Fetzen
        float y = vUv.y, x = vUv.x;
        float speed = vKind == 1 ? 2.6 : 1.8;
        vec2 np = vec2(x * 1.4 + vRand * 13.0, y * 2.2 - uTime * speed);
        float n = fbm(np), n2 = fbm(np * 2.3 + 5.0);
        float width = pow(max(0.0, 1.0 - y), 0.55) * (0.5 + 0.5 * smoothstep(0.0, 0.15, y));
        float dx = abs(x + (n - 0.5) * 1.1 * y + (n2 - 0.5) * 0.3) / max(0.02, width);
        float body = smoothstep(1.05, 0.25, dx + (0.5 - n) * 0.9 * y + y * y * (0.4 + 0.8 * n2));
        float temp = clamp(body * (1.2 - 0.9 * y) * (0.7 + 0.6 * n), 0.0, 1.0);
        float L = (vKind == 2 ? 5000.0 : 7000.0) * vI;     // cd/m², Kern einer Holzflamme
        // heiß leuchtet hell, der kühle Rand nur dunkelrot (Leuchtdichte etwa mit der dritten Potenz der Temperatur)
        vec3 col = flameColor(temp) * L * temp * temp * temp * smoothstep(0.0, 0.3, body) * uLumScale;
        // dicht vor dem Auge ausblenden (Fackel in der Hand)
        col *= smoothstep(0.15, 0.6, vDepth);
        frag = vec4(col * soft * (1.0 - fog * 0.9), 0.0);
        return;
    }
    if (uMode == 1) {
        float s = exp(-vUv.x * vUv.x * 3.0) * smoothstep(1.0, 0.4, abs(vUv.y));
        vec3 col = flameColor(0.5 - 0.3 * vAge) * 2500.0 * vI * s * uLumScale;
        frag = vec4(col * soft * (1.0 - fog), 0.0);
        return;
    }
    // Ruß
    float r2 = dot(vUv, vUv);
    if (r2 > 1.0) discard;
    float n = fbm(vUv * 1.8 + vRand * 23.0 + vec2(0.0, -vAge * 0.8));
    float a = smoothstep(1.0, 0.15, r2 + (0.5 - n) * 0.8) * (1.0 - r2) * vI * 0.75 * soft;
    // von unten rot angestrahlt, oben grau vom Himmel
    float bottom = clamp(0.5 - 0.5 * vUv.y, 0.0, 1.0) * (1.0 - vAge);
    vec3 col = mix(uSkyHorizon, uSkyTop, 0.5) * 0.12 + flameColor(0.3) * 50.0 * uLumScale * bottom * bottom;
    col = mix(col, uFogColor, fog);
    frag = vec4(col * a, a);
}
