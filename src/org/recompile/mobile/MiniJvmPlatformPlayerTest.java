package org.recompile.mobile;

import javax.microedition.media.Player;
import javax.microedition.media.control.VolumeControl;

public final class MiniJvmPlatformPlayerTest {
    public static void main(String[] args) throws Exception {
        FakeBackend backend = new FakeBackend();
        MobilePlatform.miniJvmAudioBackend = backend;
        Player player = new MiniJvmPlatformPlayer(new byte[] { 1, 2, 3 }, "audio/midi");

        byte[] tones = MiniJvmToneSequence.toMidi(new byte[] {-2, 1, -3, 30, -4, 64, -5, 0, 60, 16, -6, 0, -7, 0, -9, 2, 62, 8, -1, 16});
        javax.sound.midi.Sequence parsed = javax.sound.midi.MidiSystem.getSequence(new java.io.ByteArrayInputStream(tones));
        if (parsed.getResolution() != 64 || parsed.getTickLength() != 192) throw new AssertionError("Tone durations/blocks/repeats were not preserved");
        for (byte[] invalid : new byte[][] {{}, {-2, 2, 60, 8}, {-2, 1, -7, 0}, {-2, 1, 60, 0}, {-2, 1, -9, 1, 60, 8}}) {
            try { MiniJvmToneSequence.toMidi(invalid); throw new AssertionError("Invalid tone accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        MobilePlatform.isMiniJvm = true;
        com.nokia.mid.sound.Sound sound = new com.nokia.mid.sound.Sound(new byte[] {0}, com.nokia.mid.sound.Sound.FORMAT_TONE);
        sound.init(new byte[] {0}, com.nokia.mid.sound.Sound.FORMAT_TONE);
        sound.play(1); sound.stop(); sound.resume(); sound.release();
        backend.handle.starts = 0; backend.handle.stops = 0; backend.handle.closes = 0;

        player.setLoopCount(-1);
        player.start();
        if (player.getState() != Player.STARTED || backend.handle.starts != 1 || backend.handle.loopCount != -1) {
            throw new AssertionError("miniJVM player must forward loop and start operations");
        }
        VolumeControl volume = (VolumeControl) player.getControl("VolumeControl");
        volume.setLevel(45);
        if (backend.handle.volume != 45) throw new AssertionError("volume must reach the backend");
        player.stop();
        Player tone = new MiniJvmPlatformPlayer(new byte[] {1}, "audio/x-tone-seq");
        tone.realize();
        javax.microedition.media.control.ToneControl control = (javax.microedition.media.control.ToneControl) tone.getControl("ToneControl");
        if (control == null) throw new AssertionError("ToneControl is missing from the native player");
        control.setSequence(new byte[] {'M', 'T', 'h', 'd', 0});
        if (backend.handle.closes != 1) throw new AssertionError("Replaced tone handle was not released");
        tone.prefetch();
        try { control.setSequence(new byte[] {'M', 'T', 'h', 'd', 0}); throw new AssertionError("prefetched tone changed"); }
        catch (IllegalStateException expected) { }
        // FakeBackend intentionally reuses its handle; reset its counters for
        // the pre-existing lifecycle assertions below.
        backend.handle.closes = 0;
        player.close();
        if (backend.handle.stops != 1 || backend.handle.closes != 1 || player.getState() != Player.CLOSED) {
            throw new AssertionError("miniJVM player must forward stop and close operations");
        }
    }

    private static final class FakeBackend implements MiniJvmAudioBackend {
        final FakeHandle handle = new FakeHandle();
        public Handle create(byte[] data) { return handle; }
        public Handle createFile(String path) { return handle; }
        public void playTone(int note, int duration, int volume) { }
    }

    private static final class FakeHandle implements MiniJvmAudioBackend.Handle {
        int closes;
        int loopCount;
        int starts;
        int stops;
        int volume;
        public void start() { starts++; }
        public void stop() { stops++; }
        public void close() { closes++; }
        public void setLoopCount(int count) { loopCount = count; }
        public long setMediaTime(long value) { return value; }
        public long getMediaTime() { return 0; }
        public long getDuration() { return 1; }
        public boolean isRunning() { return starts > stops; }
        public void setVolume(int level) { volume = level; }
    }
}
