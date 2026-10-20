// sea.vert — die See der Vorführung: ein Gitter um das Auge, gehoben von denselben zwölf Wellenzügen wie DemoSea.
layout(location = 0) in vec2 aGrid;
uniform mat4 uVP;
uniform vec3 uCam;
uniform vec4 uWave[12];   // Amplitude, kx, kz, ω
uniform float uPhase[12];
uniform float uTime, uLevel, uCell;
out vec3 vWorld, vNrm;
void main() {
    vec2 c = floor(uCam.xz / uCell) * uCell;
    // Abstand wächst nach außen (feiner nah am Auge)
    vec2 g = aGrid;
    float r = length(g);
    vec2 xz = c + g * (1.0 + r * r * 0.00004);
    float h = uLevel, dx = 0.0, dz = 0.0;
    for (int i = 0; i < 12; i++) {
        vec4 w = uWave[i];
        float th = w.y * xz.x + w.z * xz.y - w.w * uTime + uPhase[i];
        h += w.x * cos(th);
        dx -= w.x * w.y * sin(th);
        dz -= w.x * w.z * sin(th);
    }
    vWorld = vec3(xz.x, h, xz.y);
    vNrm = normalize(vec3(-dx, 1.0, -dz));
    gl_Position = uVP * vec4(vWorld, 1.0);
}
