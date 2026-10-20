// check.frag — Selbsttest: Bildpunkt (i, j) ist Tropfen i·uStep von Bündel j; Ausgabe Lage und „lebt“.
uniform int uStep;
out vec4 frag;
void main() {
    int i = int(gl_FragCoord.x) * uStep, j = int(gl_FragCoord.y);
    vec3 pos, vel;
    float size;
    bool alive;
    sprayParticle(parcels[j], i, 0.0, pos, vel, size, alive);
    frag = vec4(pos, alive ? size : -1.0);
}
