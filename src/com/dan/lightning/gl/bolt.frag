// bolt.frag — Querschnitt des Kanals: Kern (Gauß) und Hof (Lorentz, auf die Bandbreite beschnitten und
// normiert). Das Integral quer über das Band ist vI · 1 Bildpunkt; ausgegeben wird Leuchtdichte, vorbelichtet,
// mit der Farbe des Kanals, begrenzt auf den Bereich halber Gleitkommazahlen.
in float vOff;
in float vAlong;
in float vI;
flat in float vHalf, vHaloS, vHaloF, vLen;
out vec4 frag;
uniform vec3 uColour;
const float CORE = 0.6;
void main() {
    float x = vOff;
    // Kern: quer Gauß, längs genau über die Länge des Stücks (weich auf einem Bildpunkt, damit Knicke geschlossen
    // bleiben und sich nichts doppelt); Hof: um das Stück als Kapsel, an den Enden rund auslaufend
    float along = clamp(vAlong + 0.5, 0.0, 1.0) * clamp(vLen + 0.5 - vAlong, 0.0, 1.0);
    float core = exp(-0.5 * x * x / (CORE * CORE)) / (2.5066283 * CORE) * along * vLen / (vLen + 1e-3 + 0.0);
    float ex = max(0.0, max(-vAlong, vAlong - vLen));
    float r = length(vec2(ex, x));
    float halo = 1.0 / (1.0 + (r / vHaloS) * (r / vHaloS)) / (2.0 * vHaloS * atan(vHalf / vHaloS)) * smoothstep(vHalf, vHalf * 0.6, r)
               * vLen / (vLen + 1.6 * vHaloS);
    float L = vI * ((1.0 - vHaloF) * core + vHaloF * halo);
    frag = vec4(min(uColour * L, vec3(60000.0)), 0.0);
}
