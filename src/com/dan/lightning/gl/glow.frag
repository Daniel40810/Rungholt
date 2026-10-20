// glow.frag — das Leuchten der Luft um den Blitz: Einfachstreuung des Lichts der Lichtpunkte entlang jedes Blicks,
// geschlossen gerechnet (Integral von 1/r² entlang einer Geraden: (θb − θa)/D), bis zur Szene oder zur
// Wolkenuntergrenze. Streufunktion: halb gleichmäßig, halb nach vorn (Henyey-Greenstein 0,7). Mit PANO: ins
// Rundumbild. Ausgabe: Leuchtdichte, vorbelichtet, additiv.
in vec2 vUV;
out vec4 frag;
uniform mat4 uInvVP;
uniform vec3 uCam;
uniform sampler2D uDepth;
uniform int uReversed;
uniform int uUseDepth;
uniform int uPano;
uniform float uMaxDist;
uniform float uScatter;         // Streukoeffizient 1/m
uniform float uSigma;           // Auslöschung 1/m
uniform float uCeil;            // Wolkenuntergrenze (m), ≤ 0: keine
uniform int uCount;
uniform vec4 uPos[8];
uniform vec4 uRgb[8];           // cd · Vorbelichtung
const float PI = 3.14159265;
float hg(float c, float g) { return (1.0 - g * g) / (4.0 * PI * pow(1.0 + g * g - 2.0 * g * c, 1.5)); }
void main() {
    vec3 d;
    float tmax = uMaxDist;
    if (uPano == 1) {
        float az = (vUV.x - 0.5) * 2.0 * PI, el = (vUV.y - 0.5) * PI;
        d = vec3(sin(az) * cos(el), sin(el), -cos(az) * cos(el));
    } else {
        vec4 c = uInvVP * vec4(vUV * 2.0 - 1.0, uReversed == 1 ? 0.5 : 0.0, 1.0);
        d = normalize(c.xyz / c.w - uCam);
        if (uUseDepth == 1) {
            float dz = textureLod(uDepth, vUV, 0.0).r;
            bool sky = uReversed == 1 ? dz <= 0.0 : dz >= 1.0;
            if (!sky) {
                vec4 p = uInvVP * vec4(vUV * 2.0 - 1.0, uReversed == 1 ? dz : dz * 2.0 - 1.0, 1.0);
                tmax = min(tmax, length(p.xyz / p.w - uCam));
            }
        }
    }
    if (uCeil > 0.0 && uCam.y < uCeil && d.y > 1e-4) tmax = min(tmax, (uCeil - uCam.y) / d.y);
    if (d.y < -1e-4 && uCam.y > 0.0) tmax = min(tmax, uCam.y / -d.y + 1.0);
    vec3 L = vec3(0.0);
    // Mit t − tc = D·tan θ wird das Integral von σ·I·p(Θ)·T/r² über den Blick zu σ·I/D · ∫ p(−sin θ)·T dθ: der
    // Streuwinkel hängt nur von θ ab (cos Θ = −sin θ). Acht Stützstellen in θ, die Durchlässigkeit an jeder.
    for (int i = 0; i < 8; i++) {
        if (i >= uCount) break;
        vec3 q = uPos[i].xyz - uCam;
        float tc = dot(q, d);
        // Abstand des Blicks zum Licht, nicht kleiner als die Größe der Quelle
        float D = sqrt(dot(q - d * tc, q - d * tc) + uPos[i].w * uPos[i].w);
        float ta = atan(-tc / D), tb = atan((tmax - tc) / D);
        float dth = (tb - ta) / 8.0, acc = 0.0;
        for (int k = 0; k < 8; k++) {
            float th = ta + (float(k) + 0.5) * dth;
            float t = tc + D * tan(th), r = D / cos(th);
            float ph = 0.55 / (4.0 * PI) + 0.45 * hg(-sin(th), 0.7);
            acc += ph * exp(-uSigma * (max(0.0, t) + r));
        }
        L += uRgb[i].rgb * (uScatter * acc * dth / D);
    }
    frag = vec4(min(L, vec3(60000.0)), 0.0);
}
