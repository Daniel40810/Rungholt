// sea.frag — Wasser: Himmel gespiegelt nach Fresnel, Tiefe dunkel grün-blau, Sonnenglitzer, Dunst zur Ferne.
in vec3 vWorld, vNrm;
out vec4 frag;
uniform vec3 uCam, uSunDir, uSunCol, uSkyTop, uSkyHor;
uniform float uFogDist;
vec3 skyCol(vec3 d) {
    vec3 c = mix(uSkyHor, uSkyTop, pow(clamp(d.y, 0.0, 1.0), 0.45));
    float s = max(dot(d, uSunDir), 0.0);
    return c + uSunCol * pow(s, 300.0) * 40.0;
}
void main() {
    vec3 N = normalize(vNrm), V = normalize(uCam - vWorld);
    float d = length(vWorld - uCam);
    N = normalize(mix(N, vec3(0.0, 1.0, 0.0), clamp(d / 3000.0, 0.0, 0.8)));
    vec3 R = reflect(-V, N);
    R.y = abs(R.y);
    float f = 0.02 + 0.98 * pow(1.0 - max(dot(N, V), 0.0), 5.0);
    vec3 deep = vec3(0.012, 0.035, 0.04) * (uSkyTop + uSkyHor);
    vec3 col = mix(deep, skyCol(R), f);
    col = mix(col, uSkyHor, 1.0 - exp(-d / uFogDist));
    frag = vec4(col, 1.0);
}
