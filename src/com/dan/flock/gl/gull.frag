// gull.frag — Gefieder matt, beidseitig; Licht wie die übrige Szene (vorbelichtet), Dunst zur Ferne. Gegenlicht
// scheint durch die Flügel (helle Säume).
in vec3 vWorld, vNrm, vCol;
out vec4 frag;
uniform vec3 uCam, uSunDir, uSunCol, uMoonDir, uMoonCol, uSkyTop, uSkyHor;
uniform float uFogDist;
const float PI = 3.14159265;
void main() {
    vec3 N = normalize(vNrm), V = normalize(uCam - vWorld);
    float back = dot(N, V) < 0.0 ? 1.0 : 0.0;
    if (back > 0.5) N = -N;
    float up = N.y * 0.5 + 0.5;
    vec3 amb = mix(uSkyHor * 0.3, mix(uSkyHor, uSkyTop, 0.5), up);
    float ns = max(dot(N, uSunDir), 0.0), nm = max(dot(N, uMoonDir), 0.0);
    // durchscheinend: Licht von der anderen Seite der dünnen Fläche
    float tr = max(dot(-N, uSunDir), 0.0) * 0.25;
    vec3 col = vCol * ((uSunCol * (ns + tr) + uMoonCol * nm) / PI + amb);
    float d = length(vWorld - uCam);
    col = mix(col, uSkyHor, 1.0 - exp(-d / max(1.0, uFogDist)));
    frag = vec4(col, 1.0);
}
