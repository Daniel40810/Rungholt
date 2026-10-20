// shadow.frag — dunkelt den Grund unter dem Tier ab (Mischung „mal Quelle“): ein weicher Kontaktschatten unter dem Bauch
// und, so weit die Sonne scheint, ein Schlagschatten zur Seite.
in vec2 vLocal;
in vec2 vSun;
in float vLen;
out vec4 frag;
uniform float uSunShadow;               // 0 bedeckt … 1 volle Sonne
float blob(vec2 p, vec2 r) { return 1.0 - smoothstep(0.55, 1.0, length(p / r)); }
void main() {
    float L = vLen;
    float contact = blob(vLocal - vec2(-0.02 * L, 0.0), vec2(0.50 * L, 0.15 * L)) * 0.55;
    // Schlagschatten: vom Körper zur Spitze des Versatzes gestreckt
    vec2 a = vec2(0.0), b = vSun;
    vec2 pa = vLocal - a, ba = b - a;
    float t = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-4), 0.0, 1.0);
    vec2 q = vLocal - (a + ba * t);
    float castSh = blob(q, vec2(0.48 * L, 0.13 * L)) * 0.6 * uSunShadow;
    float k = max(contact, castSh);
    frag = vec4(vec3(1.0 - k), 1.0);
}
