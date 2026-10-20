// body.frag — Anstrich matt mit etwas Glanz, Licht von Sonne und Mond (Bestrahlung, vorbelichtet) und vom Himmel
// (Strahldichte oben und am Horizont), von unten etwas Meer. Fensterglas spiegelt den Himmel und glimmt nachts, Lampen
// und Anzeigen leuchten selbst. Dunst zur Ferne wie die übrige Szene.
in vec3 vWorld, vNrm, vCol;
flat in int vMat;
out vec4 frag;
uniform vec3 uCam, uSunDir, uSunCol, uMoonDir, uMoonCol, uSkyTop, uSkyHor;
uniform float uFogDist, uLamp, uWindowGlow, uScreen;
const float PI = 3.14159265;

void main() {
    vec3 N = normalize(vNrm);
    vec3 V = normalize(uCam - vWorld);
    if (dot(N, V) < 0.0) N = -N;            // dünne Teile von beiden Seiten
    float up = N.y * 0.5 + 0.5;
    vec3 amb = mix(uSkyHor * 0.35, mix(uSkyHor, uSkyTop, 0.5), up);
    float ns = max(dot(N, uSunDir), 0.0), nm = max(dot(N, uMoonDir), 0.0);
    vec3 col;
    if (vMat == 2) {
        col = vCol * uLamp + vCol * amb * 0.6;
    } else if (vMat == 3) {
        col = vCol * uScreen + vCol * amb * 0.2;
    } else if (vMat == 1) {
        float f = 0.04 + 0.96 * pow(1.0 - max(dot(N, V), 0.0), 5.0);
        vec3 R = reflect(-V, N);
        vec3 sky = mix(uSkyHor, uSkyTop, clamp(R.y, 0.0, 1.0));
        col = vCol * amb + sky * f + vec3(1.0, 0.8, 0.5) * uWindowGlow;
        col += uSunCol * pow(max(dot(R, uSunDir), 0.0), 400.0) * 0.6;
    } else {
        vec3 H = normalize(uSunDir + V);
        float spec = pow(max(dot(N, H), 0.0), 40.0) * 0.06 * ns;
        col = vCol * ((uSunCol * ns + uMoonCol * nm) / PI + amb) + uSunCol * spec;
    }
    float d = length(vWorld - uCam);
    float f = 1.0 - exp(-d / max(1.0, uFogDist));
    col = mix(col, uSkyHor, f);
    frag = vec4(col, 1.0);
}
