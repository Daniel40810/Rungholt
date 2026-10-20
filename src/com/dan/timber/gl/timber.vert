// timber.vert — ein Haus je Instanz: das Netz des Typs (örtlich: x entlang des Firsts, z quer), gestreckt in Länge
// und Breite, um die Hochachse gedreht (Richtung des Firsts in rad von Nord im Uhrzeigersinn: örtlich +x zeigt nach
// (sin h, 0, −cos h)) und an seinen Ort gesetzt. Das feste Netz der Bauwerke kommt mit einer Instanz ohne Drehung.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec2 aUV;
layout(location = 3) in vec2 aMatAo;    // Werkstoff, Verdeckung
layout(location = 4) in vec4 iA;        // x, y, z, Richtung (rad)
layout(location = 5) in vec4 iB;        // Streckung Länge, Breite, Zustand, Zufall
uniform mat4 uVP;
out vec3 vWorld;
out vec3 vNormal;
out vec2 vUV;
flat out int vMat;
out float vAO;
out float vLocalY;
flat out vec2 vState;                    // Zustand, Zufall
flat out float vBurn;                    // seit Haithabu Phase 15: Brand 0..2 (über 1 eingestürzt)
out vec3 vLocal;                         // örtliche Lage im Netz (vor Streckung und Drehung)

void main() {
    vec3 sc = vec3(iB.x, 1.0, iB.y);
    vec3 p = aPos * sc;
    // seit Haithabu Phase 15: der Brand steckt im Zufall (2 · Stufe + Zufall); eingestürzt sinkt alles zu einem Haufen
    float bq = floor(iB.w * 0.5);
    vBurn = bq / 255.0;
    if (vBurn > 1.0) {
        float j = fract(sin(dot(aPos.xz, vec2(12.9898, 78.233))) * 43758.5453);
        p.y = p.y * 0.28 + 0.15 * j * step(0.3, aPos.y);
    }
    vec3 n = normalize(aNrm / sc);
    float s = sin(iA.w), c = cos(iA.w);
    vec3 w = vec3(p.x * s + p.z * c, p.y, -p.x * c + p.z * s) + iA.xyz;
    vNormal = vec3(n.x * s + n.z * c, n.y, -n.x * c + n.z * s);
    vWorld = w;
    vUV = aUV;
    vMat = int(aMatAo.x + 0.5);
    vAO = aMatAo.y;
    vLocalY = aPos.y;
    vState = vec2(iB.z, iB.w - 2.0 * bq);
    vLocal = aPos;
    gl_Position = uVP * vec4(w, 1.0);
}
