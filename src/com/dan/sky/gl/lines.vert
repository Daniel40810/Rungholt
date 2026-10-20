// lines.vert — Sternbildfiguren (aus Sternbild) als dünne Linien am Himmel; Strahlenbrechung wie die Sterne.
#include "sky_common.glsl"
in vec3 aDir;
uniform mat4 uVP;
uniform mat3 uEqToScene;
uniform float uEyeR;
out float vVis;
void main() {
    vec3 d = normalize(uEqToScene * aDir);
    float alt = degrees(asin(clamp(d.y, -1.0, 1.0)));
    d = dirFromAltAz(radians(alt + refractionTrue(alt)), azimuthOf(d));
    vVis = smoothstep(-0.002, 0.02, d.y);
    vec4 c = uVP * vec4(d, 0.0);
    c.z = 0.5 * c.w;
    gl_Position = c;
}
