// check.vert — Selbsttest: Punkte (uP[i]) mit der Modellmatrix, wie Floater.toWorld; je Bildpunkt einer.
uniform vec4 uP[64];
uniform mat4 uModel;
out vec4 vOut;
void main() {
    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
    vOut = vec4(0.0);
}
