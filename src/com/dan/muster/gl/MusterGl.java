package com.dan.muster.gl;

import com.dan.muster.Muster;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Darstellung des Muster-Felds mit JOGL (Paketvertrag Punkt 3). Die Shader liegen als Ressourcen in diesem Package
 * ({@code muster.vert}, {@code muster.frag}), damit das Herauskopieren des Ordners reicht. Gezeichnet wird ein
 * Vollbild-Dreieck, das das Feld als Farbe zeigt; jedes echte Package hat hier seinen Renderer.
 */
public final class MusterGl {
    private int program, vao;

    public void init(GL4 gl) {
        program = gl.glCreateProgram();
        int vs = compile(gl, GL4.GL_VERTEX_SHADER, "muster.vert"), fs = compile(gl, GL4.GL_FRAGMENT_SHADER, "muster.frag");
        gl.glAttachShader(program, vs);
        gl.glAttachShader(program, fs);
        gl.glLinkProgram(program);
        int[] ok = new int[1];
        gl.glGetProgramiv(program, GL4.GL_LINK_STATUS, ok, 0);
        gl.glDeleteShader(vs);
        gl.glDeleteShader(fs);
        if (ok[0] == 0) throw new IllegalStateException("muster: Programm lässt sich nicht binden");
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
    }

    /** Zeichnet das Feld in den gebundenen Bildspeicher. */
    public void draw(GL4 gl, Muster m, float t) {
        gl.glUseProgram(program);
        gl.glUniform1f(gl.glGetUniformLocation(program, "uStrength"), (float) m.strength);
        gl.glUniform1f(gl.glGetUniformLocation(program, "uTime"), t);
        gl.glUniform1f(gl.glGetUniformLocation(program, "uSeed"), (float) (m.seed % 1000));
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindVertexArray(0);
    }

    public void dispose(GL4 gl) {
        if (program != 0) gl.glDeleteProgram(program);
        if (vao != 0) gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        program = vao = 0;
    }

    /** Liest eine Shader-Datei aus diesem Package (Ressource neben der Klasse). */
    public static String source(String file) {
        try (InputStream in = MusterGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("muster: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("muster: " + file + ": " + e.getMessage(), e);
        }
    }

    private static int compile(GL4 gl, int type, String file) {
        int sh = gl.glCreateShader(type);
        gl.glShaderSource(sh, 1, new String[]{source(file)}, null, 0);
        gl.glCompileShader(sh);
        int[] ok = new int[1];
        gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("muster: " + file + " lässt sich nicht übersetzen");
        return sh;
    }
}
