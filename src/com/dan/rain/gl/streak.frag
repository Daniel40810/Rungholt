// streak.frag — Licht in einem Regentropfen: Er bündelt den Himmel hinter dem Auge und das Licht der Sonne von vorn
// (Gegenlicht glitzert), dazu Dunst zur Ferne. Weiche Ränder, wo der Streifen in Wasser oder Wand steckt.
// Ausgabe vormultipliziert (ONE, ONE_MINUS_SRC_ALPHA), HDR, vorbelichtet.
in vec2 vUv;
in float vAlpha;
in float vDepth;
in vec3 vWorld;
out vec4 frag;
uniform vec3 uEye, uSunDir, uSunColor, uMoonDir, uMoonColor, uSkyTop, uSkyHorizon;
uniform float uFogDist;
uniform sampler2D uSceneDepth;
uniform int uHasDepth, uReversed;
uniform float uNear, uFar;
uniform float uStrength;

float hg(float c, float g) { return (1.0 - g * g) / (12.566 * pow(1.0 + g * g - 2.0 * g * c, 1.5)); }

float sceneDepth() {
    float d = texelFetch(uSceneDepth, ivec2(gl_FragCoord.xy), 0).r;
    if (uReversed == 1) return d <= 0.0 ? 1e9 : uNear / d;
    float z = d * 2.0 - 1.0;
    return 2.0 * uNear * uFar / (uFar + uNear - z * (uFar - uNear));
}

void main() {
    float shape = exp(-vUv.x * vUv.x * 2.5) * smoothstep(1.0, 0.45, abs(vUv.y));
    float a = vAlpha * shape * uStrength;
    if (uHasDepth == 1) a *= clamp((sceneDepth() - vDepth) / 0.15, 0.0, 1.0);
    if (a < 0.002) discard;
    vec3 V = normalize(vWorld - uEye);
    vec3 col = mix(uSkyHorizon, uSkyTop, 0.45) * 0.95
             + uSunColor * (hg(dot(V, uSunDir), 0.7) * 12.566 * 0.12 + 0.02)
             + uMoonColor * (hg(dot(V, uMoonDir), 0.7) * 12.566 * 0.12 + 0.02);
    float f = 1.0 - exp(-max(vDepth, 0.0) / uFogDist);
    col = mix(col, uSkyHorizon, f);
    frag = vec4(col * a, a);
}
