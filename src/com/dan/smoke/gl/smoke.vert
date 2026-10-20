// smoke.vert — Rauchbälle als Bildtafeln ohne Eckpuffer: Instanz = Ball, sechs Ecken je Tafel. Die Fahne liegt im
// Speicherpuffer (smoke_common.glsl), der Ball folgt aus Fahne und Nummer. Seit Haithabu Phase 11 rechnet der
// Vertex-Shader das Licht einmal je Ball (Sonne und Mond mit Vorwärtsstreuung, Himmel, Feuerschein von unten) – ein
// Ball ist weich genug, dass das reicht, und die Pixel bleiben billig.
uniform mat4 uVP;
uniform vec3 uEye;
uniform int uPer;
uniform vec3 uSunDir, uSunColor, uMoonDir, uMoonColor, uSkyTop, uSkyHorizon;
uniform int uFireN;
uniform vec4 uFirePos[8];        // Ort, Größe der Quelle (m)
uniform vec4 uFireRgb[8];        // Lichtstärke RGB · Vorbelichtung
out vec2 vUv;
out float vAlpha;
out float vDepth;
out vec3 vWorld;
out vec3 vCenter;
flat out float vRadius;
flat out float vBlack;
flat out float vAge;
flat out float vRand;
flat out vec3 vSunMoon;          // Sonne und Mond mit Phasenfunktion, ohne Selbstschatten
flat out vec3 vSkyLo, vSkyHi;    // Himmel unten und oben am Ball
flat out vec3 vFire;             // Feuerschein am Ball

float hg(float c, float g) { return (1.0 - g * g) / (12.566 * pow(1.0 + g * g - 2.0 * g * c, 1.5)); }

void main() {
    int inst = gl_InstanceID, pi = inst / uPer, i = inst % uPer;
    SmokePlume P = plumes[pi];
    vec4 pr;
    float alpha, age, rnd;
    smokeParticle(P, i, pr, alpha, age, rnd);
    vBlack = P.b.x;
    vAge = age;
    vRand = rnd;
    vRadius = pr.w;
    vCenter = pr.xyz;
    if (alpha < 0.004) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0); vAlpha = 0.0; vUv = vec2(0.0); vDepth = 0.0; vWorld = vec3(0.0);
        vSunMoon = vec3(0.0); vSkyLo = vec3(0.0); vSkyHi = vec3(0.0); vFire = vec3(0.0);
        return;
    }
    int c = gl_VertexID % 6;
    vec2 q = c == 0 ? vec2(-1, -1) : c == 1 ? vec2(1, -1) : c == 2 ? vec2(1, 1) : c == 3 ? vec2(-1, -1) : c == 4 ? vec2(1, 1) : vec2(-1, 1);
    // langsam drehen, damit die Flocken nicht starr mitlaufen
    float ang = rnd * 6.2831 + age * (rnd - 0.5) * 0.05;
    vec2 qr = vec2(q.x * cos(ang) - q.y * sin(ang), q.x * sin(ang) + q.y * cos(ang));
    vUv = qr;
    vec3 toEye = normalize(uEye - pr.xyz);
    vec3 right = normalize(cross(toEye, vec3(0.0, 1.0, 0.0)) + vec3(1e-5, 0.0, 0.0));
    vec3 up = cross(right, toEye);
    vec3 wp = pr.xyz + (right * q.x + up * q.y) * pr.w;
    vWorld = wp;
    vAlpha = alpha;
    vDepth = length(wp - uEye);
    // Licht am Ball
    vec3 V = -toEye;
    vSunMoon = uSunColor * (hg(dot(V, uSunDir), 0.45) * 12.566 * 0.6 + 0.25) * smoothstep(-0.03, 0.05, uSunDir.y)
             + uMoonColor * (hg(dot(V, uMoonDir), 0.45) * 12.566 * 0.6 + 0.25) * smoothstep(-0.03, 0.05, uMoonDir.y);
    vSkyLo = mix(uSkyHorizon, uSkyTop, 0.35);
    vSkyHi = mix(uSkyHorizon, uSkyTop, 0.65);
    vec3 fire = vec3(0.0);
    for (int k = 0; k < 8; k++) {
        if (k >= uFireN) break;
        vec3 d = uFirePos[k].xyz - pr.xyz;
        float d2 = dot(d, d) + uFirePos[k].w * uFirePos[k].w + pr.w * pr.w * 0.25;
        fire += uFireRgb[k].rgb / d2 * (0.6 + 0.4 * clamp(normalize(d).y * -1.0 + 0.5, 0.0, 1.0));
    }
    vFire = fire;
    gl_Position = uVP * vec4(wp, 1.0);
}
