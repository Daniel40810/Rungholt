// figure.vert — com.dan.figure: ein Mensch je Instanz. Die Gelenke wie FigureMesh.pose (dieselben Formeln):
// Hüfte, Knie, Schulter, Ellbogen, Rumpf, Kopf zur Seite; Senkung hält den tieferen Fuß auf dem Boden.
// Ecke: Ort, Normale (Körper: x rechts, y oben, z vorn), Fläche der Kleidung, Teil.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec2 aSlot;      // Fläche, Teil
layout(location = 3) in vec4 iPos;       // Fuß x, y, z, Richtung (Azimut)
layout(location = 4) in vec4 iPose;      // Schrittphase, Gang, Bücken, Zeigen
layout(location = 5) in vec4 iMisc;      // Größe, Kleidung (Index), Blick zur Seite, Merkmale (1 Mütze, 2 Rucksack)
uniform mat4 uVP;
uniform vec3 uPal[160];                  // 16 Kleidungen × 10 Flächen
out vec3 vWorld, vNrm, vCol;

const float HIP = 0.93, KNEE = 0.48, ANKLE = 0.08, SHOULDER = 1.43, ELBOW = 1.13, NECK = 1.52, THIGH = 0.45, SHIN = 0.40;
const float HIP_SWING = 0.42, KNEE_SWING = 1.05;

// dreht (y, z) um die x-Achse durch py: positiv schwingt ein hängendes Glied nach vorn
void swing(inout vec3 p, inout vec3 n, float py, float a) {
    float c = cos(a), s = sin(a), y = p.y - py;
    p = vec3(p.x, py + y * c + p.z * s, -y * s + p.z * c);
    n = vec3(n.x, n.y * c + n.z * s, -n.y * s + n.z * c);
}
float footH(float h, float k) { return HIP - (THIGH * cos(h) + SHIN * cos(h - k)) - ANKLE; }

void main() {
    float a = 6.2831853 * iPose.x, g = clamp(iPose.y, 0.0, 1.4), bend = iPose.z, point = iPose.w;
    float hl = HIP_SWING * g * sin(a), hr = -hl;
    float kl = KNEE_SWING * g * pow(max(0.0, cos(a)), 1.5) + 0.08 * g;
    float kr = KNEE_SWING * g * pow(max(0.0, -cos(a)), 1.5) + 0.08 * g;
    hl += 0.5 * bend; hr += 0.5 * bend; kl += bend; kr += bend;
    float al = -0.65 * HIP_SWING * g * sin(a), ar = -al;
    float el = 0.25 + 0.25 * g, er = el;
    al += 0.35 * bend; ar += 0.35 * bend;
    ar = mix(ar, 1.45, point);
    er = mix(er, 0.08, point);
    float torso = bend + 0.05 * g;
    float drop = min(footH(hl, kl), footH(hr, kr));

    int part = int(aSlot.y + 0.5), slot = int(aSlot.x + 0.5);
    int flags = int(iMisc.w + 0.5);
    vec3 p = aPos, n = aNrm;
    // ohne Mütze und Rucksack: die Teile in den Körper falten
    if (part == 12 && (flags & 1) == 0) p = vec3(0.0, 1.64, 0.0);
    if (part == 11 && (flags & 2) == 0) p = vec3(0.0, 1.24, 0.0);
    if (part == 3) swing(p, n, HIP, hl);
    else if (part == 4) swing(p, n, HIP, hr);
    else if (part == 5) { swing(p, n, KNEE, -kl); swing(p, n, HIP, hl); }
    else if (part == 6) { swing(p, n, KNEE, -kr); swing(p, n, HIP, hr); }
    else if (part == 13) { swing(p, n, ANKLE, -(hl - kl)); swing(p, n, KNEE, -kl); swing(p, n, HIP, hl); }   // Fuß waagerecht
    else if (part == 14) { swing(p, n, ANKLE, -(hr - kr)); swing(p, n, KNEE, -kr); swing(p, n, HIP, hr); }
    else if (part != 0) {
        if (part == 2 || part == 12) {     // Kopf zur Seite drehen (um den Hals)
            float c = cos(iMisc.z), s = sin(iMisc.z);
            p = vec3(p.x * c + p.z * s, p.y, -p.x * s + p.z * c);
            n = vec3(n.x * c + n.z * s, n.y, -n.x * s + n.z * c);
        }
        if (part == 9) swing(p, n, ELBOW, el);
        if (part == 10) swing(p, n, ELBOW, er);
        if (part == 7 || part == 9) swing(p, n, SHOULDER, al);
        if (part == 8 || part == 10) swing(p, n, SHOULDER, ar);
        swing(p, n, HIP, -torso);
    }
    p.y -= drop;
    p *= iMisc.x;
    float h = iPos.w;
    vec3 R = vec3(cos(h), 0.0, sin(h)), F = vec3(sin(h), 0.0, -cos(h));
    vWorld = iPos.xyz + R * p.x + vec3(0.0, p.y, 0.0) + F * p.z;
    vNrm = R * n.x + vec3(0.0, n.y, 0.0) + F * n.z;
    vCol = uPal[int(iMisc.y + 0.5) * 10 + slot];
    gl_Position = uVP * vec4(vWorld, 1.0);
}
