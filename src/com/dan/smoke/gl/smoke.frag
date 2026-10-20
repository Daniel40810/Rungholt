// smoke.frag — weicher Rauchball: Flocken aus einer Rauschtextur, Selbstschatten (unten dunkler), Licht je Ball aus
// dem Vertex-Shader (Sonne, Mond, Himmel, Feuerschein), Dunst. Weiche Ränder an der Szene (Tiefenkopie).
// Ausgabe vormultipliziert (ONE, ONE_MINUS_SRC_ALPHA), HDR, vorbelichtet.
in vec2 vUv;
in float vAlpha;
in float vDepth;
in vec3 vWorld;
in vec3 vCenter;
flat in float vRadius;
flat in float vBlack;
flat in float vAge;
flat in float vRand;
flat in vec3 vSunMoon;
flat in vec3 vSkyLo, vSkyHi;
flat in vec3 vFire;
out vec4 frag;
uniform vec3 uFogColor;
uniform float uFogDist, uStrength, uTime;
uniform sampler2D uSceneDepth;
uniform sampler2D uNoise;        // seit Phase 11: kachelbares Rauschen (vier Lagen) statt Rechnen je Pixel
uniform int uHasDepth, uReversed;
uniform float uNear, uFar;

float sceneDepth() {
    float d = texelFetch(uSceneDepth, ivec2(gl_FragCoord.xy), 0).r;
    if (uReversed == 1) return d <= 0.0 ? 1e9 : uNear / d;
    float z = d * 2.0 - 1.0;
    return 2.0 * uNear * uFar / (uFar + uNear - z * (uFar - uNear));
}

void main() {
    float r2 = dot(vUv, vUv);
    if (r2 > 1.0) discard;
    // Flocken: das Rauschen wandert mit dem Alter, junger Rauch ist körniger
    float n = texture(uNoise, vUv * (0.2 + 0.075 * vRand) + vec2(vRand * 3.87, vRand * 1.31 + vAge * 0.005)).r;
    float shape = smoothstep(1.0, 0.1, r2 + (0.55 - n) * 0.85) * (0.45 + 0.9 * n) * (1.0 - r2) * (1.0 - r2) * 1.6;
    float a = min(0.95, vAlpha * shape * uStrength);
    if (uHasDepth == 1) a *= clamp((sceneDepth() - vDepth) / max(0.5, vRadius * 0.6), 0.0, 1.0);
    // dicht vor dem Auge ausblenden
    a *= smoothstep(1.0, 3.0 + vRadius * 2.5, vDepth);
    if (a < 0.002) discard;
    // Selbstschatten: die obere Seite des Balls heller, unten dunkler
    float lit = clamp(0.55 + 0.45 * (vWorld.y - vCenter.y) / max(0.3, vRadius) + 0.25 * n, 0.15, 1.2);
    float alb = mix(0.82, 0.07, vBlack);
    vec3 amb = mix(vSkyLo, vSkyHi, clamp(lit, 0.0, 1.0)) * (0.7 + 0.3 * lit);
    vec3 col = (vSunMoon * lit * 0.42 + amb) * alb + vFire * mix(0.9, 0.35, vBlack);
    float f = 1.0 - exp(-max(vDepth, 0.0) / uFogDist);
    col = mix(col, uFogColor, f);
    a *= 1.0 - f * 0.6;
    frag = vec4(col * a, a);
}
