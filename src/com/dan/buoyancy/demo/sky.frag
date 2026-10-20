// sky.frag — schlichter Himmel der Vorführung: Verlauf vom Horizont nach oben, Sonne mit Hof.
in vec2 vUV;
out vec4 frag;
uniform mat4 uInvVP;
uniform vec3 uSunDir, uSunCol, uSkyTop, uSkyHor;
vec3 skyCol(vec3 d) {
    vec3 c = mix(uSkyHor, uSkyTop, pow(clamp(d.y, 0.0, 1.0), 0.45));
    float s = max(dot(d, uSunDir), 0.0);
    return c + uSunCol * (pow(s, 1200.0) * 300.0 + pow(s, 16.0) * 0.06);
}
void main() {
    vec4 a = uInvVP * vec4(vUV * 2.0 - 1.0, -1.0, 1.0), b = uInvVP * vec4(vUV * 2.0 - 1.0, 1.0, 1.0);
    vec3 d = normalize(b.xyz / b.w - a.xyz / a.w);
    frag = vec4(skyCol(d), 1.0);
}
