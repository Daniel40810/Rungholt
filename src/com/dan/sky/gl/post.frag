// post.frag — Hilfe für Szenen ohne eigene Nachbearbeitung (die Demo): HDR-Bild mit Belichtung, Stäbchensehen
// (nachts farblos und ins Blaue verschoben), Filmkurve (ACES nach Narkowicz) und Gamma ins Fenster.
in vec2 vUV;
out vec4 frag;
uniform sampler2D uHdr;
uniform float uExposure, uScotopic;
vec3 aces(vec3 x) { return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0); }
void main() {
    vec3 c = texture(uHdr, vUV).rgb * uExposure;
    float ys = dot(c, vec3(0.02, 0.62, 0.36));
    c = mix(c, ys * vec3(0.62, 0.78, 1.08), uScotopic);
    vec2 q = vUV - 0.5;
    c *= 1.0 - 0.22 * dot(q, q);
    frag = vec4(pow(aces(c), vec3(1.0 / 2.2)), 1.0);
}
