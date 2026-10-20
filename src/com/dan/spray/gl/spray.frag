// spray.frag — Licht im Wasser in der Luft: Vorwärtsstreuung der Sonne (Henyey-Greenstein, Gegenlicht leuchtet),
// Himmel und Meer als Umgebung, Dunst zur Ferne. Weiche Ränder an der Szene (Tiefe aus einer Kopie).
// Ausgabe vormultipliziert (ONE, ONE_MINUS_SRC_ALPHA), HDR, vorbelichtet.
in vec2 vUv;
in float vAlpha;
in float vDepth;
in vec3 vWorld;
flat in int vKind;
flat in float vRand;
flat in float vAge;
flat in float vKeep;
out vec4 frag;
uniform vec3 uEye, uSunDir, uSunColor, uMoonDir, uMoonColor, uSkyTop, uSkyHorizon;
uniform float uFogDist;
uniform sampler2D uSceneDepth;
uniform int uHasDepth, uReversed;
uniform float uNear, uFar;
uniform vec2 uViewport;
uniform float uStrength;         // Deckkraft gesamt (Stärke im Bedienfeld)

float hg(float c, float g) { return (1.0 - g * g) / (12.566 * pow(1.0 + g * g - 2.0 * g * c, 1.5)); }

float sceneDepth() {
    float d = texelFetch(uSceneDepth, ivec2(gl_FragCoord.xy), 0).r;
    if (uReversed == 1) return d <= 0.0 ? 1e9 : uNear / d;
    float z = d * 2.0 - 1.0;
    return 2.0 * uNear * uFar / (uFar + uNear - z * (uFar - uNear));
}

float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = fract(sin(dot(i, vec2(127.1, 311.7))) * 43758.55), b = fract(sin(dot(i + vec2(1, 0), vec2(127.1, 311.7))) * 43758.55);
    float c = fract(sin(dot(i + vec2(0, 1), vec2(127.1, 311.7))) * 43758.55), d = fract(sin(dot(i + vec2(1, 1), vec2(127.1, 311.7))) * 43758.55);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

void main() {
    float r2 = dot(vUv, vUv);
    float shape, wshade = 1.0;
    if (vKind == 1) {
        // Nebelball mit Flocken, die sich mit dem Alter verwischen
        float n = vnoise(vUv * 2.2 + vRand * 37.0 + vAge * 0.15) * 0.6 + vnoise(vUv * 5.1 - vRand * 11.0) * 0.4;
        shape = exp(-r2 * 3.2) * smoothstep(1.0, 0.6, r2) * (0.35 + 0.9 * n);
    } else if (vKind == 3) {
        // Weißwasser: dichter, blumenkohlartiger Ballen mit zerfranstem Rand
        float n = vnoise(vUv * 1.7 + vRand * 23.0 + vAge * 0.25) * 0.5 + vnoise(vUv * 4.3 - vRand * 7.0 - vAge * 0.4) * 0.3
                + vnoise(vUv * 9.0 + vRand * 3.0) * 0.2;
        shape = smoothstep(1.0, 0.15, r2 + (0.5 - n) * 0.9) * (0.35 + 0.8 * n);
        wshade = 0.55 + 0.6 * n;
    } else {
        // Streifen: quer weich, längs an den Enden ausgeblendet
        shape = exp(-vUv.x * vUv.x * 3.0) * smoothstep(1.0, 0.55, abs(vUv.y));
    }
    float a = min(0.97, vAlpha * shape * uStrength * (vKind == 1 ? 0.10 : vKind == 3 ? 0.42 : 1.0));
    if (uHasDepth == 1) {
        float dz = sceneDepth() - vDepth;
        a *= clamp(dz / (vKind == 1 || vKind == 3 ? 3.0 : 0.3), 0.0, 1.0);
    }
    // dicht vor dem Auge ausblenden (sonst deckt ein einzelner Ball das halbe Bild)
    a *= smoothstep(1.5, vKind == 1 || vKind == 3 ? 14.0 : 3.0, vDepth);
    // die Schwelle gilt für die Deckkraft vor dem Ausdünnen, sonst summieren sich die Ränder der übrigen Bälle
    if (a * vKeep < 0.002) discard;
    vec3 V = normalize(vWorld - uEye);          // vom Auge zum Tropfen
    float cs = dot(V, uSunDir), cm = dot(V, uMoonDir);
    float g = vKind == 1 ? 0.55 : vKind == 3 ? 0.25 : 0.72;
    vec3 sun = uSunColor * (hg(cs, g) * 12.566 * 0.35 + 0.08);
    vec3 moon = uMoonColor * (hg(cm, g) * 12.566 * 0.35 + 0.08);
    vec3 amb = mix(uSkyHorizon, uSkyTop, 0.5) * 0.9;
    vec3 col = (sun + moon) * 0.35 + amb;
    if (vKind == 3) {
        // vielfach gestreut: hell wie Schaum, oben heller als unten (Selbstschatten)
        float top = clamp(0.5 + 0.5 * vUv.y, 0.0, 1.0);
        col = ((uSunColor + uMoonColor) * (0.14 + 0.14 * top) + mix(uSkyHorizon, uSkyTop, 0.6) * 0.95) * (0.55 + 0.4 * top) * wshade;
    }
    float f = 1.0 - exp(-max(vDepth, 0.0) / uFogDist);
    col = mix(col, uSkyHorizon, f);
    a *= 1.0 - f * 0.7;
    frag = vec4(col * a, a);
}
