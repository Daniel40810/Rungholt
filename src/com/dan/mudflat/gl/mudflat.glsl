// mudflat.glsl — com.dan.mudflat (Phase 5): das Watt als Material. Ein Baustein zum Einsetzen in den Pixelshader einer
// App (#include "/com/dan/mudflat/gl/mudflat.glsl"); er beleuchtet nicht selbst, sondern liefert die Oberfläche:
// Albedo, Normale (Rippel, Wattwurmhaufen), Anteil des Wasserfilms und Rauigkeit. Die App mischt daraus Licht und die
// Spiegelung ihres Himmels (Fresnel; mit dem Film wird das Watt zum Spiegel).
//
// Zustand je Zelle aus MudflatGl: uMudState RGBA8 (R Nässe 0..1, G Sediment 0 Sand … 1 Schlick, BA Richtung der
// letzten Strömung, −1..1 auf 0..1) über uMudRect (x0, z0, Zelle in m, an). Ohne Zustand (w = 0) gilt die Nässe aus der
// Höhe über dem Wasser. uMudParams: x Rippel 0..1, y Abdunkeln bei Nässe, z Wattwurmhaufen je m², w Startwert.
// Raum: x Ost, z Süd, y oben, m.
// Phase 6: die Länge der Strömungsrichtung (BA) ist ihre Stärke (0 still … 1 ab 0,5 m/s): schwache Strömung, flache
// Rippel. mudflatR nimmt dazu die Rinnsale aus mudflat_relief.glsl (0..1) und die Richtung bergab: darin steht bei
// Ebbe zuletzt Wasser und läuft sichtbar ab (uMudTime, s).
uniform sampler2D uMudState;
uniform vec4 uMudRect;
uniform vec4 uMudParams;
uniform float uMudTime;
// Phase 7: Spuren (Stiefel, Robben, Räder) als Tiefe 0..1 (= 3 cm) in einem Fenster um die Kamera (TrackMap)
uniform sampler2D uMudTracks;
uniform vec4 uMudTrackRect;   // x0, z0, Größe (m), an

// Tiefe der Spur am Ort (0..1) und der Hang ihres Grunds (Ableitung der Höhe nach x und z)
float mudTrack(vec2 xz, out vec2 slope) {
    slope = vec2(0.0);
    if (uMudTrackRect.w <= 0.0) return 0.0;
    vec2 uv = (xz - uMudTrackRect.xy) / uMudTrackRect.z;
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return 0.0;
    float e = 1.0 / float(textureSize(uMudTracks, 0).x);
    float d = textureLod(uMudTracks, uv, 0.0).r;
    float dx = textureLod(uMudTracks, uv + vec2(e, 0.0), 0.0).r - textureLod(uMudTracks, uv - vec2(e, 0.0), 0.0).r;
    float dz = textureLod(uMudTracks, uv + vec2(0.0, e), 0.0).r - textureLod(uMudTracks, uv - vec2(0.0, e), 0.0).r;
    slope = -vec2(dx, dz) * 0.03 / (2.0 * e * uMudTrackRect.z);
    return d;
}

struct MudSurface {
    vec3 albedo;     // linear
    vec3 normal;     // mit Rippeln und Haufen
    float film;      // 0 matt … 1 geschlossener Wasserfilm (Spiegel)
    float rough;     // 0 glatt … 1 matt
    float wetness;   // Nässe 0..1
    float sediment;  // 0 Sand … 1 Schlick
};

float mudHash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7)) + uMudParams.w * 0.137) * 43758.5453); }
vec2 mudHash2(vec2 p) { return vec2(mudHash(p), mudHash(p + 19.19)); }
float mudNoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(mudHash(i), mudHash(i + vec2(1, 0)), u.x), mix(mudHash(i + vec2(0, 1)), mudHash(i + vec2(1, 1)), u.x), u.y);
}
float mudFbm(vec2 p) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) { s += a * mudNoise(p); p = p * 2.07 + 13.3; a *= 0.5; }
    return s;
}

// Albedo eines Sediments (wie WetField.color): Sand, Mischwatt, Schlick, trocken
vec3 mudColor(float s) {
    vec3 sand = vec3(0.340, 0.280, 0.190), misch = vec3(0.250, 0.200, 0.130), schlick = vec3(0.150, 0.125, 0.085);
    return s < 0.5 ? mix(sand, misch, s * 2.0) : mix(misch, schlick, s * 2.0 - 1.0);
}

// xz: Ort; n: Normale des Geländes; above: Grund über dem Wasserspiegel am Ort (m, negativ = unter Wasser);
// px: Größe eines Bildpunkts am Ort (m).
MudSurface mudflatR(vec2 xz, vec3 n, float above, float px, float runnel, vec2 downhill) {
    MudSurface m;
    vec4 st = vec4(clamp(1.0 - above * 0.5, 0.25, 1.0), 0.4, 1.0, 0.5);
    if (uMudRect.w > 0.0) {
        vec2 uv = (xz - uMudRect.xy) / (uMudRect.z * vec2(textureSize(uMudState, 0)));
        if (all(greaterThanEqual(uv, vec2(0.0))) && all(lessThanEqual(uv, vec2(1.0)))) st = textureLod(uMudState, uv, 0.0);
    }
    float W = st.r, S = st.g;
    vec2 flow = st.ba * 2.0 - 1.0;
    float strength = clamp(length(flow), 0.0, 1.0);
    flow = strength > 0.05 ? flow / max(strength, 1e-4) : vec2(1.0, 0.0);
    if (above < 0.0) W = 1.0;
    // Flecken im Sediment (ein paar Meter bis hundert Meter)
    float spots = mudFbm(xz * 0.012) - 0.5, fine = mudFbm(xz * 0.35 + 7.0) - 0.5;
    S = clamp(S + 0.25 * spots, 0.0, 1.0);
    float mid = mudFbm(xz * 0.9 + 3.0) - 0.5;
    vec3 alb = mudColor(S) * (1.0 + 0.18 * fine + 0.22 * mid);

    // Rippel quer zur letzten Strömung: Sand gröber (14 cm), Schlick feiner (7 cm), Kamm schärfer als Tal
    float lam = mix(0.14, 0.07, S);
    vec2 side = vec2(-flow.y, flow.x);
    float warp = 1.6 * mudNoise(xz * 0.6) + 0.6 * mudNoise(xz * 2.1 + 3.0);
    float ph = (dot(xz, flow) / lam + warp) * 6.2831853;
    float rip = sin(ph) + 0.28 * sin(2.0 * ph + 1.3);             // Höhe in Einheiten der Amplitude
    float drip = cos(ph) + 0.56 * cos(2.0 * ph + 1.3);            // Ableitung nach der Phase
    vec2 tSlope;
    float track = mudTrack(xz, tSlope);
    float tVis = 1.0 - smoothstep(0.04, 0.16, px);
    float amp = 0.007 * uMudParams.x * (1.0 - 0.6 * S) * (0.6 + 0.8 * mudNoise(xz * 0.08 + 11.0)) * mix(0.3, 1.0, smoothstep(0.1, 0.7, strength)) * (1.0 - 0.85 * track);
    float vis = 1.0 - smoothstep(lam * 0.35, lam * 1.5, px);      // jenseits der Auflösung: nur noch im Mittel
    vec2 grad = flow * drip * amp * 6.2831853 / lam * vis;
    // eine zweite, schwächere Schar (Interferenzrippel) in Prielnähe und auf Sand
    float ph2 = (dot(xz, side) / (lam * 1.7) + warp * 0.7) * 6.2831853;
    grad += side * cos(ph2) * amp * 0.35 * 6.2831853 / (lam * 1.7) * vis * (1.0 - S);
    float trough = 0.5 - 0.5 * (rip / 1.28);                      // 1 im Tal, 0 auf dem Kamm
    grad += tSlope * tVis;

    // Wattwurmhaufen: Kringel aus nassem Kot, auf Sand und Mischwatt
    float wormVis = 1.0 - smoothstep(0.01, 0.04, px);
    if (wormVis > 0.0 && uMudParams.z > 0.0) {
        float cellW = inversesqrt(uMudParams.z);
        vec2 c = floor(xz / cellW);
        for (int b = -1; b <= 1; b++)
            for (int a = -1; a <= 1; a++) {
                vec2 id = c + vec2(a, b);
                vec2 h = mudHash2(id);
                if (h.x > 0.85 * (1.0 - 0.8 * S)) continue;
                vec2 ctr = (id + 0.2 + 0.6 * h) * cellW;
                vec2 d = xz - ctr;
                float r = length(d), R = 0.03 + 0.025 * h.y;
                if (r > R * 1.3) continue;
                float coil = 0.5 + 0.5 * sin(atan(d.y, d.x) * 3.0 + r * 220.0);
                float body = 1.0 - smoothstep(R * 0.6, R * 1.3, r);
                // der Haufen ist frischer, feuchter Sand: etwas dunkler, die Windungen heller; das Loch daneben dunkel
                alb *= 1.0 - 0.08 * body * wormVis + 0.12 * coil * body * wormVis;
                float hole = 1.0 - smoothstep(0.004, 0.009, length(xz - ctr - vec2(R * 1.9, 0.0)));
                alb *= 1.0 - 0.5 * hole * wormVis;
                grad += d / max(r, 1e-4) * body * 0.9 * wormVis;
                trough *= 1.0 - body;
            }
    }

    // Schill (Muschelbruch) in den Rinnen und an steilen Kanten
    float shellZone = max(1.0 - smoothstep(0.94, 0.985, n.y), (1.0 - smoothstep(0.05, 0.25, S)) * smoothstep(0.6, 0.85, mudNoise(xz * 0.05 + 41.0)));
    if (shellZone > 0.0) {
        float shellVis = 1.0 - smoothstep(0.01, 0.05, px);
        vec2 sc = floor(xz / 0.035);
        float sh = step(0.93, mudHash(sc)) * shellVis + (1.0 - shellVis) * 0.06;
        alb = mix(alb, vec3(0.44, 0.42, 0.38), sh * shellZone * 0.6);
    }

    // Algenmatten auf Schlick in feuchten Senken
    float alg = smoothstep(0.62, 0.72, mudFbm(xz * 0.02 + 31.0)) * smoothstep(0.55, 0.85, S) * smoothstep(0.3, 0.7, W);
    alb = mix(alb, vec3(0.045, 0.075, 0.030), alg * 0.75);

    // Nässe: dunkler; Wasserfilm gleich nach dem Trockenfallen, Wasser in den Rippeltälern, Pfützen in Mulden
    alb *= 1.0 - uMudParams.y * W;
    float sheet = smoothstep(0.55, 0.97, W) * (0.55 + 0.45 * mudNoise(xz * 0.15 + 5.0));
    float inTrough = smoothstep(0.08, 0.5, W) * mix(0.35, smoothstep(0.55, 0.9, trough), vis) * smoothstep(0.0, 0.3, uMudParams.x);
    float pn = mudFbm(xz * 0.045 + 77.0);
    float puddle = smoothstep(0.66 - 0.12 * W, 0.69 - 0.12 * W, pn + 0.12 * mid) * smoothstep(0.15, 0.45, W);
    float shore = 1.0 - smoothstep(0.0, 0.06, above);
    // Rinnsale: das Wasser bleibt darin länger und läuft bergab (kleine Wellen, die mit der Zeit wandern)
    float inRun = runnel * smoothstep(0.18, 0.5, W);
    // in tiefen Spuren steht das Wasser zuletzt; aufgewühlter Sand ist dunkler
    float inPrint = smoothstep(0.5, 0.9, track) * smoothstep(0.35, 0.7, W) * tVis;
    alb *= 1.0 - 0.22 * track * mix(0.4, 1.0, tVis);
    float film = clamp(max(max(max(sheet, inTrough * 0.9), max(max(puddle, shore), inRun)), inPrint * 0.5), 0.0, 1.0);
    alb *= 1.0 - 0.25 * puddle - 0.2 * inRun;
    if (inRun > 0.01) {
        float dl = length(downhill);
        vec2 dh = dl > 1e-4 ? downhill / dl : flow;
        float ph = dot(xz, dh) * 9.0 - uMudTime * 4.0 + mudNoise(xz * 0.7) * 3.0;
        grad += dh * cos(ph) * 0.06 * inRun * (1.0 - smoothstep(0.05, 0.3, px));
    }

    vec3 nn = normalize(n + vec3(-grad.x, 0.0, -grad.y));
    m.normal = normalize(mix(nn, n, film * 0.85 * (1.0 - 0.8 * inRun)));   // stehendes Wasser ist glatt, fließendes nicht
    m.albedo = alb;
    m.film = film;
    m.rough = mix(mix(0.9, 0.45, W), 0.04, film);
    m.wetness = W;
    m.sediment = S;
    return m;
}

MudSurface mudflat(vec2 xz, vec3 n, float above, float px) { return mudflatR(xz, n, above, px, 0.0, vec2(0.0)); }
