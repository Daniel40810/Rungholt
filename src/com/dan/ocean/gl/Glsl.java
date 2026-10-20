package com.dan.ocean.gl;

import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Lädt und übersetzt die Shader dieses Packages (Ressourcen neben der Klasse). {@code #include "datei"} wird
 * eingesetzt, vor den Text kommen {@code #version 430 core} und die Defines. Fehler kommen mit Dateiname zurück.
 */
final class Glsl {
    private Glsl() { }

    static String source(String file, String... defines) {
        StringBuilder sb = new StringBuilder("#version 430 core\n");
        for (String d : defines) sb.append("#define ").append(d).append('\n');
        expand(file, sb, new HashSet<>());
        return sb.toString();
    }

    private static void expand(String file, StringBuilder sb, Set<String> seen) {
        if (!seen.add(file)) return;
        for (String l : read(file).split("\r?\n", -1)) {
            String t = l.trim();
            if (t.startsWith("#include")) expand(t.substring(t.indexOf('"') + 1, t.lastIndexOf('"')), sb, seen);
            else if (t.startsWith("#extension")) sb.insert(sb.indexOf("\n") + 1, l + "\n");
            else sb.append(l).append('\n');
        }
    }

    static String read(String file) {
        try (InputStream in = Glsl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("ocean: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("ocean: " + file + ": " + e.getMessage(), e);
        }
    }

    /** Übersetzt die Stufen (Endung bestimmt die Art) und bindet ein Programm. */
    static int program(GL4 gl, String name, String[] defines, String... files) {
        int prog = gl.glCreateProgram();
        int[] sh = new int[files.length];
        for (int i = 0; i < files.length; i++) {
            String f = files[i];
            int type = f.endsWith(".vert") ? GL4.GL_VERTEX_SHADER : f.endsWith(".frag") ? GL4.GL_FRAGMENT_SHADER : GL4.GL_COMPUTE_SHADER;
            sh[i] = gl.glCreateShader(type);
            gl.glShaderSource(sh[i], 1, new String[]{source(f, defines)}, null, 0);
            gl.glCompileShader(sh[i]);
            int[] ok = new int[1];
            gl.glGetShaderiv(sh[i], GL4.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                String log = log(gl, sh[i], true);
                for (int s : sh) if (s != 0) gl.glDeleteShader(s);
                gl.glDeleteProgram(prog);
                throw new IllegalStateException("ocean: " + f + " lässt sich nicht übersetzen:\n" + log);
            }
            gl.glAttachShader(prog, sh[i]);
        }
        gl.glLinkProgram(prog);
        int[] ok = new int[1];
        gl.glGetProgramiv(prog, GL4.GL_LINK_STATUS, ok, 0);
        for (int s : sh) { gl.glDetachShader(prog, s); gl.glDeleteShader(s); }
        if (ok[0] == 0) {
            String log = log(gl, prog, false);
            gl.glDeleteProgram(prog);
            throw new IllegalStateException("ocean: Programm " + name + " lässt sich nicht binden:\n" + log);
        }
        return prog;
    }

    private static String log(GL4 gl, int id, boolean shader) {
        int[] len = new int[1];
        if (shader) gl.glGetShaderiv(id, GL4.GL_INFO_LOG_LENGTH, len, 0); else gl.glGetProgramiv(id, GL4.GL_INFO_LOG_LENGTH, len, 0);
        byte[] b = new byte[Math.max(1, len[0])];
        if (shader) gl.glGetShaderInfoLog(id, b.length, len, 0, b, 0); else gl.glGetProgramInfoLog(id, b.length, len, 0, b, 0);
        return new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8);
    }
}
