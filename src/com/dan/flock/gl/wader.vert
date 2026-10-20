// wader.vert — com.dan.flock seit Rungholt Phase 8: ein Vogel des Watts je Instanz. Wie gull.vert (Innen- und
// Außenflügel um Schulter und Handgelenk, Beine nach iLegs, Querneigung und Richtung), dazu Teil 4: Hals, Kopf und
// Schnabel neigen sich um den Halsansatz zum Stochern (iProbe, rad; negativ: Kopf eingezogen beim Rasten).
// Ecken in Körperkoordinaten: x rechts, y oben, z nach hinten, wahre Größe in m.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec3 aCol;
layout(location = 3) in float aPart;
layout(location = 4) in vec4 iPos;      // Ort, Maßstab
layout(location = 5) in vec4 iFwd;      // Richtung (Einheitsvektor), Querneigung (rad)
layout(location = 6) in vec4 iWing;     // Anhebung innen, zusätzlich außen, Schwung, Faltung
layout(location = 7) in vec2 iLegs;     // Beine 0..1, Stochern (rad)
uniform mat4 uVP;
uniform vec3 uShoulder;                 // rechte Schulter
uniform float uWrist;                   // x des rechten Handgelenks
uniform vec3 uNeck;                     // Drehpunkt des Halses
out vec3 vWorld, vNrm, vCol;

mat3 rotZ(float a) { float c = cos(a), s = sin(a); return mat3(c, s, 0.0, -s, c, 0.0, 0.0, 0.0, 1.0); }
mat3 rotY(float a) { float c = cos(a), s = sin(a); return mat3(c, 0.0, -s, 0.0, 1.0, 0.0, s, 0.0, c); }
mat3 rotX(float a) { float c = cos(a), s = sin(a); return mat3(1.0, 0.0, 0.0, 0.0, c, s, 0.0, -s, c); }

void main() {
    int part = int(aPart + 0.5);
    vec3 p = aPos, n = aNrm;
    float side = p.x < 0.0 ? -1.0 : 1.0;
    if (part == 1 || part == 2) {
        vec3 sh = vec3(uShoulder.x * side, uShoulder.y, uShoulder.z);
        mat3 Rin = rotY(-side * iWing.z) * rotZ(side * iWing.x);
        if (part == 1) {
            p = sh + Rin * (p - sh);
            n = Rin * n;
        } else {
            vec3 wr = vec3(uWrist * side, sh.y, sh.z - 0.03);
            mat3 Rout = rotY(-side * iWing.w) * rotZ(side * iWing.y);
            p = sh + Rin * (wr - sh) + Rin * Rout * (p - wr);
            n = Rin * Rout * n;
        }
    } else if (part == 3) {
        p.y = mix(p.y * 0.25, p.y, iLegs.x);   // im Flug angelegt
        p.z = mix(p.z + 0.4 * abs(p.y), p.z, iLegs.x);
    } else if (part == 4) {
        // Kopf nach vorn unten (z ist hinten: ein Drehen um +x senkt den Schnabel)
        mat3 R = rotX(-iLegs.y);
        p = uNeck + R * (p - uNeck);
        n = R * n;
    }
    p *= iPos.w;
    vec3 f = normalize(iFwd.xyz);
    vec3 r = normalize(cross(f, vec3(0.0, 1.0, 0.0)) + vec3(1e-5, 0.0, 0.0));
    vec3 u = cross(r, f);
    float c = cos(iFwd.w), s = sin(iFwd.w);
    vec3 rb = r * c - u * s, ub = r * s + u * c;
    vec3 w = iPos.xyz + rb * p.x + ub * p.y - f * p.z;
    vWorld = w;
    vNrm = rb * n.x + ub * n.y - f * n.z;
    vCol = aCol;
    gl_Position = uVP * vec4(w, 1.0);
}
