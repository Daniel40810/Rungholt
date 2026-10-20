// terrain.vert — Gelände aus dem Viererbaum (CDLOD, aus Glacier und Haithabu). Ein Gitter je Knoten, Lage und Größe je Instanz.
// Eingaben: aGrid (0..1 im Knoten), aNode (x0, z0, Kante in m, Stufe). Höhe aus terrain_height.glsl.
// Geomorphing: im äußeren Teil ihres Bereichs gleiten die ungeraden Punkte auf die Lage der gröberen Stufe; Höhe und
// Mip-Stufe hängen nur von der Endlage ab, darum passen Nachbarknoten. Ausgaben: vWorld, vNormal (grob), vDist.
// Phase 6: auf dem Watt die Feinform aus com.dan.mudflat (mudflat_relief.glsl): Prielkanten mit Abbruch bis 3 km,
// Rinnsale und Wellenrücken bis 400 m (dort ist das Gitter 2 m fein); vSmooth gibt dem Pixelshader die geglättete
// Höhe und ihre Neigung für die Rinnsale.
#include "common.glsl"
#include "terrain_height.glsl"
#include "/com/dan/mudflat/gl/mudflat_relief.glsl"
layout(location = 0) in vec2 aGrid;
layout(location = 1) in vec4 aNode;
uniform float uGridDim;
out vec3 vWorld;
out vec3 vNormal;
out float vDist;
out vec2 vSmooth;

// Grund mit Feinform: Raster, auf dem Watt (Anteil watt) dazu Kante, Rinnsale und Wellenrücken
float groundAt(vec2 p, float d, float watt, out vec2 sm) {
    float hf = terrainHeight(p, d);
    // Phase 13: der Kleirücken der Spuren (heute, auf dem Watt)
    float sp = watt > 0.0 && d < 3000.0 && uSpuren.x * (1.0 - uEra) > 0.0 ? spurHeight(p) * watt : 0.0;
    sm = vec2(hf, 0.0);
    if (watt <= 0.0 || d > 3000.0 || uMudRelief.w <= 0.0) return hf + sp;
    float hs = heightAt(p, 2.0);
    vec2 g = vec2(heightAt(p + vec2(16.0, 0.0), 2.0) - heightAt(p - vec2(16.0, 0.0), 2.0),
                  heightAt(p + vec2(0.0, 16.0), 2.0) - heightAt(p - vec2(0.0, 16.0), 2.0)) / 32.0;
    float sl = length(g);
    sm = vec2(hs, sl);
    float r = mudEdge(p, hf, hs, sl) * (1.0 - smoothstep(1500.0, 3000.0, d)) + mudRunnelDepth(p, hs, sl) * (1.0 - smoothstep(250.0, 420.0, d));
    return hf + r * watt + sp;
}

void main() {
    vec2 w = aNode.xy + aGrid * aNode.z;
    float hc = heightAt(w, 3.0);
    float d0 = distance(vec3(w.x, hc, w.y), uCam);
    float end = uLodRange.x * exp2(aNode.w), start = end * uLodRange.y;
    float morph = clamp((d0 - start) / (end * 0.97 - start), 0.0, 1.0);
    vec2 frac = fract(aGrid * uGridDim * 0.5) * 2.0 / uGridDim;
    w -= frac * aNode.z * morph;
    hc = heightAt(w, 3.0);
    float d = distance(vec3(w.x, hc, w.y), uCam);
    float watt = d < 3000.0 ? artShare(w, 0u) : 0.0;
    vec2 sm, tmp;
    float h = groundAt(w, d, watt, sm);
    vec3 world = vec3(w.x, h, w.y);
    float e = clamp(d / (64.0 * uLodRange.z), 0.75, 400.0);
    float hx0 = groundAt(w - vec2(e, 0.0), d, watt, tmp), hx1 = groundAt(w + vec2(e, 0.0), d, watt, tmp);
    float hz0 = groundAt(w - vec2(0.0, e), d, watt, tmp), hz1 = groundAt(w + vec2(0.0, e), d, watt, tmp);
    vSmooth = sm;
    vNormal = normalize(vec3(hx0 - hx1, 2.0 * e, hz0 - hz1));
    vWorld = world;
    vDist = d;
    // Erdkrümmung wie auf dem Meer (com.dan.ocean): in 10 km liegt der Boden 7,8 m tiefer als seine Höhe sagt
    vec2 dh = world.xz - uCam.xz;
    gl_Position = uVP * vec4(world.x, world.y - dot(dh, dh) / (2.0 * EARTH_R), world.z, 1.0);
}
