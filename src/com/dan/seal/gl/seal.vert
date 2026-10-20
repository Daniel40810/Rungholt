// seal.vert — com.dan.seal (Phase 7): ein Seehund je Instanz. Die Wirbelsäule biegt sich nach der Haltung (Kopf heben,
// Kopf drehen, Schwanz heben, Buckel, Schwanzschlag, Neigung), Querschnitt und Flossen hängen im Rahmen ihrer Stelle s;
// dann dreht die Rolle den Körper um seine Achse, die Richtung (Azimut) stellt ihn in die Welt. Dieselbe Rechnung wie
// SealBody.deform in Java. Körper: x rechts, y oben, z vorn, Länge 1; Welt: x Ost, y oben, z Süd.
layout(location = 0) in vec3 aOff;      // Versatz im Rahmen der Stelle s
layout(location = 1) in vec3 aNrm;
layout(location = 2) in float aS;       // Stelle entlang der Wirbelsäule (0 Nase … 0,93 Schwanzansatz)
layout(location = 3) in float aPart;    // 0 Rumpf, 1 Vorderflosse, 2 Hinterflosse
layout(location = 4) in float aAng;     // Winkel um die Achse (0 oben)
layout(location = 5) in vec4 iA;        // x, y, z, Länge
layout(location = 6) in vec4 iB;        // Richtung, Rolle, Nässe, Fell (+2 beim Jungen)
layout(location = 7) in vec4 iC;        // Kopf heben, Kopf drehen, Schwanz heben, Buckel
layout(location = 8) in vec4 iD;        // Schwanzschlag, Vorderflosse, Hinterflossen auffächern, Neigung
uniform mat4 uVP;
out vec3 vWorld, vNrm;
out vec4 vBody;                         // s, Winkel, Seite, Teil
out vec3 vInst;                         // Nässe, Fell, Junges

const float ANCHOR = 0.45;

float pitchAt(float s) {
    float head = 1.0 - smoothstep(0.10, 0.36, s), tail = smoothstep(0.62, 0.92, s);
    return iD.w + iC.x * head - iC.z * tail + iC.w * clamp((s - ANCHOR) * 2.4, -1.0, 1.0);
}
float yawAt(float s) { return iC.y * (1.0 - smoothstep(0.08, 0.40, s)) + iD.x * smoothstep(0.50, 1.0, s); }

vec3 spine(float s) {
    float ds = (ANCHOR - s) / 10.0;
    vec3 p = vec3(0.0);
    for (int k = 0; k < 10; k++) {
        float m = s + (float(k) + 0.5) * ds, ph = pitchAt(m), yw = yawAt(m);
        p += vec3(sin(yw) * cos(ph), sin(ph), cos(yw) * cos(ph)) * ds;
    }
    return p;
}

void main() {
    vec3 o = aOff, n = aNrm;
    int part = int(aPart + 0.5);
    if (part == 1) {
        float a = iD.y, c = cos(a), si = sin(a);
        float ry = o.y + 0.03, rz = o.z;
        o.y = -0.03 + ry * c - rz * si;
        o.z = ry * si + rz * c;
        n = vec3(n.x, n.y * c - n.z * si, n.y * si + n.z * c);
    } else if (part == 2) {
        float side = o.x < 0.0 ? -1.0 : 1.0, a = side * iD.z, c = cos(a), si = sin(a);
        vec3 o1 = vec3(o.x * c - o.z * si, o.y, o.x * si + o.z * c), n1 = vec3(n.x * c - n.z * si, n.y, n.x * si + n.z * c);
        float b = -0.5 * iC.z, cb = cos(b), sb = sin(b);
        o = vec3(o1.x, o1.y * cb - o1.z * sb, o1.y * sb + o1.z * cb);
        n = vec3(n1.x, n1.y * cb - n1.z * sb, n1.y * sb + n1.z * cb);
    }
    float s = aS, ph = pitchAt(s), yw = yawAt(s);
    vec3 f = vec3(sin(yw) * cos(ph), sin(ph), cos(yw) * cos(ph));
    vec3 r = vec3(cos(yw), 0.0, -sin(yw));
    vec3 u = cross(f, r);
    vec3 p = spine(s) + r * o.x + u * o.y + f * o.z;
    vec3 q = r * n.x + u * n.y + f * n.z;
    float cr = cos(iB.y), sr = sin(iB.y);
    p = vec3(p.x * cr - p.y * sr, p.x * sr + p.y * cr, p.z);
    q = vec3(q.x * cr - q.y * sr, q.x * sr + q.y * cr, q.z);
    // in die Welt: rechts, oben, vorn nach dem Azimut
    float h = iB.x;
    vec3 R = vec3(cos(h), 0.0, sin(h)), F = vec3(sin(h), 0.0, -cos(h));
    vec3 w = iA.xyz + (R * p.x + vec3(0.0, 1.0, 0.0) * p.y + F * p.z) * iA.w;
    vWorld = w;
    vNrm = R * q.x + vec3(0.0, 1.0, 0.0) * q.y + F * q.z;
    vBody = vec4(s, aAng, aOff.x < 0.0 ? -1.0 : 1.0, aPart);
    vInst = vec3(iB.z, fract(iB.w), iB.w >= 2.0 ? 1.0 : 0.0);
    gl_Position = uVP * vec4(w, 1.0);
}
