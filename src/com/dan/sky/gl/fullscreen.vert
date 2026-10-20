// fullscreen.vert — ein Dreieck über das ganze Ziel, vUV 0..1. Ohne Eckpunktpuffer (gl_VertexID).
out vec2 vUV;
void main() {
    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    vUV = p;
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
