// scene.frag — Demo-Szene: Wasser mit Fresnel, Himmel und den Regenringen aus com.dan.rain; Pfähle und Steg matt,
// nass dunkler. Linear, HDR (Filmkurve in post.frag).
in vec3 vPos, vNormal;
flat in int vKind;
uniform vec3 uEye, uSunDir, uSunColor, uSkyTop, uSkyHorizon;
uniform sampler2D uRipple;
uniform vec3 uRippleP;     // Kachel in m, Stärke, an
uniform float uFogDist, uWet;
out vec4 frag;
vec3 sky(vec3 d) { return mix(uSkyHorizon, uSkyTop, clamp(d.y * 2.0, 0.0, 1.0)); }
void main() {
    vec3 N = normalize(vNormal), V = normalize(uEye - vPos);
    float dist = length(uEye - vPos);
    vec3 col;
    if (vKind == 0) {
        if (uRippleP.z > 0.0) {
            vec2 s = texture(uRipple, vPos.xz / uRippleP.x).rg * uRippleP.y * exp(-dist / 60.0);
            N = normalize(N + vec3(-s.x, 0.0, -s.y));
        }
        float F = 0.02 + 0.98 * pow(1.0 - max(dot(N, V), 0.0), 5.0);
        vec3 r = reflect(-V, N); r.y = abs(r.y);
        col = mix(vec3(0.012, 0.04, 0.04) * (uSkyTop * 3.0 + 0.05), sky(r), F);
        col += uSunColor * pow(max(dot(N, normalize(uSunDir + V)), 0.0), 300.0) * 0.6;
    } else {
        vec3 alb = vPos.y < 0.8 ? vec3(0.06, 0.07, 0.05) : vec3(0.36, 0.30, 0.24);
        alb *= 1.0 - 0.45 * uWet;
        col = alb * (uSunColor * max(dot(N, uSunDir), 0.0) / 3.14159 + uSkyTop * 3.0 * (0.6 + 0.4 * N.y));
        // nass glänzt es
        vec3 r = reflect(-V, N);
        col += sky(r) * 0.08 * uWet * pow(1.0 - max(dot(N, V), 0.0), 3.0);
    }
    float f = 1.0 - exp(-dist / uFogDist);
    frag = vec4(mix(col, uSkyHorizon, f), 1.0);
}
