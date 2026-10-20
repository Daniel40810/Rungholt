// snowfall.frag — weiche helle Scheibe; Licht vom Himmel ringsum und ein Teil von Sonne und Mond (Flocken streuen
// fast gleichmäßig, etwas mehr vorwärts); Dunst nach Entfernung. Vormultipliziert gemischt.
uniform vec3 uSunDir, uSunColor, uMoonDir, uMoonColor, uSky, uFogColor;
uniform float uFogDist;
in vec2 vQuad;
in float vAlpha;
in float vDist;
in vec3 vDir;
out vec4 frag;
void main() {
    float r2 = dot(vQuad, vQuad);
    if (r2 > 1.0) discard;
    float a = vAlpha * (1.0 - r2 * r2) * 0.95;
    const float alb = 0.8 / 3.14159;
    float fwd = 0.6 + 0.8 * pow(max(dot(vDir, uSunDir), 0.0), 3.0);
    vec3 col = alb * (uSky * 1.6 + uSunColor * 0.5 * fwd * smoothstep(-0.02, 0.05, uSunDir.y)
                      + uMoonColor * 0.5 * smoothstep(-0.02, 0.05, uMoonDir.y));
    col = mix(col, uFogColor, 1.0 - exp(-vDist / max(1.0, uFogDist)));
    frag = vec4(col * a, a);
}
