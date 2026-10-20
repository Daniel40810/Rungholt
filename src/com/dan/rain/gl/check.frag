// check.frag — Selbsttest: Bildpunkt (i, 0..63) = Tropfen i + 64·j; schreibt Lage und „lebt“ zum Vergleich mit Rain.Snapshot.drop.
out vec4 frag;
void main() {
    ivec2 p = ivec2(gl_FragCoord.xy);
    vec3 pos, vel;
    float d;
    bool live;
    rainDrop(p.x + p.y * 64, pos, vel, d, live);
    frag = vec4(pos, live ? d : -d);
}
