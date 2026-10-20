// backdrop.frag — für Szenen ohne eigenes Himmels-Package (Demo): Modus 0 zeichnet das Himmelspanorama hinter die
// See, Modus 1 bringt das HDR-Bild mit Belichtung und Filmkurve ins Fenster.
in vec2 vUV;
out vec4 frag;
uniform int uMode;
uniform mat4 uInvVP;
uniform sampler2D uTex;
uniform float uExposure;

void main() {
    if (uMode == 0) {
        vec2 ndc = vUV * 2.0 - 1.0;
        vec4 a = uInvVP * vec4(ndc, -1.0, 1.0), b = uInvVP * vec4(ndc, 1.0, 1.0);
        vec3 d = normalize(b.xyz / b.w - a.xyz / a.w);
        float az = atan(d.x, -d.z) / 6.28318530718 + 0.5;
        float el = asin(clamp(d.y, -1.0, 1.0)) / 3.14159265359 + 0.5;
        frag = vec4(textureLod(uTex, vec2(az, el), 0.0).rgb, 1.0);
    } else {
        vec3 x = texture(uTex, vUV).rgb * uExposure;
        vec3 c = clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
        frag = vec4(pow(c, vec3(1.0 / 2.2)), 1.0);
    }
}
