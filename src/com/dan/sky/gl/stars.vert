// stars.vert — Sterne und Planeten als Lichtpunkte: Richtung J2000 → Szene (Präzession, Sternzeit, Breite),
// Strahlenbrechung, Auslöschung aus der Durchlässigkeitstabelle, Funkeln (stärker an der Kimm), Grenzgröße fürs
// Auge. Die Bestrahlung verteilt sich auf einen schmalen Kern und einen schwachen Hof (Streulicht im Auge); die
// Punktgröße reicht genau so weit, wie etwas zu sehen ist.
#include "sky_common.glsl"
in vec3 aDir;        // Einheitsvektor, Äquator J2000
in float aMag;       // scheinbare Helligkeit in mag
in vec3 aColor;      // Farbe mit Leuchtdichte 1
uniform mat4 uVP;
uniform mat3 uEqToScene;
uniform float uEyeR, uPre, uPixelSr, uLimit, uGain, uTime, uTwinkle, uVeil, uMoonRadius;
uniform vec3 uMoonTrue;
flat out vec3 vP;    // Bestrahlung in Bildpunkt-Einheiten (vorbelichtet)
flat out float vSize;
const float MAG0 = 2.54e-6 / 43965.5;   // Bestrahlung eines Sterns 0. Größe in Szeneneinheiten (2,54 µlx)
const float S0 = 0.65, S1 = 2.4, HALO = 0.06, SEEN = 0.003;

float hash(float n) { return fract(sin(n * 12.9898) * 43758.5453); }

void main() {
    vec3 d = normalize(uEqToScene * aDir);
    vec3 dTrue = d;
    float alt = degrees(asin(clamp(d.y, -1.0, 1.0)));
    float app = alt + refractionTrue(alt);
    d = dirFromAltAz(radians(app), azimuthOf(d));
    vec4 c = uVP * vec4(d, 0.0);
    c.z = 0.5 * c.w;
    gl_Position = c;
    vP = vec3(0.0);
    vSize = 0.0;
    gl_PointSize = 0.0;
    if (alt < -2.0 || hitsGround(uEyeR, d.y) || dot(dTrue, uMoonTrue) > cos(uMoonRadius * 1.05)) { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); return; }
    vec3 T = transmittance(uEyeR, d.y);
    float mApp = aMag - 2.5 * log(max(T.g, 1e-12)) / log(10.0);
    float vis = smoothstep(uLimit + 0.6, uLimit - 0.4, mApp) * (1.0 - uVeil);
    // Funkeln: zwei Schwingungen je Stern, Tiefe wächst mit der Luftmasse
    float h1 = hash(float(gl_VertexID) + 0.17), h2 = hash(float(gl_VertexID) * 1.31 + 4.2);
    float airmass = 1.0 / max(0.05, d.y + 0.15 * pow(max(0.0, app) + 3.885, -1.253));
    float depth = uTwinkle * clamp(0.08 + 0.05 * airmass, 0.0, 0.6);
    float tw = 1.0 + depth * sin(uTime * (11.0 + 9.0 * h1) + 30.0 * h2) * sin(uTime * (5.0 + 4.0 * h2) + 7.0 * h1);
    vec3 E = MAG0 * pow(10.0, -0.4 * aMag) * aColor * T * vis * uGain * tw;
    vec3 P = E * uPre / uPixelSr;
    float p = max(P.r, max(P.g, P.b));
    float peak0 = p * (1.0 - HALO) / (2.0 * PI * S0 * S0), peak1 = p * HALO / (2.0 * PI * S1 * S1);
    if (peak0 < SEEN) { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); return; }
    float r0 = S0 * sqrt(2.0 * log(peak0 / SEEN));
    float r1 = peak1 > SEEN ? S1 * sqrt(2.0 * log(peak1 / SEEN)) : 0.0;
    vSize = clamp(2.0 * max(r0, r1) + 1.0, 1.5, 40.0);
    vP = P;
    gl_PointSize = vSize;
}
