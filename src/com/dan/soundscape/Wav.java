package com.dan.soundscape;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.BufferedOutputStream;

/** Schreibt verschränktes Stereo (float, ±1) als WAV mit 16 bit. */
public final class Wav {
    private Wav() { }

    public static void write(File f, float[] stereo, int sampleRate) throws IOException {
        File dir = f.getAbsoluteFile().getParentFile();
        if (dir != null) dir.mkdirs();
        int n = stereo.length, bytes = n * 2;
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f)))) {
            o.writeBytes("RIFF");
            o.writeInt(Integer.reverseBytes(36 + bytes));
            o.writeBytes("WAVEfmt ");
            o.writeInt(Integer.reverseBytes(16));
            o.writeShort(Short.reverseBytes((short) 1));
            o.writeShort(Short.reverseBytes((short) 2));
            o.writeInt(Integer.reverseBytes(sampleRate));
            o.writeInt(Integer.reverseBytes(sampleRate * 4));
            o.writeShort(Short.reverseBytes((short) 4));
            o.writeShort(Short.reverseBytes((short) 16));
            o.writeBytes("data");
            o.writeInt(Integer.reverseBytes(bytes));
            for (float v : stereo) o.writeShort(Short.reverseBytes((short) Math.max(-32767, Math.min(32767, Math.round(v * 32767)))));
        }
    }
}
