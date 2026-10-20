package com.dan.buoyancy.demo;

import com.dan.buoyancy.Berg;
import com.dan.buoyancy.Bergs;
import com.dan.buoyancy.BrashField;
import com.dan.buoyancy.Sea;

import java.util.Locale;

/**
 * Prüfung der Eisberge (seit Glacier Phase 9) ohne Fenster: Schwimmlage gegen ρ<sub>Eis</sub>/ρ<sub>Wasser</sub>,
 * Kentern einer stehenden Platte, Treiben im Wind, Schmelze mit wanderndem Schwerpunkt, Stöße, Stranden, Treibeis und
 * die Rechenzeit für 200 Berge. Endet mit „Alles in Ordnung.“.
 */
public final class BergCheck {
    private static int bad, all;

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) { bild(new java.io.File(args[1])); return; }
        long t0 = System.nanoTime();
        floating(); capsize(); drift(); melt(); collide(); ground(); brash(); speed();
        System.out.printf(Locale.GERMAN, "%d Prüfungen, %.1f s%n", all, (System.nanoTime() - t0) / 1e9);
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    /** Ein Quader als Netz (Umlauf nach außen), Mitte im Ursprung. */
    public static Object[] box(double a, double b, double c) {
        float x = (float) (a / 2), y = (float) (b / 2), z = (float) (c / 2);
        float[] p = {-x, -y, -z, x, -y, -z, x, y, -z, -x, y, -z, -x, -y, z, x, -y, z, x, y, z, -x, y, z};
        int[] i = {0, 2, 1, 0, 3, 2, 4, 5, 6, 4, 6, 7, 0, 1, 5, 0, 5, 4, 3, 7, 6, 3, 6, 2, 0, 4, 7, 0, 7, 3, 1, 2, 6, 1, 6, 5};
        return new Object[]{p, i};
    }

    static Berg berg(double a, double b, double c, int samples) {
        Object[] m = box(a, b, c);
        return new Berg((float[]) m[0], (int[]) m[1], samples);
    }

    static Sea flat(double bed) {
        return new Sea() {
            @Override public double height(double x, double z) { return 0; }
            @Override public double level() { return 0; }
            @Override public double bed(double x, double z) { return bed; }
        };
    }

    static Bergs world(Sea s) {
        Bergs w = new Bergs();
        w.sea = s;
        w.windSpeed = 0;
        return w;
    }

    static void run(Bergs w, double seconds, double sceneFactor) {
        for (double t = 0; t < seconds; t += 1 / 60.0) w.step(1 / 60.0, sceneFactor / 60.0);
    }

    static void floating() {
        System.out.println("Schwimmen");
        Bergs w = world(flat(-1e9));
        Berg b = w.add(berg(20, 20, 20, 216));
        b.place(0, 0, 0, null, null, null);
        b.settle(0);
        run(w, 1 / 60.0, 1);
        double s0 = b.submerged();
        run(w, 30, 1);
        double[] df = b.draftAndFreeboard(0);
        check(Math.abs(s0 - 0.9) < 0.01 && Math.abs(b.submerged() - 0.9) < 0.01,
                String.format(Locale.GERMAN, "Würfel 20 m Gletschereis im Süßwasser: %.1f %% unter Wasser (900/1000 = 90 %%), nach 30 s %.1f %%, Tiefgang %.1f m", 100 * s0, 100 * b.submerged(), df[0]));
        double[] v = b.velocity();
        check(Math.abs(v[1]) < 0.01 && b.capsizes() == 0, String.format(Locale.GERMAN, "liegt ruhig (%.3f m/s), kentert nicht", v[1]));
    }

    /** Eine stehende Platte 40 × 10 × 40 m (hoch und dünn) ist instabil und legt sich hin. */
    static void capsize() {
        System.out.println("Kentern");
        Bergs w = world(flat(-1e9));
        int[] ev = {0};
        w.onCapsize = b -> ev[0]++;
        Berg b = w.add(berg(40, 40, 8, 200));
        b.place(0, 0, 0, new double[]{Math.cos(0.02), Math.sin(0.02), 0, 0}, null, null);
        b.settle(0);
        double t = 0;
        while (t < 120 && ev[0] == 0) { run(w, 1, 1); t += 1; }
        double[] r = b.rotation();
        // die dünne Achse (z) steht jetzt senkrecht: die Platte liegt
        check(ev[0] > 0 && Math.abs(r[5]) > 0.9 || ev[0] > 0 && Math.abs(r[2 + 3]) > 0.9,
                String.format(Locale.GERMAN, "stehende Platte 40 × 40 × 8 m kentert nach %.0f s und liegt flach (dünne Achse senkrecht: %.2f)", t, Math.abs(r[5])));
        // Handrechnung: ein Quader schwimmt aufrecht stabil, wenn B²/(12·T) > (H − T)/2 (BM > BG)
        double H = 40, B = 8, T = 0.9 * H;
        check(B * B / (12 * T) < (H - T) / 2, String.format(Locale.GERMAN, "Handrechnung: BM %.2f m < BG %.2f m, also instabil", B * B / (12 * T), (H - T) / 2));
    }

    static void drift() {
        System.out.println("Treiben");
        Bergs w = world(flat(-1e9));
        w.windSpeed = 10;
        w.windFromDeg = 270;   // aus West: treibt nach Osten (+x)
        Berg b = w.add(berg(12, 12, 12, 125));
        b.place(0, 0, 0, null, null, null);
        b.settle(0);
        run(w, 240, 1);
        double[] v = b.velocity();
        double share = v[0] / 10;
        check(v[0] > 0 && share > 0.005 && share < 0.06 && Math.abs(v[2]) < 0.2 * v[0],
                String.format(Locale.GERMAN, "Wind 10 m/s aus West: treibt mit %.2f m/s nach Osten (%.1f %% des Winds; beobachtet 1–5 %% [G])", v[0], 100 * share));
    }

    static void melt() {
        System.out.println("Schmelze");
        Bergs w = world(flat(-1e9));
        w.meltAir = 0.2;
        w.meltWater = 1.5;
        w.meltLine = 3;
        Berg b = w.add(berg(16, 30, 16, 300));
        b.place(0, 0, 0, null, null, null);
        b.settle(0);
        double v0 = b.volume();
        double cg0 = b.cgLocal()[1];
        run(w, 60, 86400 / 60.0 * 2);    // zwei Tage in einer Minute
        check(b.volume() < 0.9 * v0 && b.volume() > 0.2 * v0, String.format(Locale.GERMAN, "zwei Tage Schmelze: %,.0f → %,.0f m³", v0, b.volume()));
        check(Math.abs(b.submerged() - 0.9) < 0.03, String.format(Locale.GERMAN, "schwimmt weiter mit %.0f %% unter Wasser", 100 * b.submerged()));
        double dcg = b.cgLocal()[1] - cg0;
        check(dcg > 0.05, String.format(Locale.GERMAN, "der Schwerpunkt wandert nach oben, unter Wasser schmilzt es schneller: %+.2f m", dcg));
    }

    static void collide() {
        System.out.println("Stöße");
        Bergs w = world(flat(-1e9));
        Berg a = w.add(berg(10, 10, 10, 125)), b = w.add(berg(10, 10, 10, 125));
        a.place(-12, 0, 0, null, new double[]{1, 0, 0}, null);
        b.place(12, 0, 0, null, new double[]{-1, 0, 0}, null);
        a.settle(0); b.settle(0);
        a.place(-12, a.cgWorld()[1], 0, null, new double[]{1, 0, 0}, null);
        b.place(12, b.cgWorld()[1], 0, null, new double[]{-1, 0, 0}, null);
        double min = 1e9;
        for (int i = 0; i < 60 * 30; i++) {
            w.step(1 / 60.0, 1 / 60.0);
            double[] ca = a.cgWorld(), cb = b.cgWorld();
            min = Math.min(min, Math.hypot(ca[0] - cb[0], ca[2] - cb[2]));
        }
        check(min > 8 && w.contacts() >= 0, String.format(Locale.GERMAN, "zwei Würfel 10 m mit 1 m/s gegeneinander: kleinster Abstand der Mitten %.1f m (Kante 10 m)", min));
        check(a.velocity()[0] <= 0.05 && b.velocity()[0] >= -0.05, String.format(Locale.GERMAN, "sie prallen ab oder bleiben liegen (%.2f / %.2f m/s)", a.velocity()[0], b.velocity()[0]));
    }

    static void ground() {
        System.out.println("Stranden");
        Bergs w = world(flat(-6));
        Berg b = w.add(berg(14, 14, 14, 125));
        b.place(0, -2, 0, null, null, null);
        run(w, 40, 1);
        double[] df = b.draftAndFreeboard(0);
        check(b.grounded() && df[0] < 6.6 && b.submerged() < 0.85, String.format(Locale.GERMAN, "Würfel 14 m über 6 m Wasser: sitzt auf (Tiefgang %.1f m statt 12,6 m, %.0f %% unter Wasser)", df[0], 100 * b.submerged()));
    }

    static void brash() {
        System.out.println("Treibeis");
        Sea s = flat(-20);
        Bergs w = world(s);
        w.windSpeed = 8;
        w.windFromDeg = 270;
        BrashField f = new BrashField(128, 128, -256, -256, 4);
        w.brash = f;
        f.add(0, 0, 400, 15, s);
        double a0 = f.at(0, 0);
        for (int i = 0; i < 60 * 60; i++) w.step(1 / 60.0, 1 / 60.0);
        double moved = f.at(0 + 8 * 0.03 * 60, 0);
        check(a0 > 0.1 && moved > 0.3 * a0 && f.at(0, 0) < a0, String.format(Locale.GERMAN, "Treibeis treibt mit dem Wind: Bedeckung %.2f, nach 60 s %.0f m weiter %.2f", a0, 8 * 0.03 * 60, moved));
    }

    static void speed() {
        System.out.println("Rechenzeit");
        Bergs w = world(flat(-1e9));
        w.windSpeed = 6;
        java.util.Random r = new java.util.Random(3);
        for (int i = 0; i < 200; i++) {
            double s = 6 + 18 * r.nextDouble();
            Berg b = w.add(berg(s, s * (0.5 + r.nextDouble()), s * (0.6 + 0.6 * r.nextDouble()), 60));
            b.place((i % 20) * 40, 0, (i / 20) * 40, null, null, null);
            b.settle(0);
        }
        run(w, 2, 1);
        long t0 = System.nanoTime();
        int steps = 120;
        for (int i = 0; i < steps; i++) w.step(1 / 60.0, 1 / 60.0);
        double ms = (System.nanoTime() - t0) / 1e6 / steps;
        check(ms < 4, String.format(Locale.GERMAN, "200 Eisberge: %.2f ms je Schritt (1/60 s; Budget 4 ms im Faden der Physik)", ms));
    }

    /** Ohne Fenster: eine stehende Platte kentert, von der Seite, sechs Augenblicke (die Proben als Kästchen). */
    static void bild(java.io.File out) throws Exception {
        Bergs w = world(flat(-1e9));
        Berg b = w.add(berg(40, 40, 8, 400));
        b.place(0, 0, 0, new double[]{Math.cos(0.02), Math.sin(0.02), 0, 0}, null, null);
        b.settle(0);
        double[] at = {0.1, 6, 12, 16, 20, 40};
        int tw = 300, th = 340;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(tw * at.length, th + 30, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        double t = 0;
        double[] p = new double[3];
        for (int k = 0; k < at.length; k++) {
            while (t < at[k]) { w.step(1 / 60.0, 1 / 60.0); t += 1 / 60.0; }
            int x0 = k * tw, wl = 120;
            g.setColor(new java.awt.Color(150, 190, 215));
            g.fillRect(x0, 0, tw, wl);
            g.setColor(new java.awt.Color(20, 70, 90));
            g.fillRect(x0, wl, tw, th - wl);
            double sc = 4.2;
            for (int s2 = 0; s2 < b.sampleCount(); s2++) {
                if (!b.sampleAlive(s2)) continue;
                p = b.sample(s2);
                int px = (int) (x0 + tw / 2 + p[2] * sc), py = (int) (wl - p[1] * sc);
                int sz = (int) Math.ceil(b.cell() * sc);
                g.setColor(p[1] > 0 ? new java.awt.Color(235, 242, 248) : new java.awt.Color(120, 190, 215));
                g.fillRect(px - sz / 2, py - sz / 2, sz, sz);
            }
            double[] c = b.cgWorld();
            g.setColor(new java.awt.Color(226, 67, 47));
            g.fillOval((int) (x0 + tw / 2 + c[2] * sc) - 4, (int) (wl - c[1] * sc) - 4, 8, 8);
            g.setColor(new java.awt.Color(11, 17, 24));
            g.fillRect(x0, th, tw, 30);
            g.setColor(new java.awt.Color(220, 228, 235));
            g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 14));
            g.drawString(String.format(Locale.GERMAN, "%.0f s · %.0f %% unter Wasser", at[k], 100 * b.submerged()), x0 + 10, th + 20);
        }
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", out);
        System.out.println("Bild: " + out.getPath() + " · " + b.describe(0));
    }

    static void check(boolean c, String what) {
        all++;
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    private BergCheck() { }
}
