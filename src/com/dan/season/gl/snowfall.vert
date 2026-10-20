// snowfall.vert — eine Flocke je sechs Ecken (zwei Dreiecke zur Kamera), Ort aus der Nummer: fester Platz im Würfel um
// das Auge, Sinken, Taumeln, Wind; am Rand des Würfels wiederkehrend. Größe 3 bis 9 mm, in der Ferne mindestens 1,5 px.
uniform mat4 uVP;
uniform vec3 uEye;
uniform float uTime;
uniform vec3 uWind;
uniform vec2 uBox;        // Kante (m), Sinkgeschwindigkeit (m/s)
uniform float uPx;        // Pixel je Bogenmaß
out vec2 vQuad;
out float vAlpha;
out float vDist;
out vec3 vDir;

float h1(uint n) {
    n = (n << 13u) ^ n;
    n = n * (n * n * 15731u + 789221u) + 1376312589u;
    return float(n & 0x7fffffffu) / float(0x7fffffff);
}

void main() {
    uint id = uint(gl_VertexID) / 6u;
    int c = gl_VertexID % 6;
    vec2 q = vec2((c == 1 || c == 4 || c == 5) ? 1.0 : -1.0, (c == 2 || c == 3 || c == 5) ? 1.0 : -1.0);
    float B = uBox.x;
    vec3 r = vec3(h1(id * 3u + 1u), h1(id * 3u + 2u), h1(id * 3u + 3u));
    float speed = uBox.y * (0.7 + 0.6 * h1(id * 7u + 11u));
    float ph = h1(id * 5u + 4u) * 6.2832, fr = 0.35 + 0.4 * h1(id * 5u + 9u);
    vec3 p = r * B + vec3(0.0, -speed, 0.0) * uTime + uWind * uTime
           + vec3(0.25 * sin(uTime * fr * 2.1 + ph), 0.0, 0.25 * cos(uTime * fr * 1.7 + ph * 1.3));
    // in den Würfel um das Auge falten
    p = uEye + mod(p - uEye + 0.5 * B, B) - 0.5 * B;
    vec3 d = p - uEye;
    float dist = length(d);
    vec3 f = d / max(dist, 1e-3);
    vec3 right = normalize(cross(f, vec3(0.0, 1.0, 0.0)) + vec3(1e-4, 0.0, 0.0));
    vec3 up = cross(right, f);
    float size = 0.003 + 0.006 * h1(id * 13u + 2u);
    float minSize = 1.5 * dist / uPx;
    float s = max(size, minSize);
    // Fläche bleibt erhalten: größere Scheibe, durchsichtiger; am Rand des Würfels ausblenden
    float a = (size * size) / (s * s);
    a *= 1.0 - smoothstep(0.30 * B, 0.48 * B, dist);
    a *= smoothstep(0.25, 0.6, dist);
    vAlpha = a;
    vQuad = q;
    vDist = dist;
    vDir = f;
    gl_Position = uVP * vec4(p + (right * q.x + up * q.y) * s, 1.0);
}
