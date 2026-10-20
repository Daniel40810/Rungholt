// lightning.glsl — Licht der Blitze für fremde Shader (Paket com.dan.lightning). Bis zu acht Lichtpunkte, die
// LightningGl.bind setzt: Ort in m (Szenenraum) und Lichtstärke als RGB in cd mal Vorbelichtung. Licht aus der Wolke
// ist schon auf die Untergrenze gesetzt und gedämpft; ltgPos.w ist die Größe der Quelle in m (dort hört 1/d² auf zu
// wachsen: ein Stück Kanal einige zehn Meter, die leuchtende Wolkenunterseite rund einen Kilometer). Ohne Schatten.
//   ltgIrradiance(p, n)  Beleuchtungsstärke auf eine Fläche mit Normale n (vorbelichtet, lx)
//   ltgIrradiance(p)     dasselbe quer zum Licht (für Nebel, Tropfen, Partikel)
//   ltgSpecular(p, n, v, a)  Glanz nach GGX mit Rauigkeit a (Leuchtdichte, vorbelichtet)
#define LTG_MAX 8
#define LTG_PI 3.14159265
uniform int ltgCount;
uniform vec4 ltgPos[LTG_MAX];
uniform vec4 ltgRgb[LTG_MAX];
uniform float ltgSigma;

vec3 ltgIrradiance(vec3 p, vec3 n) {
    vec3 e = vec3(0.0);
    for (int i = 0; i < LTG_MAX; i++) {
        if (i >= ltgCount) break;
        vec3 l = ltgPos[i].xyz - p;
        float d = sqrt(dot(l, l) + 1.0), d2 = dot(l, l) + ltgPos[i].w * ltgPos[i].w;
        e += ltgRgb[i].rgb * max(0.0, dot(n, l / d)) * exp(-ltgSigma * d) / d2;
    }
    return e;
}

vec3 ltgIrradiance(vec3 p) {
    vec3 e = vec3(0.0);
    for (int i = 0; i < LTG_MAX; i++) {
        if (i >= ltgCount) break;
        vec3 l = ltgPos[i].xyz - p;
        float d2 = dot(l, l) + ltgPos[i].w * ltgPos[i].w;
        e += ltgRgb[i].rgb * exp(-ltgSigma * sqrt(dot(l, l))) / d2;
    }
    return e;
}

vec3 ltgSpecular(vec3 p, vec3 n, vec3 v, float a) {
    vec3 s = vec3(0.0);
    float a2 = max(1e-4, a * a);
    for (int i = 0; i < LTG_MAX; i++) {
        if (i >= ltgCount) break;
        vec3 l = ltgPos[i].xyz - p;
        float d = sqrt(dot(l, l) + 1.0), d2 = dot(l, l) + ltgPos[i].w * ltgPos[i].w;
        l /= d;
        float nl = dot(n, l), nv = max(1e-3, dot(n, v));
        if (nl <= 0.0) continue;
        vec3 h = normalize(l + v);
        float nh = max(0.0, dot(n, h));
        float dd = nh * nh * (a2 - 1.0) + 1.0;
        float D = a2 / (LTG_PI * dd * dd);
        float k = a * 0.5;
        float G = nl / (nl * (1.0 - k) + k) * nv / (nv * (1.0 - k) + k);
        float F = 0.02 + 0.98 * pow(1.0 - max(0.0, dot(h, v)), 5.0);
        s += ltgRgb[i].rgb * exp(-ltgSigma * d) / d2 * D * G * F / (4.0 * nv);
    }
    return s;
}
