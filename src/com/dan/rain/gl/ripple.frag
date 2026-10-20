// ripple.frag — Ringe auf dem Wasser als kachelbare Höhenkarte (RIPPLE_TILE m): Ein Gitter von Zellen, jede mit
// mehreren Lagen von Einschlägen in festem Takt und zufälliger Phase. Ein Ring läuft mit 0,3 m/s nach außen,
// wird breiter und klingt in gut einer Sekunde ab; große Tropfen machen höhere Ringe.
in vec2 vUv;
out vec4 frag;
uniform float uTime, uTile, uPeriod, uActive;
uniform int uCells, uLayers;
uniform uint uSeed;

uint h(uint x) { x ^= x >> 16u; x *= 0x7feb352du; x ^= x >> 15u; x *= 0x846ca68bu; x ^= x >> 16u; return x; }
float r01(uint x) { return float(h(x) >> 8u) / 16777216.0; }

void main() {
    float cs = uTile / float(uCells);
    vec2 p = vUv * uTile;
    ivec2 c = ivec2(floor(p / cs));
    float height = 0.0;
    for (int dy = -2; dy <= 2; dy++)
        for (int dx = -2; dx <= 2; dx++) {
            ivec2 cc = c + ivec2(dx, dy);
            ivec2 cw = ivec2((cc.x % uCells + uCells) % uCells, (cc.y % uCells + uCells) % uCells);
            for (int l = 0; l < 4; l++) {
                if (l >= uLayers) break;
                uint s = h(uint(cw.x + cw.y * uCells + l * uCells * uCells) ^ uSeed);
                float phase = r01(s) * uPeriod;
                float e = floor((uTime + phase) / uPeriod);
                float age = uTime + phase - e * uPeriod;
                uint se = h(s ^ uint(int(e)) * 2654435761u);
                if (r01(se) > uActive) continue;
                vec2 at = (vec2(cc) + vec2(r01(se + 1u), r01(se + 2u))) * cs;
                float d = 1.0 + 3.0 * r01(se + 3u) * r01(se + 4u);
                float r = length(p - at);
                float front = r - 0.30 * age;
                float w = 0.02 + 0.05 * age;
                float env = exp(-front * front / (w * w)) * exp(-age * 2.2) / (1.0 + r * 6.0);
                height += 0.0012 * d * env * cos(front * 6.2831853 / 0.07);
            }
        }
    frag = vec4(height, 0.0, 0.0, 1.0);
}
