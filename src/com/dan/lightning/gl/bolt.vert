// bolt.vert — der Kanal als Band quer zum Blick, ohne Eckpuffer: sechs Ecken je Stück aus einem Speicherpuffer.
// Stück i: seg[2i] = Anfang (xyz) und Weg des Leitblitzes, seg[2i+1] = Ende (xyz) und Ordnung + halbe Sichtbarkeit.
// Die Leuchtdichte quer über das Band ist so geformt, dass ihr Integral über das Bild genau die Beleuchtungsstärke
// des Stücks am Auge ergibt: ein schmaler Kern (Gauß, 0,6 Bildpunkte) und ein Hof (Lorentz) für das Licht, das
// Regen und Dunst auf dem Weg nach vorn streuen. Mit PANO: dasselbe ins Rundumbild (Azimut von Nord, Höhe).
layout(std430, binding = 0) readonly buffer Segs { vec4 seg[]; };
uniform mat4 uVP;
uniform vec3 uCam;
uniform vec2 uViewport;
uniform float uPixelAngle;      // Winkel eines Bildpunkts (rad)
uniform int uBase;              // erstes Stück dieses Blitzes
uniform vec4 uLevel;            // cd je m (vorbelichtet): Hauptkanal, Ordnung 1, 2, 3
uniform vec2 uLeader;           // Front des Leitblitzes (m Weg), seine Stärke (cd/m, vorbelichtet)
uniform float uSigma;           // Auslöschung der Luft 1/m
uniform float uScatter;         // Streuung der Luft 1/m (Hof)
uniform int uPano;
out float vOff;                 // Abstand zur Mittellinie in Bildpunkten (mit Vorzeichen)
out float vAlong;               // Lage entlang des Stücks in Bildpunkten (0 = Anfang)
out float vI;                   // Leuchtdichte × Breite: I / (d · Winkel) in Bildpunkten
flat out float vHalf, vHaloS, vHaloF, vLen;

const float CORE = 0.6;

vec2 panoUV(vec3 p) {
    vec3 d = normalize(p - uCam);
    return vec2(0.5 + atan(d.x, -d.z) / 6.2831853, 0.5 + asin(clamp(d.y, -1.0, 1.0)) / 3.14159265);
}

void main() {
    int i = uBase + gl_VertexID / 6, c = gl_VertexID % 6;
    vec4 A = seg[2 * i], B = seg[2 * i + 1];
    int ord = int(floor(B.w));
    float vis = fract(B.w) * 2.0;
    float lv = ord == 0 ? uLevel.x : ord == 1 ? uLevel.y : ord == 2 ? uLevel.z : uLevel.w;
    if (uLeader.y > 0.0 && A.w <= uLeader.x) lv += uLeader.y * (0.5 + 0.5 * exp(-(uLeader.x - A.w) / 120.0));
    // Ecken: 0 1 2 / 2 1 3 aus (Ende, Seite)
    int corner = c == 0 ? 0 : c == 1 ? 1 : c == 2 ? 2 : c == 3 ? 2 : c == 4 ? 1 : 3;
    bool atB = corner >= 2;
    float side = (corner & 1) == 0 ? -1.0 : 1.0;
    vec3 P = atB ? B.xyz : A.xyz;
    float dist = max(1.0, length(P - uCam));
    float tr = exp(-uSigma * dist);
    // Hof: das Licht, das Regen und Dunst unter kleinem Winkel nach vorn streuen (das weite Leuchten rechnet glow.frag)
    float haloF = clamp(0.04 + 0.6 * (1.0 - exp(-uScatter * dist)), 0.0, 0.35);
    float haloS = 1.5 + 6.0 * haloF;
    float half_ = 3.0 * CORE + 5.0 * haloS;
    // schräg zum Blick erscheint ein Stück kürzer, sein Licht drängt sich auf weniger Bildpunkte
    vec3 sd = B.xyz - A.xyz;
    float sinPhi = length(sd) > 1e-3 ? length(cross(normalize(sd), (P - uCam) / dist)) : 1.0;
    vI = lv * vis * tr / (dist * uPixelAngle * max(0.05, sinPhi));
    vHalf = half_;
    vHaloS = haloS;
    vHaloF = haloF;
    vAlong = 0.0;
    vLen = 0.0;
    if (lv * vis <= 0.0) { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); vOff = 0.0; return; }
    if (uPano == 1) {
        vec2 ua = panoUV(A.xyz), ub = panoUV(B.xyz);
        if (abs(ua.x - ub.x) > 0.5) { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); vOff = 0.0; return; }
        vec2 pa = ua * uViewport, pb = ub * uViewport;
        vec2 dir = pb - pa;
        float len = length(dir);
        dir = len > 1e-4 ? dir / len : vec2(1.0, 0.0);
        vec2 nrm = vec2(-dir.y, dir.x);
        vec2 q = (atB ? pb : pa) + nrm * side * half_ + dir * (atB ? 1.0 : -1.0) * half_;
        vOff = side * half_;
        vAlong = atB ? len + half_ : -half_;
        vLen = len;
        gl_Position = vec4(q / uViewport * 2.0 - 1.0, 0.0, 1.0);
        return;
    }
    vec4 ca = uVP * vec4(A.xyz, 1.0), cb = uVP * vec4(B.xyz, 1.0);
    const float NEAR = 1e-3;
    if (ca.w < NEAR && cb.w < NEAR) { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); vOff = 0.0; return; }
    if (ca.w < NEAR) ca = mix(ca, cb, (NEAR - ca.w) / (cb.w - ca.w));
    if (cb.w < NEAR) cb = mix(cb, ca, (NEAR - cb.w) / (ca.w - cb.w));
    vec2 pa = (ca.xy / ca.w * 0.5 + 0.5) * uViewport, pb = (cb.xy / cb.w * 0.5 + 0.5) * uViewport;
    vec2 dir = pb - pa;
    float len = length(dir);
    dir = len > 1e-4 ? dir / len : vec2(1.0, 0.0);
    vec2 nrm = vec2(-dir.y, dir.x);
    vec4 cc = atB ? cb : ca;
    vec2 q = (atB ? pb : pa) + nrm * side * half_ + dir * (atB ? 1.0 : -1.0) * half_;
    vOff = side * half_;
    vAlong = atB ? len + half_ : -half_;
    vLen = len;
    gl_Position = vec4((q / uViewport * 2.0 - 1.0) * cc.w, cc.z, cc.w);
}
