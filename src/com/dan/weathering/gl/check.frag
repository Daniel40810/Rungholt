// check.frag — Selbsttest: rechnet die Masken für ein Probengitter wie WeatheringGl.probe() und Weathering.surface().
// Untere Hälfte: Nässe, Rost, Algen, Salz. Obere Hälfte: Farbe (aus Grau 0,5) und Rauheit.
uniform int uN;
out vec4 frag;
void main() {
    int i = int(gl_FragCoord.x), jj = int(gl_FragCoord.y);
    int j = jj % uN;
    float y0 = uWthWater.z - 1.0, y1 = uWthWater.x + uWthWet.x + 3.0;
    float y = y0 + (y1 - y0) * (float(j) + 0.5) / float(uN);
    float ss = (float(i) + 0.5) * 0.37;
    float a = float(i % 8) * 0.785398;
    float ny = float(i % 5) * 0.2 - 0.2;
    float r = sqrt(max(0.0, 1.0 - ny * ny));
    vec3 N = vec3(cos(a) * r, ny, sin(a) * r);
    float below = mod(y1 - y, 1.2);
    float sus = float(j % 4) / 3.0;
    vec2 mapUV = vec2((float(i) + 0.5) / float(uN), (float(j) + 0.5) / float(uN));
    vec3 albedo = vec3(0.5);
    float rough = 0.5;
    vec4 m;
    float wet = wthApply(albedo, rough, 0.5, vec3(0.0, y, 0.0), N, vec2(ss, y), below, sus, mapUV, m);
    frag = jj < uN ? vec4(wet, m.x, m.y, m.z) : vec4(albedo, rough);
}
