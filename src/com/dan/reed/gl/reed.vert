// reed.vert — ein Halm (Schilf, Binse, Grasbüschel) je Instanz, die Form aus der Nummer der Ecke: uBlades Blätter
// zu uSegs Abschnitten, je Abschnitt zwei Dreiecke. Blatt 0 ist der Stängel (immer zur Kamera gedreht, damit er nie
// von der Kante gesehen verschwindet), die anderen Blätter stehen schräg ab. Biegung im Wind wie Reed.sway():
// stetige Neigung (U²), Böenwellen mit dem Wind, Zittern; der Halm biegt sich wie ein Kragträger (Auslenkung ∝ t²).
// Zum Rand der Reichweite werden die Halme schmal (kein Aufpoppen).
layout(location = 0) in vec4 aPos;    // x, y (Boden), z, Höhe
layout(location = 1) in vec4 aParam;  // Richtung der Ruhelage, Phase, Ton, Art (0 Schilf, 1 Binse, 2 Gras, 3 Steppe)
uniform mat4 uVP;
uniform vec3 uCam;
uniform float uTime;
uniform vec2 uWindTo;
uniform vec3 uWind;                   // Geschwindigkeit, Böigkeit, Nässe
uniform float uStiff[4];
uniform float uRange[4];
uniform int uBlades, uSegs;
uniform vec2 uSnowFrost;              // Schneehöhe (m), Reif 0..1 (seit Haithabu Phase 12)
out vec3 vWorld;
out vec3 vNormal;
out float vT;
out float vTon;
flat out int vKind;
out float vPanicle;

float h1(float n) { return fract(sin(n * 12.9898) * 43758.5453); }

void main() {
    int kind = int(aParam.w + 0.5);
    int per = uSegs * 6;
    int blade = gl_VertexID / per, r = gl_VertexID % per, seg = r / 6, c = r % 6;
    // Ecke im Abschnitt: (Seite, Ebene)
    int side = (c == 1 || c == 3 || c == 4) ? 1 : 0;
    int lvl = (c == 2 || c == 4 || c == 5) ? seg + 1 : seg;
    float t = float(lvl) / float(uSegs);
    float hb = aParam.y * 7.31 + float(blade) * 3.17;
    float height = aPos.w;
    float stiff = uStiff[kind];
    vec3 base = aPos.xyz;
    // Blätter: Schilf und Binse am Stängel, Gras als Büschel um die Mitte
    float ang = aParam.x + float(blade) * 2.39996 + h1(hb) * 0.6;
    vec2 out2 = vec2(cos(ang), sin(ang));
    float bh = height, width, outBend;
    if (kind == 0) {          // Schilf: Stängel mit Rispe, Blätter breit und überhängend
        if (blade == 0) { width = 0.014; outBend = 0.0; }
        else { bh = height * (0.45 + 0.35 * h1(hb + 1.0)); width = 0.022; outBend = 0.35; base.y += height * (0.15 + 0.35 * h1(hb + 2.0)); }
    } else if (kind == 1) {   // Binse: steife runde Halme, leicht gespreizt
        width = 0.006; outBend = 0.08 + 0.1 * h1(hb); bh = height * (0.75 + 0.25 * h1(hb + 1.0));
        base.xz += out2 * 0.03;
    } else if (kind == 3) {   // Steppe (seit Glacier): fester Horst aus harten, steilen Blättern, weit ausgebreitet
        width = 0.007; outBend = 0.14 + 0.22 * h1(hb); bh = height * (0.65 + 0.35 * h1(hb + 1.0));
        base.xz += out2 * 0.10 * h1(hb + 3.0);
    } else {                  // Gras: Büschel aus schmalen Blättern
        width = 0.010; outBend = 0.30 + 0.35 * h1(hb); bh = height * (0.55 + 0.45 * h1(hb + 1.0));
        base.xz += out2 * 0.06 * h1(hb + 3.0);
    }
    // Schnee: der Halm wächst aus der Schneedecke, oben bleibt er, wo er war; kurzes Gras liegt darunter
    float sd = uSnowFrost.x;
    if (sd > 0.0) {
        float vis = bh - sd;
        if (vis < 0.04) { gl_Position = vec4(0.0, 0.0, -2.0, 1.0); return; }
        base.y += sd;
        bh = vis;
    }
    // Wind (gleich Reed.sway): Neigung, Böenwelle, Zittern; Nässe lässt hängen
    float u = uWind.x;
    float lean = min(0.7, 0.0045 * u * u * (1.15 - stiff));
    float along = dot(aPos.xz, uWindTo);
    float k = 6.2831853 / 18.0, cs = 0.7 * max(0.5, u);
    float wave = 0.5 + 0.5 * sin(k * (along - cs * uTime) + 2.0 * sin(0.11 * (aPos.x - aPos.z)));
    float flutter = (0.015 + 0.004 * u) * (1.1 - stiff) * sin(uTime * (2.2 + 0.25 * u) + aParam.y);
    float sideF = 0.6 * flutter * cos(uTime * 1.7 + aParam.y * 1.3);
    float a = lean + uWind.y * lean * 1.6 * wave + flutter + uWind.z * 0.08;
    vec2 perp = vec2(-uWindTo.y, uWindTo.x);
    vec2 bend = uWindTo * a + perp * sideF + out2 * outBend;
    // Kragträger: Auslenkung ∝ t², Länge bleibt etwa erhalten
    float t2 = t * t;
    vec3 p = base + vec3(bend.x * bh * t2, bh * t - 0.5 * dot(bend, bend) * bh * t2 * t, bend.y * bh * t2);
    // Breite: Stängel zur Kamera, Blätter quer zu ihrer Richtung; zur Spitze schmal; am Rand der Reichweite dünn
    vec3 toCam = uCam - p;
    float dist = length(toCam);
    vec3 across = blade == 0 && kind == 0 ? normalize(cross(vec3(0.0, 1.0, 0.0), toCam / max(dist, 1e-3)) + vec3(1e-4, 0.0, 0.0))
                                          : vec3(-out2.y, 0.0, out2.x);
    float taper = kind == 0 && blade == 0 ? 1.0 - 0.3 * t : 1.0 - 0.85 * t;
    float fade = 1.0 - smoothstep(0.7, 1.0, dist / uRange[kind]);
    // in der Ferne breiter (sonst flimmern die dünnen Halme), Fläche bleibt etwa gleich sichtbar
    float grow = max(1.0, dist / 25.0);
    float pan = 0.0;
    if (kind == 0 && blade == 0 && t > 0.82) { pan = smoothstep(0.82, 0.9, t); width = mix(width, 0.05, pan); }
    p += across * (float(side) - 0.5) * width * taper * fade * grow;
    vNormal = normalize(cross(across, vec3(bend.x * 2.0 * t, 1.0, bend.y * 2.0 * t)));
    vWorld = p;
    vT = t;
    vTon = aParam.z;
    vKind = kind;
    vPanicle = pan;
    gl_Position = uVP * vec4(p, 1.0);
}
