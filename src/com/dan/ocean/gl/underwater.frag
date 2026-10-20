// underwater.frag — seit Rungholt Phase 6: der Blick unter Wasser. Über die ganze Szene (Tiefe als Kopie) ein trübes
// Wasser: Durchlässigkeit T = exp(−σ · Weg), Streulicht in der Farbe des Wassers, mit der Tiefe unter dem Spiegel
// dunkler (Licht von oben, gedämpft auf dem Weg hinab). Ausgabe (Streulicht · (1 − T), T) für die Mischung ONE,
// SRC_ALPHA: Bild = Streulicht + Bild · T.
uniform mat4 uInvVP;
uniform vec3 uCam;
uniform sampler2D uDepth;
uniform int uReversed;
uniform vec2 uViewport;
uniform float uSurface;     // Wasserspiegel über dem Auge (m)
uniform vec3 uScatter;      // Streulicht dicht unter dem Spiegel (Strahldichte, linear)
uniform vec2 uSigma;        // Auslöschung (1/m) im Blick, Abnahme des Lichts mit der Tiefe (1/m)
out vec4 frag;

void main() {
    vec2 uv = gl_FragCoord.xy / uViewport;
    float d = texelFetch(uDepth, ivec2(gl_FragCoord.xy), 0).r;
    bool sky = uReversed == 1 ? d <= 0.0 : d >= 1.0;
    float zN = sky ? (uReversed == 1 ? 1e-6 : 0.999999) : (uReversed == 1 ? d : d * 2.0 - 1.0);
    vec4 w = uInvVP * vec4(uv * 2.0 - 1.0, zN, 1.0);
    vec3 P = w.xyz / w.w;
    float dist = sky ? 1.0e4 : length(P - uCam);
    // nur der Teil des Wegs unter dem Spiegel
    vec3 dir = normalize(P - uCam);
    if (dir.y > 1e-4) dist = min(dist, max(0.0, (uSurface - uCam.y) / dir.y));
    float T = exp(-uSigma.x * dist);
    float mid = max(0.0, uSurface - (uCam.y + dir.y * min(dist, 30.0) * 0.5));
    vec3 L = uScatter * exp(-uSigma.y * mid);
    frag = vec4(L * (1.0 - T), T);
}
