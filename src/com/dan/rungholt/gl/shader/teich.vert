// teich.vert — Phase 9: Fething und Pütten als ebene Fläche; aLocal: Ort im Teich (m) und Rand 0..1.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aLocal;
uniform mat4 uVP;
out vec3 vWorld, vLocal;
void main() {
    vWorld = aPos;
    vLocal = aLocal;
    gl_Position = uVP * vec4(aPos, 1.0);
}
