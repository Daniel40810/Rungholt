// kino.frag — was der Kinomodus über das fertige Bild legt (com.dan.kino). Mischung außen: ONE, ONE_MINUS_SRC_ALPHA.
//   uMode 0: ein Bild (vormultipliziert) mal uGain: das gemerkte Bild der Überblendung, die Texttafel (uFlipY = 1)
//   uMode 1: Film: Randabdunklung und Korn (dunkel), Stärke uGain, Korn wechselt mit uZeit
//   uMode 2: eine Farbe uColor mit Deckkraft uGain (das Schwarz)
in vec2 vUV;
out vec4 frag;
uniform sampler2D uTex;
uniform int uMode = 0;
uniform int uFlipY = 0;
uniform float uGain = 1.0;
uniform float uZeit = 0.0;
uniform vec2 uSize = vec2(1920.0, 1080.0);
uniform vec3 uColor = vec3(0.0);

float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }

void main() {
    if (vUV.x > 1.0 || vUV.y > 1.0) discard;
    if (uMode == 0) {
        vec2 uv = uFlipY == 1 ? vec2(vUV.x, 1.0 - vUV.y) : vUV;
        frag = textureLod(uTex, uv, 0.0) * uGain;
    } else if (uMode == 1) {
        vec2 q = (vUV - 0.5) * vec2(uSize.x / uSize.y, 1.0);
        float vig = smoothstep(0.45, 1.25, length(q)) * 0.38;
        // Korn in Körnern von 1,5 Bildpunkten, 24-mal je Sekunde neu
        vec2 cell = floor(gl_FragCoord.xy / 1.5);
        float n = hash(cell + floor(uZeit * 24.0) * vec2(17.0, 31.0));
        float grain = max(0.0, n - 0.5) * 0.09;
        frag = vec4(0.0, 0.0, 0.0, clamp((vig + grain) * uGain, 0.0, 1.0));
    } else {
        frag = vec4(uColor * uGain, uGain);
    }
}
