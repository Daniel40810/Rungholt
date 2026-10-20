// ice.vert — ein Rechteck über dem Grund in Höhe des Eises (zwei Dreiecke aus der Nummer der Ecke).
uniform mat4 uVP;
uniform vec4 uRect;      // x, z der Nordwestecke, Kantenlänge (m), Höhe der Eisfläche (m)
out vec3 vWorld;
void main() {
    int c = gl_VertexID % 6;
    vec2 q = vec2((c == 1 || c == 4 || c == 5) ? 1.0 : 0.0, (c == 2 || c == 3 || c == 5) ? 1.0 : 0.0);
    vec3 p = vec3(uRect.x + q.x * uRect.z, uRect.w, uRect.y + q.y * uRect.z);
    vWorld = p;
    gl_Position = uVP * vec4(p, 1.0);
}
