// scene.vert — Demo-Szene von com.dan.rain: leicht bewegtes Wasser aus vier Wellenzügen, Pfähle und ein Steg.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in float aKind;      // 0 Wasser, 1 Pfahl oder Steg
uniform mat4 uVP;
uniform float uT, uAmp;
out vec3 vPos, vNormal;
flat out int vKind;
const vec2 K[4] = vec2[](vec2(0.21, 0.05), vec2(-0.08, 0.31), vec2(0.45, -0.22), vec2(0.13, 0.62));
const float PH[4] = float[](0.0, 1.7, 3.1, 4.4);
void main() {
    vec3 p = aPos;
    vec3 n = aNormal;
    vKind = int(aKind + 0.5);
    if (vKind == 0) {
        float h = 0.0, dx = 0.0, dz = 0.0;
        for (int i = 0; i < 4; i++) {
            float k = length(K[i]), w = sqrt(9.81 * k), a = uAmp / (1.0 + float(i));
            float th = dot(K[i], p.xz) - w * uT + PH[i];
            h += a * cos(th);
            dx -= a * sin(th) * K[i].x;
            dz -= a * sin(th) * K[i].y;
        }
        p.y = h;
        n = normalize(vec3(-dx, 1.0, -dz));
    }
    vPos = p;
    vNormal = n;
    gl_Position = uVP * vec4(p, 1.0);
}
