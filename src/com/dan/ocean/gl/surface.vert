// surface.vert — das Ringgitter um die Kamera: jede Ecke liest die Auslenkung aller Kaskaden an ihrem Ruhepunkt
// (gefiltert nach Eckenabstand, damit kleine Wellen nicht zwischen den Ecken flimmern), dazu die Dünung, und sinkt mit
// der Entfernung um d²/2R unter die Waagerechte (Erdkrümmung, die Kimm liegt richtig).
#include "ocean_common.glsl"
layout(location = 0) in vec2 aPos;      // Lage relativ zum Mittelpunkt in m
layout(location = 1) in float aSpacing; // Eckenabstand an dieser Stelle in m
out vec2 vRest;
out vec3 vWorld;
out float vHeight;
out float vDist;
out float vDepth;

void main() {
    vec2 rest = uCenter + aPos;
    vec3 d = vec3(0.0);
    for (int c = 0; c < 4; c++) {
        if (c >= uCascades) break;
        float texel = uLength[c] / float(textureSize(uDisp, 0).x);
        float lod = max(0.0, log2(aSpacing / texel) + 0.5);
        d += textureLod(uDisp, vec3(rest / uLength[c], float(c)), lod).xyz * cascadeGain(rest, c);
    }
    vec3 sd, ss;
    swell(rest, sd, ss);
    d += sd * swellGain(rest);
    // seit Haithabu: zum Ufer hin flacher (unter 1,5 m Tiefe), auf dem Trockenen ganz still. Seit Rungholt Phase 4 zählt
    // die Tiefe unter dem Spiegel der langen Welle (etwa der Tide je Zelle aus com.dan.tide), nicht unter dem Ruhespiegel
    float depth = bedDepth(rest);
    vec3 lw = longWave(rest);
    // Spiegel über dem Ruhespiegel: das Mittel der nassen Zellen im Umkreis (die Fläche reicht so eine halbe Zelle über
    // die letzte nasse hinaus, die Wasserlinie schneidet das Gelände selbst); ganz trocken: 1 m unter Grund und
    // Ruhespiegel, nie darüber (sonst stiege die Fläche an Deichen hinauf)
    float rise = 0.0;
    if (lw.z > 0.0) {
        rise = lw.y > 0.0 ? lw.x : min(-depth, 0.0) - 1.0;
        // seit Rungholt Phase 6 (uLongSoft): am Rand der nassen Zellen sinkt der Spiegel über eine halbe Zelle unter den
        // Grund, mit Rauschen versetzt; die Wasserlinie schneidet das Gelände dann weich statt in Treppen der Zellen
        if (uLongSoft > 0.0 && lw.y > 0.0) {
            float wn = lw.y + uLongSoft * 0.45 * (vnoise(rest / 23.0) - 0.5 + 0.3 * (vnoise(rest / 9.0 + 9.1) - 0.5));
            rise = mix(min(-depth, 0.0) - 0.3, lw.x, smoothstep(0.2, 0.75, wn));
        }
        depth += rise;            // Tiefe unter dem Spiegel der langen Welle
    }
    d *= smoothstep(0.0, 1.5, depth);
    vec2 flatPos = rest + d.xz;
    float dist = length(flatPos - uCam.xz);
    vec3 world = vec3(flatPos.x, uLevel + rise + d.y - dist * dist / (2.0 * EARTH_R), flatPos.y);
    vRest = rest;
    vWorld = world;
    vHeight = d.y;
    vDist = dist;
    vDepth = depth;
    gl_Position = uVP * vec4(world, 1.0);
}
