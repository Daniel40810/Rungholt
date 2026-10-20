// body.vert — ein Körper (Tonne, Schiff, Brücke, Wischer) mit seiner Modellmatrix.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec3 aCol;
layout(location = 3) in float aMat;
uniform mat4 uVP, uModel;
out vec3 vWorld, vNrm, vCol;
flat out int vMat;
void main() {
    vec4 w = uModel * vec4(aPos, 1.0);
    vWorld = w.xyz;
    vNrm = mat3(uModel) * aNrm;
    vCol = aCol;
    vMat = int(aMat + 0.5);
    gl_Position = uVP * w;
}
