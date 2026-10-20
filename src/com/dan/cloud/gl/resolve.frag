// resolve.frag — zeitliche Wiederverwendung: das Wolkenbild des letzten Bilds über die mittlere Entfernung an die
// neue Stelle geholt, auf die Spanne der Nachbarn des neuen Bilds begrenzt (gegen Schlieren) und zu 85 % gemischt.
// So glättet sich das Zittern der Schrittweite über die Bilder, auch wenn die Kamera sich dreht.
in vec2 vUV;
out vec4 outColor;
uniform sampler2D uCurr, uCurrDist, uHist;
uniform mat4 uInvVP, uPrevVP;
uniform int uReversed, uHistValid;
uniform vec3 uCam;
uniform float uBlend;
void main() {
    vec4 cur = texture(uCurr, vUV);
    float dist = texture(uCurrDist, vUV).r;
    if (uHistValid == 0) { outColor = cur; return; }
    vec2 ndc = vUV * 2.0 - 1.0;
    float zn = uReversed == 1 ? 1.0 : -1.0, zm = uReversed == 1 ? 0.5 : 0.0;
    vec4 a = uInvVP * vec4(ndc, zn, 1.0), b = uInvVP * vec4(ndc, zm, 1.0);
    vec3 d = normalize(b.xyz / b.w - a.xyz / a.w);
    vec4 prev = uPrevVP * vec4(uCam + d * dist, 1.0);
    vec2 puv = prev.xy / prev.w * 0.5 + 0.5;
    if (prev.w <= 0.0 || any(lessThan(puv, vec2(0.0))) || any(greaterThan(puv, vec2(1.0)))) { outColor = cur; return; }
    vec4 hist = texture(uHist, puv);
    vec2 px = 1.0 / vec2(textureSize(uCurr, 0));
    vec4 mn = cur, mx = cur;
    for (int y = -1; y <= 1; y++)
        for (int x = -1; x <= 1; x++) {
            vec4 s = texture(uCurr, vUV + vec2(x, y) * px);
            mn = min(mn, s);
            mx = max(mx, s);
        }
    hist = clamp(hist, mn, mx);
    outColor = mix(cur, hist, uBlend);
}
