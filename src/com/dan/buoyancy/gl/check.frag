// check.frag — Selbsttest: Punkt i mit der Modellmatrix in die Welt
uniform vec4 uP[64];
uniform mat4 uModel;
out vec4 frag;
void main() {
    int i = int(gl_FragCoord.x);
    frag = vec4((uModel * vec4(uP[i].xyz, 1.0)).xyz, 1.0);
}
