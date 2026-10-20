// sky_view.glsl — Koordinaten der Himmelstabelle: u = Azimut/2π (umlaufend), v nach Hillaire (0 Zenit, 0,5 Kimm,
// 1 Nadir; dicht an der Kimm). Szene x Ost, y oben, z Süd.
const int SKYVIEW_W = 256, SKYVIEW_H = 128;

vec3 skyViewDir(vec2 uv, float r) {
    float vHorizon = sqrt(max(0.0, r * r - R_BOTTOM * R_BOTTOM));
    float beta = acos(clamp(vHorizon / r, -1.0, 1.0));
    float zenithHorizon = PI - beta;
    float zen;
    if (uv.y < 0.5) {
        float c = 1.0 - 2.0 * uv.y;
        c = 1.0 - c * c;
        zen = zenithHorizon * c;
    } else {
        float c = uv.y * 2.0 - 1.0;
        zen = zenithHorizon + beta * c * c;
    }
    float az = uv.x * 2.0 * PI;
    return vec3(sin(zen) * sin(az), cos(zen), -sin(zen) * cos(az));
}

vec2 skyViewUv(vec3 d, float r) {
    float vHorizon = sqrt(max(0.0, r * r - R_BOTTOM * R_BOTTOM));
    float beta = acos(clamp(vHorizon / r, -1.0, 1.0));
    float zenithHorizon = PI - beta;
    float zen = acos(clamp(d.y, -1.0, 1.0));
    float v;
    if (zen < zenithHorizon) {
        float c = zen / zenithHorizon;
        c = 1.0 - sqrt(max(0.0, 1.0 - c));
        v = c * 0.5;
    } else {
        float c = (zen - zenithHorizon) / beta;
        v = sqrt(max(0.0, c)) * 0.5 + 0.5;
    }
    float az = atan(d.x, -d.z) / (2.0 * PI);
    return vec2(fract(az), v);
}
