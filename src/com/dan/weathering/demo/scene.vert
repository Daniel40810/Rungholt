// scene.vert — Demo-Szene: Pfahl, Steinblock, Wasser. Ecken: Lage, Normale, Flächenkoordinaten (s, t), Kante oben, Art.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aSt;
layout(location = 3) in vec2 aEdge;    // y der Kante darüber, Plattenhöhe (0 = keine Stöße)
layout(location = 4) in float aKind;   // 0 Stahl, 1 Stein, 2 Wasser
uniform mat4 uVP;
uniform float uLevel, uTime, uHs;
out vec3 vPos, vNormal;
out vec2 vSt, vEdge;
flat out int vKind;
void main() {
    vec3 p = aPos;
    vec3 n = aNormal;
    vKind = int(aKind + 0.5);
    if (vKind == 2) {
        float a = 0.25 * uHs;
        float w1 = sin(p.x * 0.55 + uTime * 1.3) * a, w2 = sin(p.z * 0.8 - uTime * 1.7 + p.x * 0.3) * a * 0.6;
        p.y = uLevel + w1 + w2;
        n = normalize(vec3(-cos(p.x * 0.55 + uTime * 1.3) * 0.55 * a - cos(p.z * 0.8 - uTime * 1.7 + p.x * 0.3) * 0.3 * a * 0.6, 1.0,
                           -cos(p.z * 0.8 - uTime * 1.7 + p.x * 0.3) * 0.8 * a * 0.6));
    }
    vPos = p;
    vNormal = n;
    vSt = aSt;
    vEdge = aEdge;
    gl_Position = uVP * vec4(p, 1.0);
}
