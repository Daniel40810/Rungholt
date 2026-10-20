// scene.vert — Demo-Szene von com.dan.spray: Wasser aus zwölf Wellenzügen (wie SineSea) und Körper.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in float aKind;      // 0 Wasser, 1 Körper
uniform mat4 uVP;
uniform float uT, uLevel;
uniform float uA[12], uW[12], uPh[12];
uniform vec2 uK[12];
out vec3 vPos, vNormal;
flat out int vKind;
void main() {
    vec3 p = aPos;
    vec3 n = aNormal;
    vKind = int(aKind + 0.5);
    if (vKind == 0) {
        float h = uLevel, dx = 0.0, dz = 0.0;
        for (int i = 0; i < 12; i++) {
            float th = dot(uK[i], p.xz) - uW[i] * uT + uPh[i];
            h += uA[i] * cos(th);
            dx -= uA[i] * sin(th) * uK[i].x;
            dz -= uA[i] * sin(th) * uK[i].y;
        }
        p.y = h;
        n = normalize(vec3(-dx, 1.0, -dz));
    }
    vPos = p;
    vNormal = n;
    gl_Position = uVP * vec4(p, 1.0);
}
