// ice.vert — ein Eiskörper: Lage im Körper (für Risse, Bänder, Glitzer, die mit ihm wandern), in der Welt, Normale,
// Verdeckung (Spaltengrund, Kerbe).
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in float aAo;   // Verdeckung je Ecke (ohne Feld: 1)
uniform mat4 uVP, uModel;
out vec3 vW, vN, vL;
out float vAo;
void main() {
    vec4 w = uModel * vec4(aPos, 1.0);
    vW = w.xyz;
    vN = normalize(mat3(uModel) * aNrm);
    vL = aPos;
    vAo = aAo;
    gl_Position = uVP * w;
}
