// forest.frag — Licht auf Stamm, Ästen und Laub: Laub beidseitig und gegen die Sonne durchscheinend, Rinde nur von
// vorn; Sonne mit der Sonnenschattenkarte des Benutzers (3×3-PCF) und den Wolkenschatten von oben; Mond, Himmel von
// oben (unten in der Krone weniger), Dunst nach Entfernung; nasse Rinde dunkler. Ausgabe: HDR, linear.
in vec3 vWorld;
in vec3 vNormal;
in vec3 vColor;
in float vLeafy;
in float vH;
out vec4 frag;
uniform vec3 uCam;
uniform vec3 uSunDir, uSunColor, uSkyColor, uFogColor, uMoonDir, uMoonColor;
uniform float uFogDist, uSkyWeight, uWet;
uniform sampler2D uShadow;
uniform vec4 uShadowRect;
uniform sampler2D uSky;     // Rundumbild des Himmels für den Dunst (uSkyOn), sonst uFogColor
uniform int uSkyOn;
uniform sampler2DShadow uSunShadow;
uniform mat4 uSunShadowM;
uniform vec2 uSunShadowP;   // an (1) / aus (0), Versatz
uniform vec2 uTone;         // Tonemapping an (1) / aus (0), Belichtung (nur Demos)

float sunShadow(vec3 w, vec3 n) {
    if (uSunShadowP.x < 0.5) return 1.0;
    vec4 q = uSunShadowM * vec4(w + n * 0.12 + uSunDir * 0.6, 1.0);
    vec3 s = q.xyz / q.w * 0.5 + 0.5;
    if (any(lessThan(s.xy, vec2(0.002))) || any(greaterThan(s.xy, vec2(0.998))) || s.z > 1.0) return 1.0;
    vec2 tx = 1.0 / vec2(textureSize(uSunShadow, 0));
    float sum = 0.0;
    for (int j = -1; j <= 1; j++)
        for (int i = -1; i <= 1; i++)
            sum += texture(uSunShadow, vec3(s.xy + vec2(i, j) * tx, s.z - uSunShadowP.y));
    return sum / 9.0;
}

void main() {
    vec3 n = normalize(vNormal);
    vec3 V = normalize(uCam - vWorld);
    bool leaf = vLeafy > 0.25;
    float glow = vLeafy > 0.75 ? 1.0 : 0.12;   // Kronenhülle: kaum Durchscheinen
    if (leaf) { if (dot(n, V) < 0.0) n = normalize(n - 1.3 * dot(n, V) * V); }
    else if (!gl_FrontFacing) n = -n;
    vec3 alb = vColor * (leaf ? 1.0 : 1.0 - 0.35 * uWet);
    float cloud = uShadowRect.w > 0.0 ? textureLod(uShadow, (vWorld.xz - uShadowRect.xy) / uShadowRect.z, 0.0).r : 1.0;
    float sh = sunShadow(vWorld, n) * cloud;
    float up = smoothstep(-0.02, 0.05, uSunDir.y);
    float nl = dot(n, uSunDir);
    float diff = leaf ? max(nl, 0.0) * 0.8 + 0.2 * max(-nl, 0.0) : max(nl, 0.0);
    float back = leaf ? pow(max(dot(-V, uSunDir), 0.0), 3.0) * max(-nl, 0.0) * 0.6 + pow(max(dot(-V, uSunDir), 0.0), 8.0) * 0.25 : 0.0;
    vec3 sun = uSunColor * up * sh;
    vec3 moon = uMoonColor * smoothstep(-0.02, 0.05, uMoonDir.y) * cloud * max(dot(n, uMoonDir) * 0.7 + 0.3, 0.0);
    float skyAmt = uSkyWeight * (0.45 + 0.35 * vH + 0.2 * n.y);
    vec3 col = alb * (sun * diff + moon + uSkyColor * skyAmt) + alb * vec3(0.75, 1.0, 0.4) * sun * back * glow;
    // nasses Laub glänzt matt im Gegenlicht
    if (uWet > 0.0) col += uSunColor * up * sh * pow(max(dot(reflect(-uSunDir, n), V), 0.0), 24.0) * 0.06 * uWet;
    float d = length(uCam - vWorld);
    vec3 fogC = uFogColor;
    if (uSkyOn == 1) {
        vec3 hd = normalize(vec3(vWorld.x - uCam.x, 0.0, vWorld.z - uCam.z) + vec3(0.0, 1e-4, 0.0));
        fogC = textureLod(uSky, vec2(atan(hd.x, -hd.z) / 6.28318530718 + 0.5, 0.5), 0.0).rgb;
    }
    col = mix(col, fogC, 1.0 - exp(-d / uFogDist));
    if (uTone.x > 0.5) {
        vec3 x = col * uTone.y;
        col = pow(clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0), vec3(1.0 / 2.2));
    }
    frag = vec4(col, 1.0);
}
