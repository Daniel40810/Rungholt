package com.dan.store;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Liest und schreibt die öffentlichen Felder eines Objekts (aus Lighthouse, Haithabu F25 A). Der Paketvertrag (Punkt 5) sagt: Jedes Package
 * stellt seine Eingänge als öffentliche Felder bereit. Damit lässt sich jedes Preset und der ganze Zustand der Szene
 * ohne Handarbeit je Package speichern; neue Felder späterer Phasen kommen von selbst mit.
 * <p>
 * Berücksichtigt werden öffentliche, nicht statische, nicht finale Felder einfacher Art: double, float, int, long,
 * boolean, String und Aufzählungen. Alles andere (Felder mit Objekten, Arrays, Listen) bleibt außen vor. Nur Java,
 * kein Bezug auf eine App: Jedes Projekt kann die Klasse samt Textformat mitnehmen.
 */
public final class Felder {
    private Felder() { }

    /** Die Art eines Felds, wie sie in der Spalte TYP der Presets steht (DOUBLE, INT, BOOLEAN, STRING, ENUM). */
    public static String typ(Class<?> c) {
        if (c == double.class || c == float.class || c == Double.class || c == Float.class) return "DOUBLE";
        if (c == int.class || c == long.class || c == short.class || c == Integer.class || c == Long.class) return "INT";
        if (c == boolean.class || c == Boolean.class) return "BOOLEAN";
        if (c == String.class) return "STRING";
        if (c.isEnum()) return "ENUM";
        return null;
    }

    /** Die passenden Felder einer Klasse in Quelltextreihenfolge (soweit die JVM sie so liefert). */
    public static List<Field> felder(Class<?> c) {
        List<Field> l = new ArrayList<>();
        for (Field f : c.getFields()) {
            int m = f.getModifiers();
            if (Modifier.isStatic(m) || Modifier.isFinal(m) || typ(f.getType()) == null) continue;
            l.add(f);
        }
        return l;
    }

    /** Alle passenden Felder als Text, ohne die Namen in {@code ohne}. */
    public static LinkedHashMap<String, String> lesen(Object o, Set<String> ohne) {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        for (Field f : felder(o.getClass())) {
            if (ohne != null && ohne.contains(f.getName())) continue;
            try {
                Object v = f.get(o);
                m.put(f.getName(), text(v));
            } catch (IllegalAccessException e) {
                // öffentlich, aber nicht erreichbar (Modul): auslassen
            }
        }
        return m;
    }

    public static LinkedHashMap<String, String> lesen(Object o) { return lesen(o, null); }

    /** Die Art je Feldname (für die Spalte TYP der Presets). */
    public static Map<String, String> typen(Class<?> c) {
        Map<String, String> m = new LinkedHashMap<>();
        for (Field f : felder(c)) m.put(f.getName(), typ(f.getType()));
        return m;
    }

    static String text(Object v) {
        if (v == null) return "";
        if (v instanceof Double d) return Double.isNaN(d) ? "NaN" : Zahl.text(d);
        if (v instanceof Float d) return Float.isNaN(d) ? "NaN" : Zahl.text(d);
        if (v instanceof Enum<?> e) return e.name();
        return v.toString();
    }

    /**
     * Schreibt die Werte in die gleichnamigen Felder. Gibt zurück, was nicht ging (unbekanntes Feld, falscher Wert);
     * eine leere Liste heißt: alles übernommen. Namen in {@code ohne} werden übergangen, ohne Meldung.
     */
    public static List<String> schreiben(Object o, Map<String, String> werte, Set<String> ohne) {
        List<String> fehler = new ArrayList<>();
        Map<String, Field> fs = new LinkedHashMap<>();
        for (Field f : felder(o.getClass())) fs.put(f.getName(), f);
        for (Map.Entry<String, String> e : werte.entrySet()) {
            if (ohne != null && ohne.contains(e.getKey())) continue;
            Field f = fs.get(e.getKey());
            if (f == null) { fehler.add(e.getKey() + ": unbekannt"); continue; }
            try {
                setzen(o, f, e.getValue());
            } catch (RuntimeException | IllegalAccessException ex) {
                fehler.add(e.getKey() + ": „" + e.getValue() + "“ passt nicht (" + ex.getClass().getSimpleName() + ")");
            }
        }
        return fehler;
    }

    public static List<String> schreiben(Object o, Map<String, String> werte) { return schreiben(o, werte, null); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setzen(Object o, Field f, String s) throws IllegalAccessException {
        Class<?> c = f.getType();
        if (c == double.class) f.setDouble(o, Zahl.lies(s));
        else if (c == float.class) f.setFloat(o, (float) Zahl.lies(s));
        else if (c == int.class) f.setInt(o, (int) Math.round(Zahl.lies(s)));
        else if (c == long.class) f.setLong(o, Math.round(Zahl.lies(s)));
        else if (c == short.class) f.setShort(o, (short) Math.round(Zahl.lies(s)));
        else if (c == boolean.class) {
            if (!s.equals("true") && !s.equals("false")) throw new IllegalArgumentException(s);
            f.setBoolean(o, Boolean.parseBoolean(s));
        }
        else if (c == String.class) f.set(o, s);
        else if (c.isEnum()) f.set(o, Enum.valueOf((Class) c, s));
        else if (c == Double.class) f.set(o, s.isEmpty() ? null : Zahl.lies(s));
        else if (c == Integer.class) f.set(o, s.isEmpty() ? null : (int) Math.round(Zahl.lies(s)));
        else if (c == Boolean.class) f.set(o, s.isEmpty() ? null : Boolean.parseBoolean(s));
        else throw new IllegalArgumentException(c.getSimpleName());
    }

    // ------------------------------------------------------------------ Textformat

    /**
     * Schreibt Schlüssel und Werte als Zeilen {@code schluessel=wert}, UTF-8; Zeilenumbruch und Rückstrich im Wert
     * werden als {@code \n} und {@code \\} geschrieben. Zeilen mit # am Anfang sind Kommentare.
     */
    public static String alsText(Map<String, String> m, String kopf) {
        StringBuilder b = new StringBuilder();
        if (kopf != null) for (String z : kopf.split("\\R")) b.append("# ").append(z).append('\n');
        for (Map.Entry<String, String> e : m.entrySet()) {
            String v = e.getValue() == null ? "" : e.getValue();
            b.append(e.getKey()).append('=').append(v.replace("\\", "\\\\").replace("\r", "").replace("\n", "\\n")).append('\n');
        }
        return b.toString();
    }

    /** Liest das Textformat von {@link #alsText}. Zeilen ohne = und Kommentare werden übergangen. */
    public static LinkedHashMap<String, String> ausText(String text) {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        if (text == null) return m;
        for (String z : text.split("\\R")) {
            if (z.isBlank() || z.startsWith("#")) continue;
            int i = z.indexOf('=');
            if (i <= 0) continue;
            String v = z.substring(i + 1);
            StringBuilder o = new StringBuilder();
            for (int k = 0; k < v.length(); k++) {
                char ch = v.charAt(k);
                if (ch == '\\' && k + 1 < v.length()) {
                    char n = v.charAt(++k);
                    o.append(n == 'n' ? '\n' : n);
                } else o.append(ch);
            }
            m.put(z.substring(0, i).strip(), o.toString());
        }
        return m;
    }

    /** Die Einträge mit dem Präfix {@code vor + "."}, ohne Präfix. */
    public static LinkedHashMap<String, String> teil(Map<String, String> m, String vor) {
        LinkedHashMap<String, String> t = new LinkedHashMap<>();
        String p = vor + ".";
        for (Map.Entry<String, String> e : m.entrySet()) if (e.getKey().startsWith(p)) t.put(e.getKey().substring(p.length()), e.getValue());
        return t;
    }
}
