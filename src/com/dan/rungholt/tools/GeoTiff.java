package com.dan.rungholt.tools;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Liest ein einkanaliges Höhenmodell als GeoTIFF ohne GDAL (aus Glacier und Haithabu, mit LZW und geografischen
 * Koordinaten): klassisches TIFF (kein BigTIFF), Kacheln oder Streifen, unkomprimiert, Deflate oder LZW
 * (so schreibt GDAL den Zuschnitt aus Copernicus GLO-30), Prädiktor keiner, waagerecht oder Fließkomma, Werte als
 * float32, int16 oder uint16. Die Lage kommt aus ModelTiepoint und ModelPixelScale (linke obere Ecke, Pixelgröße),
 * das Bezugssystem aus dem GeoKey 3072 (projiziert, etwa 25832 = UTM 32 Nord der BAW-Küstenhöhen) oder 2048 (geografisch, 4326 = WGS84;
 * dann sind Ecke und Pixelgröße in Grad: east0 = Länge, north0 = Breite).
 */
public final class GeoTiff {
    public final int width, height;
    /** Werte zeilenweise von Nord nach Süd; NaN, wo der Wert fehlt (GDAL_NODATA). */
    public final float[] data;
    /** Linke obere Ecke (Ost, Nord) und Pixelgröße in Metern, bei {@link #geographic} in Grad (Länge, Breite). */
    public final double east0, north0, sizeX, sizeY;
    /** EPSG-Code des Bezugssystems, 0 wenn unbekannt. */
    public final int epsg;
    /** Geografische Koordinaten (Grad) statt Metern. */
    public boolean geographic() { return epsg == 4326 || epsg == 4258 || (epsg > 4000 && epsg < 5000); }

    private GeoTiff(int w, int h, float[] d, double e0, double n0, double sx, double sy, int epsg) {
        width = w; height = h; data = d; east0 = e0; north0 = n0; sizeX = sx; sizeY = sy; this.epsg = epsg;
    }

    /** Wert am Pixel (Spalte, Zeile), NaN außerhalb. */
    public float at(int col, int row) {
        if (col < 0 || row < 0 || col >= width || row >= height) return Float.NaN;
        return data[row * width + col];
    }

    @Override public String toString() {
        if (geographic()) return String.format(java.util.Locale.GERMAN, "%d × %d Pixel zu %.2f″, Länge %.4f° bis %.4f°, Breite %.4f° bis %.4f°, EPSG %d",
                width, height, sizeX * 3600, east0, east0 + width * sizeX, north0 - height * sizeY, north0, epsg);
        return String.format(java.util.Locale.GERMAN, "%d × %d Pixel zu %.2f m, Ost %.0f–%.0f, Nord %.0f–%.0f, EPSG %d",
                width, height, sizeX, east0, east0 + width * sizeX, north0 - height * sizeY, north0, epsg);
    }

    public static GeoTiff read(File f) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            byte[] head = new byte[8];
            raf.readFully(head);
            ByteOrder bo = head[0] == 'I' ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
            ByteBuffer hb = ByteBuffer.wrap(head).order(bo);
            int magic = hb.getShort(2) & 0xffff;
            if (magic == 43) throw new IOException("BigTIFF wird nicht gelesen (Ausschnitt kleiner wählen)");
            if (magic != 42) throw new IOException("keine TIFF-Datei");
            long ifd = hb.getInt(4) & 0xffffffffL;
            Map<Integer, long[]> tags = new HashMap<>();
            Map<Integer, double[]> dtags = new HashMap<>();
            Map<Integer, String> stags = new HashMap<>();
            raf.seek(ifd);
            byte[] nb = new byte[2];
            raf.readFully(nb);
            int n = ByteBuffer.wrap(nb).order(bo).getShort() & 0xffff;
            byte[] ents = new byte[n * 12];
            raf.readFully(ents);
            ByteBuffer eb = ByteBuffer.wrap(ents).order(bo);
            for (int i = 0; i < n; i++) {
                int p = i * 12;
                int tag = eb.getShort(p) & 0xffff, type = eb.getShort(p + 2) & 0xffff;
                long count = eb.getInt(p + 4) & 0xffffffffL;
                int size = switch (type) { case 1, 2, 6, 7 -> 1; case 3, 8 -> 2; case 4, 9, 11 -> 4; case 5, 10, 12 -> 8; default -> 1; };
                long bytes = size * count;
                ByteBuffer vb;
                if (bytes <= 4) vb = ByteBuffer.wrap(ents, p + 8, 4).slice().order(bo);
                else {
                    byte[] v = new byte[(int) bytes];
                    raf.seek(eb.getInt(p + 8) & 0xffffffffL);
                    raf.readFully(v);
                    vb = ByteBuffer.wrap(v).order(bo);
                }
                if (type == 2) {
                    byte[] s = new byte[(int) count];
                    vb.get(s);
                    stags.put(tag, new String(s, java.nio.charset.StandardCharsets.US_ASCII).replace("\0", "").trim());
                } else if (type == 12 || type == 11) {
                    double[] d = new double[(int) count];
                    for (int k = 0; k < count; k++) d[k] = type == 12 ? vb.getDouble(k * 8) : vb.getFloat(k * 4);
                    dtags.put(tag, d);
                } else {
                    long[] l = new long[(int) count];
                    for (int k = 0; k < count; k++) l[k] = switch (size) {
                        case 1 -> vb.get(k) & 0xff;
                        case 2 -> vb.getShort(k * 2) & 0xffff;
                        case 8 -> vb.getInt(k * 8) & 0xffffffffL;   // RATIONAL: nur Zähler
                        default -> vb.getInt(k * 4) & 0xffffffffL;
                    };
                    tags.put(tag, l);
                }
            }
            int w = (int) one(tags, 256), h = (int) one(tags, 257);
            int bps = (int) opt(tags, 258, 1), comp = (int) opt(tags, 259, 1), spp = (int) opt(tags, 277, 1);
            int pred = (int) opt(tags, 317, 1), fmt = (int) opt(tags, 339, 1);
            if (spp != 1) throw new IOException("nur ein Kanal wird gelesen, die Datei hat " + spp);
            if (comp != 1 && comp != 5 && comp != 8 && comp != 32946) throw new IOException("Kompression " + comp + " wird nicht gelesen (unkomprimiert, LZW oder Deflate wählen)");
            if (!(fmt == 3 && bps == 32) && !(fmt != 3 && bps == 16)) throw new IOException("Werte mit " + bps + " Bit, Format " + fmt + " werden nicht gelesen");
            boolean tiled = tags.containsKey(322);
            int tw = tiled ? (int) one(tags, 322) : w, th = tiled ? (int) one(tags, 323) : (int) opt(tags, 278, h);
            long[] offs = tiled ? tags.get(324) : tags.get(273), cnts = tiled ? tags.get(325) : tags.get(279);
            int across = (w + tw - 1) / tw;
            int bytesPer = bps / 8;
            float nodata = Float.NaN;
            if (stags.containsKey(42113)) try { nodata = Float.parseFloat(stags.get(42113)); } catch (NumberFormatException ignored) { }
            float[] out = new float[w * h];
            Inflater inf = new Inflater();
            byte[] raw = new byte[tw * th * bytesPer];
            for (int s = 0; s < offs.length; s++) {
                byte[] in = new byte[(int) cnts[s]];
                raf.seek(offs[s]);
                raf.readFully(in);
                int rows = tiled ? th : Math.min(th, h - s * th);
                int len = tw * rows * bytesPer;
                if (comp == 1) System.arraycopy(in, 0, raw, 0, Math.min(len, in.length));
                else if (comp == 5) lzw(in, raw, len);
                else {
                    inf.reset();
                    inf.setInput(in);
                    try {
                        int got = 0;
                        while (got < len && !inf.finished()) {
                            int r = inf.inflate(raw, got, len - got);
                            if (r == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                            got += r;
                        }
                    } catch (DataFormatException e) { throw new IOException("Deflate-Fehler in Block " + s, e); }
                }
                int x0 = tiled ? (s % across) * tw : 0, y0 = tiled ? (s / across) * th : s * th;
                ByteBuffer bb = ByteBuffer.wrap(raw).order(bo);
                byte[] rowBuf = new byte[tw * bytesPer];
                int prev16 = 0;
                for (int r = 0; r < rows; r++) {
                    int y = y0 + r;
                    if (y >= h) break;
                    int base = r * tw * bytesPer;
                    if (pred == 3) {
                        // Fließkomma-Prädiktor: Bytes waagerecht aufsummiert, dann nach Bytestelle sortiert (höchstes zuerst)
                        for (int k = 1; k < tw * bytesPer; k++) raw[base + k] += raw[base + k - 1];
                        for (int c = 0; c < tw; c++)
                            for (int b = 0; b < bytesPer; b++) {
                                int dst = bo == ByteOrder.BIG_ENDIAN ? b : bytesPer - 1 - b;
                                rowBuf[c * bytesPer + dst] = raw[base + b * tw + c];
                            }
                        System.arraycopy(rowBuf, 0, raw, base, tw * bytesPer);
                    }
                    for (int c = 0; c < tw; c++) {
                        int x = x0 + c;
                        if (x >= w) break;
                        float v;
                        if (bps == 32) v = bb.getFloat(base + c * 4);
                        else {
                            int s16 = bb.getShort(base + c * 2);
                            if (pred == 2 && c > 0) { s16 += prev16; }
                            prev16 = s16;
                            v = fmt == 2 ? (short) s16 : (s16 & 0xffff);
                        }
                        out[y * w + x] = v == nodata ? Float.NaN : v;
                    }
                    prev16 = 0;
                }
            }
            inf.end();
            double[] scale = dtags.getOrDefault(33550, new double[]{1, 1, 0}), tie = dtags.get(33922);
            if (tie == null) throw new IOException("keine Lage (ModelTiepoint) in der Datei");
            double e0 = tie[3] - tie[0] * scale[0], n0 = tie[4] + tie[1] * scale[1];
            int epsg = 0;
            long[] gk = tags.get(34735);
            if (gk != null) for (int k = 4; k + 3 < gk.length; k += 4) if ((gk[k] == 3072 || gk[k] == 2048) && gk[k + 1] == 0 && epsg == 0) epsg = (int) gk[k + 3];
            // geografisch ohne Projektionsschlüssel: WGS84 annehmen, wenn die Pixel kleiner als ein Grad sind
            if (epsg == 0 && scale[0] < 1 && Math.abs(e0) <= 180 && Math.abs(n0) <= 90) epsg = 4326;
            return new GeoTiff(w, h, out, e0, n0, scale[0], scale[1], epsg);
        }
    }

    /**
     * LZW nach TIFF 6.0: Codes von 9 bis 12 Bit, höchstwertiges Bit zuerst, 256 = Tabelle leeren, 257 = Ende,
     * die Breite wächst einen Code früher („early change“), wie libtiff schreibt.
     */
    static void lzw(byte[] in, byte[] out, int len) throws IOException {
        byte[][] table = new byte[4096][];
        for (int i = 0; i < 256; i++) table[i] = new byte[]{(byte) i};
        int next = 258, bits = 9, pos = 0;
        long buf = 0;
        int have = 0, ip = 0;
        byte[] prev = null;
        while (pos < len) {
            while (have < bits) {
                if (ip >= in.length) return;
                buf = (buf << 8) | (in[ip++] & 0xff);
                have += 8;
            }
            int code = (int) ((buf >>> (have - bits)) & ((1 << bits) - 1));
            have -= bits;
            if (code == 257) return;
            if (code == 256) {
                next = 258;
                bits = 9;
                prev = null;
                continue;
            }
            byte[] entry;
            if (code < next && table[code] != null) entry = table[code];
            else if (code == next && prev != null) {
                entry = java.util.Arrays.copyOf(prev, prev.length + 1);
                entry[prev.length] = prev[0];
            } else throw new IOException("LZW: ungültiger Code " + code);
            int n = Math.min(entry.length, len - pos);
            System.arraycopy(entry, 0, out, pos, n);
            pos += n;
            if (prev != null && next < 4096) {
                byte[] e = java.util.Arrays.copyOf(prev, prev.length + 1);
                e[prev.length] = entry[0];
                table[next++] = e;
            }
            prev = entry;
            if (next >= (1 << bits) - 1 && bits < 12) bits++;
        }
    }

    private static long one(Map<Integer, long[]> t, int tag) throws IOException {
        long[] v = t.get(tag);
        if (v == null || v.length == 0) throw new IOException("TIFF-Feld " + tag + " fehlt");
        return v[0];
    }

    private static long opt(Map<Integer, long[]> t, int tag, long def) {
        long[] v = t.get(tag);
        return v == null || v.length == 0 ? def : v[0];
    }

    /** Kurzer Test: {@code GeoTiff datei.tif} */
    public static void main(String[] a) throws IOException {
        long t0 = System.nanoTime();
        GeoTiff g = read(new File(a[0]));
        float mn = Float.MAX_VALUE, mx = -Float.MAX_VALUE;
        int nan = 0;
        for (float v : g.data) { if (Float.isNaN(v)) nan++; else { mn = Math.min(mn, v); mx = Math.max(mx, v); } }
        System.out.printf(java.util.Locale.GERMAN, "%s%nHöhen %.2f … %.2f m, %d ohne Wert, %.1f s%n", g, mn, mx, nan, (System.nanoTime() - t0) / 1e9);
        if (a.length > 1) System.out.println(g.at(Integer.parseInt(a[1]), Integer.parseInt(a[2])));
    }

}
