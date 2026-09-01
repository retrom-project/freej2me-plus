package org.recompile.mobile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class MiniJvmMediaResolverTest {
    public static void main(String[] args) throws Exception {
        byte[] midi = new byte[] {
            'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0, 96,
            'M', 'T', 'r', 'k', 0, 0, 0, 4, 0, (byte) 0xff, 0x2f, 0
        };
        byte[] resolved = MiniJvmMediaResolver.readBounded(new OneByteAtATimeStream(midi), 1024);
        if (resolved.length != midi.length) throw new AssertionError("partial reads truncated media");
        for (int i = 0; i < midi.length; i++) {
            if (resolved[i] != midi[i]) throw new AssertionError("media changed at byte " + i);
        }
        try {
            MiniJvmMediaResolver.readBounded(new ByteArrayInputStream(new byte[5]), 4);
            throw new AssertionError("oversized media was accepted");
        } catch (IOException expected) {
            if (!"Media stream exceeds limit".equals(expected.getMessage())) throw expected;
        }

        byte[] boundedMidi = MiniJvmMediaResolver.readMediaBounded(new EndlessTrailingStream(midi), 1024);
        if (boundedMidi.length != midi.length) throw new AssertionError("MIDI consumed bytes after its final track");
        for (int i = 0; i < midi.length; i++) {
            if (boundedMidi[i] != midi[i]) throw new AssertionError("bounded MIDI changed at byte " + i);
        }

        byte[] packedMidi = new byte[midi.length + 5202];
        packedMidi[0] = (byte) midi.length;
        packedMidi[1] = (byte) (midi.length >>> 8);
        packedMidi[2] = 0x50;
        packedMidi[3] = 0x25;
        packedMidi[4] = 0x02;
        packedMidi[5] = 0;
        packedMidi[6] = 0x52;
        packedMidi[7] = 0x25;
        System.arraycopy(midi, 0, packedMidi, 5202, midi.length);
        byte[] unpackedMidi = MiniJvmMediaResolver.readMediaBounded(new EndlessTrailingStream(packedMidi), 8192);
        if (unpackedMidi.length != midi.length) throw new AssertionError("packed MIDI length prefix was not removed");
        for (int i = 0; i < midi.length; i++) {
            if (unpackedMidi[i] != midi[i]) throw new AssertionError("packed MIDI changed at byte " + i);
        }
        byte[] tiny = MiniJvmMediaResolver.readMediaBounded(new ByteArrayInputStream(new byte[] { 1, 2, 3 }), 16);
        if (tiny.length != 3 || tiny[2] != 3) throw new AssertionError("short non-MIDI media was rejected");
    }

    private static final class OneByteAtATimeStream extends InputStream {
        private final ByteArrayInputStream source;

        OneByteAtATimeStream(byte[] data) { source = new ByteArrayInputStream(data); }
        public int read() { return source.read(); }
        public int read(byte[] bytes, int offset, int length) {
            return source.read(bytes, offset, Math.min(1, length));
        }
    }

    private static final class EndlessTrailingStream extends InputStream {
        private final byte[] source;
        private int position;

        EndlessTrailingStream(byte[] data) { source = data; }
        public int read() { return position < source.length ? source[position++] & 0xff : 0; }
        public int read(byte[] bytes, int offset, int length) {
            int copied = 0;
            while (copied < length && position < source.length) bytes[offset + copied++] = source[position++];
            while (copied < length) bytes[offset + copied++] = 0;
            return copied;
        }
    }
}
