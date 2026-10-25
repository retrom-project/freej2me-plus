package org.recompile.mobile;

import java.io.ByteArrayOutputStream;
import javax.microedition.media.control.ToneControl;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

/** Converts the MIDP ToneControl byte grammar to the same MIDI audio backend. */
final class MiniJvmToneSequence {
    private final byte[] data;
    private final int[][] blocks = new int[128][];
    private final Sequence sequence;
    private final Track track;
    private long tick;
    private int events;

    private MiniJvmToneSequence(byte[] data, int resolution, int tempo) throws Exception {
        this.data = data;
        sequence = new Sequence(Sequence.PPQ, resolution);
        track = sequence.createTrack();
        int micros = 60000000 / tempo;
        MetaMessage message = new MetaMessage();
        message.setMessage(0x51, new byte[] {(byte) (micros >> 16), (byte) (micros >> 8), (byte) micros}, 3);
        track.add(new MidiEvent(message, 0));
    }

    static byte[] toMidi(byte[] data) {
        if (data == null) throw new IllegalArgumentException("Tone sequence is null");
        if (data.length >= 4 && data[0] == 'M' && data[1] == 'T' && data[2] == 'h' && data[3] == 'd') return data.clone();
        try {
            if (data.length < 4 || data[0] != ToneControl.VERSION || data[1] != 1) throw new IllegalArgumentException("Tone sequence version");
            int position = 2, tempo = 120, resolution = 64;
            if (data[position] == ToneControl.TEMPO) {
                tempo = checked(data[position + 1], 5, 127) * 4;
                position += 2;
            }
            if (position < data.length && data[position] == ToneControl.RESOLUTION) {
                resolution = checked(data[position + 1], 1, 127);
                position += 2;
            }
            MiniJvmToneSequence decoder = new MiniJvmToneSequence(data, resolution, tempo);
            while (position < data.length && data[position] == ToneControl.BLOCK_START) {
                int number = checked(data[position + 1], 0, 127), start = position + 2;
                if (decoder.blocks[number] != null) throw new IllegalArgumentException("Duplicate tone block");
                position = start;
                while (position < data.length && data[position] != ToneControl.BLOCK_END) position = decoder.event(position, false);
                if (position + 1 >= data.length || data[position + 1] != number || start == position) throw new IllegalArgumentException("Invalid tone block");
                decoder.blocks[number] = new int[] {start, position};
                position += 2;
            }
            if (position == data.length) throw new IllegalArgumentException("Tone sequence is empty");
            while (position < data.length) position = decoder.event(position, true);
            MetaMessage end = new MetaMessage();
            end.setMessage(0x2f, new byte[0], 0);
            decoder.track.add(new MidiEvent(end, decoder.tick));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MidiSystem.write(decoder.sequence, 0, output);
            return output.toByteArray();
        } catch (IllegalArgumentException error) { throw error; }
        catch (Exception error) { throw new IllegalArgumentException("Invalid tone sequence: " + error); }
    }

    private int event(int position, boolean play) throws Exception {
        if (++events > 100000 || position + 1 >= data.length) throw new IllegalArgumentException("Tone sequence limit or truncation");
        int command = data[position], value = data[position + 1];
        if (command == ToneControl.PLAY_BLOCK) {
            int[] block = blocks[checked(value, 0, 127)];
            if (block == null) throw new IllegalArgumentException("Undefined tone block");
            if (play) for (int p = block[0]; p < block[1];) p = event(p, true);
        } else if (command == ToneControl.SET_VOLUME) {
            int volume = checked(value, 0, 100);
            if (play) add(ShortMessage.CONTROL_CHANGE, 7, volume * 127 / 100, tick);
        } else if (command == ToneControl.REPEAT) {
            int repeat = checked(value, 2, 127);
            if (position + 3 >= data.length || data[position + 2] < ToneControl.SILENCE) throw new IllegalArgumentException("Invalid repeated tone");
            for (int i = 0; i < repeat; i++) event(position + 2, play);
            return position + 4;
        } else {
            checked(command, -1, 127);
            checked(value, 1, 127);
            if (play) {
                if (command >= 0) {
                    add(ShortMessage.NOTE_ON, command, 127, tick);
                    add(ShortMessage.NOTE_OFF, command, 0, tick + value * 4);
                }
                tick += value * 4;
            }
        }
        return position + 2;
    }
    private void add(int command, int a, int b, long at) throws Exception {
        ShortMessage message = new ShortMessage();
        message.setMessage(command, 0, a, b);
        track.add(new MidiEvent(message, at));
    }
    private static int checked(int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) throw new IllegalArgumentException("Tone parameter out of range");
        return value;
    }
}
