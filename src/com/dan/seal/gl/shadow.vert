// shadow.vert — com.dan.seal (Phase 7): ein Schatten je liegendem oder robbendem Tier, als Viereck auf dem Grund. Es
// deckt den Kontaktschatten unter dem Bauch und den Schlagschatten zur Sonne hin ab.
layout(location = 0) in vec4 iA;        // x, Grund, z, Länge
layout(location = 1) in float iH;       // Richtung
uniform mat4 uVP;
uniform vec3 uSunDir;
out vec2 vLocal;                        // m: längs, quer zur Richtung
out vec2 vSun;                          // Versatz des Schlagschattens (m, längs, quer)
out float vLen;
void main() {
    vec2 c = vec2((gl_VertexID & 1) == 0 ? -1.0 : 1.0, (gl_VertexID & 2) == 0 ? -1.0 : 1.0);
    float L = iA.w, h = iH;
    vec2 F = vec2(sin(h), -cos(h)), R = vec2(cos(h), sin(h));
    float sy = max(uSunDir.y, 0.12);
    vec2 off = -uSunDir.xz / sy * 0.16 * L;
    off *= min(1.0, 1.6 * L / max(length(off), 1e-4));
    vec2 offL = vec2(dot(off, F), dot(off, R));
    float ext = 0.62 * L + length(off);
    vec2 loc = vec2(c.y * ext, c.x * (0.30 * L + length(off)));
    vec2 xz = iA.xz + F * loc.x + R * loc.y;
    vLocal = loc;
    vSun = offL;
    vLen = L;
    gl_Position = uVP * vec4(xz.x, iA.y + 0.05, xz.y, 1.0);
}
