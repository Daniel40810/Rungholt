// figur.frag — Licht wie die Seehunde (vorbelichtet): Sonne, Mond, Himmel, Dunst.
in vec3 vWorld, vNrm, vCol;
out vec4 frag;
uniform vec3 uCam, uSunDir, uSunCol, uMoonDir, uMoonCol, uSkyTop, uSkyHor;
uniform float uFogDist;
const float PI = 3.14159265;
void main() {
    vec3 N = normalize(vNrm);
    float up = N.y * 0.5 + 0.5;
    vec3 amb = mix(uSkyHor * 0.3, mix(uSkyHor, uSkyTop, 0.5), up);
    vec3 c = vCol * ((uSunCol * max(dot(N, uSunDir), 0.0) + uMoonCol * max(dot(N, uMoonDir), 0.0)) / PI + amb);
    float d = length(vWorld - uCam);
    frag = vec4(mix(c, uSkyHor, 1.0 - exp(-d / max(1.0, uFogDist))), 1.0);
}
