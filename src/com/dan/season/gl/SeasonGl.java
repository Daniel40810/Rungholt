package com.dan.season.gl;

import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Gemeinsames der GL-Teile von {@code com.dan.season}: Blick und Licht als kleine Werteklassen, Shader laden und binden.
 * Licht wie in der App: Sonne und Mond als Bestrahlung (vorbelichtet), der Himmel als Bestrahlung/π mal Gewicht; die
 * Flächen malen Lambert mit Albedo/π in der Farbe.
 */
public final class SeasonGl {
    private SeasonGl() {}

    /** Blick: Matrix (Spalten), Auge, umgekehrte Tiefe, Bildgröße, Öffnungswinkel. */
    public static final class View {
        public float[] viewProj;
        public double[] eye = new double[3];
        public boolean reversedZ;
        public int width = 1, height = 1;
        public double fovyDeg = 50;
    }

    /** Licht: Richtungen zu Sonne und Mond, ihre Bestrahlung; Himmel (Bestrahlung/π, schon gewichtet); Dunst. */
    public static final class Light {
        public double[] sunDir = {0, 1, 0}, sunColor = {1, 1, 1}, moonDir = {0, -1, 0}, moonColor = {0, 0, 0};
        public double[] sky = {0.2, 0.25, 0.3}, fogColor = {0.5, 0.55, 0.6};
        public double fogDistance = 25_000;
        /** Schattenkarte der Wolken (r = Durchlässigkeit) mit Ecke x, z und Kante (m); 0 = ohne. */
        public int cloudShadow;
        public double[] cloudRect = {0, 0, 1};
    }

    static String read(String file) {
        try (InputStream in = SeasonGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("season: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("season: " + file + ": " + e.getMessage(), e);
        }
    }

    static int program(GL4 gl, String vertFile, String fragFile) {
        int p = gl.glCreateProgram();
        int vs = shader(gl, GL4.GL_VERTEX_SHADER, "#version 430 core\n" + read(vertFile), vertFile);
        int fs = shader(gl, GL4.GL_FRAGMENT_SHADER, "#version 430 core\n" + read(fragFile), fragFile);
        gl.glAttachShader(p, vs);
        gl.glAttachShader(p, fs);
        gl.glLinkProgram(p);
        gl.glDeleteShader(vs);
        gl.glDeleteShader(fs);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            byte[] b = new byte[4096];
            int[] len = new int[1];
            gl.glGetProgramInfoLog(p, b.length, len, 0, b, 0);
            throw new IllegalStateException("season: Programm lässt sich nicht binden: " + new String(b, 0, len[0], StandardCharsets.UTF_8));
        }
        return p;
    }

    static int shader(GL4 gl, int type, String src, String name) {
        int s = gl.glCreateShader(type);
        gl.glShaderSource(s, 1, new String[]{src}, null, 0);
        gl.glCompileShader(s);
        int[] ok = new int[1];
        gl.glGetShaderiv(s, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            byte[] b = new byte[4096];
            int[] len = new int[1];
            gl.glGetShaderInfoLog(s, b.length, len, 0, b, 0);
            throw new IllegalStateException("season: " + name + " lässt sich nicht übersetzen:\n" + new String(b, 0, len[0], StandardCharsets.UTF_8));
        }
        return s;
    }

    static void set3(GL4 gl, int prog, String name, double[] v) {
        gl.glUniform3f(gl.glGetUniformLocation(prog, name), (float) v[0], (float) v[1], (float) v[2]);
    }

    static int loc(GL4 gl, int prog, String name) { return gl.glGetUniformLocation(prog, name); }
}
