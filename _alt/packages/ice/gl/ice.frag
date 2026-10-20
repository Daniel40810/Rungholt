// ice.frag — Eis als Material (Werkbuch Glacier, F10 A). Je Bildpunkt:
//  1. Dicke: Abstand zur Rückseite (aus ice_back.frag) minus Abstand zur Vorderseite, längs des Sehstrahls.
//  2. Risse: 2 bis 8 Proben (Stufe) längs des gebrochenen Strahls in der Zellkachel (IceNoise); Rissflächen streuen (weiß) und
//     spiegeln die Sonne mit Totalreflexion (Blitzen).
//  3. Licht im Eis nach Kubelka-Munk je Kanal: K = 2α (Warren & Brandt), S = ¾ σ (1 − g) aus den Blasen plus Rissen.
//     Reflexion R im Licht von vorn, diffuse Durchlässigkeit T im Licht von hinten (Leuchten der Kanten im Gegenlicht),
//     dazu der ungestreute Anteil exp(−(α + σ) d), durch den man den Himmel dahinter sieht.
//  4. Oberfläche: Moränenbänder (Schutt), Schnee auf flachen Stellen, Schmelzwasserfilm (glatt, Fresnel), Glitzer.
//  5. Spiegelung des Himmels und der Sonne nach Fresnel, Wasserfarbe unter dem Spiegel, Dunst.
// Licht in den Einheiten der App (vorbelichtet, wie die anderen Packages): Sonne als Bestrahlung, Himmel als Leuchtdichte.
#include "ice_common.glsl"
in vec3 vW, vN, vL;
in float vAo;                   // Verdeckung: Grund von Spalten, Kerbe (1 = offen)
out vec4 frag;
uniform vec3 uEye;
uniform vec3 uSun, uSunColor, uMoonDir, uMoonColor, uSkyTop, uSkyHor, uFogColor;
uniform float uFogDist;
uniform int uSkyTex;            // 1: Himmel aus dem Rundumbild uPano (Azimut von Nord, Höhe), 0: Verlauf
uniform sampler2D uPano;
uniform vec3 uAlpha;            // Absorption je Kanal (1/m)
uniform float uSigma, uG;       // Streuung der Blasen (1/m), Asymmetrie
uniform vec4 uMat;              // Risse, Moräne, Schmelzfilm, Schnee (je 0..1)
uniform float uCrust;           // Dicke der verwitterten Kruste (m); Streuung dort CRUST_SIGMA
uniform float uGlitter, uCrackCell;
uniform int uSteps;              // Proben längs des gebrochenen Strahls (Stufe: 2, 4, 6, 8)
uniform vec3 uBandDir, uSeedOff;
uniform float uBandSpacing;     // Abstand der Moränenbänder (m)
uniform sampler2D uBack;        // Abstand zur Rückseite (m), 0 = keine
uniform vec2 uTarget;           // Größe des Ziels in Bildpunkten
uniform float uDefThick, uThickScale;
uniform sampler3D uCrack;       // R: Nähe zur Rissfläche, GBA: deren Normale
uniform mat3 uRot;              // Drehung Körper → Welt
uniform float uWater;           // Wasserspiegel (y); darunter Farbe des Wassers
uniform vec3 uWaterAbs;         // Auslöschung im Wasser je Kanal (1/m)
uniform vec3 uWaterColor;       // Farbe des Wassers im Licht (Albedo der Streuung, trübes Gletscherwasser türkis)
// Phase 7: Ausschnitte (Quader, um die Hochachse gedreht), in denen dieser Körper nicht gezeichnet wird – dort steht ein
// Abschnitt der Front als Zellen. A: Mitte x, z, Tangente x, z; B: halbe Breite, halbe Tiefe, y unten, y oben.
uniform int uCutN, uUseCut;
uniform vec4 uCutA[8], uCutB[8];

vec3 skyDir(vec3 d) {
    if (uSkyTex == 1) {
        float az = atan(d.x, -d.z) / (2.0 * PI) + 0.5;
        float el = asin(clamp(d.y, -1.0, 1.0)) / PI + 0.5;
        return textureLod(uPano, vec2(az, el), 0.0).rgb;
    }
    if (d.y < 0.0) return uSkyHor * mix(0.6, 0.25, clamp(-d.y * 4.0, 0.0, 1.0));
    return mix(uSkyHor, uSkyTop, sqrt(clamp(d.y, 0.0, 1.0)));
}

void main() {
    if (uUseCut == 1) {
        for (int i = 0; i < uCutN; i++) {
            vec2 d = vW.xz - uCutA[i].xy, t = uCutA[i].zw, m = vec2(t.y, -t.x);
            if (abs(dot(d, t)) < uCutB[i].x && abs(dot(d, m)) < uCutB[i].y && vW.y > uCutB[i].z && vW.y < uCutB[i].w) discard;
        }
    }
    vec3 n = normalize(vN);
    if (!gl_FrontFacing) n = -n;
    // leichte Beulen: geschmolzene Mulden und Rippen brechen die ebenen Flächen (Ableitung des Rauschens im Körper)
    {
        vec3 q = (vL + uSeedOff) * 0.35;
        float e = 0.15, c0 = ifbm(q);
        vec3 g = vec3(ifbm(q + vec3(e, 0, 0)), ifbm(q + vec3(0, e, 0)), ifbm(q + vec3(0, 0, e))) - c0;
        vec3 gw = uRot * g / e;
        // in der Ferne ausblenden: die Beulen sind wenige Meter groß und flimmern sonst (Befund Vorschau Phase 5)
        float fade = 1.0 - smoothstep(150.0, 700.0, length(uEye - vW));
        n = normalize(n - 0.12 * fade * (gw - dot(gw, n) * n));
        // seit Glacier Phase 16 (Befund „Auf dem Eis“: flache Facetten aus dem Raster von 6,25 m): in der Nähe eine
        // zweite, feinere Lage Buckel und Rinnen (rund 1 m), so wie die Sonne blankes Eis aufraut
        float fine = 1.0 - smoothstep(25.0, 90.0, length(uEye - vW));
        if (fine > 0.0) {
            vec3 q2 = (vL + uSeedOff) * 1.6;
            float e2 = 0.08, c2 = ifbm(q2);
            vec3 g2 = vec3(ifbm(q2 + vec3(e2, 0, 0)), ifbm(q2 + vec3(0, e2, 0)), ifbm(q2 + vec3(0, 0, e2))) - c2;
            vec3 gw2 = uRot * g2 / e2;
            n = normalize(n - 0.07 * fine * (gw2 - dot(gw2, n) * n));
        }
    }
    vec3 v = normalize(uEye - vW);
    // geglättete Normalen an steilen Kanten zeigen manchmal vom Auge weg (die Fläche selbst noch nicht): dann wäre
    // alles Spiegelung (Fresnel → 1) und die Kante schwarz. Die Normale wird zum Auge zurückgebogen.
    float nd = dot(n, v);
    if (nd < 0.08) n = normalize(n + v * (0.08 - nd));
    float nv = max(dot(n, v), 1e-3);
    float dist = length(uEye - vW);
    float near = 1.0 - smoothstep(300.0, 1500.0, dist);

    // 1 Dicke
    float back = texture(uBack, gl_FragCoord.xy / uTarget).r;
    // ohne Rückseite (Gletscher, offene Flächen): die Grunddicke, in Spalten und Kerben dünner (Licht kommt durch die Wand)
    float d = (back > dist + 1e-3 ? back - dist : uDefThick * mix(0.05, 1.0, vAo * vAo)) * uThickScale;

    // 2 Risse längs des gebrochenen Strahls
    vec3 rd = refract(-v, n, 1.0 / N_ICE);
    if (dot(rd, rd) < 1e-6) rd = -v;
    mat3 inv = transpose(uRot);
    vec3 L = vL + uSeedOff;
    vec3 rdL = inv * rd;
    float tile = uCrackCell * 8.0;
    float reach = min(d, uCrackCell * 3.0);
    float edgeSum = 0.0, glint = 0.0;
    vec3 h = normalize(uSun + v);
    vec3 attG = vec3(0.0);
    float steps = max(float(uSteps), 1.0);
    for (int k = 0; k < uSteps; k++) {
        float t = (float(k) + 0.5) / steps * reach;
        vec4 c = texture(uCrack, (L + rdL * t) / tile);
        float e = c.r;
        vec3 cn = normalize(uRot * (c.gba * 2.0 - 1.0));
        edgeSum += e;
        // Spiegelung an der Rissfläche (Luftspalt: Totalreflexion ab knapp 50° Einfall); Weg hin und zurück im Eis
        float m = pow(abs(dot(cn, h)), 220.0);
        vec3 att = exp(-(uAlpha + 0.5 * uSigma * (1.0 - uG)) * 2.0 * t);
        attG += att * e * m;
    }
    // in der Ferne das Mittel der Kachel statt der einzelnen Flächen (flimmert sonst)
    float crackS = uMat.x * mix(0.23, edgeSum / steps, near) * 1.2;
    attG *= near;
    // Kruste ungleich: Flecken, dazu senkrechte Rinnen, in denen Schmelzwasser die Kruste weggewaschen hat (blau)
    float blotch = ifbm(L * 0.25 + 5.0);
    float runnel = smoothstep(0.55, 0.8, ifbm(vec3(L.x * 0.9, L.y * 0.06, L.z * 0.9)));
    // an steilen Flächen (frisch gebrochen, von der Sonne nur gestreift) ist die Kruste dünn
    // in Spalten (verdeckt) frisch und blau: keine Sonne, kein Wind, der die Haut aufraut
    float crust = uCrust * (0.2 + 1.3 * blotch) * (1.0 - 0.85 * uMat.z * runnel) * mix(0.45, 1.0, smoothstep(0.15, 0.8, n.y)) * mix(0.25, 1.0, vAo);

    // 3 Licht im Eis
    vec3 K = 2.0 * uAlpha;
    vec3 S = vec3(0.75 * uSigma * (1.0 - uG) + crackS);
    // Kruste über dem Eis: zwei Schichten nach Kubelka-Munk (wie IceOptics.layered)
    vec3 R1, T1, R2, T2;
    km(K, vec3(0.75 * CRUST_SIGMA * (1.0 - uG)), crust, R1, T1);
    km(K, S, d, R2, T2);
    vec3 den = max(vec3(1e-6), 1.0 - R1 * R2);
    vec3 R = R1 + T1 * T1 * R2 / den;
    vec3 T = T1 * T2 / den;
    vec3 Dt = exp(-(uAlpha + uSigma + crackS) * d - CRUST_SIGMA * crust);
    float ns = max(dot(n, uSun), 0.0), nm = max(dot(n, uMoonDir), 0.0);
    vec3 amb = mix(uSkyHor, uSkyTop, 0.5 + 0.5 * n.y) * vAo;
    float sunVis = mix(0.15, 1.0, smoothstep(0.25, 0.85, vAo));
    vec3 E = (uSunColor * ns + uMoonColor * nm) * sunVis + amb;
    // in Spalten und Kerben kommt das Licht durch die Eiswände: nach 12 bis 20 m Eis blau (Werkbuch: „blau in der Tiefe“)
    float occ = 1.0 - vAo;
    float sunUp0 = smoothstep(-0.05, 0.1, uSun.y);
    E += occ * (mix(uSkyHor, uSkyTop, 0.6) * exp(-uAlpha * 12.0) * 0.9 + uSunColor * sunUp0 * exp(-uAlpha * 20.0) * 0.25);
    // Licht von hinten: was an den anderen, besonnten Flächen des Körpers eintritt, durchläuft das Eis und kommt hier
    // gefiltert heraus (Leuchten im Schatten, blau mit der Dicke); am stärksten, wenn die Sonne genau dahinter steht
    float sunUp = smoothstep(-0.05, 0.1, uSun.y);
    vec3 Eback = uSunColor * sunUp * (0.8 + 0.6 * max(-dot(n, uSun), 0.0)) + uMoonColor * max(-dot(n, uMoonDir), 0.0)
               + mix(uSkyHor, uSkyTop, 0.5 - 0.5 * n.y);
    vec3 bg = skyDir(normalize(mix(-v, rd, 0.5)));
    float F = fresnelIce(nv);
    vec3 sub = R * E + T * Eback + Dt * bg + Dt * uSunColor * pow(max(dot(-v, uSun), 0.0), 300.0) * 4.0;
    vec3 col = (1.0 - F) * sub;

    // 4 Oberfläche: Schutt in Bändern, Schnee, Film
    vec3 Lm = L * (1.35 / uBandSpacing);
    float b = fract(dot(L, normalize(uBandDir)) / uBandSpacing + 0.35 * ifbm(Lm));
    // Bänder mit weichen Rändern, darin Schutt in Flecken (grob) und Körnern (fein, in der Nähe)
    float stripe = smoothstep(0.0, 0.07, b) * (1.0 - smoothstep(0.07, 0.2, b));
    float grain = mix(0.6, ifbm(L * 0.8), near);
    float mor = clamp(uMat.y * (stripe * (0.35 + 0.7 * ifbm(L * (8.0 / uBandSpacing))) * (0.6 + 0.6 * grain) + 0.25 * smoothstep(0.75, 0.95, ifbm(L * 1.7)) * near), 0.0, 1.0);
    col = mix(col, vec3(0.13, 0.12, 0.105) * (0.7 + 0.6 * mix(0.5, ifbm(L * 3.0), near)) * E, mor);
    float snow = clamp(uMat.w * smoothstep(0.45, 0.85, n.y) * (0.65 + 0.5 * ifbm(L * 0.6)), 0.0, 1.0);
    col = mix(col, vec3(0.86, 0.89, 0.93) * E, snow);
    float wet = uMat.z * (1.0 - snow);
    col *= 1.0 - 0.12 * wet;
    // seit Glacier Phase 16 (Befund „Auf dem Eis“ ausgewaschen): auf flachem, blankem Eis in der Nähe Staub und
    // Kryokonit in Flecken (bis 22 % dunkler); ab 400 m wie bisher, damit die Zunge von oben gleich aussieht
    float flach = smoothstep(0.6, 0.9, n.y) * (1.0 - snow) * (1.0 - mor);
    float dust = mix(1.0, 0.78 + 0.22 * ifbm(L * 0.45 + 11.0), 1.0 - smoothstep(60.0, 400.0, dist));
    col *= mix(1.0, dust, flach);

    // 5 Spiegelung, Sonne, Rissblitzen, Glitzer
    vec3 rv = reflect(-v, n);
    col += F * (0.6 + 0.4 * wet) * skyDir(rv) * (1.0 - 0.8 * snow) * mix(0.3, 1.0, vAo);
    float p = mix(24.0, 1600.0, wet);
    float sp = pow(max(dot(n, h), 0.0), p) * (p + 8.0) / (8.0 * PI);
    col += uSunColor * fresnelIce(max(dot(h, v), 0.0)) * sp * ns * (1.0 - 0.7 * snow) * sunVis;
    col += uSunColor * attG * uMat.x * 2.5 * (1.0 - snow) * (1.0 - mor) * step(0.0, ns + 0.2);
    vec3 cell = floor(L * 28.0);
    vec3 r3 = vec3(ihash(cell), ihash(cell + 17.3), ihash(cell + 41.9));
    vec3 gn = normalize(n + (r3 - 0.5) * 0.7);
    float gl = pow(max(dot(reflect(-uSun, gn), v), 0.0), 2500.0) * step(0.8, ihash(cell + 3.1));
    col += uSunColor * gl * uGlitter * 30.0 * (1.0 - mor) * ns * (1.0 - smoothstep(40.0, 250.0, dist));

    // unter dem Wasserspiegel: auf dem Weg durchs Wasser (bis zum Eis und zurück zur Oberfläche) geschluckt und vom
    // trüben Wasser überstrahlt – tief unten bleibt nur das Türkis des Sees (die Oberfläche selbst zeichnet die App)
    if (vW.y < uWater) {
        float dep = uWater - vW.y;
        float path = dep + dep / max(v.y, 0.25);
        vec3 tr = exp(-uWaterAbs * path);
        vec3 lit = uSunColor * sunUp * max(uSun.y, 0.0) + uSkyTop;
        col = col * tr + uWaterColor * lit * (1.0 - tr);
    }
    col = mix(col, uFogColor, 1.0 - exp(-dist / max(1.0, uFogDist)));
    frag = vec4(col, 1.0);
}
