// figure.frag — com.dan.figure: Licht vorbelichtet wie die Flächen der App: Sonne, Mond, Himmel; Stoff mit etwas
// Glanz von oben (Regenjacken), Dunst nach der Entfernung.
in vec3 vWorld, vNrm, vCol;
out vec4 frag;
uniform vec3 uCam, uSunDir, uSunCol, uMoonDir, uMoonCol, uSkyTop, uSkyHor;
uniform float uFogDist, uShadow;
const float PI = 3.14159265;
void main() {
    vec3 N = normalize(vNrm);
    vec3 V = normalize(uCam - vWorld);
    if (dot(N, V) < 0.0) N = normalize(N - V * dot(N, V) * 1.05);
    float up = N.y * 0.5 + 0.5;
    vec3 amb = mix(uSkyHor * 0.35, mix(uSkyHor, uSkyTop, 0.5), up);
    vec3 sun = uSunCol * max(dot(N, uSunDir), 0.0) * uShadow + uMoonCol * max(dot(N, uMoonDir), 0.0);
    vec3 c = vCol * (sun / PI + amb);
    vec3 H = normalize(uSunDir + V);
    c += uSunCol * uShadow * 0.04 * pow(max(dot(N, H), 0.0), 40.0) / PI;
    float d = length(vWorld - uCam);
    frag = vec4(mix(c, uSkyHor, 1.0 - exp(-d / max(1.0, uFogDist))), 1.0);
}
