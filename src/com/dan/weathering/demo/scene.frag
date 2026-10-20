// scene.frag — Demo-Szene: Lambert und Glanz unter Sonne und Himmel, darüber die Verwitterung aus weathering.glsl;
// Filmkurve und Gamma gleich hier (die Demo hat keine Nachbearbeitung).
in vec3 vPos, vNormal;
in vec2 vSt, vEdge;
flat in int vKind;
uniform vec3 uCam, uSunDir;
uniform float uSunGain;
uniform vec2 uMapScale;   // Umfang, Höhe der Nässekarte in m
uniform float uMapBottom;
uniform int uShow;        // 0 Bild, 1 Nässe, 2 Masken
out vec4 frag;

vec3 tone(vec3 c) {
    c *= 1.6;
    c = clamp((c * (2.51 * c + 0.03)) / (c * (2.43 * c + 0.59) + 0.14), 0.0, 1.0);
    return pow(c, vec3(1.0 / 2.2));
}

vec3 skyCol(vec3 d) { return mix(vec3(0.55, 0.62, 0.70), vec3(0.20, 0.36, 0.62), clamp(d.y, 0.0, 1.0)); }

void main() {
    vec3 N = normalize(vNormal);
    vec3 V = normalize(uCam - vPos);
    vec3 L = uSunDir;
    vec3 sunC = vec3(1.0, 0.96, 0.9) * 2.4 * uSunGain;
    if (vKind == 2) {
        float F = 0.02 + 0.98 * pow(1.0 - max(dot(N, V), 0.0), 5.0);
        vec3 body = vec3(0.012, 0.045, 0.042) * (0.6 + uSunGain);
        vec3 col = mix(body, skyCol(reflect(-V, N)), F);
        vec3 H = normalize(L + V);
        col += sunC * pow(max(dot(N, H), 0.0), 400.0) * 2.0;
        frag = vec4(tone(col), mix(0.72, 0.97, F));
        return;
    }
    vec3 albedo = vKind == 0 ? vec3(0.018, 0.018, 0.02) : vec3(0.28, 0.27, 0.25);
    if (vKind == 0 && vSt.y > 4.0) albedo = vec3(0.75, 0.75, 0.73);
    if (vKind == 0 && vSt.y > 6.5) albedo = vec3(0.62, 0.05, 0.03);
    float rough = vKind == 0 ? 0.35 : 0.85;
    float porosity = vKind == 0 ? 0.25 : 0.9;
    float sus = vKind == 0 ? (vSt.y < 4.0 ? 1.0 : 0.35) : 0.0;
    float below = vEdge.y > 0.0 ? mod(vEdge.x - vPos.y, vEdge.y) : vEdge.x - vPos.y;
    vec2 mapUV = vKind == 0 ? vec2(vSt.x / uMapScale.x, (vPos.y - uMapBottom) / uMapScale.y) : vec2(-1.0);
    vec4 m;
    float wet = wthApply(albedo, rough, porosity, vPos, N, vSt, below, sus, mapUV, m);
    if (uShow == 1) { frag = vec4(vec3(wet), 1.0); return; }
    if (uShow == 2) { frag = vec4(m.x + m.z, m.y + m.z, m.w + m.z + wet * 0.3, 1.0); return; }
    float ndl = max(dot(N, L), 0.0);
    vec3 H = normalize(L + V);
    float a = rough * rough, a2 = a * a;
    float nh = max(dot(N, H), 0.0);
    float D = a2 / (3.14159 * pow(nh * nh * (a2 - 1.0) + 1.0, 2.0));
    float F = 0.04 + 0.96 * pow(1.0 - max(dot(H, V), 0.0), 5.0);
    vec3 col = albedo / 3.14159 * (sunC * ndl) + albedo * skyCol(N) * 0.35;
    col += sunC * ndl * D * F * 0.25;
    // Wasserfilm: spiegelt den Himmel
    float Fw = 0.02 + 0.98 * pow(1.0 - max(dot(N, V), 0.0), 5.0);
    col += skyCol(reflect(-V, N)) * Fw * wet * 0.5;
    frag = vec4(tone(col), 1.0);
}
