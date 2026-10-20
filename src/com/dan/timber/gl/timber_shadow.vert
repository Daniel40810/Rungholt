// timber_shadow.vert — Tiefe aus Richtung der Sonne für die Schattenkarte: dieselbe Lage wie timber.vert.
layout(location = 0) in vec3 aPos;
layout(location = 4) in vec4 iA;
layout(location = 5) in vec4 iB;
uniform mat4 uLightVP;

void main() {
    vec3 p = aPos * vec3(iB.x, 1.0, iB.y);
    // seit Haithabu Phase 15: ein eingestürztes Haus ist ein niedriger Haufen
    float burn = floor(iB.w * 0.5) / 255.0;
    if (burn > 1.0) p.y *= 0.28;
    float s = sin(iA.w), c = cos(iA.w);
    vec3 w = vec3(p.x * s + p.z * c, p.y, -p.x * c + p.z * s) + iA.xyz;
    gl_Position = uLightVP * vec4(w, 1.0);
}
