// room_smoke.frag — Rauch in einem Raum (com.dan.smoke.RoomSmoke) als Volumen: der Blickstrahl wird innerhalb der Hülle
// (Kasten mit Satteldach) bis zur Szenentiefe abgetastet. Dichte: Schicht unter dem Dach, Dunst darunter, die Fahne über
// dem Feuer, mit Rauschen verwirbelt. Licht: Sonne und Mond nur, wo der Strahl zu ihnen durch eine Öffnung ins Freie
// geht (Lichtstrahlen durch Tür und Rauchloch), gedämpft vom Rauch auf dem Weg; Lichter im Raum (Herd, Lampen); Himmel
// durch die Öffnungen und Rückstrahlung als gleichmäßiges Licht. Helligkeit wie die Flächen der App: Sonne und
// Lichter als Bestrahlung ohne 1/π (wie timber, das Lambert ohne 1/π malt), darum hier mal π; das gleichmäßige Licht
// kommt schon in dieser Einheit. Ausgabe: gestreutes Licht und Durchlässigkeit
// (Mischung ONE, SRC_ALPHA). Örtliche Meter des Raums: x entlang des Firsts, y über dem Boden, z quer.
out vec4 frag;
uniform mat4 uInvVP;
uniform vec3 uEye, uForward;
uniform vec2 uSize;            // Größe des Ziels (in halber Auflösung die halbe)
uniform float uDepthScale;     // Pixel des Ziels → Pixel der Tiefe (1 oder 2)
uniform sampler2D uSceneDepth;
uniform int uHasDepth, uReversed;
uniform float uNear, uFar;
uniform vec4 uRoomA;          // Ort x, y (Boden), z, Richtung (rad)
uniform vec4 uRoomB;          // halbe Länge, halbe Breite innen, Wandhöhe, Firsthöhe innen
uniform vec4 uRoomC;          // Neigung der Dachunterseite (tan), Zahl der Öffnungen
uniform vec4 uRoomO[16];      // je Öffnung: Seite, a0, a1, Dreieck; y0, y1, Fläche, 0
uniform vec4 uSmokeA;         // Unterkante der Schicht, Auslöschung oben, unten (1/m), Übergang (m)
uniform vec4 uSmokeB;         // Feuer x, y, z (örtlich), Q konvektiv (kW)
uniform float uPlumeC;        // σ_Fahne(z) = uPlumeC / ṁ(z)
uniform float uAlbedo, uStrength, uTime;
uniform vec3 uSunDir, uSunColor, uMoonDir, uMoonColor;
uniform vec3 uAmbient;        // gleichmäßiges Licht im Raum (Himmel durch die Öffnungen, Rückstrahlung), wie die Flächen
uniform int uLightN;
uniform vec4 uLightPos[4];    // Szene: Ort, Größe
uniform vec4 uLightRgb[4];    // Lichtstärke (E = I / d²)
uniform sampler3D uNoise;
uniform int uSteps;

vec3 roomLocal(vec3 w) {
    vec3 d = w - uRoomA.xyz;
    float s = sin(uRoomA.w), c = cos(uRoomA.w);
    return vec3(d.x * s - d.z * c, d.y, d.x * c + d.z * s);
}
vec3 roomDir(vec3 d) {
    float s = sin(uRoomA.w), c = cos(uRoomA.w);
    return vec3(d.x * s - d.z * c, d.y, d.x * c + d.z * s);
}
float roomRoofY(float z) { return uRoomB.w - abs(z) * uRoomC.x; }
bool roomInside(vec3 p, float m) {
    return abs(p.x) < uRoomB.x - m && abs(p.z) < uRoomB.y - m && p.y > -0.3 && p.y < roomRoofY(p.z) - m;
}
vec4 roomExit(vec3 p, vec3 d) {
    float best = 1e9, face = -1.0;
    if (d.x > 1e-6) { float t = (uRoomB.x - p.x) / d.x; if (t > -1e-3 && t < best) { best = t; face = 0.0; } }
    if (d.x < -1e-6) { float t = (-uRoomB.x - p.x) / d.x; if (t > -1e-3 && t < best) { best = t; face = 1.0; } }
    if (d.z > 1e-6) { float t = (uRoomB.y - p.z) / d.z; if (t > -1e-3 && t < best && p.y + d.y * t <= uRoomB.z) { best = t; face = 2.0; } }
    if (d.z < -1e-6) { float t = (-uRoomB.y - p.z) / d.z; if (t > -1e-3 && t < best && p.y + d.y * t <= uRoomB.z) { best = t; face = 3.0; } }
    for (int k = 0; k < 2; k++) {
        float s = k == 0 ? 1.0 : -1.0;
        float den = d.y + s * uRoomC.x * d.z;
        if (den > 1e-6) {
            float t = (uRoomB.w - p.y - s * uRoomC.x * p.z) / den;
            if (t > 0.0 && t < best && (p.z + d.z * t) * s >= -1e-4) { best = t; face = 4.0 + float(k); }
        }
    }
    if (d.y < -1e-6) { float t = -p.y / d.y; if (t < best) { best = t; face = 6.0; } }
    vec3 q = p + d * best;
    return vec4(face, face < 1.5 ? q.z : q.x, q.y, best);
}
float roomOpen(vec4 e) {
    int n = int(uRoomC.y + 0.5);
    for (int i = 0; i < 8; i++) {
        if (i >= n) break;
        vec4 a = uRoomO[i * 2], b = uRoomO[i * 2 + 1];
        if (abs(a.x - e.x) > 0.5 || e.z < b.x || e.z > b.y) continue;
        float c = 0.5 * (a.y + a.z), hw = 0.5 * (a.z - a.y);
        if (a.w > 0.5) hw *= (b.y - e.z) / max(1e-3, b.y - b.x);
        if (abs(e.y - c) <= hw) return 1.0;
    }
    return 0.0;
}
float roomTau(vec3 p, vec3 q) {
    float L = length(q - p);
    if (L < 1e-4) return 0.0;
    float y0 = min(p.y, q.y), y1 = max(p.y, q.y), zi = uSmokeA.x, w = uSmokeA.w;
    float frac;
    if (y1 - y0 < 1e-3) frac = smoothstep(zi - w, zi + w, y0);
    else {
        float a = clamp((y1 - (zi - w)) / (y1 - y0), 0.0, 1.0), b = clamp((y1 - (zi + w)) / (y1 - y0), 0.0, 1.0);
        frac = 0.5 * (a + b);
    }
    return L * (uSmokeA.z + (uSmokeA.y - uSmokeA.z) * frac);
}

float hg(float c, float g) { return (1.0 - g * g) / (12.566 * pow(1.0 + g * g - 2.0 * g * c, 1.5)); }

float plumeFlow(float qc, float z) {
    z = max(0.05, z);
    float z53 = pow(z, 5.0 / 3.0);
    return 0.071 * pow(qc, 1.0 / 3.0) * z53 * (1.0 + 0.026 * pow(qc, 2.0 / 3.0) / z53);
}

float density(vec3 p, out float n) {
    float zi = uSmokeA.x, w = uSmokeA.w;
    // Rauschen: langsam steigend und quer treibend; in der Schicht wogt die Unterkante
    vec3 q = p * vec3(0.32, 0.55, 0.32) + vec3(0.013, -0.045, 0.021) * uTime;
    n = texture(uNoise, q).r * 0.65 + texture(uNoise, q * 2.7 + 0.37).r * 0.35;
    float ziN = zi + (n - 0.5) * 0.5;
    float t = smoothstep(ziN - w, ziN + w, p.y);
    float s = (uSmokeA.z + (uSmokeA.y - uSmokeA.z) * t) * (0.45 + 1.1 * n);
    float zz = p.y - uSmokeB.y;
    if (uSmokeB.w > 0.05 && zz > 0.0 && p.y < zi + w) {
        float b = 0.12 * zz + 0.15;
        vec2 dxz = p.xz - uSmokeB.xz;
        float sp = min(3.0, uPlumeC / max(1e-3, plumeFlow(uSmokeB.w, zz)));
        s += sp * exp(-dot(dxz, dxz) / (b * b)) * (0.5 + n);
    }
    return s * uStrength;
}

float depthAt(ivec2 q) {
    float d = texelFetch(uSceneDepth, q, 0).r;
    if (uReversed == 1) return d <= 0.0 ? 1e9 : uNear / d;
    float zn = d * 2.0 - 1.0;
    return 2.0 * uNear * uFar / (uFar + uNear - zn * (uFar - uNear));
}

// Seit Phase 11b in halber Auflösung: je Pixel die nächste der vier Tiefen darunter (kein Rauch über nahen Kanten)
float sceneDist(vec3 rd) {
    if (uHasDepth == 0) return 1e9;
    ivec2 q = ivec2(gl_FragCoord.xy * uDepthScale);
    ivec2 mx = textureSize(uSceneDepth, 0) - 1;
    float z = depthAt(min(q, mx));
    if (uDepthScale > 1.5) {
        z = min(z, depthAt(min(q + ivec2(1, 0), mx)));
        z = min(z, depthAt(min(q + ivec2(0, 1), mx)));
        z = min(z, depthAt(min(q + ivec2(1, 1), mx)));
    }
    return z / max(1e-3, dot(rd, uForward));
}

void main() {
    vec2 ndc = gl_FragCoord.xy / uSize * 2.0 - 1.0;
    vec4 a = uInvVP * vec4(ndc, 0.5, 1.0);
    vec3 rd = normalize(a.xyz / a.w - uEye);
    float tScene = sceneDist(rd);
    // Strahl im Raum: Schnitt mit dem Kasten um die Hülle
    vec3 ro = roomLocal(uEye), dl = roomDir(rd);
    vec3 lo = vec3(-uRoomB.x, 0.0, -uRoomB.y), hi = vec3(uRoomB.x, uRoomB.w, uRoomB.y);
    vec3 inv = 1.0 / (dl + vec3(1e-7));
    vec3 t0 = (lo - ro) * inv, t1 = (hi - ro) * inv;
    vec3 tmin = min(t0, t1), tmax = max(t0, t1);
    float ta = max(max(tmin.x, tmin.y), max(tmin.z, 0.03)), tb = min(min(tmax.x, tmax.y), tmax.z);
    tb = min(tb, tScene);
    if (tb <= ta) { frag = vec4(0.0, 0.0, 0.0, 1.0); return; }
    int N = clamp(int((tb - ta) / 0.2), 10, uSteps);
    float ds = (tb - ta) / float(N);
    // versetzter Anfang je Pixel (gegen Treppen)
    float jit = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    vec3 sl = roomDir(uSunDir), ml = roomDir(uMoonDir);
    float sunUp = smoothstep(-0.02, 0.04, uSunDir.y), moonUp = smoothstep(-0.02, 0.04, uMoonDir.y);
    float phS = hg(dot(dl, sl), 0.55), phM = hg(dot(dl, ml), 0.55);
    vec3 L = vec3(0.0);
    float T = 1.0;
    for (int i = 0; i < 64; i++) {
        if (i >= N) break;
        float t = ta + (float(i) + jit) * ds;
        vec3 p = ro + dl * t;
        if (!roomInside(p, 0.0)) continue;
        float n;
        float sig = density(p, n);
        if (sig < 1e-4) continue;
        vec3 src = vec3(0.0);
        if (sunUp > 0.0) {
            vec4 e = roomExit(p, sl);
            if (roomOpen(e) > 0.5) src += uSunColor * sunUp * phS * exp(-roomTau(p, p + sl * e.w));
        }
        if (moonUp > 0.0) {
            vec4 e = roomExit(p, ml);
            if (roomOpen(e) > 0.5) src += uMoonColor * moonUp * phM * exp(-roomTau(p, p + ml * e.w));
        }
        for (int k = 0; k < 4; k++) {
            if (k >= uLightN) break;
            vec3 lp = roomLocal(uLightPos[k].xyz), d = lp - p;
            float d2 = dot(d, d) + uLightPos[k].w * uLightPos[k].w;
            src += uLightRgb[k].rgb / d2 * hg(dot(dl, d * inversesqrt(max(d2, 1e-4))), 0.3) * exp(-roomTau(p, lp));
        }
        // das gleichmäßige Licht kommt vor allem von unten (Lichtfleck, Wände, Herd): oben in der Schicht ist es gedämpft
        src = uAmbient * exp(-roomTau(p, vec3(p.x, 0.4, p.z))) + 3.14159 * src;
        float st = exp(-sig * ds);
        // Streuung über das Stück integriert (Energie bleibt bei großen Schritten erhalten)
        L += T * src * uAlbedo * (1.0 - st);
        T *= st;
        if (T < 0.01) break;
    }
    frag = vec4(L, T);
}
