// surface.frag — Optik der See: Normale aus den Ableitungen aller Kaskaden und der Dünung, Fresnel mit Spiegelung des
// Himmels (Panorama-Textur), Streulicht im Wasser in Nordsee-Grün, Licht im Wellenkamm (durchscheinend gegen die
// Sonne), Sonnen- und Mondglitzer, Schaum aus Überschlag (mit Nachleuchten), Schaumstraßen ab Bft 7, Brandungsringe
// um Hindernisse, Ringwellen nach Einschlägen, Dunst zur Kimm, Wolkenschatten, Schatten eines Körpers und eine feine Störung wie Regenringe (alle
// optional). Ausgabe: HDR-Farbe, linear.
#include "ocean_common.glsl"
in vec2 vRest;
in vec3 vWorld;
in float vHeight;
in float vDist;
in float vDepth;
uniform vec3 uBedColor;
out vec4 frag;
uniform sampler2D uSky;        // Himmel als Panorama (Azimut von Nord, Höhe), linear
uniform float uSkyGain;
uniform vec3 uSunDir, uSunColor, uMoonDir, uMoonColor;
uniform vec3 uWaterColor, uSssColor;
uniform float uHs;
uniform float uFogDist;
uniform float uWindTo;
uniform float uWindSpeed;
uniform vec4 uObst[8];         // x, z, Radius
uniform int uObstCount;
uniform sampler2D uObstField;    // seit Haithabu Phase 8: Abstand zum nächsten Körper (m), viele Pfähle
uniform vec4 uObstFieldRect;     // Ecke x, z, Breite, Tiefe (m)
uniform int uObstFieldOn;
uniform vec2 uRingHs;            // Brandungsring: Beginn und volle Stärke über H_s (m)
uniform vec4 uHullA[8];          // seit Haithabu Phase 9: Rumpf x, z, sin und cos des Kurses
uniform vec4 uHullB[8];          // halbe Länge, halbe Breite in der Wasserlinie (m), Völligkeit
uniform int uHullCount;
uniform vec4 uRipple[32];      // x, z, Alter, Stärke
uniform int uRippleCount;
uniform float uFoamGain;
uniform sampler2D uCloudShadow;  // Anteil direkten Lichts von oben (Wolken), optional
uniform vec4 uShadowRect;       // x, z der Ecke, Kantenlänge in m, an (1) / aus (0)
uniform sampler2D uBodyShadow;   // Schatten eines Körpers (Turm, Schiff) auf dem Wasser, optional
uniform vec4 uBodyRect;         // wie uShadowRect
uniform sampler2D uDetail;      // feine Störung (Regenringe): Steigung in RG, kachelbar, optional
uniform vec4 uDetailP;          // Kachel in m, Stärke, Reichweite in m, an (1) / aus (0)
uniform sampler2D uPointTex;    // Punktlicht: Lichtstärke cd je Richtung vom Licht (Rundumbild), optional
uniform vec4 uPointPos;         // Ort des Punktlichts, Faktor (0 = aus)
uniform float uPointSigma;      // Auslöschung der Luft auf dem Weg zum Wasser (1/m)
uniform int uFlashN;            // einfache Punktlichter (Blitze): Zahl
uniform vec4 uFlashPos[8];      // Ort, Größe der Quelle (m)
uniform vec4 uFlashRgb[8];      // Lichtstärke RGB in cd · Vorbelichtung
uniform sampler2D uBrash;       // seit Glacier Phase 9: Treibeis, Anteil der bedeckten Fläche 0..1, optional
uniform vec4 uBrashRect;        // Ecke x, z, Breite, Tiefe (m)
uniform int uBrashOn;
uniform sampler2D uMask;        // seit Glacier Phase 13: ein gestauter Seearm (1) als eigener Spiegel, optional
uniform vec3 uMaskRect;         // Ecke x, z, Kantenlänge (m)
uniform int uMaskMode;          // 1: nur innen zeichnen, −1: nur außen, 0: aus
uniform int uUnder;             // seit Rungholt Phase 6: das Auge liegt unter dem Spiegel

// Lichtstärke des Punktlichts in Richtung w (vom Licht weg), mal Faktor
vec3 pointIntensity(vec3 w) {
    float az = atan(w.x, -w.z) / 6.28318530718;
    float el = degrees(asin(clamp(w.y, -1.0, 1.0)));
    return textureLod(uPointTex, vec2(fract(az + 1.0), 0.5 + 0.5 * sign(el) * sqrt(abs(el) / 90.0)), 0.0).rgb * uPointPos.w;
}

vec3 sky(vec3 d) {
    float az = atan(d.x, -d.z) / 6.28318530718 + 0.5;
    float el = asin(clamp(d.y, -1.0, 1.0)) / 3.14159265359 + 0.5;
    return textureLod(uSky, vec2(az, el), 0.0).rgb * uSkyGain;
}

float smoothr(float a, float b, float x) { return smoothstep(a, b, x); }

// Ringwellen: Steigung (dh/dx, dh/dz) aller Einschläge, Formel wie Ripples.ring()
vec2 rippleSlope(vec2 p) {
    vec2 g = vec2(0.0);
    for (int i = 0; i < 32; i++) {
        if (i >= uRippleCount) break;
        vec4 r = uRipple[i];
        vec2 dv = p - r.xy;
        float rr = length(dv), tau = r.z;
        if (rr > 0.9 * tau + 1.0 || rr < 1e-4) continue;
        float front = rr - 0.9 * tau, w = 0.15 + 0.12 * tau;
        float env = exp(-front * front / (w * w)) * exp(-tau / 1.4) / (1.0 + rr * 1.5);
        float k = 6.28318530718 / 0.35;
        float dh = r.w * 0.06 * env * (-k * sin(k * front) - 2.0 * front / (w * w) * cos(k * front));
        g += dh * dv / rr;
    }
    return g;
}

void main() {
    // seit Haithabu: kein Wasser über dem Grund oder außerhalb seines Rechtecks
    if (vDepth < -0.02) discard;
    if (uMaskMode != 0) {
        vec2 mu = (vRest - uMaskRect.xy) / uMaskRect.z;
        float m = all(greaterThan(mu, vec2(0.0))) && all(lessThan(mu, vec2(1.0))) ? textureLod(uMask, mu, 0.0).r : 0.0;
        if (uMaskMode == 1 && m < 0.5 || uMaskMode == -1 && m >= 0.5) discard;
    }
    // Wolkenschatten (aus com.dan.cloud über die App): Anteil direkten Lichts an dieser Stelle
    float shade = 1.0;
    if (uShadowRect.w > 0.0) shade = textureLod(uCloudShadow, (vWorld.xz - uShadowRect.xy) / uShadowRect.z, 0.0).r;
    if (uBodyRect.w > 0.0) {
        vec2 bu = (vWorld.xz - uBodyRect.xy) / uBodyRect.z;
        if (all(greaterThan(bu, vec2(0.0))) && all(lessThan(bu, vec2(1.0)))) shade *= textureLod(uBodyShadow, bu, 0.0).r;
    }
    vec3 sunC = uSunColor * shade, moonC = uMoonColor * shade;
    // Hindernisse: innen kein Wasser, außen ein Brandungsring
    float ring = 0.0;
    for (int i = 0; i < 8; i++) {
        if (i >= uObstCount) break;
        float ds = length(vWorld.xz - uObst[i].xy) - uObst[i].z;
        if (ds < 0.0) discard;
        float width = 0.5 + 0.8 * uHs;
        float n = vnoise(vec2(vWorld.x * 0.8 + uTime * 0.35, vWorld.z * 0.8 - uTime * 0.2));
        ring = max(ring, smoothr(uRingHs.x, uRingHs.y, uHs) * exp(-ds / width) * (0.45 + 0.55 * n));
    }
    // Rümpfe: innen kein Wasser (offene Boote), außen ein schmaler Saum aus Schaum
    for (int i = 0; i < 8; i++) {
        if (i >= uHullCount) break;
        vec2 d = vWorld.xz - uHullA[i].xy;
        vec2 f = vec2(uHullA[i].z, -uHullA[i].w);
        float s = dot(d, f), q = d.x * -f.y + d.y * f.x;
        float u = s / uHullB[i].x;
        float ds;
        if (abs(u) >= 1.0) ds = length(vec2(abs(s) - uHullB[i].x, q));
        else ds = abs(q) - uHullB[i].y * pow(max(1.0 - pow(abs(u), uHullB[i].z), 0.0), 0.8);
        if (ds < 0.0) discard;
        float n = vnoise(vec2(vWorld.x * 1.3 + uTime * 0.5, vWorld.z * 1.3 - uTime * 0.3));
        ring = max(ring, 0.35 * exp(-ds / 0.35) * (0.4 + 0.6 * n));
    }
    if (uObstFieldOn == 1) {
        vec2 fu = (vWorld.xz - uObstFieldRect.xy) / uObstFieldRect.zw;
        if (all(greaterThan(fu, vec2(0.0))) && all(lessThan(fu, vec2(1.0)))) {
            float ds = textureLod(uObstField, fu, 0.0).r;
            float width = 0.5 + 0.8 * uHs;
            float n = vnoise(vec2(vWorld.x * 0.8 + uTime * 0.35, vWorld.z * 0.8 - uTime * 0.2));
            ring = max(ring, smoothr(uRingHs.x, uRingHs.y, uHs) * exp(-ds / width) * (0.45 + 0.55 * n));
        }
    }
    // Ableitungen und Schaum aller Kaskaden
    float sx = 0.0, sz = 0.0, jx = 0.0, jz = 0.0, keep = 1.0;
    for (int c = 0; c < 4; c++) {
        if (c >= uCascades) break;
        vec3 uv = vec3(vRest / uLength[c], float(c));
        float g = cascadeGain(vRest, c);
        vec4 dv = texture(uDeriv, uv) * g;
        sx += dv.x; sz += dv.y; jx += dv.z; jz += dv.w;
        keep *= 1.0 - clamp(texture(uDisp, uv).w * g, 0.0, 1.0);
    }
    vec3 swd, sws;
    swell(vRest, swd, sws);
    sws *= swellGain(vRest);
    sx += sws.x; sz += sws.y;
    vec2 rs = rippleSlope(vWorld.xz);
    // seit Glacier Phase 8: Neigung der langen Welle; an ihrer steilen Stirn bricht sie weiß
    float longFoam = 0.0;
    if (uLongR0.w > 0.0 || uLongR1.w > 0.0) {
        vec3 l0 = longWave(vRest);
        if (l0.z > 0.0 && l0.y > 0.0) {
            float e = uLongR0.w > 0.0 && longLevel(uLongH0, uLongR0, vRest).z > 0.0 ? uLongR0.z : uLongR1.z;
            vec2 ls = vec2(longWave(vRest + vec2(e, 0.0)).x - longWave(vRest - vec2(e, 0.0)).x,
                           longWave(vRest + vec2(0.0, e)).x - longWave(vRest - vec2(0.0, e)).x) / (2.0 * e);
            rs += ls;
            longFoam = smoothstep(0.04, 0.15, length(ls)) * smoothstep(0.15, 0.8, l0.x);
        }
    }
    if (uDetailP.w > 0.0) rs += texture(uDetail, vWorld.xz / uDetailP.x).rg * uDetailP.y * exp(-vDist / uDetailP.z);
    // seit Rungholt Phase 6: Strömung. Kräuselung als Rauschen, das mit dem Wasser zieht (zwei Phasen, überblendet,
    // damit es sich nicht endlos dehnt); Stärke mit der Geschwindigkeit, nur nah
    vec2 fv = flowAt(vRest);
    float fsp = length(fv);
    if (fsp > 0.04 && vDist < 900.0) {
        float k = 1.0 / 1.7, amp = 0.22 * smoothstep(0.05, 1.0, fsp) * (1.0 - smoothstep(300.0, 900.0, vDist));
        vec2 g = vec2(0.0);
        for (int ph = 0; ph < 2; ph++) {
            float t = fract(uTime / 4.0 + 0.5 * float(ph));
            vec2 q = (vRest - fv * t * 4.0) * k + float(ph) * 7.7;
            float e = 0.15;
            vec2 gg = vec2(vnoise(q + vec2(e, 0.0)) - vnoise(q - vec2(e, 0.0)), vnoise(q + vec2(0.0, e)) - vnoise(q - vec2(0.0, e))) / (2.0 * e) * k;
            g += gg * (1.0 - abs(2.0 * t - 1.0));
        }
        rs += g * amp;
    }
    vec3 n = normalize(vec3(-(sx / max(0.2, 1.0 + jx + 0.5 * sws.z) + rs.x), 1.0, -(sz / max(0.2, 1.0 + jz + 0.5 * sws.z) + rs.y)));

    vec3 V = normalize(uCam - vWorld);
    // seit Rungholt Phase 6: von unten. Im Snellschen Fenster (bis 48,6° vom Lot) der gebrochene Himmel, außerhalb die
    // Totalreflexion des trüben Wassers; das Wasser zwischen Auge und Fläche legt danach drawUnderwater darüber
    if (uUnder == 1 && uCam.y < vWorld.y) {
        vec3 up = sky(vec3(0.0, 1.0, 0.0));
        vec3 eD = up * 3.5 + sunC * max(uSunDir.y, 0.0);
        vec3 deep = uWaterColor * eD * 0.11 * 0.6;
        vec3 t = refract(-V, -n, 1.33);
        vec3 c = deep;
        if (dot(t, t) > 0.0) {
            float Fu = 0.02 + 0.98 * pow(1.0 - clamp(dot(V, -n), 0.0, 1.0), 5.0);
            c = mix(sky(normalize(t)) * 0.85 + sunC * pow(max(dot(normalize(t), uSunDir), 0.0), 400.0) * 2.0, deep, Fu);
        }
        frag = vec4(c, 1.0);
        return;
    }
    if (dot(n, V) < 0.02) n = normalize(n + V * (0.02 - dot(n, V)));
    float ndv = clamp(dot(n, V), 0.0, 1.0);
    float F = 0.02 + 0.98 * pow(1.0 - ndv, 5.0);
    vec3 r = reflect(-V, n);
    r.y = abs(r.y);
    vec3 refl = sky(r);

    // Wasserkörper: Streulicht aus Himmel und Sonne, im Kamm heller und türkis. Maßstab (Phase 3, physikalisches
    // Licht): Bestrahlung von oben E = Sonne · sin h + Himmel (≈ 3,5 · Zenit-Strahldichte); das Wasser gibt davon
    // rund 1 % je Steradiant zurück (Nordsee, grün), uWaterColor · 0,11.
    vec3 skyUp = sky(vec3(0.0, 1.0, 0.0));
    float sunUp = max(uSunDir.y, 0.0);
    vec3 eDown = skyUp * 3.5 + sunC * sunUp + moonC * max(uMoonDir.y, 0.0);
    vec3 body = uWaterColor * eDown * 0.11;
    // Trübung: schnelles, flaches Wasser wirbelt Schwebstoff auf (Farbe uFlowSilt.rgb, Stärke .a)
    float silt = uFlowRect.w > 0.0 ? clamp(uFlowSilt.a * (smoothstep(0.1, 1.0, fsp) * 0.7 + 0.3 * (1.0 - smoothstep(0.3, 3.0, vDepth))), 0.0, 0.85) : 0.0;
    body = mix(body, uFlowSilt.rgb * eDown * 0.11, silt);
    float crest = smoothstep(0.25, 1.0, vHeight / max(0.4 * uHs, 0.05));
    vec3 Lh = normalize(vec3(uSunDir.x, 0.0, uSunDir.z) + vec3(0.0, 1e-4, 0.0));
    vec3 Vh = normalize(vec3(V.x, 0.0, V.z) + vec3(0.0, 1e-4, 0.0));
    float back = pow(clamp(dot(-Vh, Lh) * 0.5 + 0.5, 0.0, 1.0), 4.0);
    float grazing = pow(1.0 - clamp(V.y, 0.0, 1.0), 2.0);
    vec3 sss = uSssColor * crest * grazing * (sunC * back * sunUp * 0.9 + skyUp * 0.35) * 0.15;
    // seit Haithabu: im Flachwasser scheint der Grund durch (Weg hin und zurück, schräg länger), bei uBedRect.w = 0 nie
    if (uBedRect.w > 0.0 && vDepth < 8.0) {
        float path = max(vDepth, 0.0) * (1.0 + 1.0 / max(V.y, 0.15));
        float T = exp(-uBedMap.z * (1.0 + 2.0 * silt) * path);
        vec3 bedLit = uBedColor / 3.14159265 * (sunC * sunUp * exp(-uBedMap.z * max(vDepth, 0.0) / max(sunUp, 0.15)) + skyUp * 3.5);
        body = mix(body, bedLit, T);
    }
    vec3 col = mix(body + sss, refl, F);

    // Glitzer: Sonne und Mond, rauer mit der Entfernung (die kleinen Wellen sind dort weggefiltert)
    float rough = clamp(0.012 + vDist * 0.00002, 0.012, 0.12);
    float a2 = rough * rough;
    vec3 Hs = normalize(uSunDir + V);
    float nh = max(dot(n, Hs), 0.0);
    float D = a2 / (3.14159265 * pow(nh * nh * (a2 - 1.0) + 1.0, 2.0));
    col += sunC * D * F * 0.25 * smoothstep(-0.02, 0.02, uSunDir.y);
    vec3 Hm = normalize(uMoonDir + V);
    float nm = max(dot(n, Hm), 0.0);
    float Dm = a2 / (3.14159265 * pow(nm * nm * (a2 - 1.0) + 1.0, 2.0));
    col += moonC * Dm * F * 0.25 * smoothstep(-0.02, 0.02, uMoonDir.y);
    // Punktlicht (Leuchtfeuer): Bestrahlung I/d² aus seiner Richtung, Glitzer wie Sonne und Mond
    vec3 pointE = vec3(0.0);
    vec3 Lp = vec3(0.0, 1.0, 0.0);
    if (uPointPos.w > 0.0) {
        vec3 tp = uPointPos.xyz - vWorld;
        float dp = max(length(tp), 0.5);
        Lp = tp / dp;
        pointE = pointIntensity(-Lp) / (dp * dp) * exp(-uPointSigma * dp);
        vec3 Hp = normalize(Lp + V);
        float np = max(dot(n, Hp), 0.0);
        float Dp = a2 / (3.14159265 * pow(np * np * (a2 - 1.0) + 1.0, 2.0));
        col += pointE * Dp * F * 0.25 + uWaterColor * pointE * max(Lp.y, 0.0) * 0.11;
    }
    // einfache Punktlichter (Blitze): Licht im Wasserkörper und auf dem Schaum; den Glanz zeigt die Spiegelung
    vec3 flashE = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        if (i >= uFlashN) break;
        vec3 tf = uFlashPos[i].xyz - vWorld;
        float d2 = dot(tf, tf) + uFlashPos[i].w * uFlashPos[i].w;
        flashE += uFlashRgb[i].rgb * max(dot(n, normalize(tf)), 0.0) / d2 * exp(-uPointSigma * sqrt(dot(tf, tf)));
    }
    col += uWaterColor * flashE * 0.11;

    // Schaum: Überschlag aller Kaskaden, Straßen in Windrichtung, Brandungsringe; feine Struktur aus Rauschen
    float foam = (1.0 - keep) * uFoamGain;
    vec2 wd = vec2(sin(uWindTo), -cos(uWindTo));
    float along = dot(vRest, wd), across = dot(vRest, vec2(-wd.y, wd.x));
    float warp = vnoise(vec2(along / 23.0, across / 3.1)) * 1.6;
    float streak = smoothr(13.0, 17.5, uWindSpeed) * smoothr(0.58, 0.82, vnoise(vec2(along / 34.0 + warp, across / 0.9)) * 0.6 + vnoise(vec2(along / 9.0 - warp, across / 0.35)) * 0.4) * 0.75;
    // seit Haithabu: Schaumsaum am Ufer, wenn Wind die kleinen Wellen auflaufen lässt
    if (uBedRect.w > 0.0) {
        float shore = (1.0 - smoothstep(0.02, 0.22, vDepth)) * smoothr(2.5, 9.0, uWindSpeed);
        ring = max(ring, shore * (0.35 + 0.65 * vnoise(vec2(vWorld.x * 1.3 - uTime * 0.4, vWorld.z * 1.3 + uTime * 0.3))) * 0.6);
    }
    // Schaum in Streifen, die mit der Strömung ziehen (ab 0,4 m/s), dichter im Flachen
    float flowFoam = 0.0;
    if (fsp > 0.3) {
        vec2 fd = fv / fsp;
        float al = dot(vRest, fd), ac = dot(vRest, vec2(-fd.y, fd.x));
        float sn = vnoise(vec2(al / 7.0 - uTime * fsp / 7.0, ac / 0.9)) * 0.6 + vnoise(vec2(al / 2.3 - uTime * fsp / 2.3, ac / 0.4)) * 0.4;
        flowFoam = smoothstep(0.6, 0.85, sn) * smoothstep(0.35, 1.1, fsp) * (0.35 + 0.4 * (1.0 - smoothstep(0.3, 2.0, vDepth)));
    }
    foam = clamp(max(max(max(max(foam, streak), ring), longFoam), flowFoam), 0.0, 1.0);
    // Struktur: in Windrichtung gestrecktes Rauschen; wenig Schaum zeigt nur die Spitzen (zerrissene Schaumköpfe),
    // viel Schaum deckt alles. In der Ferne wird die Struktur weich, damit nichts flimmert.
    vec2 fp = vec2(along * 0.55, across);
    float detail = vnoise(fp * 0.9) * 0.45 + vnoise(fp * 2.7 + 7.3) * 0.33 + vnoise(fp * 8.1 + 3.1) * 0.22;
    float soft = clamp(vDist / 400.0, 0.0, 1.0);
    float edge = mix(0.22, 0.6, soft);
    float level = 1.0 - min(foam, 0.8) * 0.9;
    float foamVis = mix(smoothstep(level, level + edge, detail), foam, soft * 0.7) * (0.45 + 0.45 * clamp(foam * 1.5, 0.0, 1.0));
    // Schaum als matte Fläche mit Albedo 0,6: Strahldichte = 0,6/π · Bestrahlung
    vec3 foamCol = vec3(0.93, 0.97, 1.0) * 0.6 / 3.14159265 * (sunC * max(dot(n, uSunDir), 0.0) + moonC * max(dot(n, uMoonDir), 0.0) + skyUp * 3.5 + pointE * max(dot(n, Lp), 0.0) + flashE);
    col = mix(col, foamCol, foamVis);

    // seit Glacier Phase 9: Treibeis. Die Brocken sind Zellen aus Rauschen (rund 1,7 m); es liegen so viele, wie das
    // Feld Fläche bedeckt. In der Ferne nur noch die Bedeckung als weißer Schleier.
    if (uBrashOn == 1) {
        vec2 bu = (vWorld.xz - uBrashRect.xy) / uBrashRect.zw;
        if (all(greaterThanEqual(bu, vec2(0.0))) && all(lessThanEqual(bu, vec2(1.0)))) {
            float cover = textureLod(uBrash, bu, 0.0).r;
            if (cover > 0.003) {
                vec2 g = vWorld.xz / 1.7, gi = floor(g);
                float best = 9.0, second = 9.0, hc = 1.0;
                for (int y = -1; y <= 1; y++)
                    for (int x = -1; x <= 1; x++) {
                        vec2 c = gi + vec2(float(x), float(y));
                        vec2 pnt = c + vec2(hash21(c), hash21(c + 17.3));
                        float d = length(g - pnt);
                        if (d < best) { second = best; best = d; hc = hash21(c + 5.1); }
                        else if (d < second) second = d;
                    }
                float piece = step(hc, cover * 1.15) * smoothstep(0.05, 0.16, second - best);
                float far = smoothstep(250.0, 1200.0, vDist);
                float amount = mix(piece, cover * 0.85, far);
                vec3 iceCol = vec3(0.80, 0.88, 0.94) * 0.75 / 3.14159265 * (sunC * max(uSunDir.y, 0.0) + moonC * max(uMoonDir.y, 0.0) + skyUp * 3.5);
                col = mix(col, iceCol * (0.85 + 0.3 * hc), amount);
            }
        }
    }

    // Dunst zur Kimm
    float f = 1.0 - exp(-vDist / uFogDist);
    vec3 fogCol = sky(normalize(vec3(-V.x, 0.0, -V.z) + vec3(0.0, 0.002, 0.0)));
    col = mix(col, fogCol, f);
    frag = vec4(col, 1.0);
}
