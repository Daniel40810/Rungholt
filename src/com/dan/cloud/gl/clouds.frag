// clouds.frag — Raymarching durch die Wolkenschicht in halber (oder viertel) Auflösung. Je Schritt die Dichte; wo
// Wolke ist, ein kurzer Weg zum Licht (6 wachsende Schritte), Streuung mit zwei Keulen (vorwärts: Silberrand,
// rückwärts: helle Seite), Mehrfachstreuung in vier Oktaven (Wrenninge), Pulvereffekt an den Rändern, Himmelslicht
// von oben und Meerlicht von unten. Danach die Linsen der Lentikularis und die hohe Schicht als dünne Flächen, zum
// Schluss Dunst nach der Entfernung.
// Ausgabe 0: eingestreutes Licht (vorbelichtet) und Durchlässigkeit; Ausgabe 1: mittlere Entfernung (für die
// Wiederverwendung im nächsten Bild). Mit PANORAMA: Rundumbild (Azimut von Nord, Höhe), ohne Zittern.
#include "cloud_common.glsl"
in vec2 vUV;
layout(location = 0) out vec4 outColor;
layout(location = 1) out float outDist;
uniform mat4 uInvVP;
uniform int uReversed;
uniform vec3 uCam;
uniform vec3 uLightDir;     // zum Gestirn (Sonne oder Mond)
uniform vec3 uAmbTop, uAmbBot;
uniform float uHaze;        // Auslöschung des Dunsts je m
uniform vec2 uSteps;        // höchste Schrittzahl, Schrittweite nah am Auge in m (wächst mit der Entfernung)
uniform int uLightSteps;
uniform float uMaxDist;
uniform int uFrame;
uniform vec4 uFlash;        // Ort relativ zur Szene, Stärke
uniform vec2 uTarget;       // Größe des Ziels in Bildpunkten
const float STEP_GROW = 5000.0;
const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);
uniform vec4 uDeck;        // Farbe der fernen geschlossenen Decke, Anteil 0..1 (ab 4/8 wachsend)
uniform int uFlat;          // 1 = Stufe „Niedrig“: die untere Schicht als dünne Fläche (2.5D)

vec3 viewDir(vec2 uv) {
#ifdef PANORAMA
    float az = (uv.x - 0.5) * 2.0 * PI, el = (uv.y - 0.5) * PI;
    return vec3(sin(az) * cos(el), sin(el), -cos(az) * cos(el));
#else
    vec2 ndc = uv * 2.0 - 1.0;
    float zn = uReversed == 1 ? 1.0 : -1.0, zm = uReversed == 1 ? 0.5 : 0.0;
    vec4 a = uInvVP * vec4(ndc, zn, 1.0);
    vec4 b = uInvVP * vec4(ndc, zm, 1.0);
    return normalize(b.xyz / b.w - a.xyz / a.w);
#endif
}

float ign(vec2 px) {
    px += float(uFrame % 64) * 5.588238;
    return fract(52.9829189 * fract(dot(px, vec2(0.06711056, 0.00583715))));
}

// Licht, das am Punkt p ankommt: optische Dicke zum Gestirn in wachsenden Schritten
float lightDepth(vec3 p) {
    float tau = 0.0, step = 40.0 * max(0.5, uType1.y / 900.0), t = 0.0, dummy;
    for (int i = 0; i < 8; i++) {
        if (i >= uLightSteps) break;
        float len = step * pow(2.0, float(i));
        t += len * 0.5;
        tau += density(p + uLightDir * t, false, dummy) * len;
        t += len * 0.5;
    }
    return tau;
}

void main() {
    vec3 d = viewDir(vUV);
    vec3 L = vec3(0.0);
    float T = 1.0, distSum = 0.0, distW = 0.0;
    float c = dot(d, uLightDir);
    if (uFlat == 1) {
        // 2.5D: eine Fläche in mittlerer Höhe; die Dichte dort (Grundform und Fransen) gibt die Dicke
        float hm = uType1.x + uType1.y * 0.45;
        vec2 fs = shell(uCam, d, hm - 1.0, hm + 1.0);
        if (fs.x >= 0.0 && fs.x < uMaxDist) {
            vec3 p = uCam + d * fs.x;
            float tt;
            float s = density(p, true, tt);
            float slant = 1.0 / max(0.08, abs(dot(normalize(vec3(p.x, p.y + EARTH_R, p.z)), d)));
            float tau = s * uType1.y * 0.6 * slant;
            if (tau > 0.0) {
                float th = exp(-tau);
                float inside = exp(-s * uType1.y * 0.3);
                vec3 Lf = (sunAt(hm) * (mix(hg(c, 0.8), hg(c, -0.25), 0.3) * (0.4 + 0.6 * inside) + 0.05 * inside) + mix(uAmbBot, uAmbTop, 0.5)) * (1.0 - th);
                L = Lf;
                T = th;
                distSum = fs.x;
                distW = 1.0;
            }
        }
    }
    vec2 seg = shell(uCam, d, uType1.x, uType1.x + uType1.z);
    if (uFlat == 0 && seg.x >= 0.0 && seg.x < uMaxDist) {
        float t0 = seg.x, t1 = min(seg.y, uMaxDist);
        // Schrittweite wächst mit der Entfernung (ferne Wolken sind klein im Bild); leere Luft mit größeren Schritten
#ifdef PANORAMA
        float j = 0.5;
#else
        float j = ign(gl_FragCoord.xy);
#endif
        float t = t0 + j * uSteps.y * (1.0 + t0 / STEP_GROW);
        for (int k = 0; k < 128; k++) {
            if (float(k) >= uSteps.x || t > t1 || T < 0.01) break;
            float dt = uSteps.y * (1.0 + t / STEP_GROW);
            vec3 p = uCam + d * t;
            float tt;
            float s = density(p, true, tt);
            if (s <= 0.0) { t += dt * 1.7; continue; }
            float tauL = lightDepth(p);
            float h = heightOf(p);
            // Mehrfachstreuung (Wrenninge 2015): vier Oktaven mit schwächerer Auslöschung und weicherer Keule. Ab der
            // zweiten ist das Licht durchmischt: halb Farbe der Sonne, halb Farbe des Himmels (sonst wird eine dicke
            // Schicht bei tiefer Sonne bräunlich statt grau)
            float ms0 = 0.0, ms = 0.0, a = 1.0, bb = 1.0, cc = 1.0;
            for (int o = 0; o < 4; o++) {
                float m = a * mix(hg(c, 0.8 * cc), hg(c, -0.25 * cc), 0.3) * exp(-tauL * bb);
                if (o == 0) ms0 = m; else ms += m;
                a *= 0.75; bb *= 0.3; cc *= 0.6;
            }
            vec3 sunC = sunAt(h);
            float ls = max(1e-9, dot(sunC, LUMA));
            vec3 diffC = ls * mix(sunC / ls, uAmbTop / max(1e-9, dot(uAmbTop, LUMA)), 0.5);
            // Pulvereffekt: dünne Ränder, die vom Licht wegzeigen, bleiben dunkel
            float powder = mix(1.0, 1.0 - exp(-2.4 * s / uType2.x), 0.5 - 0.5 * c);
            vec3 amb = mix(uAmbBot, uAmbTop, clamp(tt, 0.0, 1.0)) * (0.35 + 0.65 * clamp(tt, 0.0, 1.0));
            vec3 S = s * ((sunC * ms0 + diffC * ms) * powder + amb);
            if (uFlash.w > 0.0) {
                float df = length(p - uFlash.xyz);
                S += s * vec3(0.9, 0.95, 1.1) * uFlash.w / (1.0 + df * df / 250000.0);
            }
            float st = exp(-s * dt);
            L += T * (S - S * st) / s;
            float dT = T * (1.0 - st);
            distSum += dT * t;
            distW += dT;
            T *= st;
            t += dt;
        }
    }
    float dist = distW > 1e-4 ? distSum / distW : uMaxDist;
    // geschlossene Decke jenseits der größten Entfernung (bis zur Kimm)
    if (uFlat == 0 && uDeck.w > 0.0 && d.y > -0.005 && seg.x >= 0.0 && seg.x < 400000.0 && seg.y > uMaxDist && T > 0.01) {
        L += T * uDeck.w * uDeck.rgb;
        if (distW <= 1e-4) dist = uMaxDist;
        T *= 1.0 - uDeck.w;
    }
    // Lentikularis (seit Glacier Phase 3): bis zu drei glatte Linsenlagen über 4,6 km, von unten nach oben
    if (uLens.x > 0.0 && T > 0.01) {
        for (int i = 0; i < LENS_SLICES; i++) {
            float hl = LENS_HEIGHT + float(i) * LENS_STEP;
            vec2 ls = shell(uCam, d, hl - 1.0, hl + 1.0);
            if (ls.x < 0.0 || ls.x > uMaxDist * 2.0) continue;
            vec3 p = uCam + d * ls.x;
            float slant = 1.0 / max(0.08, abs(dot(normalize(vec3(p.x, p.y + EARTH_R, p.z)), d)));
            float tau = lensTau(p.xz, i) * slant;
            if (tau <= 0.0) continue;
            float th = exp(-tau);
            // glatt und hell; die Seite vom Licht weg liegt im Eigenschatten der Linse
            float self = exp(-0.8 * lensTau(p.xz + uLightDir.xz / max(0.15, uLightDir.y) * 150.0, i));
            vec3 Ll = (sunAt(hl) * (hg(c, 0.6) * 0.6 + 0.12) * (0.35 + 0.65 * self) + uAmbTop * 0.7 + uAmbBot * 0.1) * (1.0 - th);
            float fl = exp(-hazeDepth(uHaze, uCam.y, d, ls.x));
            L += T * Ll * fl;
            T *= mix(1.0, th, fl);
            if (distW <= 1e-4) dist = ls.x;
        }
    }
    // hohe Schicht: dünne Fläche in 6,5 km, hinter der unteren
    if (uOff.z > 0.0 && T > 0.01) {
        vec2 hs = shell(uCam, d, HIGH_HEIGHT - 1.0, HIGH_HEIGHT + 1.0);
        if (hs.x >= 0.0 && hs.x < uMaxDist * 2.0) {
            vec3 p = uCam + d * hs.x;
            float slant = 1.0 / max(0.08, abs(dot(normalize(vec3(p.x, p.y + EARTH_R, p.z)), d)));
            float tau = highTau(p.xz) * slant;
            if (tau > 0.0) {
                float th = exp(-tau);
                vec3 Lh = (sunAt(HIGH_HEIGHT) * (hg(c, 0.6) * 0.7 + 0.05) + uAmbTop * 0.6) * (1.0 - th);
                float fh = exp(-hazeDepth(uHaze, uCam.y, d, hs.x));
                L += T * Lh * fh;
                T *= mix(1.0, th, fh);
                if (distW <= 1e-4) dist = hs.x;
            }
        }
    }
    // Dunst zwischen Auge und Wolke: die Wolke verblasst zur Kimm (dort verschwindet sie im Himmel dahinter)
    // Bei geschlossener Decke bleibt sie im Dunst als graue Fläche stehen (der Himmel dahinter ist der klare)
    float fog = exp(-hazeDepth(uHaze, uCam.y, d, dist)), keep = mix(fog, 1.0, uDeck.w);
    L = L * fog + (1.0 - T) * (keep - fog) * uDeck.rgb;
    T = 1.0 - (1.0 - T) * keep;
    outColor = vec4(L, T);
    outDist = dist;
}
