// ice_common.glsl — die Optik des Eises, Zeile für Zeile wie IceOptics (Java): Kubelka-Munk für eine Schicht (stabil mit
// tanh und sech), Fresnel nach Schlick, dazu Rauschen und der Himmel (Rundumbild oder Verlauf).
const float PI = 3.14159265359;
const float N_ICE = 1.31;
const float F0_ICE = 0.0181;          // ((1,31 − 1)/(1,31 + 1))²
const float CRUST_SIGMA = 400.0;     // Streuung der verwitterten Kruste (1/m), wie IceOptics.CRUST_SIGMA

// Reflexion R und diffuse Durchlässigkeit T einer Schicht der Dicke d bei Absorption K und Streuung S (je Kanal, 1/m)
void km(vec3 K, vec3 S, float d, out vec3 R, out vec3 T) {
    S = max(S, vec3(1e-6));
    vec3 a = 1.0 + K / S;
    vec3 b = sqrt(max(a * a - 1.0, vec3(0.0)));
    vec3 x = min(b * S * max(d, 0.0), vec3(40.0));
    vec3 e = exp(-2.0 * x);
    vec3 th = (1.0 - e) / (1.0 + e);
    vec3 sech = 2.0 * exp(-x) / (1.0 + e);
    vec3 den = a * th + b;
    R = th / den;
    T = b * sech / den;
}

float fresnelIce(float c) { float m = 1.0 - clamp(c, 0.0, 1.0); return F0_ICE + (1.0 - F0_ICE) * m * m * m * m * m; }

float ihash(vec3 p) { return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453); }
float inoise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = ihash(i), n100 = ihash(i + vec3(1, 0, 0)), n010 = ihash(i + vec3(0, 1, 0)), n110 = ihash(i + vec3(1, 1, 0));
    float n001 = ihash(i + vec3(0, 0, 1)), n101 = ihash(i + vec3(1, 0, 1)), n011 = ihash(i + vec3(0, 1, 1)), n111 = ihash(i + vec3(1, 1, 1));
    return mix(mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y), mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y), f.z);
}
float ifbm(vec3 p) { return 0.5 * inoise(p) + 0.25 * inoise(p * 2.03 + 7.1) + 0.125 * inoise(p * 4.1 - 3.7) + 0.125 * inoise(p * 8.3 + 1.3); }
