package com.dan.mudflat;

import java.util.Locale;

/**
 * Die Feinform des Watts unter 10 m (Phase 6), dieselbe Rechnung wie {@code gl/mudflat_relief.glsl} (gleiche
 * Ganzzahl-Hashes, Gleitkomma in float): Prielkante mit Abbruch, Rinnsale, Wellenrücken. Die App legt sie über ihr
 * Höhenraster; Kamera, Tiere und Prüfungen fragen hier, das Bild im Shader.
 * <ul>
 * <li><b>Kante</b>: wo der Grund nahe seinem geglätteten Mittel liegt und der Hang mehr als 2 % fällt, wird er steiler
 * (höchstens {@link #edge} m) und bekommt oben eine Stufe von 0,3 m; Buchten aus Rauschen (5 bis 15 m) [G].</li>
 * <li><b>Rinnsale</b>: ein Netz flacher Rinnen (grob 20 bis 30 m Abstand, fein 7 m), {@link #runnel} m tief, nicht in
 * Prielen und nicht an der Kante; dort läuft das Wasser bei Ebbe zuletzt ab [G].</li>
 * <li><b>Wellenrücken</b>: flache Buckel von 3 bis 5 m, 5 cm hoch.</li>
 * </ul>
 */
public final class Relief {
    /** Höchste Änderung an der Kante (m) und Tiefe der Rinnsale (m) [G]. */
    public float edge = 0.45f, runnel = 0.12f;
    public int seed = 1362;
    public boolean on = true;

    static int hashU(int x, int z, int salt, int seed) {
        int h = x * 374761393 + z * 668265263 + salt * (int) 2246822519L + seed * (int) 3266489917L;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    float hash(int x, int z, int salt) { return (hashU(x, z, salt, seed) & 16777215) / 16777216f; }

    float noise(float px, float pz, int salt) {
        float fx0 = (float) Math.floor(px), fz0 = (float) Math.floor(pz);
        float fx = px - fx0, fz = pz - fz0;
        float ux = fx * fx * (3 - 2 * fx), uz = fz * fz * (3 - 2 * fz);
        int ix = (int) fx0, iz = (int) fz0;
        float a = hash(ix, iz, salt), b = hash(ix + 1, iz, salt), d = hash(ix, iz + 1, salt), e = hash(ix + 1, iz + 1, salt);
        float ab = a + (b - a) * ux, de = d + (e - d) * ux;
        return ab + (de - ab) * uz;
    }

    static float smooth(float a, float b, float x) { float t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); }

    /** Rinnsale am Ort: {grob, fein}, je 0 außerhalb … 1 in der Mitte einer Rinne. */
    public float[] runnels(double x, double z) {
        float fx = (float) x, fz = (float) z;
        float wx = noise(fx / 31f, fz / 31f, 7) - 0.5f, wz = noise(fx / 31f + 17f, fz / 31f + 17f, 7) - 0.5f;
        float n1 = noise(fx / 23f + wx * 1.6f, fz / 23f + wz * 1.6f, 3);
        float r1 = 1 - smooth(0, 0.055f, Math.abs(n1 - 0.5f));
        float vx = noise(fx / 11f, fz / 11f, 9) - 0.5f, vz = noise(fx / 11f + 5f, fz / 11f + 5f, 9) - 0.5f;
        float n2 = noise(fx / 7f + vx * 1.2f, fz / 7f + vz * 1.2f, 5);
        float r2 = (1 - smooth(0, 0.07f, Math.abs(n2 - 0.5f))) * smooth(0.35f, 0.65f, noise(fx / 40f, fz / 40f, 11));
        return new float[]{r1, r2};
    }

    /** Kante und Wellenrücken: Änderung der Höhe (m) aus der Rasterhöhe hf, der geglätteten hs und deren Neigung. */
    public float edge(double x, double z, double hf, double hs, double slope) {
        if (!on) return 0;
        float fx = (float) x, fz = (float) z, sl = (float) slope;
        float scallop = (noise(fx / 9f, fz / 9f, 1) - 0.5f) * 0.7f + (noise(fx / 3.3f, fz / 3.3f, 2) - 0.5f) * 0.25f;
        float e = (float) (hf - hs) + scallop;
        float w = smooth(0.02f, 0.07f, sl);
        float steep = Math.max(-edge, Math.min(edge, 1.6f * e)) * (float) Math.exp(-e * e / 1.6f);
        float lip = 0.3f * (smooth(-0.06f, 0.06f, e - 0.15f) - 0.5f) * smooth(0.03f, 0.08f, sl);
        float bumps = 0.05f * (noise(fx / 4.1f, fz / 4.1f, 4) - 0.5f) * 2 * (1 - w);
        return (steep + lip) * w + bumps;
    }

    /** Tiefe der Rinnsale (m, negativ), nicht in Prielen (hs unter NHN −1,8 m) und nicht an der Kante. */
    public float runnelDepth(double x, double z, double hs, double slope) {
        if (!on) return 0;
        float[] r = runnels(x, z);
        float where = smooth(-1.8f, -1.2f, (float) hs) * (1 - smooth(0.04f, 0.09f, (float) slope));
        return -runnel * (r[0] + 0.45f * r[1]) * where;
    }

    /** Die ganze Feinform (m). */
    public float delta(double x, double z, double hf, double hs, double slope) { return edge(x, z, hf, hs, slope) + runnelDepth(x, z, hs, slope); }

    /** Die Werte für {@code uMudRelief}: Kante, Rinnsale, Startwert, an. */
    public float[] uniform() { return new float[]{edge, runnel, seed % 4096, on ? 1 : 0}; }

    public String describe() {
        return String.format(Locale.GERMAN, "Feinform: Kante bis %.2f m mit Stufe 0,3 m, Rinnsale %.0f cm tief%s", edge, runnel * 100, on ? "" : " (aus)");
    }
}
