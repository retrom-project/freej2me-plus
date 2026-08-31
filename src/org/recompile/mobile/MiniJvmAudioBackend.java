/*
 * This file is part of FreeJ2ME.
 *
 * FreeJ2ME is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 */
package org.recompile.mobile;

import java.io.InputStream;

/**
 * Small media bridge for runtimes that do not provide Java Sound.
 *
 * <p>The desktop frontend keeps using javax.sound. The miniJVM frontend can
 * install this backend and route the common MIDI and PCM paths to its native
 * audio engine without making the emulator core depend on miniJVM classes.</p>
 */
public interface MiniJvmAudioBackend
{
	public Handle create(InputStream stream) throws Exception;
	public void playTone(int note, int duration, int volume) throws Exception;

	public interface Handle
	{
		public void start();
		public void stop();
		public void close();
		public void setLoopCount(int count);
		public long setMediaTime(long microseconds);
		public long getMediaTime();
		public long getDuration();
		public boolean isRunning();
		public void setVolume(int level);
	}
}
