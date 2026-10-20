// forest.vert — ein Baum je Instanz: Netz der Art, Variante und Stufe (Fuß bei 0, örtlich), gedreht, skaliert, die
// Krone in der Ferne verbreitert (ausgedünnter Bestand), im Wind gebogen und an seinen Ort gesetzt. Mit DEPTH_ONLY
// nur die Lage für die Sonnenschattenkarte.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec3 aCol;
layout(location = 3) in vec2 aAux;      // Höhe im Baum 0..1, Laub (1 Blatt, 0,5 Kronenhülle, 0 Holz)
layout(location = 4) in vec4 iA;        // x, y, z, Drehung (rad)
layout(location = 5) in vec4 iB;        // Maßstab, Tönung, Phase, Verbreiterung
uniform mat4 uVP;
uniform vec3 uCam;
uniform float uTime;
uniform vec4 uWind;                     // Richtung (wohin) x, z, Geschwindigkeit (m/s), Böigkeit 0..1
#ifndef DEPTH_ONLY
out vec3 vWorld;
out vec3 vNormal;
out vec3 vColor;
out float vLeafy;
out float vH;
#endif

void main() {
    float c = cos(iA.w), s = sin(iA.w);
    vec3 p = aPos * iB.x;
    p.xz *= mix(1.0, iB.w, smoothstep(0.15, 0.4, aAux.x));
    p = vec3(c * p.x - s * p.z, p.y, s * p.x + c * p.z);
    // Wind: stetige Neigung mit U², Böen laufen über die Fläche, der Baum schwingt in seiner Phase
    float U = uWind.z;
    float wave = sin(uTime * 0.7 - dot(iA.xz, uWind.xy) * 0.045 + iB.z * 0.3);
    float gust = 1.0 + uWind.w * 0.6 * wave;
    float lean = clamp(U * U * 0.0007, 0.0, 0.07) * gust;
    float swing = sin(uTime * (0.9 + 0.25 * fract(iB.z)) + iB.z) * 0.0025 * U;
    float h2 = aAux.x * aAux.x;
    vec2 dir = uWind.xy;
    p.xz += (dir * lean + vec2(swing, swing * 0.6)) * p.y * h2;
    // Laub zittert (klein, nur nah sichtbar)
    float fl = step(0.75, aAux.y) * 0.012 * min(U, 14.0) * sin(uTime * 6.3 + dot(aPos, vec3(3.1, 1.7, 2.3)) + iB.z);
    p += vec3(fl, fl * 0.5, -fl * 0.7);
    vec3 w = iA.xyz + p;
    gl_Position = uVP * vec4(w, 1.0);
#ifndef DEPTH_ONLY
    vNormal = vec3(c * aNrm.x - s * aNrm.z, aNrm.y, s * aNrm.x + c * aNrm.z);
    vColor = aCol * mix(vec3(0.84, 0.88, 0.86), vec3(1.14, 1.08, 0.96), iB.y);
    vWorld = w;
    vLeafy = aAux.y;
    vH = aAux.x;
#endif
}
