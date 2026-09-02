package org.recompile.mobile;

import javax.microedition.media.Player;
import javax.microedition.media.control.VolumeControl;

public final class MiniJvmPlatformPlayerTest {
    public static void main(String[] args) throws Exception {
        FakeBackend backend = new FakeBackend();
        MobilePlatform.miniJvmAudioBackend = backend;
        Player player = new MiniJvmPlatformPlayer(new byte[] { 1, 2, 3 }, "audio/midi");

        player.setLoopCount(-1);
        player.start();
        if (player.getState() != Player.STARTED || backend.handle.starts != 1 || backend.handle.loopCount != -1) {
            throw new AssertionError("miniJVM player must forward loop and start operations");
        }
        VolumeControl volume = (VolumeControl) player.getControl("VolumeControl");
        volume.setLevel(45);
        if (backend.handle.volume != 45) throw new AssertionError("volume must reach the backend");
        player.stop();
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
