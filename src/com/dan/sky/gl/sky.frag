// sky.frag — der Himmel je Bildpunkt: Streulicht aus der Himmelstabelle (Sonne, Mond, Nachthimmel, Lichtglocken,
// Schleier), Milchstraße, Regenbogen, Blitzleuchten und – nur im Hauptbild – Sonnen- und Mondscheibe. Die Scheiben
// werden mit der Strahlenbrechung gezeichnet (an der Kimm abgeplattet), der Mond mit Phase, Libration, Oberfläche
// aus FMoon und Erdschein. Mit PANORAMA: dasselbe als Rundumbild (Azimut von Nord, Höhe) für die Spiegelungen.
// Ausgabe: HDR, linear, mal Vorbelichtung uPre.
#include "sky_common.glsl"
#include "sky_view.glsl"
in vec2 vUV;
out vec4 frag;
uniform sampler2D uSkyView, uMilky, uMoonTex;
uniform mat4 uInvVP;
uniform int uReversed;
uniform float uPre, uEyeR, uNight, uMilkyGain, uVeil, uRainbow, uPixelAngle;
uniform float uSea;                   // > 0: unter der Kimm eine schlichte Wasserfläche (für Szenen ohne Meer)
uniform mat3 uSceneToEq;              // Szene → Äquator J2000
uniform vec3 uSunApp, uSunTrue, uSunE, uSunEye;
uniform float uSunRadius;
uniform vec3 uMoonTrue, uMoonX, uMoonY, uMoonLight, uMoonK, uEarthshine;
uniform float uMoonRadius, uMoonPeak;
uniform vec2 uLibration;              // Grad
uniform vec4 uFlash;                  // Richtung, Stärke

vec3 viewRay(vec2 uv) {
    vec2 ndc = uv * 2.0 - 1.0;
    float zn = uReversed == 1 ? 1.0 : -1.0, zm = uReversed == 1 ? 0.5 : 0.0;
    vec4 a = uInvVP * vec4(ndc, zn, 1.0);
    vec4 b = uInvVP * vec4(ndc, zm, 1.0);
    return normalize(b.xyz / b.w - a.xyz / a.w);
}

float gss(float x, float m, float sd) { float d = (x - m) / sd; return exp(-0.5 * d * d); }

// Regenbogen um den Gegenpunkt der Sonne: Hauptbogen 40,8–42,2° (rot außen), Nebenbogen 50,5–52,8° umgekehrt,
// innen heller, dazwischen Alexanders dunkles Band (Werte wie Geyser).
vec3 rainbow(vec3 d) {
    float th = degrees(acos(clamp(dot(d, -uSunApp), -1.0, 1.0)));
    vec3 bands = vec3(gss(th, 42.2, 0.55) + 0.43 * gss(th, 50.5, 0.7),
                      gss(th, 41.5, 0.55) + 0.43 * gss(th, 51.6, 0.7),
                      gss(th, 40.8, 0.55) + 0.43 * gss(th, 52.8, 0.7));
    float inside = th < 40.5 ? 0.35 * smoothstep(30.0, 40.5, th) * 0.4 + 0.05 : 0.0;
    float band = (th > 42.6 && th < 50.0) ? -0.04 : 0.0;
    float sun = smoothstep(-0.02, 0.06, uSunApp.y);
    return uRainbow * sun * smoothstep(0.0, 0.03, d.y) * uSunEye / PI * 0.05 * (1.6 * bands + inside + band);
}

vec3 milkyWay(vec3 d) {
    vec3 e = uSceneToEq * d;
    float ra = atan(e.y, e.x) / (2.0 * PI);
    float dec = asin(clamp(e.z, -1.0, 1.0));
    float m = texture(uMilky, vec2(fract(ra), 0.5 - dec / PI)).r;
    return m * m * 3.5 * uNight * vec3(1.0, 0.96, 0.9) * transmittance(uEyeR, max(d.y, 0.0)) * uMilkyGain;
}

vec3 flashGlow(vec3 d) {
    float c = dot(d, uFlash.xyz);
    return uFlash.w * 2e-4 * vec3(0.92, 0.96, 1.08) * (0.25 + exp((c - 1.0) * 12.0));
}

// Richtung mit der Brechung zurückgerechnet: wo der Strahl ohne Luft hinzeigen würde
vec3 trueDir(vec3 d) {
    float alt = degrees(asin(clamp(d.y, -1.0, 1.0)));
    float t = alt - refractionApparent(alt);
    return dirFromAltAz(radians(t), azimuthOf(d));
}

#ifndef PANORAMA
vec3 sunDisc(vec3 d, vec3 dt, vec3 tview) {
    float ang = acos(clamp(dot(dt, uSunTrue), -1.0, 1.0));
    if (ang > uSunRadius + 2.0 * uPixelAngle) return vec3(0.0);
    float x = clamp(ang / uSunRadius, 0.0, 1.0);
    float mu = sqrt(max(0.0, 1.0 - x * x));
    float limb = (1.0 - 0.6 * (1.0 - mu)) / 0.8;
    vec3 center = uSunE / (PI * uSunRadius * uSunRadius) * tview;
    vec3 L = center * limb;
    // Anzeige: die Scheibe nicht über das Achtfache der Weißgrenze heben, damit sie tief am Himmel orange bleibt
    float m = max(center.r, max(center.g, center.b)) * uPre;
    L *= min(1.0, 8.0 / max(m, 1e-9));
    float edge = smoothstep(uSunRadius + uPixelAngle, uSunRadius - uPixelAngle, ang);
    return L * edge;
}

vec4 moonDisc(vec3 dt, vec3 tview) {
    float s = sin(uMoonRadius);
    vec2 q = vec2(dot(dt, uMoonX), dot(dt, uMoonY)) / s;
    float r = length(q);
    if (dot(dt, uMoonTrue) < 0.0 || r > 1.0 + 2.0 * uPixelAngle / uMoonRadius) return vec4(0.0);
    vec2 qc = r > 0.999 ? q * (0.999 / r) : q;
    float nz = sqrt(max(0.0, 1.0 - dot(qc, qc)));
    vec3 n = vec3(qc, nz);
    float cosI = dot(n, uMoonLight);
    float lon = degrees(atan(qc.x, nz)) + uLibration.x, lat = degrees(asin(clamp(qc.y, -1.0, 1.0))) + uLibration.y;
    float alb = texture(uMoonTex, vec2(fract((lon + 180.0) / 360.0), clamp((lat + 90.0) / 180.0, 0.0, 1.0))).r;
    float shade = cosI > 0.0 ? 0.86 * cosI / (cosI + max(1e-4, nz)) * 2.0 + 0.14 * cosI : 0.0;
    vec3 L = (uMoonK * shade + uEarthshine) * alb * tview;
    float peak = uMoonPeak * max(tview.g, 1e-9) * uPre;
    L *= min(1.0, 6.0 / max(peak, 1e-9));
    float a = smoothstep(1.0 + uPixelAngle / uMoonRadius, 1.0 - uPixelAngle / uMoonRadius, r);
    return vec4(L, a);
}
#endif

void main() {
#ifdef PANORAMA
    float az = (vUV.x - 0.5) * 2.0 * PI;
    float el = (vUV.y - 0.5) * PI;
    vec3 d = vec3(sin(az) * cos(el), sin(el), -cos(az) * cos(el));
#else
    vec3 d = viewRay(vUV);
#endif
    vec3 col = texture(uSkyView, skyViewUv(d, uEyeR)).rgb;
    if (uSea > 0.0 && d.y < 0.0) {
        // glattes Wasser: Fresnel-Spiegelung des Himmels und etwas Licht aus der Tiefe
        vec3 r = vec3(d.x, -d.y, d.z);
        float c = clamp(-d.y, 0.0, 1.0), F = 0.02 + 0.98 * pow(1.0 - c, 5.0);
        vec3 up = texture(uSkyView, skyViewUv(vec3(0.0, 1.0, 0.0), uEyeR)).rgb;
        col = texture(uSkyView, skyViewUv(r, uEyeR)).rgb * F + up * vec3(0.02, 0.06, 0.055) * (1.0 - F);
    }
    float open = 1.0 - uVeil;
    bool ground = hitsGround(uEyeR, d.y);
    if (!ground) {
        col += milkyWay(d) * open;
        col += rainbow(d) * (1.0 - 0.7 * uVeil);
    }
    col += flashGlow(d);
#ifndef PANORAMA
    if (!ground) {
        vec3 tview = transmittance(uEyeR, d.y) * open;
        vec3 dt = trueDir(d);
        col += sunDisc(d, dt, tview);
        vec4 m = moonDisc(dt, tview);
        col += m.rgb * m.a;
    }
#endif
    frag = vec4(col * uPre, 1.0);
}
