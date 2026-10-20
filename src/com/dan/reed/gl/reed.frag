// reed.frag — Licht auf den Halmen: beidseitig (dünne Blätter), unten dunkler (Eigenschatten im Bestand), gegen die
// Sonne durchscheinend, die Rispe des Schilfs purpurbraun bis silbrig; Dunst nach Entfernung. Ausgabe: HDR, linear.
// Seit Haithabu Phase 3: Mondlicht und eine Schattenkarte von oben (Wolken), am Ort jedes Halms gelesen.
in vec3 vWorld;
in vec3 vNormal;
in float vT;
in float vTon;
flat in int vKind;
in float vPanicle;
out vec4 frag;
uniform vec3 uCam;
uniform vec3 uColor[4];
uniform float uSeason;
uniform vec2 uSnowFrost;   // Schneehöhe (m), Reif 0..1 (seit Haithabu Phase 12)
uniform vec3 uSunDir, uSunColor, uSkyColor, uFogColor;
uniform float uFogDist;
uniform vec3 uMoonDir, uMoonColor;
uniform float uSkyWeight;
uniform sampler2D uShadow;
uniform vec4 uShadowRect;   // x, z der Ecke, Kantenlänge (m), an (1) / aus (0)

void main() {
    vec3 n = normalize(vNormal);
    vec3 v = normalize(uCam - vWorld);
    if (dot(n, v) < 0.0) n = -n;
    vec3 alb = uColor[vKind] * (0.8 + 0.4 * vTon);
    // Steppe (seit Glacier): grüne Basis, strohgelbe, ausgebleichte Spitzen
    if (vKind == 3) alb = mix(alb * vec3(0.55, 0.75, 0.45), alb * 1.25, smoothstep(0.1, 0.6, vT));
    // Rispe: Spätsommer purpurbraun, Herbst und Winter silbrig hell
    vec3 pan = mix(vec3(0.16, 0.09, 0.08), vec3(0.34, 0.30, 0.25), smoothstep(0.55, 0.95, uSeason));
    alb = mix(alb, pan, vPanicle);
    // Reif: feine Eiskristalle auf den Halmen, weißgrau, oben mehr als unten
    alb = mix(alb, vec3(0.28, 0.29, 0.31), uSnowFrost.y * 0.65 * (0.55 + 0.45 * vT));
    float occl = mix(0.35, 1.0, smoothstep(0.0, 0.7, vT));
    float shade = uShadowRect.w > 0.0 ? textureLod(uShadow, (vWorld.xz - uShadowRect.xy) / uShadowRect.z, 0.0).r : 1.0;
    float diff = abs(dot(n, uSunDir)) * 0.75 + 0.25;
    float back = pow(max(dot(-v, uSunDir), 0.0), 4.0) * 0.6;
    vec3 sun = uSunColor * smoothstep(-0.02, 0.05, uSunDir.y) * shade;
    vec3 moon = uMoonColor * smoothstep(-0.02, 0.05, uMoonDir.y) * shade * (abs(dot(n, uMoonDir)) * 0.75 + 0.25);
    vec3 col = alb * (sun * (diff + back) + moon + uSkyColor * uSkyWeight) * occl;
    float d = length(uCam - vWorld);
    col = mix(col, uFogColor, 1.0 - exp(-d / uFogDist));
    frag = vec4(col, 1.0);
}
