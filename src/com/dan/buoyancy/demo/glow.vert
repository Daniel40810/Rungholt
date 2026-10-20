// glow.vert — ein Licht als Punkt; Größe wächst mit der Stärke
layout(location = 0) in vec4 aPos;      // x, y, z, Stärke (vorbelichtet)
layout(location = 1) in vec3 aCol;
uniform mat4 uVP;
uniform float uScale;
out vec3 vCol;
void main() {
    gl_Position = uVP * vec4(aPos.xyz, 1.0);
    vCol = aCol * aPos.w;
    gl_PointSize = clamp(3.0 + sqrt(aPos.w) * uScale, 3.0, 64.0);
}
