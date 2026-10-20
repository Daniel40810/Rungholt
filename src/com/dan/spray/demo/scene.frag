// scene.frag — Demo-Szene: Wasser mit Fresnel und Himmel, Körper matt; linear, HDR (Filmkurve in post.frag).
in vec3 vPos, vNormal;
flat in int vKind;
uniform vec3 uEye, uSunDir;
uniform float uSun;
out vec4 frag;
vec3 sky(vec3 d) { return mix(vec3(0.55, 0.60, 0.66), vec3(0.20, 0.30, 0.48), clamp(d.y, 0.0, 1.0)) * (0.3 + 0.7 * uSun); }
void main() {
    vec3 N = normalize(vNormal), V = normalize(uEye - vPos);
    vec3 sunC = vec3(1.0, 0.95, 0.88) * 2.2 * uSun;
    vec3 col;
    if (vKind == 0) {
        float F = 0.02 + 0.98 * pow(1.0 - max(dot(N, V), 0.0), 5.0);
        vec3 r = reflect(-V, N); r.y = abs(r.y);
        col = mix(vec3(0.015, 0.05, 0.05) * (0.4 + uSun), sky(r), F);
        col += sunC * pow(max(dot(N, normalize(uSunDir + V)), 0.0), 300.0) * 0.6;
    } else {
        vec3 alb = vPos.y < 1.5 ? vec3(0.05) : vec3(0.35, 0.33, 0.30);
        col = alb * (sunC * max(dot(N, uSunDir), 0.0) / 3.14159 + sky(vec3(0.0, 1.0, 0.0)) * (0.6 + 0.4 * N.y));
    }
    float f = 1.0 - exp(-length(uEye - vPos) / 6000.0);
    frag = vec4(mix(col, sky(vec3(0.0, 0.02, 0.0)), f), 1.0);
}
