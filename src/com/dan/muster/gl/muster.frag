#version 430 core
// muster.frag — zeigt ein bewegtes Feld; Platzhalter für den Renderer eines echten Packages.
in vec2 vUV;
out vec4 frag;
uniform float uStrength;
uniform float uTime;
uniform float uSeed;
void main() {
    float v = 0.5 + 0.5 * sin(vUV.x * 12.0 + uTime + uSeed) * sin(vUV.y * 9.0 - uTime * 0.7) * uStrength;
    frag = vec4(mix(vec3(0.04, 0.10, 0.16), vec3(0.25, 0.70, 0.66), v), 1.0);
}
