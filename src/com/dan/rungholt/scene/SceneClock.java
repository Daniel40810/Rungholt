package com.dan.rungholt.scene;

import com.dan.rungholt.wiring.Clock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Die Uhr der Szene mit Datum (Werkbuch, Abschnitt 6): Sie startet am 21. Juli 2026 um 13:30 Uhr [G], dem Tag des
 * Regiebuchs „Eine Tide“, kurz nach Niedrigwasser, und läuft mit Zeitraffer bis 3600-fach (eine Tide in gut 12 s).
 * <p>
 * Zeit: deutsche Zeit, MEZ (UTC+1) im Winter, MESZ (UTC+2) im Sommer. Die wahre Ortszeit am Watt (8,7° O) liegt
 * 25 Minuten hinter der MEZ; im Sommer steht die Sonne darum erst gegen 13:25 Uhr im Süden. Für 1362 rechnet die Uhr
 * im proleptischen gregorianischen Kalender (der julianische zählte damals 8 Tage weniger) und mit der Ortszeit der
 * Zeitzone vor 1893.
 * <p>
 * Die Uhr ist zugleich die Schnittstelle {@link Clock}, über die alle Packages Zeit bekommen.
 */
public final class SceneClock implements Clock {
    public static final double MAX_LAPSE = 3600;
    /** Deutsche Zeit mit Sommerzeit. */
    public static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    /** Start der Szene: 21. Juli 2026, 13:30 Uhr MESZ [G]. */
    public static final LocalDate START_DATE = LocalDate.of(2026, 7, 21);
    public static final double START_HOUR = 13.5;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d.M.u", Locale.GERMANY),
            TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY);

    /** Szenenzeit in ms seit 1970 (UTC), als double, damit kleine Schritte nicht verloren gehen. */
    private volatile double epochMs;
    /** Zeitraffer: Szenensekunden je echter Sekunde (0 = Uhr steht). */
    public volatile double lapse = 1;
    /** Ort für Sonne, Mond und Sterne. */
    public volatile double lat = Site.LAT, lon = Site.LON;
    /** Laufzeit der Szene in s (für Wellen und Animation; läuft auch bei stehender Uhr) und letzter Schritt. */
    private double t, dt;

    public SceneClock() { this(START_DATE, START_HOUR); }

    public SceneClock(LocalDate date, double hour) { set(date, hour); }

    /** Datum und Uhrzeit (Stunden, deutsche Zeit) setzen. */
    public synchronized void set(LocalDate date, double hour) {
        hour = Math.max(0, Math.min(24 - 1e-9, hour));
        int sec = (int) Math.floor(hour * 3600);
        ZonedDateTime z = ZonedDateTime.of(date, LocalTime.ofSecondOfDay(sec), ZONE);
        epochMs = z.toInstant().toEpochMilli() + (hour * 3600 - sec) * 1000;
    }

    public synchronized void setHour(double hour) { set(date(), hour); }

    /** Läuft um dt echte Sekunden weiter. */
    public synchronized void step(double realDt) {
        dt = realDt;
        t += realDt;
        epochMs += realDt * Math.max(0, Math.min(MAX_LAPSE, lapse)) * 1000;
    }

    public synchronized void shiftDays(int days) { epochMs += days * 86_400_000.0; }

    public ZonedDateTime local() { return Instant.ofEpochMilli((long) Math.floor(epochMs)).atZone(ZONE); }
    public LocalDate date() { return local().toLocalDate(); }
    public double hour() {
        ZonedDateTime z = local();
        return z.getHour() + z.getMinute() / 60.0 + (z.getSecond() + (epochMs - Math.floor(epochMs / 1000) * 1000) / 1000) / 3600.0;
    }

    /** Sonnenhöhe und -azimut in Grad an der Kirche [G]. */
    public double[] sun() { return Sun.position(epochMs, lat, lon); }

    /** Szenenzeit direkt setzen (ms seit 1970, UTC), etwa für „Weiter wie zuletzt“ in Phase 16. */
    public synchronized void setEpochMillis(double ms) { epochMs = ms; }

    /** „21.7.2026 13:30 MESZ“, vor 1582 „23.1.1362 (jul. 15.1.) 22:00 Ortszeit“. */
    public String label() { return dateLabel() + " " + timeLabel(); }
    /** „21.7.2026“, vor dem 15.10.1582 mit dem julianischen Datum dazu: „23.1.1362 (jul. 15.1.)“ (Phase 17). */
    public String dateLabel() {
        LocalDate d = date();
        String g = DATE.format(d);
        if (!d.isBefore(GREGOR)) return g;
        int[] j = julian(d);
        return g + " (jul. " + j[2] + "." + j[1] + "." + (j[0] == d.getYear() ? "" : Integer.toString(j[0])) + ")";
    }

    /** Erster Tag des gregorianischen Kalenders (davor zählte man julianisch). */
    public static final LocalDate GREGOR = LocalDate.of(1582, 10, 15);

    /**
     * Phase 17: das julianische Datum zu einem (proleptisch) gregorianischen Tag als {Jahr, Monat, Tag}, über die
     * julianische Tageszahl (Meeus). 1362 liegt es 8 Tage zurück: 23.1.1362 gregorianisch ist der 15.1. julianisch.
     */
    public static int[] julian(LocalDate d) {
        long jd = d.toEpochDay() + 2440588;   // julianische Tageszahl am Mittag
        long c = jd + 32082, dd = (4 * c + 3) / 1461, e = c - 1461 * dd / 4, m = (5 * e + 2) / 153;
        int tag = (int) (e - (153 * m + 2) / 5 + 1), monat = (int) (m + 3 - 12 * (m / 10)), jahr = (int) (dd - 4800 + m / 10);
        return new int[]{jahr, monat, tag};
    }

    /** Der gregorianische Tag zu einem julianischen Datum (Umkehrung von {@link #julian}). */
    public static LocalDate gregorian(int jahr, int monat, int tag) {
        long a = (14 - monat) / 12, y = jahr + 4800 - a, m = monat + 12 * a - 3;
        long jd = tag + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083;
        return LocalDate.ofEpochDay(jd - 2440588);
    }
    public String timeLabel() { return TIME.format(local()) + " " + zoneLabel(); }

    /** „MESZ“, „MEZ“ oder vor 1893 „Ortszeit“. */
    public String zoneLabel() {
        ZonedDateTime z = local();
        if (z.getYear() < 1893) return "Ortszeit";
        return ZONE.getRules().isDaylightSavings(z.toInstant()) ? "MESZ" : "MEZ";
    }

    // ------------------------------------------------------------------ Clock
    @Override public double t() { return t; }
    @Override public double dt() { return dt; }
    @Override public double epochMillis() { return epochMs; }
    @Override public double latDeg() { return lat; }
    @Override public double lonDeg() { return lon; }
}
