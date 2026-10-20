package com.dan.kino;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.image.BufferedImage;

/**
 * Dasselbe wie {@code gl/KinoGl}, nur mit Java2D: legt den Kinomodus über ein fertiges Bild. Für die Demo, die Prüfung
 * und Szenen ohne Grafikkarte (Swing-Bilder). Das gemerkte Bild der Überblendung gibt die App mit (oder null).
 */
public final class KinoBild {
    private KinoBild() { }

    /** Ein neues Bild: szene mit Überblendung, Film, Schwarz, Balken und Tafel nach dem Stand von k. */
    public static BufferedImage ueber(BufferedImage szene, BufferedImage gemerkt, Kino k, KinoMaler m, double zeit) {
        int w = szene.getWidth(), h = szene.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(szene, 0, 0, null);
        double ub = k.ueberblende();
        if (ub > 0 && gemerkt != null) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) ub));
            g.drawImage(gemerkt, 0, 0, w, h, null);
            g.setComposite(AlphaComposite.SrcOver);
        }
        double film = k.film();
        if (film > 0) {
            float r = (float) Math.hypot(w, h) / 2;
            g.setPaint(new RadialGradientPaint(w / 2f, h / 2f, r, new float[]{0f, 0.45f, 1f},
                    new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 0), new Color(0, 0, 0, (int) (0.38 * 255 * film))}));
            g.fillRect(0, 0, w, h);
            java.util.Random rnd = new java.util.Random(Double.doubleToLongBits(Math.floor(zeit * 24)));
            int n = (int) (w * h / 40 * film);
            for (int i = 0; i < n; i++) {
                int a = (int) (rnd.nextDouble() * 0.045 * 255);
                g.setColor(new Color(0, 0, 0, a));
                g.fillRect(rnd.nextInt(w), rnd.nextInt(h), 2, 2);
            }
        }
        double s = k.schwarz();
        if (s > 0) { g.setColor(new Color(0, 0, 0, (int) Math.round(255 * Math.min(1, s)))); g.fillRect(0, 0, w, h); }
        int bar = k.balkenPx(w, h);
        if (bar > 0) { g.setColor(Color.BLACK); g.fillRect(0, 0, w, bar); g.fillRect(0, h - bar, w, bar); }
        Kino.Tafel t = k.tafel();
        if (t != null) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, t.deckkraft()))));
            g.drawImage((m != null ? m : new KinoMaler()).malen(t, w, h), 0, 0, null);
        }
        g.dispose();
        return out;
    }
}
