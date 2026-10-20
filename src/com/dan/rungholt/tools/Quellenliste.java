package com.dan.rungholt.tools;

import com.dan.rungholt.data.Daten;
import com.dan.rungholt.data.Startdaten;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phase 17 (nach Glacier): die Quellenliste {@code docs/QUELLEN.md} aus denselben Textdateien wie die Startdaten der
 * Datenbank ({@code data/quellen.txt}, {@code befunde.txt}, {@code sturmfluten.txt}). So steht dieselbe Liste im Schema
 * RUNGHOLT, in {@code daten/} und auf GitHub. Aufruf (Run File) ohne Argumente schreibt {@code docs/QUELLEN.md}; die
 * Prüfung vergleicht die Datei mit {@link #text()}.
 */
public final class Quellenliste {
    private Quellenliste() { }

    public static final File DATEI = new File("docs/QUELLEN.md");

    public static String text() {
        List<Daten.Quelle> qs = Startdaten.quellen();
        List<Daten.Befund> bs = Startdaten.befunde();
        List<Daten.Sturmflut> sf = Startdaten.sturmfluten();
        StringBuilder b = new StringBuilder();
        b.append("# Rungholt – Quellen und Befunde\n\n");
        b.append("Erzeugt von `tools/Quellenliste` aus `src/com/dan/rungholt/data/quellen.txt`, `befunde.txt` und `sturmfluten.txt`. Dieselben Zeilen stehen in den Tabellen ")
                .append("`RH_QUELLE`, `RH_BEFUND`, `RH_BEFUND_QUELLE` und `RH_STURMFLUT`. Jeder Befund ist **belegt** (eine Quelle nennt es), **gemessen** ")
                .append("(aus Daten wie den Höhen der BAW abgeleitet) oder **gestaltet** [G] (so gebaut, dass es glaubhaft ist, ohne Beleg).\n\n");
        b.append(String.format(Locale.GERMAN, "%d Quellen · %d Befunde (%d belegt, %d gemessen, %d gestaltet) · %d belegte Sturmfluten%n%n",
                qs.size(), bs.size(), zahl(bs, "BELEGT"), zahl(bs, "GEMESSEN"), zahl(bs, "GESTALTET"), sf.size()));
        b.append("Datumsangaben vor 1582 zählen die Quellen julianisch. Die Uhr der App zählt proleptisch gregorianisch: Die Nacht der Grote Mandränke, ")
                .append("der 15. auf den 16. Januar 1362 der Chroniken, ist bei ihr die vom 23. auf den 24. Januar; dort steht die Springtide.\n\n");
        b.append("## Quellen\n\n| Kurz | Art | Titel | Abgerufen | Wofür |\n|---|---|---|---|---|\n");
        for (Daten.Quelle q : qs)
            b.append("| ").append(q.kurz()).append(" | ").append(art(q.art())).append(" | ")
                    .append(q.url() == null || q.url().isBlank() ? esc(q.titel()) : "[" + esc(q.titel()) + "](" + q.url() + ")").append(" | ")
                    .append(q.abgerufen() == null ? "" : q.abgerufen().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))).append(" | ")
                    .append(esc(q.bemerkung() == null ? "" : q.bemerkung())).append(" |\n");
        b.append("\n## Befunde nach Thema\n");
        Map<String, StringBuilder> themen = new LinkedHashMap<>();
        for (Daten.Befund x : bs) {
            StringBuilder t = themen.computeIfAbsent(x.thema(), k -> new StringBuilder("\n### " + thema(k) + "\n\n| Gegenstand | Befund | Text | Quellen |\n|---|---|---|---|\n"));
            t.append("| ").append(esc(x.gegenstand())).append(" | ").append(befund(x.befund())).append(" | ").append(esc(x.text())).append(" | ")
                    .append(String.join(", ", x.quellen())).append(" |\n");
        }
        for (StringBuilder t : themen.values()) b.append(t);
        b.append("\n## Die belegten Sturmfluten (1362–2013)\n\nScheitel am Pegel Husum nur, wo eine Quelle ihn nennt; Werte aus einer Chronik ohne Beleg stehen im Text.\n\n")
                .append("| Jahr | Name | Datum | Kalender | Scheitel Husum | Folgen | Quelle |\n|---|---|---|---|---|---|---|\n");
        for (Daten.Sturmflut s : sf)
            b.append("| ").append(s.jahr()).append(" | ").append(esc(s.name())).append(" | ").append(esc(s.datumText())).append(" | ")
                    .append(s.kalender() == null ? "" : s.kalender().toLowerCase(Locale.ROOT)).append(" | ")
                    .append(s.scheitelCm() == null ? "" : String.format(Locale.GERMAN, "%,.2f m %s", s.scheitelCm() / 100.0, s.bezug() == null ? "" : s.bezug()).strip()).append(" | ")
                    .append(esc(s.folgen())).append(" | ").append(s.quelle() == null ? "" : s.quelle()).append(" |\n");
        return b.toString();
    }

    private static long zahl(List<Daten.Befund> bs, String art) { return bs.stream().filter(x -> art.equals(x.befund())).count(); }

    private static String art(String a) {
        return switch (a == null ? "" : a) {
            case "WEB" -> "Netz";
            case "BUCH" -> "Buch";
            case "ARTIKEL" -> "Artikel";
            case "DATEN" -> "Daten";
            case "MESSUNG" -> "Messung";
            default -> a == null ? "" : a;
        };
    }

    private static String befund(String b) {
        return switch (b) { case "BELEGT" -> "belegt"; case "GEMESSEN" -> "gemessen"; case "GESTALTET" -> "gestaltet [G]"; default -> b; };
    }

    private static String thema(String t) {
        return switch (t) {
            case "GELAENDE" -> "Gelände"; case "MANDRAENKE" -> "Die Grote Mandränke"; case "RUNGHOLT" -> "Rungholt 1362";
            default -> t.charAt(0) + t.substring(1).toLowerCase(Locale.ROOT);
        };
    }

    private static String esc(String s) { return s == null ? "" : s.replace("|", "\\|").replace("\n", " "); }

    public static void main(String[] a) throws Exception {
        File f = a.length > 0 ? new File(a[0]) : DATEI;
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        Files.writeString(f.toPath(), text(), StandardCharsets.UTF_8);
        System.out.println("Quellenliste: " + f.getAbsolutePath());
    }
}
