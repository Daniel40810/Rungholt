// ground.vert — Bodendecke aus com.dan.ground, schon bewegt (Welt). Eingaben: Lage, Normale, Farbe (linear),
// fest (1 Stein/Erde, 0 dünn), Ausblenden 0..1.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec3 aCol;
layout(location = 3) in float aSolid;
layout(location = 4) in float aFade;
uniform mat4 uVP;
out vec3 vWorld;
out vec3 vNormal;
out vec3 vColor;
out float vSolid;
out float vFade;
void main() {
    vWorld = aPos;
    vNormal = aNrm;
    vColor = aCol;
    vSolid = aSolid;
    vFade = aFade;
    gl_Position = uVP * vec4(aPos, 1.0);
}
