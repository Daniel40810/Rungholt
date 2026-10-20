package com.dan.store;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ablage ohne Datenbank (aus Lighthouse, F40 dort): Textdateien in einem Ordner, je Eintrag eine Datei im Textformat
 * von {@link Felder#alsText}, je Art ein Unterordner. Sitzungen liegen in {@code sitzungen/N.txt} (die letzten zehn
 * bleiben), das Protokoll in {@code protokoll.txt}. Geschrieben wird über eine Zwischendatei, damit ein Absturz nie
 * eine halbe Datei hinterlässt. Eine App erweitert die Klasse um ihre Arten.
 */
public class DateiAblage implements Ablage {
    /** So viele Sitzungen bleiben liegen. */
    public static int sitzungenBehalten = 10;
    protected final File dir;

    public DateiAblage(File dir) { this.dir = dir; }

    public File dir() { return dir; }

    @Override public String wo() { return "Dateien in " + dir.getPath(); }
    @Override public boolean datenbank() { return false; }

    // ------------------------------------------------------------------ Hilfen

    /** Dateiname aus einem Namen: nur ASCII (ä → ae, ß → ss), alles Fremde wird _. */
    public static String dateiname(String name) {
        StringBuilder b = new StringBuilder();
        String n = name.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("Ä", "Ae").replace("Ö", "Oe").replace("Ü", "Ue").replace("ß", "ss");
        for (char c : n.toCharArray()) b.append(c < 128 && (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '(' || c == ')' || c == '.') ? c : '_');
        String s = b.toString().strip();
        return (s.isEmpty() ? "_" : s) + ".txt";
    }

    protected File sub(String... teile) {
        File f = dir;
        for (String t : teile) f = new File(f, t);
        return f;
    }

    public static void schreibe(File f, String text) throws Fehler {
        schreibe(f, text.getBytes(StandardCharsets.UTF_8));
    }

    public static void schreibe(File f, byte[] daten) throws Fehler {
        try {
            Files.createDirectories(f.getAbsoluteFile().getParentFile().toPath());
            File tmp = new File(f.getPath() + ".neu");
            Files.write(tmp.toPath(), daten);
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new Fehler("Datei " + f + " nicht geschrieben: " + e.getMessage(), e);
        }
    }

    public static LinkedHashMap<String, String> lies(File f) throws Fehler {
        try {
            return Felder.ausText(Files.readString(f.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new Fehler("Datei " + f + " nicht lesbar: " + e.getMessage(), e);
        }
    }

    /** Die .txt-Dateien eines Ordners, nach Namen geordnet. */
    public static List<File> dateien(File d) {
        File[] fs = d.listFiles((x, n) -> n.endsWith(".txt"));
        if (fs == null) return List.of();
        List<File> l = new ArrayList<>(Arrays.asList(fs));
        l.sort(Comparator.comparing(File::getName));
        return l;
    }

    public static void loesche(File f, String was) throws Fehler {
        if (f.isFile() && !f.delete()) throw new Fehler(was + " lässt sich nicht löschen: " + f);
    }

    /** Alle Einträge einer Art (Unterordner) als Textformat. */
    public List<LinkedHashMap<String, String>> eintraege(String art) throws Fehler {
        List<LinkedHashMap<String, String>> l = new ArrayList<>();
        for (File f : dateien(sub(art))) l.add(lies(f));
        return l;
    }

    /** Einen Eintrag schreiben (ersetzt einen gleichnamigen). */
    public void eintrag(String art, String name, Map<String, String> werte, String kopf) throws Fehler {
        schreibe(sub(art, dateiname(name)), Felder.alsText(werte, kopf));
    }

    public void loeschen(String art, String name) throws Fehler { loesche(sub(art, dateiname(name)), name); }

    /** Die nächste freie Nummer in einem Ordner mit Dateien N.txt. */
    protected long naechsteNummer(File d) {
        long n = 0;
        for (File f : dateien(d)) {
            try { n = Math.max(n, Long.parseLong(f.getName().replace(".txt", ""))); } catch (NumberFormatException e) { /* anderer Name */ }
        }
        return n + 1;
    }

    public static String rechner() {
        String n = System.getenv("COMPUTERNAME");
        if (n == null) n = System.getenv("HOSTNAME");
        return n == null ? "" : n;
    }

    // ------------------------------------------------------------------ Sitzungen

    @Override public long sitzungBeginnen(String version, String gpu, String qualitaet) throws Fehler {
        File d = sub("sitzungen");
        long id = naechsteNummer(d);
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("id", Long.toString(id));
        m.put("beginn", Long.toString(System.currentTimeMillis()));
        m.put("ende", "");
        m.put("sauber", "false");
        m.put("rechner", rechner());
        m.put("benutzer", System.getProperty("user.name", ""));
        m.put("version", version);
        m.put("gpu", gpu);
        m.put("qualitaet", qualitaet);
        schreibe(new File(d, id + ".txt"), Felder.alsText(m, "Sitzung " + id + " · " + Instant.now()));
        List<File> alle = dateien(d);
        alle.sort(Comparator.comparingLong(f -> { try { return Long.parseLong(f.getName().replace(".txt", "")); } catch (NumberFormatException e) { return 0L; } }));
        for (int i = 0; i < alle.size() - sitzungenBehalten; i++) {
            File f = alle.get(i);
            if (!f.delete()) break;
        }
        return id;
    }

    /** Die Kopfwerte einer Sitzung (ohne Zustand). */
    public LinkedHashMap<String, String> sitzung(long id) throws Fehler {
        File f = sub("sitzungen", id + ".txt");
        if (!f.isFile()) throw new Fehler("Sitzung " + id + " gibt es nicht");
        return lies(f);
    }

    @Override public void sitzungSichern(long id, Sicherung s) throws Fehler {
        LinkedHashMap<String, String> m = sitzung(id);
        m.putAll(s.spalten());
        m.put("zustandZeit", Long.toString(System.currentTimeMillis()));
        schreibe(sub("sitzungen", id + ".txt"), Felder.alsText(m, "Sitzung " + id));
        schreibe(sub("sitzungen", id + ".zustand"), s.zustand());
    }

    @Override public void sitzungBeenden(long id) throws Fehler {
        LinkedHashMap<String, String> m = sitzung(id);
        m.put("ende", Long.toString(System.currentTimeMillis()));
        m.put("sauber", "true");
        schreibe(sub("sitzungen", id + ".txt"), Felder.alsText(m, "Sitzung " + id));
    }

    /** Die jüngste Sitzung mit Zustand außer {@code ausser}: Kopfwerte plus „zustand“, oder null. */
    public LinkedHashMap<String, String> letzteSitzungText(long ausser) throws Fehler {
        LinkedHashMap<String, String> best = null;
        long bestId = -1;
        for (File f : dateien(sub("sitzungen"))) {
            long id;
            try { id = Long.parseLong(f.getName().replace(".txt", "")); } catch (NumberFormatException e) { continue; }
            if (id == ausser || id < bestId) continue;
            File z = sub("sitzungen", id + ".zustand");
            if (!z.isFile()) continue;
            best = lies(f);
            bestId = id;
        }
        if (best == null) return null;
        try {
            best.put("zustand", Files.readString(sub("sitzungen", bestId + ".zustand").toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new Fehler("Zustand der Sitzung " + bestId + " nicht lesbar: " + e.getMessage(), e);
        }
        return best;
    }

    // ------------------------------------------------------------------ Protokoll

    @Override public void protokoll(List<Protokoll.Zeile> zeilen, Long sitzung) throws Fehler {
        StringBuilder b = new StringBuilder();
        for (Protokoll.Zeile z : zeilen) b.append(zeile(z, sitzung)).append('\n');
        try {
            Files.createDirectories(dir.toPath());
            Files.writeString(new File(dir, "protokoll.txt").toPath(), b.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new Fehler("Protokoll nicht geschrieben: " + e.getMessage(), e);
        }
    }

    /** Eine Zeile der Datei: Zeit, Sitzung, Stufe, Quelle, Szenenzeit, Ort, Text (Tab getrennt); der Stapel eingerückt darunter. */
    static String zeile(Protokoll.Zeile z, Long sitzung) {
        String s = Instant.ofEpochMilli(z.zeitMs()) + "\t" + (sitzung == null ? "" : sitzung) + "\t" + z.stufe() + "\t" + z.quelle() + "\t"
                + (z.szenenzeit() == null ? "" : z.szenenzeit()) + "\t" + (z.ort() == null ? "" : z.ort()) + "\t" + z.text().replace('\n', ' ');
        if (z.detail() != null) s += "\n    " + z.detail().strip().replace("\n", "\n    ");
        return s;
    }

    /** Die letzten n Zeilen der Datei (ohne Stapel). */
    public List<String> letzteZeilen(int n) throws Fehler {
        File f = new File(dir, "protokoll.txt");
        if (!f.isFile()) return List.of();
        try {
            List<String> all = new ArrayList<>();
            for (String z : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) if (!z.startsWith("    ")) all.add(z);
            return all.subList(Math.max(0, all.size() - n), all.size());
        } catch (IOException e) {
            throw new Fehler("Protokoll nicht lesbar: " + e.getMessage(), e);
        }
    }

    @Override public void schliessen() { }
}
