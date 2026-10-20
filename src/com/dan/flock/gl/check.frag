// check.frag — Selbsttest: Flügelspitze und Schnabel einer Möwe je Bildpunkt, wie FlockGl.tipsCpu
uniform vec4 uI[64];      // je Möwe: Anhebung innen, außen, Schwung, Faltung
uniform vec3 uShoulder;
uniform float uWrist;
out vec4 frag;
mat3 rotZ(float a) { float c = cos(a), s = sin(a); return mat3(c, s, 0.0, -s, c, 0.0, 0.0, 0.0, 1.0); }
mat3 rotY(float a) { float c = cos(a), s = sin(a); return mat3(c, 0.0, -s, 0.0, 1.0, 0.0, s, 0.0, c); }
void main() {
    vec4 w = uI[int(gl_FragCoord.x)];
    vec3 sh = uShoulder, wr = vec3(uWrist, sh.y, -0.07), tip = vec3(0.70, sh.y, 0.06);
    mat3 Rin = rotY(-w.z) * rotZ(w.x), Rout = rotY(-w.w) * rotZ(w.y);
    frag = vec4(sh + Rin * (wr - sh) + Rin * Rout * (tip - wr), 1.0);
}
