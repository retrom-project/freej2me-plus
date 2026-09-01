package org.recompile.mobile;

import java.util.Vector;

import javax.microedition.media.Control;
import javax.microedition.media.Player;
import javax.microedition.media.PlayerListener;
import javax.microedition.media.TimeBase;
import javax.microedition.media.control.VolumeControl;

/** Lightweight MIDP player that avoids loading desktop Java Sound on miniJVM. */
public final class MiniJvmPlatformPlayer implements Player {
    private final MiniJvmAudioBackend.Handle handle;
    private final Vector<PlayerListener> listeners = new Vector<PlayerListener>();
    private final MiniJvmVolumeControl volumeControl = new MiniJvmVolumeControl();
    private final String contentType;
    private int state = Player.UNREALIZED;

    public MiniJvmPlatformPlayer(byte[] data, String contentType) throws Exception {
        if (MobilePlatform.miniJvmAudioBackend == null) {
            throw new IllegalStateException("miniJVM audio backend is not installed");
        }
        this.contentType = contentType == null ? "" : contentType;
        handle = MobilePlatform.miniJvmAudioBackend.create(data);
    }

    public synchronized void addPlayerListener(PlayerListener listener) {
        requireOpen();
        if (listener != null && !listeners.contains(listener)) listeners.addElement(listener);
    }

    public synchronized void close() {
        if (state == Player.CLOSED) return;
        handle.close();
        state = Player.CLOSED;
        notifyListeners(PlayerListener.CLOSED, null);
        listeners.removeAllElements();
    }

    public synchronized void deallocate() {
        requireOpen();
        if (state == Player.STARTED) handle.stop();
        if (state >= Player.PREFETCHED) state = Player.REALIZED;
    }

    public synchronized String getContentType() {
        if (state == Player.UNREALIZED || state == Player.CLOSED) {
            throw new IllegalStateException("Player is not realized");
        }
        return contentType;
    }

    public synchronized long getDuration() {
        requireOpen();
        return state >= Player.PREFETCHED ? handle.getDuration() : Player.TIME_UNKNOWN;
    }

    public synchronized long getMediaTime() {
        requireOpen();
        return state >= Player.PREFETCHED ? handle.getMediaTime() : Player.TIME_UNKNOWN;
    }

    public TimeBase getTimeBase() { return null; }
    public synchronized int getState() { return state; }

    public synchronized void prefetch() {
        requireOpen();
        if (state == Player.UNREALIZED) realize();
        if (state == Player.REALIZED) state = Player.PREFETCHED;
    }

    public synchronized void realize() {
        requireOpen();
        if (state == Player.UNREALIZED) state = Player.REALIZED;
    }

    public synchronized void removePlayerListener(PlayerListener listener) {
        requireOpen();
        listeners.removeElement(listener);
    }

    public synchronized void setLoopCount(int count) {
        requireOpen();
        if (count == 0 || state == Player.STARTED) throw new IllegalStateException("Invalid loop count state");
        handle.setLoopCount(count);
    }

    public synchronized long setMediaTime(long now) {
        requireOpen();
        if (state == Player.UNREALIZED) throw new IllegalStateException("Player is not realized");
        return handle.setMediaTime(now);
    }

    public void setTimeBase(TimeBase master) { }

    public synchronized void start() {
        requireOpen();
        if (state == Player.UNREALIZED) realize();
        if (state == Player.REALIZED) prefetch();
        if (state == Player.PREFETCHED) {
            handle.start();
            state = Player.STARTED;
            notifyListeners(PlayerListener.STARTED, Long.valueOf(handle.getMediaTime()));
        }
    }

    public synchronized void stop() {
        requireOpen();
        if (state == Player.STARTED) {
            handle.stop();
            state = Player.PREFETCHED;
            notifyListeners(PlayerListener.STOPPED, Long.valueOf(handle.getMediaTime()));
        }
    }

    public Control getControl(String controlType) {
        return controlType != null && controlType.indexOf("VolumeControl") >= 0 ? volumeControl : null;
    }

    public Control[] getControls() { return new Control[] { volumeControl }; }

    private void requireOpen() {
        if (state == Player.CLOSED) throw new IllegalStateException("Player is closed");
    }

    private void notifyListeners(String event, Object value) {
        PlayerListener[] snapshot = new PlayerListener[listeners.size()];
        listeners.copyInto(snapshot);
        for (int index = 0; index < snapshot.length; index++) {
            try { snapshot[index].playerUpdate(this, event, value); }
            catch (Throwable ignored) { }
        }
    }

    private final class MiniJvmVolumeControl implements VolumeControl {
        private int level = 100;
        private boolean muted;

        public int getLevel() { return level; }
        public boolean isMuted() { return muted; }

        public int setLevel(int value) {
            level = Math.max(0, Math.min(100, value));
            handle.setVolume(muted ? 0 : level);
            notifyListeners(PlayerListener.VOLUME_CHANGED, this);
            return level;
        }

        public void setMute(boolean value) {
            muted = value;
            handle.setVolume(muted ? 0 : level);
            notifyListeners(PlayerListener.VOLUME_CHANGED, this);
        }
    }
}
