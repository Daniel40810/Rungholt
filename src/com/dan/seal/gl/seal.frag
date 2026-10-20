// seal.frag — com.dan.seal (Phase 7): Fell des Seehunds. Grau bis graubraun nach dem Fell des Tiers, der Rücken dunkler,
// der Bauch heller, unregelmäßig gefleckt; große dunkle Augen, dunkle Nase mit Nasenlöchern, helle Schnurrhaarpolster.
// Nass ist das Fell dunkler und glänzt (Himmel und Sonne spiegeln), trocken heller und matt [G, nach Fotos].
// Licht wie die übrige Szene: Sonne und Mond als Bestrahlung, Himmel als Strahldichte, vorbelichtet; Dunst zur Ferne.
in vec3 vWorld, vNrm;
in vec4 vBody;
in vec3 vInst;
out vec4 frag;
uniform vec3 uCam, uSunDir, uSunCol, uMoonDir, uMoonCol, uSkyTop, uSkyHor;
uniform float uFogDist;
const float PI = 3.14159265;

float h21(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p), u = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + vec2(1, 0)), u.x), mix(h21(i + vec2(0, 1)), h21(i + vec2(1, 1)), u.x), u.y);
}

void main() {
    float s = vBody.x, ang = vBody.y, part = vBody.w, wet = clamp(vInst.x, 0.0, 1.0), tone = vInst.y;
    float top = cos(ang);                       // 1 Rücken, −1 Bauch
    // Grundfarbe nach dem Tier: von dunklem Graubraun bis hellem Silbergrau
    vec3 dark = mix(vec3(0.17, 0.155, 0.13), vec3(0.29, 0.29, 0.275), tone);
    vec3 light = mix(vec3(0.40, 0.36, 0.29), vec3(0.56, 0.55, 0.51), tone);
    vec3 col = mix(light, dark, smoothstep(-0.6, 0.7, top));
    // Flecken: unregelmäßig, auf Rücken und Seiten dicht, am Bauch klein und hell umrandet
    vec2 sp = vec2(s * 46.0, ang * 7.5 / PI * 3.0) + tone * 31.0;
    float n1 = vnoise(sp), n2 = vnoise(sp * 2.3 + 5.0);
    float spot = smoothstep(0.62, 0.70, n1 * 0.75 + n2 * 0.25);
    float ring = smoothstep(0.52, 0.60, n1 * 0.75 + n2 * 0.25) - spot;
    col = mix(col, col * 0.45, spot * (0.7 + 0.3 * smoothstep(-0.5, 0.5, top)) * step(part, 0.5));
    col = mix(col, col * 1.25, ring * 0.35 * (1.0 - smoothstep(-0.2, 0.6, top)) * step(part, 0.5));
    if (part < 0.5) {
        float aa = abs(ang > PI ? ang - 2.0 * PI : ang);
        // Augen: groß und dunkel, seitlich vorn am Kopf
        float eye = 1.0 - smoothstep(0.75, 1.0, length(vec2((s - 0.068) / 0.016, (aa - 0.92) / 0.24)));
        col = mix(col, vec3(0.012, 0.010, 0.010), eye);
        // Nase dunkel, Schnurrhaarpolster hell
        col = mix(col, vec3(0.03, 0.025, 0.022), 1.0 - smoothstep(0.006, 0.016, s));
        float pad = (1.0 - smoothstep(0.012, 0.03, abs(s - 0.026))) * smoothstep(0.7, 1.1, aa) * (1.0 - smoothstep(1.9, 2.4, aa));
        col = mix(col, light * 1.15, pad * 0.55);
    } else {
        col = mix(dark, light, 0.25) * 0.75;   // Flossen dunkler
    }
    if (vInst.z > 0.5) col = mix(col, vec3(0.30, 0.29, 0.27), 0.2);   // Heuler: gleichmäßiger, etwas heller
    // Nässe: dunkler und glatter
    col *= mix(1.0, 0.52, wet);
    float rough = mix(0.62, 0.18, wet);

    vec3 N = normalize(vNrm), V = normalize(uCam - vWorld);
    if (dot(N, V) < -0.2) N = -N;
    float up = N.y * 0.5 + 0.5;
    vec3 amb = mix(uSkyHor * 0.25, mix(uSkyHor, uSkyTop, 0.5), up) * mix(0.55, 1.0, up);
    float ns = max(dot(N, uSunDir), 0.0), nm = max(dot(N, uMoonDir), 0.0);
    // Fell: weicher Übergang ins Dunkle (Haare streuen)
    float wrap = max((dot(N, uSunDir) + 0.25) / 1.25, 0.0) * 0.25;
    vec3 c = col * ((uSunCol * (ns * 0.8 + wrap) + uMoonCol * nm) / PI + amb);
    // Glanz: Sonne und Himmel im nassen Fell
    vec3 H = normalize(uSunDir + V);
    float shin = mix(6.0, 26.0, 1.0 - rough);
    float fres = 0.02 + 0.98 * pow(1.0 - max(dot(N, V), 0.0), 5.0);
    // nasses Fell glänzt, aber die Haare brechen den Glanz: breit und gedämpft [G]
    float spec = pow(max(dot(N, H), 0.0), shin) * (shin + 2.0) / (8.0 * PI) * mix(0.012, 0.06, wet) * step(0.0, uSunDir.y) * (0.5 + vnoise(sp * 3.1 + 17.0));
    vec3 Rf = reflect(-V, N);
    vec3 env = mix(uSkyHor, uSkyTop, clamp(Rf.y * 1.5, 0.0, 1.0)) * PI;
    c += uSunCol * spec * ns + env * fres * mix(0.04, 0.35, wet) * mix(0.3, 1.0, up) / PI;
    float d = length(vWorld - uCam);
    c = mix(c, uSkyHor, 1.0 - exp(-d / max(1.0, uFogDist)));
    frag = vec4(c, 1.0);
}
