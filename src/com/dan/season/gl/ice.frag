// ice.frag — Eis, wo das Wasser flacher ist als die Eisgrenze. Rand ausgefranst (Rauschen), zum Rand hin jünger und
// dünner: Schwarzeis, dunkel und glatt; weiter drinnen grauweiß mit Rissen (Zellen) und Blasen; Schnee in Fahnen mit
// dem Wind; Reif als feiner heller Belag. Licht: Sonne (Wolkenschatten), Mond, Himmel; Glanz zum Himmel und zur Sonne.
uniform vec3 uEye;
uniform vec4 uRect;
uniform vec3 uBedMap;     // Maßstab, Versatz, Wasserspiegel
uniform vec4 uIce;        // Dicke (m), Grenze als Wassertiefe (m), Schnee 0..1, Reif 0..1
uniform vec2 uWindTo;
uniform vec4 uClip;      // seit Glacier Phase 11: nur in x0, z0 … x1, z1 (x1 <= x0: überall)
uniform sampler2D uBed;
uniform sampler2D uCloudShadow;
uniform vec4 uCloudRect;
uniform vec3 uSunDir, uSunColor, uMoonDir, uMoonColor, uSky, uFogColor;
uniform float uFogDist;
in vec3 vWorld;
out vec4 frag;

float hash21(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
vec2 hash22(vec2 p) { return fract(sin(vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)))) * 43758.5453); }
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1.0, 0.0)), u.x), mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), u.x), u.y);
}
float fbm(vec2 p) { return 0.5 * vnoise(p) + 0.3 * vnoise(p * 2.07 + 5.3) + 0.2 * vnoise(p * 4.13 - 2.1); }
// Abstand zur nächsten Zellgrenze (Risse)
float cells(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    float d1 = 8.0, d2 = 8.0;
    for (int y = -1; y <= 1; y++)
        for (int x = -1; x <= 1; x++) {
            vec2 g = vec2(x, y), o = hash22(i + g);
            float d = length(g + o - f);
            if (d < d1) { d2 = d1; d1 = d; } else if (d < d2) d2 = d;
        }
    return d2 - d1;
}

void main() {
    vec2 w = vWorld.xz;
    vec2 uv = (w - uRect.xy) / uRect.z;
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) discard;
    if (uClip.z > uClip.x && (w.x < uClip.x || w.y < uClip.y || w.x > uClip.z || w.y > uClip.w)) discard;
    float depth = uBedMap.z - (textureLod(uBed, uv, 0.0).r * uBedMap.x + uBedMap.y);
    float lim = uIce.y;
    float n1 = fbm(w / 45.0), n2 = fbm(w / 7.0 + 3.1);
    float edge = depth + (n1 - 0.5) * (0.35 + 0.25 * lim) + (n2 - 0.5) * 0.15;
    float cover = 1.0 - smoothstep(lim - 0.12, lim, edge);
    if (cover < 0.01 || depth < -0.3) discard;
    // jünger zum Rand: Schwarzeis
    float young = smoothstep(lim - 1.2 - 0.6 * n2, lim, edge) * (1.0 - smoothstep(0.08, 0.2, uIce.x) * 0.6);
    float d = length(uEye - vWorld);
    float near = 1.0 - smoothstep(60.0, 400.0, d);
    float crack = 1.0 - smoothstep(0.0, 0.035, cells(w / 9.0)) ;
    float crack2 = 1.0 - smoothstep(0.0, 0.05, cells(w / 2.3 + 7.0));
    vec3 grey = mix(vec3(0.075, 0.085, 0.092), vec3(0.15, 0.16, 0.17), smoothstep(0.03, 0.15, uIce.x)) * (0.85 + 0.3 * n2);
    vec3 black = vec3(0.012, 0.018, 0.020);
    vec3 alb = mix(grey, black, young);
    alb = mix(alb, vec3(0.20, 0.21, 0.22), crack * 0.6 * near * (1.0 - young));
    alb = mix(alb, alb * 0.6, crack2 * 0.4 * near * near);
    // Schnee in Fahnen quer zum Wind, auf dem jungen Eis noch keiner
    vec2 wa = vec2(dot(w, uWindTo), dot(w, vec2(-uWindTo.y, uWindTo.x)));
    float drift = fbm(vec2(wa.x / 30.0, wa.y / 6.0)) * 0.7 + fbm(w / 13.0) * 0.3;
    float snow = smoothstep(0.55 - 0.45 * uIce.z, 0.75 - 0.45 * uIce.z, drift) * step(0.02, uIce.z) * (1.0 - young);
    vec3 snowC = vec3(0.255, 0.258, 0.27) * (0.92 + 0.12 * n2);
    alb = mix(alb, snowC, snow);
    alb = mix(alb, vec3(0.22, 0.225, 0.235), uIce.w * 0.5 * (1.0 - snow));
    float shade = 1.0;
    if (uCloudRect.w > 0.0) shade = textureLod(uCloudShadow, (w - uCloudRect.xy) / uCloudRect.z, 0.0).r;
    vec3 col = alb * (uSunColor * max(uSunDir.y, 0.0) * shade + uMoonColor * max(uMoonDir.y, 0.0) + uSky * 1.15);
    // Glanz: glattes Eis spiegelt (n = 1,31), Schnee nicht
    vec3 v = normalize(uEye - vWorld);
    float gloss = (1.0 - snow) * mix(0.55, 1.0, young);
    float F = 0.018 + 0.98 * pow(1.0 - max(v.y, 0.0), 5.0);
    vec3 r = reflect(-v, vec3(0.0, 1.0, 0.0));
    col += gloss * F * uSky * 3.0 * (0.6 + 0.4 * r.y);
    col += gloss * F * uSunColor * shade * pow(max(dot(r, uSunDir), 0.0), mix(60.0, 600.0, young)) * mix(3.0, 30.0, young);
    col = mix(col, uFogColor, 1.0 - exp(-d / max(1.0, uFogDist)));
    frag = vec4(col, cover);
}
