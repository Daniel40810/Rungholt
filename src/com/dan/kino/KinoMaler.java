package com.dan.kino;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Malt eine {@link Kino.Tafel} mit Java2D in ein durchsichtiges Bild von der Größe des Fensters (volle Deckkraft; die
 * Deckkraft der Tafel legt die App beim Zeichnen darüber). Titel und Abspann stehen in der Mitte auf Schwarz, der
 * Untertitel mittig im unteren Balken (bei schmalen Balken mit Schatten über dem Bild).
 * <p>
 * Schriften und Farben sind Felder, damit jede Szene ihren Ton findet (Glacier: Eisblau im Kopf, Haithabu etwa Bernstein).
 */
public final class KinoMaler {
    public Color kopfFarbe = new Color(150, 200, 222), titelFarbe = new Color(240, 244, 247), zeileFarbe = new Color(176, 190, 200),
            untertitelFarbe = new Color(238, 242, 245), schatten = new Color(0, 0, 0, 200);
    public String titelSchrift = Font.SERIF, textSchrift = Font.SANS_SERIF;

    /** Ein neues Bild w × h mit der Tafel (oder leer). */
    public BufferedImage malen(Kino.Tafel tafel, int w, int h) {
        BufferedImage im = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        if (tafel == null) return im;
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        switch (tafel.art()) {
            case TITEL -> titel(g, tafel.zeilen(), w, h);
            case ABSPANN -> abspann(g, tafel.zeilen(), w, h);
            case UNTERTITEL -> untertitel(g, tafel.zeilen().isEmpty() ? "" : tafel.zeilen().get(0), w, h);
        }
        g.dispose();
        return im;
    }

    private void titel(Graphics2D g, List<String> z, int w, int h) {
        String kopf = z.size() > 0 ? z.get(0) : "", titel = z.size() > 1 ? z.get(1) : "", unter = z.size() > 2 ? z.get(2) : "";
        Font fk = gesperrt(new Font(textSchrift, Font.PLAIN, px(h, 0.017, 11)), 0.32f);
        Font ft = new Font(titelSchrift, Font.PLAIN, px(h, 0.066, 24));
        Font fu = new Font(textSchrift, Font.PLAIN, px(h, 0.021, 12));
        List<String> ul = umbrechen(g.getFontMetrics(fu), unter, (int) (w * 0.56), 3);
        FontMetrics mk = g.getFontMetrics(fk), mt = g.getFontMetrics(ft), mu = g.getFontMetrics(fu);
        int luft = (int) (h * 0.03);
        int hoehe = (kopf.isEmpty() ? 0 : mk.getHeight() + luft) + mt.getHeight() + (ul.isEmpty() ? 0 : luft + ul.size() * (int) (mu.getHeight() * 1.25));
        int y = (h - hoehe) / 2;
        if (!kopf.isEmpty()) {
            g.setFont(fk);
            g.setColor(kopfFarbe);
            mitte(g, kopf.toUpperCase(java.util.Locale.ROOT), w, y + mk.getAscent());
            y += mk.getHeight() + luft;
        }
        g.setFont(ft);
        g.setColor(titelFarbe);
        mitte(g, titel, w, y + mt.getAscent());
        y += mt.getHeight();
        // eine feine Linie unter dem Titel
        int lw = Math.min(mt.stringWidth(titel), (int) (w * 0.3));
        g.setColor(new Color(kopfFarbe.getRed(), kopfFarbe.getGreen(), kopfFarbe.getBlue(), 150));
        g.fillRect((w - lw / 2) / 2, y + luft / 3, lw / 2, Math.max(1, h / 900));
        if (!ul.isEmpty()) {
            y += luft;
            g.setFont(fu);
            g.setColor(zeileFarbe);
            for (String l : ul) {
                mitte(g, l, w, y + mu.getAscent());
                y += (int) (mu.getHeight() * 1.25);
            }
        }
    }

    private void abspann(Graphics2D g, List<String> z, int w, int h) {
        if (z.isEmpty()) return;
        Font ft = new Font(titelSchrift, Font.PLAIN, px(h, 0.05, 20));
        Font fz = new Font(textSchrift, Font.PLAIN, px(h, 0.02, 12));
        FontMetrics mt = g.getFontMetrics(ft), mz = g.getFontMetrics(fz);
        int zeile = (int) (mz.getHeight() * 1.6), luft = (int) (h * 0.035);
        int hoehe = mt.getHeight() + luft + (z.size() - 1) * zeile;
        int y = (h - hoehe) / 2;
        g.setFont(ft);
        g.setColor(titelFarbe);
        mitte(g, z.get(0), w, y + mt.getAscent());
        y += mt.getHeight() + luft;
        g.setFont(fz);
        for (int i = 1; i < z.size(); i++) {
            String l = z.get(i);
            // „Rolle: Name“ als zwei Farben
            int c = l.indexOf(": ");
            if (c > 0 && c < 40) {
                String a = l.substring(0, c + 1) + " ", b = l.substring(c + 2);
                int x = (w - mz.stringWidth(a + b)) / 2;
                g.setColor(kopfFarbe);
                g.drawString(a, x, y + mz.getAscent());
                g.setColor(zeileFarbe);
                g.drawString(b, x + mz.stringWidth(a), y + mz.getAscent());
            } else {
                g.setColor(zeileFarbe);
                mitte(g, l, w, y + mz.getAscent());
            }
            y += zeile;
        }
    }

    private void untertitel(Graphics2D g, String text, int w, int h) {
        Font f = new Font(textSchrift, Font.PLAIN, Math.max(15, Math.min(42, (int) Math.round(h * 0.03))));
        g.setFont(f);
        FontMetrics m = g.getFontMetrics();
        List<String> ls = umbrechen(m, text, (int) (w * 0.72), 2);
        int zeile = (int) (m.getHeight() * 1.12), hoehe = ls.size() * zeile;
        int bar = Kino.balkenPx(w, h, 1);
        boolean imBalken = bar >= hoehe + m.getHeight() * 0.6;
        int y = imBalken ? h - bar + (bar - hoehe) / 2 : h - (int) (h * 0.07) - hoehe;
        for (String l : ls) {
            int x = (w - m.stringWidth(l)) / 2, b = y + m.getAscent();
            g.setColor(schatten);
            int s = Math.max(1, f.getSize() / 14);
            if (!imBalken) for (int dx = -s; dx <= s; dx += s) for (int dy = -s; dy <= s; dy += s) g.drawString(l, x + dx, b + dy);
            else g.drawString(l, x + s, b + s);
            g.setColor(untertitelFarbe);
            g.drawString(l, x, b);
            y += zeile;
        }
    }

    // ------------------------------------------------------------------ Hilfen

    private static int px(int h, double anteil, int min) { return Math.max(min, (int) Math.round(h * anteil)); }

    private static Font gesperrt(Font f, float sperrung) { return f.deriveFont(Map.of(TextAttribute.TRACKING, sperrung)); }

    private static void mitte(Graphics2D g, String s, int w, int baseline) {
        g.drawString(s, (w - g.getFontMetrics().stringWidth(s)) / 2, baseline);
    }

    /** Bricht an Wortgrenzen um; mehr als max Zeilen enden mit „…“. */
    static List<String> umbrechen(FontMetrics m, String text, int breite, int max) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;
        StringBuilder z = new StringBuilder();
        for (String wort : text.strip().split("\\s+")) {
            String probe = z.length() == 0 ? wort : z + " " + wort;
            if (m.stringWidth(probe) <= breite || z.length() == 0) { z.setLength(0); z.append(probe); }
            else { out.add(z.toString()); z.setLength(0); z.append(wort); }
        }
        if (z.length() > 0) out.add(z.toString());
        if (out.size() > max) {
            List<String> k = new ArrayList<>(out.subList(0, max));
            k.set(max - 1, k.get(max - 1) + " …");
            return k;
        }
        return out;
    }
}
