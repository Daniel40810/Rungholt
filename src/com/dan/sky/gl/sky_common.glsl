// sky_common.glsl — die Streuung der Atmosphäre auf der Grafikkarte, Zeile für Zeile wie Scattering.java
// (Hillaire 2020): Geometrie, Tabellenkoordinaten, Phasenfunktionen, Durchlässigkeit und Mehrfachstreuung aus den
// Tabellen, Strahlengang. Raum: lokal am Auge, y oben; in der Szene x Ost, z Süd. Längen in m.
const float PI = 3.14159265359;
const float R_BOTTOM = 6360000.0, R_TOP = 6460000.0;
const vec3 RAYLEIGH = vec3(5.802e-6, 13.558e-6, 33.1e-6);
const float RAYLEIGH_H = 8000.0, MIE_H = 1200.0;
const vec3 OZONE = vec3(0.650e-6, 1.881e-6, 0.085e-6);
const float OZONE_CENTER = 25000.0, OZONE_HALF = 15000.0;
const int TRANS_W = 256, TRANS_H = 64, MS_N = 32;

uniform vec4 uAtm;            // Mie-Streuung, Mie-Auslöschung, Bodenalbedo, g
uniform sampler2D uTrans;     // Durchlässigkeit (RGBA32F)
uniform sampler2D uMs;        // Mehrfachstreuung Ψ (RGBA32F)

float rayleighDensity(float h) { return exp(-max(h, 0.0) / RAYLEIGH_H); }
float mieDensity(float h) { return exp(-max(h, 0.0) / MIE_H); }
float ozoneDensity(float h) { return max(0.0, 1.0 - abs(h - OZONE_CENTER) / OZONE_HALF); }
vec3 extinction(float h) { return RAYLEIGH * rayleighDensity(h) + uAtm.y * mieDensity(h) + OZONE * ozoneDensity(h); }

float distToTop(float r, float mu) {
    float disc = (R_TOP - r) * (R_TOP + r) + r * r * mu * mu;
    return max(0.0, -r * mu + sqrt(max(0.0, disc)));
}
float groundDisc(float r, float mu) { return (R_BOTTOM - r) * (R_BOTTOM + r) + r * r * mu * mu; }
bool hitsGround(float r, float mu) { return mu < 0.0 && groundDisc(r, mu) >= 0.0; }
float distToGround(float r, float mu) { return max(0.0, -r * mu - sqrt(max(0.0, groundDisc(r, mu)))); }

const float H_ATM = 1132254.4;  // sqrt(R_TOP² − R_BOTTOM²)

vec2 transUv(float r, float mu) {
    float rho = sqrt(max(0.0, r * r - R_BOTTOM * R_BOTTOM));
    float d = distToTop(r, mu), dMin = R_TOP - r, dMax = rho + H_ATM;
    return vec2((d - dMin) / max(1e-6, dMax - dMin), rho / H_ATM);
}
vec2 transRMu(vec2 x) {
    float rho = H_ATM * x.y, r = sqrt(rho * rho + R_BOTTOM * R_BOTTOM);
    float dMin = R_TOP - r, dMax = rho + H_ATM, d = dMin + x.x * (dMax - dMin);
    float mu = d <= 0.0 ? 1.0 : (H_ATM * H_ATM - rho * rho - d * d) / (2.0 * r * d);
    return vec2(r, clamp(mu, -1.0, 1.0));
}
// Stützstellen bei i/(n−1): Textur-Koordinate (x·(n−1)+0,5)/n
vec2 lutCoord(vec2 x, vec2 n) { return (clamp(x, 0.0, 1.0) * (n - 1.0) + 0.5) / n; }

vec3 transmittance(float r, float mu) {
    return texture(uTrans, lutCoord(transUv(r, mu), vec2(TRANS_W, TRANS_H))).rgb;
}
vec3 multiScattering(float r, float muS) {
    return texture(uMs, lutCoord(vec2(muS * 0.5 + 0.5, (r - R_BOTTOM) / (R_TOP - R_BOTTOM)), vec2(MS_N))).rgb;
}

float phaseRayleigh(float c) { return 3.0 / (16.0 * PI) * (1.0 + c * c); }
float phaseMie(float c, float g) {
    float g2 = g * g;
    return 3.0 / (8.0 * PI) * (1.0 - g2) * (1.0 + c * c) / ((2.0 + g2) * pow(max(1e-6, 1.0 + g2 - 2.0 * g * c), 1.5));
}

void addLight(inout vec3 S, vec3 up, float rr, vec3 l, vec3 e, float pR, float pM, float dR, float scM, bool physical) {
    float muL = dot(up, l);
    vec3 tl = transmittance(rr, muL);
    float shadow = hitsGround(rr, muL) ? 0.0 : 1.0;
    vec3 scR = RAYLEIGH * dR;
    vec3 s = shadow * tl * (scR * pR + scM * pM);
    if (physical) s += multiScattering(rr, muL) * (scR + scM);
    S += e * s;
}

// Strahlengang von Höhe r in Richtung v mit zwei Lichtquellen (e2 = 0 für eine). Ausgabe wie Scattering.integrate.
void integrate(float r, vec3 v, vec3 l1, vec3 e1, vec3 l2, vec3 e2, int steps, bool quadratic, bool physical,
               out vec3 L, out vec3 f, out vec3 tp) {
    float mu = v.y;
    bool ground = hitsGround(r, mu);
    float tMax = ground ? distToGround(r, mu) : distToTop(r, mu);
    L = vec3(0.0); f = vec3(0.0); tp = vec3(1.0);
    float g = uAtm.w, iso = 1.0 / (4.0 * PI);
    float c1 = dot(v, l1), c2 = dot(v, l2);
    float pR1 = physical ? phaseRayleigh(c1) : iso, pM1 = physical ? phaseMie(c1, g) : iso;
    float pR2 = physical ? phaseRayleigh(c2) : iso, pM2 = physical ? phaseMie(c2, g) : iso;
    bool two = dot(e2, e2) > 0.0;
    for (int k = 0; k < steps; k++) {
        float a = float(k) / float(steps), b = float(k + 1) / float(steps);
        float t0 = quadratic ? tMax * a * a : tMax * a, t1 = quadratic ? tMax * b * b : tMax * b;
        float tm = 0.5 * (t0 + t1), dt = t1 - t0;
        vec3 p = vec3(0.0, r, 0.0) + v * tm;
        float rr = length(p), h = rr - R_BOTTOM;
        vec3 up = p / rr;
        float dR = rayleighDensity(h), dM = mieDensity(h);
        float scM = uAtm.x * dM;
        vec3 S = vec3(0.0);
        addLight(S, up, rr, l1, e1, pR1, pM1, dR, scM, physical);
        if (two) addLight(S, up, rr, l2, e2, pR2, pM2, dR, scM, physical);
        vec3 ext = max(extinction(h), vec3(1e-12));
        vec3 sT = exp(-ext * dt);
        L += tp * (S - S * sT) / ext;
        vec3 sc = RAYLEIGH * dR + scM;
        f += tp * (sc - sc * sT) / ext;
        tp *= sT;
    }
    if (ground) {
        vec3 p = vec3(0.0, r, 0.0) + v * tMax;
        vec3 up = normalize(p);
        float m1 = dot(up, l1), m2 = dot(up, l2);
        if (m1 > 0.0) L += tp * e1 * transmittance(R_BOTTOM, m1) * m1 * uAtm.z / PI;
        if (two && m2 > 0.0) L += tp * e2 * transmittance(R_BOTTOM, m2) * m2 * uAtm.z / PI;
    }
}

// Strahlenbrechung in Grad: für die wahre Höhe (Sæmundsson) und für die scheinbare (Bennett), wie Celestial.java.
float refractionTrue(float hDeg) { float h = max(-2.0, hDeg); return 1.02 / tan(radians(h + 10.3 / (h + 5.11))) / 60.0; }
float refractionApparent(float hDeg) { float h = max(-1.5, hDeg); return 1.0 / tan(radians(h + 7.31 / (h + 4.4))) / 60.0; }

// Richtung aus Höhe und Azimut (Bogenmaß, Azimut von Nord über Ost), Szene x Ost, y oben, z Süd.
vec3 dirFromAltAz(float alt, float az) { return vec3(cos(alt) * sin(az), sin(alt), -cos(alt) * cos(az)); }
float azimuthOf(vec3 d) { return atan(d.x, -d.z); }
