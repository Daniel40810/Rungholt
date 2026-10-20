// figur.vert — Phase 7: ein Wattwanderer (eine Figur, bis die Figuren mit Phase 9 ein eigenes Package bekommen).
// Beine und Arme schwingen um Hüfte und Schulter nach der Schrittphase. Körper: x rechts, y oben, z vorn, m.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec3 aCol;
layout(location = 3) in vec2 aPart;      // Teil (0 Rumpf, 1/2 Bein links/rechts, 3/4 Arm links/rechts, 5 Kopf), Drehpunkt y
uniform mat4 uVP;
uniform vec4 uFig;                       // x, y, z, Richtung (Azimut)
uniform float uPhase;                    // Schrittphase 0..1
out vec3 vWorld, vNrm, vCol;
void main() {
    float ph = uPhase * 6.2831853, swing = 0.42 * sin(ph);
    int part = int(aPart.x + 0.5);
    float a = part == 1 ? swing : part == 2 ? -swing : part == 3 ? -0.7 * swing : part == 4 ? 0.7 * swing : 0.0;
    vec3 p = aPos, n = aNrm;
    if (a != 0.0) {
        float c = cos(a), s = sin(a), py = aPart.y;
        float y = p.y - py, z = p.z;
        p.y = py + y * c - z * s;
        p.z = y * s + z * c;
        n = vec3(n.x, n.y * c - n.z * s, n.y * s + n.z * c);
    }
    p.y += 0.025 * abs(sin(ph));
    float h = uFig.w;
    vec3 R = vec3(cos(h), 0.0, sin(h)), F = vec3(sin(h), 0.0, -cos(h));
    vWorld = uFig.xyz + R * p.x + vec3(0.0, p.y, 0.0) + F * p.z;
    vNrm = R * n.x + vec3(0.0, n.y, 0.0) + F * n.z;
    vCol = aCol;
    gl_Position = uVP * vec4(vWorld, 1.0);
}
