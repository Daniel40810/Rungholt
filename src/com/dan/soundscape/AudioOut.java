package com.dan.soundscape;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * Spielt eine {@link Soundscape} über den Lautsprecher: ein eigener Faden rechnet Stücke von 512 Werten und schreibt
 * sie in eine Leitung aus {@code javax.sound.sampled} (16 bit, Stereo, Abtastrate der Stufe). Der Puffer der Leitung
 * hält gut 90 ms; ändert sich die Stufe, öffnet der Faden die Leitung neu. Ohne Gerät bleibt es still und
 * {@link #error()} sagt warum.
 */
public final class AudioOut {
    private final Soundscape scape;
    private volatile boolean running;
    private volatile String error = "";
    private volatile double bufferMs;
    private volatile long underruns;
    private Thread thread;
    /** Puffer der Leitung in s. */
    public volatile double latency = 0.09;

    public AudioOut(Soundscape s) { scape = s; }

    public synchronized void start() {
        if (running) return;
        running = true;
        error = "";
        thread = new Thread(this::loop, "Klang");
        thread.setDaemon(true);
        thread.setPriority(Thread.MAX_PRIORITY - 1);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) thread.interrupt();
        thread = null;
    }

    public boolean running() { return running && error.isEmpty(); }
    public String error() { return error; }
    /** Größe des Puffers der Leitung (ms) und wie oft er leergelaufen ist. */
    public double bufferMs() { return bufferMs; }
    public long underruns() { return underruns; }

    private void loop() {
        SourceDataLine line = null;
        int rate = -1;
        float[] f = new float[1024];
        byte[] b = new byte[2048];
        try {
            while (running) {
                int sr = scape.sampleRate();
                if (line == null || sr != rate) {
                    if (line != null) { line.drain(); line.close(); }
                    AudioFormat fmt = new AudioFormat(sr, 16, 2, true, false);
                    line = AudioSystem.getSourceDataLine(fmt);
                    int bytes = ((int) (latency * sr)) * 4;
                    line.open(fmt, Math.max(4096, bytes));
                    line.start();
                    rate = sr;
                    bufferMs = line.getBufferSize() / 4.0 / sr * 1000;
                }
                scape.render(f, 512);
                for (int i = 0; i < 1024; i++) {
                    int v = (int) Math.max(-32767, Math.min(32767, f[i] * 32767));
                    b[2 * i] = (byte) v;
                    b[2 * i + 1] = (byte) (v >> 8);
                }
                if (line.available() >= line.getBufferSize() - 64) underruns++;
                line.write(b, 0, 2048);
            }
        } catch (Exception | LinkageError e) {
            error = "Keine Tonausgabe: " + e.getMessage();
            running = false;
        } finally {
            if (line != null) { line.stop(); line.close(); }
        }
    }
}
