package com.dan.sky;

import com.dan.sky.astro.FAstroTime;
import com.dan.sky.astro.FGeoLocation;
import com.dan.sky.astro.FMoonEphemeris;
import com.dan.sky.astro.FSun;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Der Himmel als Zahlen: Sonne, Mond und Planeten nach Datum, Uhrzeit und Ort, die Streuung in der Lufthülle mit
 * Dunst nach Sichtweite, der natürliche Nachthimmel, Lichtglocken ferner Städte, ein Bewölkungsschleier bis zu den
 * echten Wolken, Regenbogen und Blitzleuchten. Daraus das Licht für die Szene: Sonne und Mond nach der Auslöschung,
 * Himmelslicht, Dunst, Anpassungshelligkeit fürs Auge und Grenzgröße der Sterne.
 * <p>
 * Der Kern braucht nur Java. Die Grafikkarte ({@code gl/SkyGl}) zeichnet denselben Himmel; was sie braucht, steht im
 * {@link State}. Alle Eingänge sind öffentlich und zur Laufzeit änderbar; {@link #update()} rechnet nach, was sich
 * geändert hat, und ist billig, wenn sich nichts geändert hat.
 * <p>
 * Einheiten: Bestrahlung und Strahldichte in Einheiten der Szene, die Sonne bringt außerhalb der Atmosphäre
 * {@link #SUN_TOA}. Eine Einheit Strahldichte sind {@link #CD_PER_UNIT} cd/m².
 */
public final class Sky {
    /** Bestrahlung der Sonne außerhalb der Atmosphäre je Kanal (Szeneneinheiten; am Boden rund 2,3 bei hoher Sonne). */
    public static final double SUN_TOA = 2.9;
    /** Eine Einheit Strahldichte in cd/m² (eine Einheit Bestrahlung in lx): 127 500 lx Sonne außerhalb der Atmosphäre. */
    public static final double CD_PER_UNIT = 127_500 / SUN_TOA;
    /** Natürlicher Nachthimmel im Zenit, 22,0 mag/″² (Eigenleuchten der Luft und Sternlicht), Farbe leicht grünlich. */
    public static final double NIGHT_LUMINANCE = 1.08e5 * Math.pow(10, -0.4 * 22.0) / CD_PER_UNIT;
    static final double[] NIGHT_COLOR = {0.86, 1.02, 1.04};
    /** Scheinbare Helligkeit der Sonne. */
    public static final double SUN_MAG = -26.74;
    /** Leuchtdichte, bei der {@link State#exposure} die Mitte des Bilds auf 0,35 legt: Sonne 30° hoch, Sicht 40 km. */
    public static final double REFERENCE_LUMINANCE = 0.105;

    // ------------------------------------------------------------------ Eingänge

    /** Zeit in ms seit 1970 (UTC). */
    public volatile double epochMillis = System.currentTimeMillis();
    /** Ort in Grad (Nord, Ost) und Augenhöhe über dem Meer in m. */
    public volatile double latDeg = 54.0, lonDeg = 8.0, eyeHeight = 10;
    /** Meteorologische Sicht in km (1 bis 220) und Albedo des Bodens (Meer rund 0,08). */
    public volatile double visibilityKm = 40, groundAlbedo = 0.08;
    /** Bedeckung 0..1. Solange kein Wolken-Package zeichnet, graut ein Schleier den Himmel ein ({@link #cloudVeil}). */
    public volatile double cloudCover;
    public volatile boolean cloudVeil = true;
    /** Regenbogen 0..1 (Regen vor dem Betrachter, Sonne im Rücken; die Wetterregie setzt ihn nach dem Schauer). */
    public volatile double rainbow;
    /** Was gezeichnet wird. */
    public volatile boolean stars = true, planets = true, milkyWay = true, figures;
    /** Verstärkung der Sterne (1 = wie gemessen). */
    public volatile double starGain = 1;
    /** Lichtglocken ferner Orte über der Kimm. */
    public final List<LightDome> domes = new CopyOnWriteArrayList<>();

    /** Eine Lichtglocke: Richtung (Grad von Nord), Entfernung in km, Stärke (1 = fünfmal der Nachthimmel an der Kimm). */
    public record LightDome(String name, double azDeg, double distanceKm, double strength) { }

    // ------------------------------------------------------------------ Zustand

    private volatile State state;
    private volatile Scattering scattering;
    private volatile boolean rebuilding;
    private double builtVisibility = -1, builtAlbedo = -1;
    private int atmosphereVersion;
    private final ExecutorService builder = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sky-atmosphere");
        t.setDaemon(true);
        return t;
    });
    private double astroEpoch = Double.NaN, astroLat = Double.NaN, astroLon = Double.NaN;
    private Astro astro;
    private double time;
    private final List<double[]> flashes = new ArrayList<>();
    private String lightKey = "";
    private boolean lightPending;
    private Light light;

    public Sky() { }

    /** Die letzten vier gebauten Atmosphären (Hin- und Herschalten der Sicht, Prüfungen): Bau kostet rund 0,3 s. */
    private static final java.util.Map<String, Scattering> CACHE = new java.util.LinkedHashMap<>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Scattering> e) { return size() > 4; }
    };

    static Scattering scatteringFor(double vis, double albedo) {
        String key = String.format(Locale.ROOT, "%.3f|%.4f", vis, albedo);
        synchronized (CACHE) {
            Scattering s = CACHE.get(key);
            if (s != null) return s;
        }
        Scattering s = new Scattering(Atmosphere.forVisibility(vis, albedo));
        synchronized (CACHE) { CACHE.put(key, s); }
        return s;
    }

    /** Himmel für Zeit und Ort. */
    public Sky(double epochMillis, double latDeg, double lonDeg) {
        this.epochMillis = epochMillis;
        this.latDeg = latDeg;
        this.lonDeg = lonDeg;
    }

    /** Laufzeit weiterzählen (für Blitze) und nachrechnen. */
    public State step(double dt) {
        synchronized (this) { time += dt; }
        return update();
    }

    /**
     * Ein Blitz (dieselbe Form wie {@code Flash} der Verdrahtung): Ort des Kanals in m relativ zum Betrachter (x Ost,
     * y oben, z Süd), Stärke 0..1, Laufzeit in s, Dauer in s. Der Himmel leuchtet um die Richtung auf, mit
     * Nachblitzen.
     */
    public synchronized void flash(double x, double y, double z, double strength, double t, double duration) {
        double n = Math.sqrt(x * x + y * y + z * z);
        if (n < 1) return;
        flashes.add(new double[]{x / n, y / n, z / n, Math.max(0, Math.min(1, strength)), t, Math.max(0.05, duration), n});
        if (flashes.size() > 8) flashes.remove(0);
    }

    /**
     * Nachrechnen, was sich geändert hat. Atmosphäre und Licht rechnen bei Änderungen im Hintergrund weiter; der
     * Zustand trägt bis dahin die letzten Werte. Für Standbilder und Prüfungen {@link #updateNow()}.
     */
    public State update() { return update(false); }

    /** Wie {@link #update()}, aber Licht sofort (Atmosphäre: {@link #rebuildNow()}). */
    public State updateNow() { return update(true); }

    private synchronized State update(boolean sync) {
        // Atmosphäre: beim ersten Mal sofort, danach im Hintergrund
        double vis = Math.max(1, Math.min(Atmosphere.MAX_VISIBILITY_KM, visibilityKm)), alb = groundAlbedo;
        if (scattering == null) {
            scattering = scatteringFor(vis, alb);
            builtVisibility = vis;
            builtAlbedo = alb;
            atmosphereVersion++;
        } else if (!rebuilding && (Math.abs(vis / builtVisibility - 1) > 0.01 || Math.abs(alb - builtAlbedo) > 1e-3)) {
            rebuilding = true;
            builtVisibility = vis;
            builtAlbedo = alb;
            builder.execute(() -> {
                Scattering s = scatteringFor(vis, alb);
                synchronized (Sky.this) {
                    scattering = s;
                    atmosphereVersion++;
                    rebuilding = false;
                }
            });
        }
        // Gestirne: neu, wenn eine Szenensekunde vergangen ist oder der Ort sich ändert
        double ep = epochMillis;
        if (astro == null || Math.abs(ep - astroEpoch) > 1000 || latDeg != astroLat || lonDeg != astroLon) {
            astro = new Astro(ep, latDeg, lonDeg);
            astroEpoch = ep;
            astroLat = latDeg;
            astroLon = lonDeg;
        }
        // Licht: neu, wenn Sonne oder Mond sich merklich bewegt haben oder ein Eingang sich ändert
        Scattering sc = scattering;
        String key = String.format(Locale.ROOT, "%.2f|%.1f|%.2f|%.1f|%d|%.3f|%.1f|%s|%b|%d",
                astro.sunAltGeo, astro.sunAz, astro.moonAltGeo, astro.moonAz, atmosphereVersion, cloudCover, eyeHeight,
                domes.toString(), cloudVeil, Math.round(astro.moonFraction * 200));
        if (light == null || (sync && !key.equals(lightKey))) {
            light = new Light(sc, astro, this);
            lightKey = key;
        } else if (!key.equals(lightKey) && !lightPending) {
            // im Hintergrund; bis dahin gilt das letzte Licht (bei 600-fachem Zeitraffer wandert die Sonne 2,5° je s)
            lightPending = true;
            final Astro a = astro;
            builder.execute(() -> {
                Light l = new Light(sc, a, Sky.this);
                synchronized (Sky.this) {
                    light = l;
                    lightKey = key;
                    lightPending = false;
                }
            });
        }
        // Blitze: Helligkeit jetzt
        double[] fl = currentFlash();
        state = new State(this, sc, atmosphereVersion, astro, light, fl);
        return state;
    }

    /** Laufzeit in s (für {@link #flash}). */
    public synchronized double time() { return time; }

    /** Scheinbare Höhe und Azimut der Sonne in Grad, ohne Streurechnung (für Suchen nach Auf- und Untergang). */
    public static double[] sunAltAz(double epochMillis, double latDeg, double lonDeg) {
        Astro a = new Astro(epochMillis, latDeg, lonDeg);
        return new double[]{a.sunAlt, a.sunAz};
    }

    /** Der letzte Zustand (null vor dem ersten {@link #update()}). */
    public State state() { return state; }

    /** Die aktuelle Streurechnung (Tabellen auf dem Prozessor). */
    public Scattering scattering() { return scattering; }

    /** Ob die Atmosphäre gerade im Hintergrund neu gebaut wird. */
    public boolean rebuilding() { return rebuilding; }

    /** Baut die Atmosphäre sofort neu (für Standbilder und Prüfungen). */
    public synchronized State rebuildNow() {
        scattering = scatteringFor(Math.max(1, Math.min(Atmosphere.MAX_VISIBILITY_KM, visibilityKm)), groundAlbedo);
        builtVisibility = scattering.atm.visibilityKm;
        builtAlbedo = groundAlbedo;
        atmosphereVersion++;
        light = null;
        return update(true);
    }

    private double[] currentFlash() {
        double sum = 0, x = 0, y = 0, z = 0;
        for (var it = flashes.iterator(); it.hasNext(); ) {
            double[] f = it.next();
            double age = time - f[4];
            if (age > f[5] + 1) { it.remove(); continue; }
            if (age < 0) continue;
            // Hauptschlag und zwei Nachschläge, dazwischen Nachglühen
            double k = Math.exp(-age / 0.06) + 0.6 * pulse(age, f[5] * 0.35) + 0.45 * pulse(age, f[5] * 0.7) + 0.08 * Math.exp(-age / f[5]);
            double s = f[3] * k * Math.min(1, 3000 / f[6]);
            sum += s;
            x += f[0] * s;
            y += f[1] * s;
            z += f[2] * s;
        }
        if (sum < 1e-6) return new double[]{0, 1, 0, 0};
        double n = Math.sqrt(x * x + y * y + z * z);
        return new double[]{x / n, y / n, z / n, sum};
    }

    private static double pulse(double t, double at) {
        double d = (t - at) / 0.03;
        return Math.exp(-d * d);
    }

    /** Für die Grafikkarte: Lichtglocken als (Azimut rad, Entfernung km, Stärke, 0), höchstens vier. */
    public float[] domeUniforms() {
        float[] u = new float[16];
        int i = 0;
        for (LightDome d : domes) {
            if (i >= 4) break;
            u[i * 4] = (float) Math.toRadians(d.azDeg());
            u[i * 4 + 1] = (float) d.distanceKm();
            u[i * 4 + 2] = (float) d.strength();
            i++;
        }
        return u;
    }

    // ------------------------------------------------------------------ Gestirne

    /** Was die Astronomie für einen Zeitpunkt liefert (einmal je Szenensekunde). */
    static final class Astro {
        final double jdUt, jde, lst;
        final double[] eqToScene;
        final double sunAltGeo, sunAz, sunAlt, sunDistanceAu;
        final double[] sunDirGeo, sunDir;
        final double moonAltGeo, moonAz, moonAlt, moonDistanceKm, moonRadiusDeg, moonFraction, moonPhaseAngle, moonAge;
        final double moonBrightLimb, moonTilt, librationLon, librationLat, moonMag;
        final boolean moonWaxing;
        final String moonPhaseName;
        final double[] moonDirGeo, moonDir, moonLight, moonNorth;
        final Planets.Planet[] planets;
        final double[][] planetDirs;

        Astro(double epochMs, double lat, double lon) {
            jdUt = epochMs / 86_400_000.0 + 2440587.5;
            jde = jdUt + FAstroTime.deltaTDays(jdUt);
            lst = Celestial.localSiderealTime(jdUt, lon);
            double[] hor = Celestial.horizon(lst, lat);
            eqToScene = Celestial.mul(hor, Celestial.precession(jde));
            FSun sun = FSun.at(jde);
            sunDistanceAu = sun.distanceAu;
            sunDirGeo = Celestial.apply(hor, Celestial.equatorial(sun.rightAscension, sun.declination));
            double[] sa = Celestial.altAz(sunDirGeo);
            sunAltGeo = sa[0];
            sunAz = sa[1];
            sunAlt = sunAltGeo + Celestial.refraction(sunAltGeo);
            sunDir = Celestial.direction(sunAlt, sunAz);
            FMoonEphemeris me = FMoonEphemeris.atJd(jdUt, new FGeoLocation("Ort", lat, lon, 0, "UTC"));
            moonAltGeo = me.altitude;
            moonAz = me.azimuth;
            moonAlt = moonAltGeo + Celestial.refraction(moonAltGeo);
            moonDirGeo = Celestial.direction(moonAltGeo, moonAz);
            moonDir = Celestial.direction(moonAlt, moonAz);
            moonDistanceKm = me.distanceKm();
            moonRadiusDeg = me.moon.apparentRadiusDeg();
            moonFraction = me.illuminatedFraction();
            moonPhaseAngle = me.phase.phaseAngle;
            moonWaxing = me.phase.waxing;
            moonAge = me.phase.ageDays;
            moonPhaseName = me.phase.phaseName();
            moonBrightLimb = me.phase.brightLimbAngle;
            moonTilt = me.limbTiltFromVertical;
            librationLon = me.libration.longitude;
            librationLat = me.libration.latitude;
            // Licht am Mond: von dort zur Sonne (Sonne in AE, Mond in km)
            double au = 149_597_870.7;
            double[] sp = {sunDirGeo[0] * sunDistanceAu * au, sunDirGeo[1] * sunDistanceAu * au, sunDirGeo[2] * sunDistanceAu * au};
            double[] mp = {moonDirGeo[0] * moonDistanceKm, moonDirGeo[1] * moonDistanceKm, moonDirGeo[2] * moonDistanceKm};
            moonLight = normalize(new double[]{sp[0] - mp[0], sp[1] - mp[1], sp[2] - mp[2]});
            // Nordpol des Monds ≈ Pol der Ekliptik (die Mondachse steht 1,5° daneben)
            moonNorth = Celestial.apply(eqToScene, Celestial.equatorial(270, 90 - Celestial.OBLIQUITY_J2000));
            double i = moonPhaseAngle;
            moonMag = -12.73 + 0.026 * Math.abs(i) + 4e-9 * Math.pow(i, 4) + 5 * Math.log10(moonDistanceKm / 384_400.0);
            planets = Planets.at(jde);
            planetDirs = new double[planets.length][];
            for (int p = 0; p < planets.length; p++) planetDirs[p] = Celestial.refract(Celestial.apply(eqToScene, planets[p].equatorialJ2000()));
        }
    }

    // ------------------------------------------------------------------ Licht

    /** Licht aus Sonne, Mond und Himmel am Auge (nur neu, wenn sich etwas bewegt hat). */
    static final class Light {
        final double[] sunToa, moonToa, sunColor, moonColor, skyIrradiance, skyZenith, overcast;
        final double adaptation, sunDisc, moonDisc;

        Light(Scattering sc, Astro a, Sky sky) {
            double cover = Math.max(0, Math.min(1, sky.cloudCover));
            double r = Atmosphere.BOTTOM + Math.max(1, sky.eyeHeight);
            sunToa = scale(new double[]{1, 1, 1}, SUN_TOA * Math.pow(1 / a.sunDistanceAu, 2));
            double ratio = Math.pow(10, -0.4 * (a.moonMag - SUN_MAG));
            moonToa = scale(new double[]{1.06, 0.98, 0.88}, SUN_TOA * ratio);
            double dip = Math.sqrt(Math.max(0, r * r - Atmosphere.BOTTOM * Atmosphere.BOTTOM)) / r;
            double muHorizon = -dip + 2e-4;
            sunDisc = discAbove(a.sunAlt, 0.2666);
            moonDisc = discAbove(a.moonAlt, a.moonRadiusDeg);
            double[] tSun = sc.transmittance(r, Math.max(muHorizon, Math.sin(Math.toRadians(a.sunAlt))));
            double[] tMoon = sc.transmittance(r, Math.max(muHorizon, Math.sin(Math.toRadians(a.moonAlt))));
            double direct = 1 - 0.9 * cover;
            sunColor = new double[3];
            moonColor = new double[3];
            for (int c = 0; c < 3; c++) {
                sunColor[c] = sunToa[c] * tSun[c] * sunDisc * direct;
                moonColor[c] = moonToa[c] * tMoon[c] * moonDisc * direct;
            }
            // Himmel in 8 × 5 Richtungen: Bestrahlung einer waagerechten Fläche und mittlere Leuchtdichte unter 45°
            double[] edges = {0, 6, 15, 30, 50, 90};
            double[] irr = new double[3];
            double lumSum = 0, lumW = 0;
            Clear clear = new Clear(sc, a, sky, sunToa, moonToa);
            for (int b = 0; b < edges.length - 1; b++) {
                double e0 = Math.toRadians(edges[b]), e1 = Math.toRadians(edges[b + 1]), em = 0.5 * (e0 + e1);
                double band = (Math.sin(e1) - Math.sin(e0)) * 2 * Math.PI / 8;
                for (int k = 0; k < 8; k++) {
                    double az = (k + 0.5) / 8 * 2 * Math.PI;
                    double[] d = {Math.cos(em) * Math.sin(az), Math.sin(em), -Math.cos(em) * Math.cos(az)};
                    double[] L = clear.radiance(d);
                    for (int c = 0; c < 3; c++) irr[c] += L[c] * Math.sin(em) * band;
                    if (edges[b + 1] <= 50) {
                        lumSum += lum(L) * band;
                        lumW += band;
                    }
                }
            }
            double[] zen = clear.radiance(new double[]{0, 1, 0});
            // Schleier (Bedeckung): Leuchtdichte nach Moon und Spencer, L(θ) = Lz (1 + 2 cos θ) / 3, E = Lz · 7π/9
            double total = lum(irr) + lum(sunColor) / Math.max(1e-9, direct) + lum(moonColor) / Math.max(1e-9, direct);
            double lz = 0.45 * total * 9 / (7 * Math.PI);
            overcast = new double[]{lz * 0.97, lz, lz * 1.04};
            double veil = sky.cloudVeil ? cover : 0;
            skyIrradiance = new double[3];
            skyZenith = new double[3];
            for (int c = 0; c < 3; c++) {
                skyIrradiance[c] = irr[c] * (1 - veil) + overcast[c] * 7 * Math.PI / 9 * veil;
                skyZenith[c] = zen[c] * (1 - veil) + overcast[c] * veil;
            }
            double meanOver = lz * (1 + 2 * 0.3) / 3;
            adaptation = (lumSum / lumW) * (1 - veil) + meanOver * veil;
        }
    }

    /**
     * Der klare Himmel ohne Scheiben und Sterne: Streuung von Sonne und Mond, natürlicher Nachthimmel, Lichtglocken.
     * Genau das, was {@code sky.frag} vor dem Schleier rechnet.
     */
    static final class Clear {
        final Scattering sc;
        final double eye, vis;
        final double[] sun, sunE, moon, moonE;
        final float[] domes;

        Clear(Scattering sc, Astro a, Sky sky, double[] sunToa, double[] moonToa) {
            this.sc = sc;
            eye = Math.max(1, sky.eyeHeight);
            sun = a.sunDir;
            moon = a.moonDir;
            sunE = sunToa;
            moonE = moonToa;
            vis = sc.atm.visibilityKm;
            domes = sky.domeUniforms();
        }

        double[] radiance(double[] d) {
            double[] L = sc.radiance(eye, d, sun, sunE, moon, moonE, 16);
            double[] n = night(sc, eye, d);
            double[] dm = domes(domes, vis, d);
            return new double[]{L[0] + n[0] + dm[0], L[1] + n[1] + dm[1], L[2] + n[2] + dm[2]};
        }
    }

    /** Natürlicher Nachthimmel: Eigenleuchten in 90 km Höhe (van Rhijn), gedämpft auf dem Weg durch die Luft. */
    static double[] night(Scattering sc, double eye, double[] d) {
        double y = Math.max(0.0, d[1]);
        double k = Math.pow(Atmosphere.BOTTOM / (Atmosphere.BOTTOM + 90_000), 2);
        double vr = 1 / Math.sqrt(1 - k * (1 - y * y));
        double[] t = sc.transmittance(Atmosphere.BOTTOM + eye, y);
        double[] out = new double[3];
        for (int c = 0; c < 3; c++) out[c] = NIGHT_LUMINANCE * NIGHT_COLOR[c] * vr * t[c];
        return out;
    }

    /** Lichtglocken (wie im Shader): Glocke über der Kimm in Richtung des Orts, breiter und schwächer mit Dunst und Entfernung. */
    static double[] domes(float[] u, double vis, double[] d) {
        double[] out = new double[3];
        double el = Math.asin(Math.max(-1, Math.min(1, d[1])));
        if (el < -0.02) return out;
        double az = Math.atan2(d[0], -d[2]);
        for (int i = 0; i < 4; i++) {
            double s = u[i * 4 + 2];
            if (s <= 0) continue;
            double dist = u[i * 4 + 1];
            double da = Math.abs(Math.IEEEremainder(az - u[i * 4], 2 * Math.PI));
            double w = Math.atan(12 / dist) + 0.05;
            double haze = Math.exp(-dist / (1.5 * vis)) * (1 + 6 / vis);
            double g = s * 5 * NIGHT_LUMINANCE * haze * Math.exp(-da * da / (w * w)) * Math.exp(-Math.max(0, el) / (0.03 + 0.12 * w));
            out[0] += g * 1.35;
            out[1] += g * 0.92;
            out[2] += g * 0.52;
        }
        return out;
    }

    static double discAbove(double altDeg, double radiusDeg) {
        double h = Math.max(-1, Math.min(1, altDeg / radiusDeg));
        return 1 - (Math.acos(h) - h * Math.sqrt(1 - h * h)) / Math.PI;
    }

    static double lum(double[] c) { return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]; }

    static double[] scale(double[] v, double s) { return new double[]{v[0] * s, v[1] * s, v[2] * s}; }

    static double[] normalize(double[] v) {
        double n = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new double[]{v[0] / n, v[1] / n, v[2] / n};
    }

    // ------------------------------------------------------------------ Zustand nach außen

    /** Alles, was Szene und Grafikkarte über den Himmel wissen müssen, für einen Augenblick. */
    public static final class State {
        /** Zeit und Ort. */
        public final double epochMillis, latDeg, lonDeg, eyeHeight, jdUt, localSiderealDeg;
        /** J2000-Äquator in die Szene (Präzession, Sternzeit, Breite), zeilenweise. */
        public final double[] eqToScene;
        /** Sonne: Richtung (scheinbar, mit Brechung, und wahr), Höhe und Azimut in Grad. */
        public final double[] sunDir, sunDirTrue;
        public final double sunAlt, sunAz;
        /** Mond: Richtung, Höhe, Azimut, Radius in Grad, Abstand in km, beleuchteter Anteil, Phasenwinkel, Alter, Libration. */
        public final double[] moonDir, moonDirTrue, moonLight, moonNorth;
        public final double moonAlt, moonAz, moonRadiusDeg, moonDistanceKm, moonFraction, moonPhaseAngle, moonAgeDays,
                moonMagnitude, librationLon, librationLat;
        public final boolean moonWaxing;
        public final String moonPhaseName;
        /** Bestrahlung außerhalb der Atmosphäre und am Auge (nach Auslöschung, Scheibenanteil über der Kimm, Bewölkung). */
        public final double[] sunToa, moonToa, sunColor, moonColor;
        /** Himmelslicht auf eine waagerechte Fläche, Strahldichte im Zenit, Schleier bei Bedeckung. */
        public final double[] skyIrradiance, skyZenith, overcast;
        /** Mittlere Leuchtdichte des Himmels unter 50° (für die Belichtung) und Grenzgröße der Sterne im Zenit. */
        public final double adaptation, limitingMagnitude;
        /** Sicht in km, Auslöschung am Boden je m, Version der Atmosphäre (für die Tabellen der Grafikkarte). */
        public final double visibilityKm, groundExtinction;
        public final Atmosphere atmosphere;
        public final int atmosphereVersion;
        /** Bedeckung, Schleier an, Regenbogen. */
        public final double cloudCover, veil, rainbow;
        /** Planeten: Name, Szenenrichtung (mit Brechung), Helligkeit, Farbe. */
        public final List<Planets.Planet> planets;
        public final double[][] planetDirs;
        /** Blitzleuchten: Richtung und Stärke jetzt. */
        public final double[] flashDir;
        public final double flash;
        /** Was gezeichnet wird. */
        public final boolean stars, planetsOn, milkyWay, figures;
        public final double starGain;
        public final float[] domes;
        private final Scattering scattering;

        State(Sky s, Scattering sc, int version, Astro a, Light l, double[] fl) {
            epochMillis = s.epochMillis;
            latDeg = s.latDeg;
            lonDeg = s.lonDeg;
            eyeHeight = Math.max(1, s.eyeHeight);
            jdUt = a.jdUt;
            localSiderealDeg = a.lst;
            eqToScene = a.eqToScene.clone();
            sunDir = a.sunDir.clone();
            sunDirTrue = a.sunDirGeo.clone();
            sunAlt = a.sunAlt;
            sunAz = a.sunAz;
            moonDir = a.moonDir.clone();
            moonDirTrue = a.moonDirGeo.clone();
            moonLight = a.moonLight.clone();
            moonNorth = a.moonNorth.clone();
            moonAlt = a.moonAlt;
            moonAz = a.moonAz;
            moonRadiusDeg = a.moonRadiusDeg;
            moonDistanceKm = a.moonDistanceKm;
            moonFraction = a.moonFraction;
            moonPhaseAngle = a.moonPhaseAngle;
            moonAgeDays = a.moonAge;
            moonMagnitude = a.moonMag;
            librationLon = a.librationLon;
            librationLat = a.librationLat;
            moonWaxing = a.moonWaxing;
            moonPhaseName = a.moonPhaseName;
            sunToa = l.sunToa.clone();
            moonToa = l.moonToa.clone();
            sunColor = l.sunColor.clone();
            moonColor = l.moonColor.clone();
            skyIrradiance = l.skyIrradiance.clone();
            skyZenith = l.skyZenith.clone();
            overcast = l.overcast.clone();
            adaptation = l.adaptation;
            atmosphere = sc.atm;
            visibilityKm = sc.atm.visibilityKm;
            groundExtinction = sc.atm.groundExtinction();
            atmosphereVersion = version;
            cloudCover = Math.max(0, Math.min(1, s.cloudCover));
            veil = s.cloudVeil ? cloudCover : 0;
            rainbow = Math.max(0, Math.min(1, s.rainbow));
            planets = List.of(a.planets);
            planetDirs = a.planetDirs;
            flashDir = new double[]{fl[0], fl[1], fl[2]};
            flash = fl[3];
            stars = s.stars;
            planetsOn = s.planets;
            milkyWay = s.milkyWay;
            figures = s.figures;
            starGain = s.starGain;
            domes = s.domeUniforms();
            scattering = sc;
            // Grenzgröße aus der Zenithelligkeit (mag/″²) nach Schaefer, vereinfacht
            double cd = lum(skyZenith) * CD_PER_UNIT;
            double b = -2.5 * Math.log10(Math.max(1e-12, cd / 1.08e5));
            limitingMagnitude = Math.max(-1, Math.min(6.6, 7.93 - 5 * Math.log10(Math.pow(10, 4.316 - b / 5) + 1)));
        }

        /**
         * Bestrahlung durch die Sonne in h m Höhe (über der Luft gedämpft; im Erdschatten aus, mit weichem Rand über die
         * Sonnenscheibe). Für Wolken: Sie leuchten noch rot, wenn die Sonne am Auge schon untergegangen ist.
         */
        public double[] sunColorAt(double h) { return lightAt(h, sunAlt, sunToa, 0.2666); }

        /** Wie {@link #sunColorAt} für den Mond. */
        public double[] moonColorAt(double h) { return lightAt(h, moonAlt, moonToa, moonRadiusDeg); }

        private double[] lightAt(double h, double altDeg, double[] toa, double radiusDeg) {
            double r = Atmosphere.BOTTOM + Math.max(1, h);
            double dip = Math.toDegrees(Math.acos(Atmosphere.BOTTOM / r));
            double frac = discAbove(altDeg + dip, radiusDeg);
            double mu = Math.sin(Math.toRadians(Math.max(altDeg, -dip + 0.05)));
            double[] t = scattering.transmittance(r, mu);
            return new double[]{toa[0] * t[0] * frac, toa[1] * t[1] * frac, toa[2] * t[2] * frac};
        }

        /** Strahldichte des Himmels in Richtung d (Szene), ohne Sonne, Mond, Sterne – wie der Shader vor den Scheiben. */
        public double[] radiance(double[] d) {
            double[] L = scattering.radiance(eyeHeight, d, sunDir, sunToa, moonDir, moonToa);
            double[] n = night(scattering, eyeHeight, d);
            double[] dm = domes(domes, visibilityKm, d);
            double[] out = new double[3];
            for (int c = 0; c < 3; c++) {
                double clear = L[c] + n[c] + dm[c];
                double ov = overcast[c] * (1 + 2 * Math.max(0, d[1])) / 3;
                out[c] = clear * (1 - veil) + ov * veil;
            }
            return out;
        }

        /**
         * Mittlere Leuchtdichte im Blickfeld (geometrisches Mittel über 4 × 3 Richtungen), damit sich das Auge an das
         * anpasst, wohin es schaut: gegen die Sonne dunkler, gegen den Nachthimmel heller. Unter der Kimm zählt das
         * Meer mit einem Siebtel des gespiegelten Himmels. forward und up: Einheitsvektoren der Kamera.
         */
        public double viewLuminance(double[] forward, double[] up, double fovyDeg, double aspect) {
            double[] right = Sky.normalize(new double[]{forward[1] * up[2] - forward[2] * up[1], forward[2] * up[0] - forward[0] * up[2], forward[0] * up[1] - forward[1] * up[0]});
            double[] u = {right[1] * forward[2] - right[2] * forward[1], right[2] * forward[0] - right[0] * forward[2], right[0] * forward[1] - right[1] * forward[0]};
            double ty = Math.tan(Math.toRadians(fovyDeg) / 2), tx = ty * aspect, sum = 0;
            int n = 0;
            for (int j = 0; j < 3; j++)
                for (int i = 0; i < 4; i++) {
                    double sx = ((i + 0.5) / 4 * 2 - 1) * tx * 0.8, sy = ((j + 0.5) / 3 * 2 - 1) * ty * 0.8;
                    double[] d = Sky.normalize(new double[]{forward[0] + right[0] * sx + u[0] * sy, forward[1] + right[1] * sx + u[1] * sy, forward[2] + right[2] * sx + u[2] * sy});
                    double l;
                    if (d[1] >= 0) l = lum(radianceFast(d));
                    else l = lum(radianceFast(new double[]{d[0], -d[1], d[2]})) / 7;
                    sum += Math.log(Math.max(1e-14, l));
                    n++;
                }
            return Math.exp(sum / n);
        }

        private double[] radianceFast(double[] d) {
            double[] L = scattering.radiance(eyeHeight, d, sunDir, sunToa, moonDir, moonToa, 12);
            double[] nn = night(scattering, eyeHeight, d);
            double ov = (1 + 2 * Math.max(0, d[1])) / 3;
            return new double[]{(L[0] + nn[0]) * (1 - veil) + overcast[0] * ov * veil, (L[1] + nn[1]) * (1 - veil) + overcast[1] * ov * veil,
                    (L[2] + nn[2]) * (1 - veil) + overcast[2] * ov * veil};
        }

        /**
         * Belichtung wie {@link #exposure(double)}, aber für eine gegebene Anpassungsleuchtdichte (etwa ein Mittel aus
         * Himmel und Blickfeld, über die Zeit geglättet).
         */
        public static double exposureFor(double adaptation, double p) {
            double a = Math.max(1e-12, adaptation);
            return 0.35 / REFERENCE_LUMINANCE * Math.pow(a / REFERENCE_LUMINANCE, -p);
        }

        /** Die Streurechnung, aus der dieser Zustand stammt. */
        public Scattering scattering() { return scattering; }

        /**
         * Belichtung für den Bildschirm mit teilweiser Anpassung: bei der Bezugshelligkeit (Tag) liegt die Bildmitte
         * bei 0,35, dunkler wird nur mit dem Exponenten p nachgeregelt (p = 1 volle Anpassung, 0 keine). So bleibt die
         * Nacht Nacht.
         */
        public double exposure(double p) {
            double a = Math.max(1e-12, adaptation);
            return 0.35 / REFERENCE_LUMINANCE * Math.pow(a / REFERENCE_LUMINANCE, -p);
        }

        /**
         * Anteil des Stäbchensehens 0..1 aus der Anpassungsleuchtdichte: unter 0,003 cd/m² sieht das Auge nur noch mit
         * den Stäbchen (farblos, ins Blaue verschoben), über 3 cd/m² nur mit den Zapfen. Für die Nachbearbeitung.
         */
        public double scotopic() {
            double l = Math.log10(Math.max(1e-12, adaptation * CD_PER_UNIT));
            double t = Math.max(0, Math.min(1, (l - Math.log10(0.003)) / (Math.log10(3) - Math.log10(0.003))));
            return 1 - t * t * (3 - 2 * t);
        }

        /** Tageszeit nach der Sonnenhöhe: Tag, goldene Stunde, Dämmerungen, Nacht. */
        public String twilight() {
            double h = sunAlt;
            if (h >= 6) return "Tag";
            if (h >= -0.83) return "Goldene Stunde";
            if (h >= -6) return "Bürgerliche Dämmerung";
            if (h >= -12) return "Nautische Dämmerung";
            if (h >= -18) return "Astronomische Dämmerung";
            return "Nacht";
        }

        /** Sicht als Wort (Seewetterbericht). */
        public String visibilityWord() {
            double v = visibilityKm / 1.852;
            if (v < 0.5) return "Nebel";
            if (v < 2) return "schlecht";
            if (v < 5) return "mäßig";
            if (v < 10) return "gut";
            return "sehr gut";
        }

        /** „Sonne 12,3° / 215° · Mond 63 % zunehmend, 22,1° / 140° · Sicht 40 km · Grenzgröße 6,1“ */
        public String describe() {
            return String.format(Locale.GERMAN, "%s · Sonne %.1f° / %03.0f° · Mond %.0f %% %s, %.1f° / %03.0f° · Sicht %.0f km (%s) · Grenzgröße %.1f mag",
                    twilight(), sunAlt, sunAz, moonFraction * 100, moonWaxing ? "zunehmend" : "abnehmend", moonAlt, moonAz,
                    visibilityKm, visibilityWord(), limitingMagnitude);
        }
    }
}
