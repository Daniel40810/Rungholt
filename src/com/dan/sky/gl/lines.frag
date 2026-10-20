// lines.frag — Linienfarbe in Bildschirmhelligkeit (unabhängig von der Belichtung), wie die blauen Linien in Sternbild.
in float vVis;
out vec4 frag;
uniform vec3 uLineColor;
void main() { frag = vec4(uLineColor * vVis, 1.0); }
