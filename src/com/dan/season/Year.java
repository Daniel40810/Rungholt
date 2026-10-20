package com.dan.season;

/**
 * Das Jahr am Ort: Schnee auf dem Boden, auf den Dächern und in den Bäumen, Eis auf dem Noor, Reif, dazu der Stand
 * der Pflanzen (Gras, Schilf, Laub). Eingänge sind Datum und Stunde, Lufttemperatur, Niederschlag, Sonnenhöhe,
 * Bedeckung und Wind, Stunde für Stunde ({@link #step}); {@link #settle} rechnet den Zustand für ein beliebiges Datum
 * aus einem gewürfelten Jahr ({@link Climate#days}) ab dem 1. September des Vorjahrs vor, damit ein Sprung im Kalender
 * sofort den richtigen Winter zeigt.
 * <p>
 * <b>Schnee</b> (Schneewasserwert in mm, Dichte in kg/m³): Niederschlag fällt unter 0,5 °C als Schnee, über 2,5 °C als
 * Regen, dazwischen gemischt. Neuschnee 100 kg/m³, er setzt sich in rund acht Tagen auf 300 (nass 450). Schmelze nach
 * dem Gradstundenverfahren (0,15 mm je K und Stunde, rund 3,6 mm je Gradtag) plus Sonne (absorbiert je nach Alter des
 * Schnees 15 bis 40 %, 334 kJ/kg) plus Regen auf Schnee. Auf den Dächern dasselbe, dazu ein wenig Wärme durch das Reet
 * der geheizten Häuser [G]; in den Kronen hält sich Schnee nur Stunden bis wenige Tage (Wind, Tauwetter, Sonne).
 * <p>
 * <b>Eis</b>: Das Noor ist flach (meist unter 2 m) und brackig (gefriert bei −0,4 °C [G]). Erst kühlt das Wasser aus
 * (Zeitkonstante 5 Tage), dann wächst das Eis nach Stefan, h² = h₀² + α²·Frostgradtage, mit α = 2,7 cm/√(K·d), unter
 * Schnee langsamer; Wind über 4 m/s hält dünnes Eis offen; es taut von oben (Luft und Sonne). Das Eis liegt zuerst im
 * flachen Wasser: {@link State#iceDepth} sagt, bis zu welcher Wassertiefe das Noor zugefroren ist.
 * <p>
 * <b>Reif</b> in klaren, windstillen Nächten unter 1 °C; die Sonne nimmt ihn am Vormittag.
 * <p>
 * Nur Java 21; keine Abhängigkeit zu anderen Packages (Paketvertrag).
 */
public final class Year {
    /** Schmelzfaktor (mm je K und Stunde), Gefrierpunkt des Noors (°C), Stefan-Faktor (m je √(K·Tag)). */
    public static final double DEGREE_HOUR = 0.15, FREEZE = -0.4, STEFAN = 0.027;
    /** Neuschnee und gesetzter Schnee (kg/m³). */
    public static final double RHO_NEW = 100, RHO_OLD = 300, RHO_WET = 450;

    public final Climate climate;
    /** Startwert des gewürfelten Jahrs. 950: ein Winter mit Schnee Mitte Januar und Eis auf dem Noor [G]. */
    public volatile long seed = 14;
    /** Verschiebung aller Temperaturen (K), etwa für einen harten Winter (negativ). */
    public volatile double shift = 0;
    /**
     * Seit Glacier Phase 11: Südhalbkugel. Die Pflanzen (Gras, Schilf, Laub, Herbstfärbung) laufen um ein halbes Jahr
     * versetzt; Temperatur, Schnee und Eis folgen ohnehin dem Klima des Orts.
     */
    public volatile boolean southern;

    // ------------------------------------------------------------------ Zustand
    private double swe, rho = RHO_NEW, roofSwe, treeSnow, ice, water = 8, frost, wet, ageH = 1000;
    private int year = Integer.MIN_VALUE, doy;
    private double hour;
    private long seriesKey = Long.MIN_VALUE;
    private double[] seriesT, seriesP;
    private double lastT = 5;

    public Year(Climate c) { this.climate = c; }

    public Year() { this(Climate.schleswig()); }

    // ------------------------------------------------------------------ Kalender

    /** Erster Tag der gewürfelten Reihe: 1. September des Vorjahrs (Tag −121). */
    public static final int FIRST = -121;

    /** Tag im Jahr (1..365) aus Monat (1..12) und Tag (der 29. Februar zählt wie der 28.). */
    public static int dayOfYear(int month, int day) {
        int d = 0;
        for (int i = 0; i < month - 1; i++) d += Climate.DAYS[i];
        return d + Math.min(day, Climate.DAYS[month - 1]);
    }

    private void series(int y) {
        long key = seed * 100_003L + y;
        if (key == seriesKey) return;
        double[][] s = climate.days(seed, y, FIRST, 365);
        seriesT = s[0];
        seriesP = s[1];
        seriesKey = key;
    }

    /** Tagesmittel der Temperatur im gewürfelten Jahr (°C), mit {@link #shift}. */
    public synchronized double dayMean(int y, int doy) {
        series(y);
        return seriesT[Math.max(0, Math.min(seriesT.length - 1, doy - FIRST))] + shift;
    }

    /** Niederschlag des Tags im gewürfelten Jahr (mm). */
    public synchronized double dayPrecip(int y, int doy) {
        series(y);
        return seriesP[Math.max(0, Math.min(seriesP.length - 1, doy - FIRST))];
    }

    /** Abweichung des Tags vom Klima (K): für die Wetterregie, damit sie zum gewürfelten Winter passt. */
    public double anomaly(int y, int doy) { return dayMean(y, doy) - climate.meanTemp(doy); }

    /** Sonnenhöhe (Grad) in Breite 54,5° zur Ortszeit hour (genähert: wahre Mittagszeit 12:30 MEZ in 9,6° Ost). */
    public static double sunAltitude(int doy, double hour) {
        double dec = Math.toRadians(23.44) * Math.sin(2 * Math.PI * (doy - 81) / 365.0);
        double lat = Math.toRadians(54.5), ha = Math.toRadians(15 * (hour - 12.4));
        return Math.toDegrees(Math.asin(Math.sin(lat) * Math.sin(dec) + Math.cos(lat) * Math.cos(dec) * Math.cos(ha)));
    }

    // ------------------------------------------------------------------ Vorrechnen

    /**
     * Den Zustand zum Datum (year, doy, hour) aus dem gewürfelten Jahr vorrechnen, ab dem 1. September des Vorjahrs,
     * Stunde für Stunde. Gleiches Datum und gleicher Startwert, gleicher Zustand.
     */
    public synchronized State settle(int y, int doy, double hour) {
        simulate(y, doy, hour, null, 0);
        return state();
    }

    /**
     * Das ganze gewürfelte Jahr Tag für Tag: je Tag von 1. September des Vorjahrs bis 31. Dezember der Stand zur
     * Stunde hour (für Kurven und Prüfungen). Danach steht der Zustand am 31. Dezember.
     */
    public synchronized java.util.List<State> trace(int y, double hour) {
        java.util.List<State> out = new java.util.ArrayList<>();
        simulate(y, 365, 23.99, out, hour);
        return out;
    }

    private void simulate(int y, int doy, double hour, java.util.List<State> out, double recordHour) {
        series(y);
        swe = 0; rho = RHO_NEW; roofSwe = 0; treeSnow = 0; ice = 0; frost = 0; wet = 0; ageH = 1000;
        water = climate.meanTemp(244) + shift;
        int last = doy - FIRST;
        for (int i = 0; i <= last; i++) {
            int d = FIRST + i;
            int dd = d <= 0 ? d + 365 : d;
            double mean = seriesT[i] + shift, p = seriesP[i];
            boolean wetDay = p > 0;
            double sum = 0.5 + 0.5 * Math.cos(2 * Math.PI * (dd - 196) / 365.0);
            double amp = (wetDay ? 1.2 : 3.0) * (0.6 + 0.6 * sum);
            double oktas = wetDay ? 7 : 3;
            int hEnd = i == last ? (int) Math.floor(hour) : 24;
            this.doy = dd;
            for (int h = 0; h < hEnd; h++) {
                if (out != null && h == (int) Math.floor(recordHour)) out.add(state());
                double t = mean + amp * Math.cos(2 * Math.PI * (h + 0.5 - 15) / 24);
                advance(1, t, p / 24, sunAltitude(dd, h + 0.5), oktas, wetDay ? 7 : 3.5);
            }
            if (i == last) {
                double fr = hour - Math.floor(hour);
                if (fr > 0) advance(fr, mean + amp * Math.cos(2 * Math.PI * (hour - 15) / 24), p / 24, sunAltitude(dd, hour), oktas, 4);
            }
        }
        this.year = y;
        this.doy = doy;
        this.hour = hour;
    }

    /**
     * Ein Schritt mit dem Wetter des Augenblicks: dtS Sekunden Szenenzeit, Lufttemperatur (°C), Niederschlag (mm/h),
     * Sonnenhöhe (Grad), Bedeckung (Achtel), Wind (m/s). Springt das Datum (mehr als 3 Stunden oder rückwärts), wird
     * vorgerechnet.
     */
    public synchronized State step(double dtS, int y, int doy, double hour, double airC, double precipMmH, double sunAltDeg, double oktas, double windMs) {
        double expect = this.hour + dtS / 3600;
        double gapH = (y - this.year) * 365 * 24.0 + (doy - this.doy) * 24.0 + hour - expect;
        if (this.year == Integer.MIN_VALUE || Math.abs(gapH) > 3 || dtS > 6 * 3600 || dtS < 0) return settle(y, doy, hour);
        if (dtS > 0) {
            // große Schritte (schneller Zeitraffer) in Stunden zerlegen
            double left = dtS / 3600;
            while (left > 1e-9) {
                double s = Math.min(1, left);
                advance(s, airC, precipMmH, sunAltDeg, oktas, windMs);
                left -= s;
            }
        }
        this.year = y;
        this.doy = doy;
        this.hour = hour;
        return state();
    }

    /**
     * Den Zustand von Hand setzen (Vorführungen, Prüfungen): Datum, Schneewasserwert am Boden und auf den Dächern (mm),
     * Dichte (kg/m³), Eisdicke (m), Wassertemperatur (°C); kein Reif, kein Schnee in den Kronen.
     */
    public synchronized State reset(int y, int doy, double hour, double sweMm, double rhoKg, double iceM, double waterC) {
        series(y);
        swe = Math.max(0, sweMm); roofSwe = Math.min(60, swe); rho = rhoKg; ice = Math.max(0, iceM); water = waterC;
        treeSnow = 0; frost = 0; wet = 0; ageH = 100;
        this.year = y; this.doy = doy; this.hour = hour;
        return state();
    }

    /** Anteil des Niederschlags, der als Regen fällt, bei der Lufttemperatur t (°C). */
    public static double rainFraction(double t) { return smooth(0.5, 2.5, t); }

    /** Ein Zeitschritt von dtH Stunden. */
    private void advance(double dtH, double t, double precip, double sunAlt, double oktas, double wind) {
        lastT = t;
        double rf = rainFraction(t), snowfall = precip * (1 - rf) * dtH, rain = precip * rf * dtH;
        double sun = 900 * Math.max(0, Math.sin(Math.toRadians(sunAlt))) * (1 - 0.72 * Math.min(1, oktas / 8));
        // Boden
        if (snowfall > 0) {
            double s0 = swe;
            swe += snowfall;
            rho = (rho * s0 + RHO_NEW * snowfall) / swe;
            if (snowfall > 0.3) ageH = 0;
        }
        ageH += dtH;
        if (swe > 0) {
            double target = wet > 0.3 ? RHO_WET : RHO_OLD;
            rho += (target - rho) * Math.min(1, dtH / (24 * 8));
            double absorb = 0.15 + 0.25 * smooth(0, 240, ageH);         // Neuschnee hell, alter grau
            double melt = (t > 0 ? DEGREE_HOUR * t : 0) * dtH
                    + (t > -2 ? sun * absorb * 3600 / 334_000 * dtH : 0)
                    + (t > 0 ? rain * t / 80 : 0);
            melt = Math.min(swe, melt);
            swe -= melt;
            if (melt > 0.05 * dtH || rain > 0) wet = Math.min(1, wet + 0.25 * dtH);
            else if (t < -1) wet = Math.max(0, wet - 0.08 * dtH);
            if (swe < 0.05) { swe = 0; rho = RHO_NEW; wet = 0; }
        } else wet = 0;
        // Dächer: dazu ein wenig Wärme aus den Häusern; steile Dächer halten höchstens rund 60 mm
        roofSwe += snowfall;
        double roofMelt = ((t > 0 ? DEGREE_HOUR * 1.3 * t : 0) + (t > -2 ? sun * 0.3 * 3600 / 334_000 : 0) + 0.02) * dtH;
        roofSwe = Math.max(0, Math.min(60, roofSwe - roofMelt));
        // Bäume: Schnee in den Kronen (4 mm füllen sie), Wind, Tauwetter und Sonne werfen ihn ab
        treeSnow = Math.min(1, treeSnow + snowfall / 4);
        double unload = 0.04 + wind / 60 + (t > 0 ? 0.25 + 0.05 * t : 0) + sun * 0.0008;
        treeSnow = Math.max(0, treeSnow - unload * dtH * (snowfall > 0.2 * dtH ? 0.2 : 1));
        // das Noor: erst das Wasser kühlen, dann Eis nach Stefan
        if (ice > 0.002) water = FREEZE;
        else water += (t - water) * Math.min(1, dtH / 120);
        water = Math.max(FREEZE, water);
        if (t < FREEZE && water <= FREEZE + 0.05) {
            double fdd = (FREEZE - t) * dtH / 24;
            double a = STEFAN / (1 + swe / 25);
            double open = ice < 0.03 ? 1 - smooth(4, 9, wind) : 1;
            ice = Math.sqrt(ice * ice + a * a * fdd * open);
        } else if (t > FREEZE && ice > 0) {
            double flux = 15 * (t - FREEZE) + (swe > 2 ? 0.2 : 0.5) * sun;     // W/m²
            ice = Math.max(0, ice - flux * 3600 * dtH / (334_000 * 917));
            if (ice < 0.004) ice = 0;
        }
        // Reif
        if (t < 1 && sunAlt < 5 && oktas < 3.5 && wind < 3.5 && precip <= 0) frost = Math.min(1, frost + 0.2 * dtH);
        else if (sunAlt > 4 && t > -1) frost = Math.max(0, frost - (0.6 + sun / 200) * dtH);
        else if (t > 3 || precip > 0) frost = Math.max(0, frost - 1.0 * dtH);
    }

    // ------------------------------------------------------------------ Pflanzen (Tag im Jahr)

    /** Gras: 0 braun und matt (Winter) … 1 saftig (Mai); Mitte Oktober 0,5. */
    static final double[] LUSH = {0.15, 0.15, 0.25, 0.6, 1.0, 0.95, 0.75, 0.65, 0.65, 0.5, 0.35, 0.2};
    /** Schilf: 0 Sommer (grün), 0,75 Herbst (gold), 1 Winter (strohgrau). */
    static final double[] REED = {1.0, 1.0, 1.0, 0.95, 0.3, 0.0, 0.0, 0.0, 0.4, 0.75, 0.9, 1.0};
    /** Laub der Bäume in der Ferne (Anteil) und Herbstfärbung 0..1 (Mitte Oktober 0,5). */
    static final double[] FOLIAGE = {0, 0, 0, 0.25, 0.9, 1, 1, 1, 1, 0.9, 0.35, 0};
    static final double[] AUTUMN = {0, 0, 0, 0, 0, 0, 0, 0, 0.15, 0.5, 0.9, 1};

    public double grassLush(int doy) { return table(LUSH, plantDay(doy)); }
    public double reedSeason(int doy) { return table(REED, plantDay(doy)); }
    public double foliage(int doy) { return table(FOLIAGE, plantDay(doy)); }
    public double autumn(int doy) { return table(AUTUMN, plantDay(doy)); }

    /** Der Tag im Pflanzenjahr: auf der Südhalbkugel um 182 Tage versetzt. */
    int plantDay(int doy) { return southern ? (doy - 1 + 182) % 365 + 1 : doy; }

    private static double table(double[] v, int doy) {
        double d = ((doy - 1) % 365 + 365) % 365 + 1;
        int a = 11;
        for (int m = 0; m < 12; m++) if (Climate.mid(m) <= d) a = m;
        int b = (a + 1) % 12;
        double da = Climate.mid(a), db = Climate.mid(b) + (b <= a ? 365 : 0), x = d < da ? d + 365 : d;
        double f = Math.max(0, Math.min(1, (x - da) / (db - da)));
        return v[a] + (v[b] - v[a]) * f;
    }

    // ------------------------------------------------------------------ Ausgang

    /**
     * Der Zustand: Schneehöhe auf ebenem Boden (m), Bedeckung 0..1, Nässe des Schnees 0..1, Schneehöhe auf den
     * Dächern (m), Schnee in den Kronen 0..1, Eisdicke (m), Wassertiefe bis zu der das Noor zugefroren ist (m), Anteil
     * Eis an der Fläche des Noors 0..1, Reif 0..1, Wassertemperatur (°C); Pflanzen: Gras, Schilf, Laub, Herbstfärbung;
     * Rückstrahlung des Bodens für den Himmel; Tag im Jahr; Lufttemperatur des letzten Schritts.
     */
    public record State(double snowDepth, double snowCover, double snowWet, double roofSnow, double treeSnow, double ice, double iceDepth,
                        double iceCover, double frost, double waterTemp, double grassLush, double reedSeason, double foliage, double autumn,
                        double groundAlbedo, int doy, double airTemp, double swe) {
        /** „Schnee 12 cm · Eis 6 cm · Reif“ */
        public String describe() {
            StringBuilder b = new StringBuilder();
            if (snowDepth >= 0.005) b.append(String.format(java.util.Locale.GERMAN, "Schnee %.0f cm%s", snowDepth * 100, snowWet > 0.5 ? " (nass)" : ""));
            else b.append("schneefrei");
            if (ice >= 0.005) b.append(String.format(java.util.Locale.GERMAN, " · Eis %.0f cm bis %.1f m Wassertiefe", ice * 100, iceDepth));
            else b.append(String.format(java.util.Locale.GERMAN, " · Noor offen (%.1f °C)", waterTemp));
            if (frost > 0.3) b.append(" · Reif");
            return b.toString();
        }
    }

    /** Ein Stand ohne Schnee, Eis und Reif am Tag doy (nur die Pflanzen), etwa für Werkzeuge ohne Wetter. */
    public State bare(int doy) {
        return new State(0, 0, 0, 0, 0, 0, 0, 0, 0, climate.meanTemp(doy), grassLush(doy), reedSeason(doy), foliage(doy), autumn(doy), 0.12, doy,
                climate.meanTemp(doy), 0);
    }

    public synchronized State state() {
        double depth = swe > 0 ? swe / rho : 0;
        double cover = smooth(0.003, 0.05, depth);
        double iceCover = smooth(0.005, 0.08, ice);
        double iceDepth = ice < 0.005 ? 0 : 0.25 + 5.3 * smooth(0.01, 0.08, ice);
        double roof = roofSwe / 180;      // 180 kg/m³ auf dem Dach
        double alb = 0.12 + (0.75 - 0.12) * 0.85 * cover * (1 - 0.3 * wet);
        return new State(depth, cover, wet, roof, treeSnow, ice, iceDepth, iceCover, frost, water, grassLush(doy), reedSeason(doy), foliage(doy),
                autumn(doy), alb, doy, lastT, swe);
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
