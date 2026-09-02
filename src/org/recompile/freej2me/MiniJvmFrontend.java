/*
 * This file is part of FreeJ2ME.
 *
 * FreeJ2ME is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 */
package org.recompile.freej2me;

import java.awt.Canvas;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.Collections;
import java.util.Map;

import org.recompile.mobile.Mobile;
import org.recompile.mobile.MobilePlatform;

/**
 * Small frontend used by freej2meOnMinijvm and the browser runtime.
 *
 * <p>The regular {@link FreeJ2ME} frontend deliberately contains the full AWT
 * configuration UI. miniJVM only needs a framebuffer, input forwarding and a
 * deterministic way to start one application, so keeping that integration in
 * a separate frontend avoids making the emulator core depend on desktop-only
 * widgets.</p>
 */
public final class MiniJvmFrontend
{
	private final MobilePlatform platform;
	private final Frame frame;
	private final LcdCanvas lcd;
	private final int width;
	private final int height;

	public MiniJvmFrontend(String jarLocation, String dataPath)
	{
		this(jarLocation, dataPath, 240, 320, 60, Collections.<String, String>emptyMap());
	}

	public MiniJvmFrontend(String jarLocation, String dataPath, int width, int height, int frameRate)
	{
		this(jarLocation, dataPath, width, height, frameRate, Collections.<String, String>emptyMap());
	}

	public MiniJvmFrontend(String jarLocation, String dataPath, int width, int height, int frameRate,
			Map<String, String> settings)
	{
		if (jarLocation == null) { throw new NullPointerException("jarLocation"); }
		Profile profile = Profile.from(settings, width, height, frameRate);

		this.width = profile.width;
		this.height = profile.height;

		MobilePlatform.isMiniJvm = true;
		Mobile.textEncoding = Mobile.supportedEncodings[Mobile.ISO_8859_1];
		System.setProperty("file.encoding", Mobile.textEncoding);
		Mobile.lcdWidth = this.width;
		Mobile.lcdHeight = this.height;
		platform = new MobilePlatform(this.width, this.height);
		platform.dataPath = normalizeDataPath(dataPath);
		Mobile.setPlatform(platform, new Runnable()
		{
			public void run() { applySettings(); }
		});

		lcd = new LcdCanvas();
		lcd.setSize(this.width, this.height);
		installInputHandlers();

		frame = new Frame("FreeJ2ME-Plus");
		frame.setSize(this.width, this.height);
		frame.add(lcd);
		frame.setVisible(true);

		platform.setPainter(new Runnable()
		{
			public void run() { lcd.repaint(); }
		});

		if (!platform.load(normalizeJarLocation(jarLocation)))
		{
			throw new IllegalArgumentException("Unable to load J2ME application: " + jarLocation);
		}

		profile.applyTo(Mobile.config.settings, Mobile.config.sysSettings);
		applySettings();
		platform.runJar();
	}

	/** Validated browser settings kept separate from the desktop config UI. */
	public static final class Profile
	{
		public final int width;
		public final int height;
		public final int frameRate;
		public final String phone;
		public final int rotation;
		public final boolean sound;
		public final String graphicsBackend;
		public final boolean halfResolution;

		private Profile(int width, int height, int frameRate, String phone, int rotation,
				boolean sound, String graphicsBackend, boolean halfResolution)
		{
			this.width = width;
			this.height = height;
			this.frameRate = frameRate;
			this.phone = phone;
			this.rotation = rotation;
			this.sound = sound;
			this.graphicsBackend = graphicsBackend;
			this.halfResolution = halfResolution;
		}

		public static Profile from(Map<String, String> source, int width, int height, int frameRate)
		{
			Map<String, String> values = source == null ? Collections.<String, String>emptyMap() : source;
			int resolvedWidth = boundedInt(values.get("width"), width > 0 ? width : 240, 1, 4096);
			int resolvedHeight = boundedInt(values.get("height"), height > 0 ? height : 320, 1, 4096);
			int resolvedFps = boundedInt(values.get("fps"), frameRate > 0 ? frameRate : 60, 1, 240);
			String phone = allowed(values.get("phone"), PHONE_VALUES, "Standard");
			if (phone.equals("Nokia")) { phone = "Standard"; }
			int rotation = allowedRotation(values.get("rotation"));
			boolean sound = !"off".equals(values.get("sound"));
			String backend = allowed(values.get("m3g.backend"), BACKEND_VALUES, "auto");
			boolean halfResolution = "on".equals(values.get("m3g.halfResolution"));
			return new Profile(resolvedWidth, resolvedHeight, resolvedFps, phone, rotation,
					sound, backend, halfResolution);
		}

		public void applyTo(Map<String, String> gameSettings, Map<String, String> systemSettings)
		{
			gameSettings.put("scrwidth", Integer.toString(width));
			gameSettings.put("scrheight", Integer.toString(height));
			gameSettings.put("fps", Integer.toString(frameRate));
			gameSettings.put("phone", phone);
			gameSettings.put("rotate", Integer.toString(rotation));
			gameSettings.put("spdhackm3ghalfres", halfResolution ? "on" : "off");
			gameSettings.put("j2mewebm3gbackend", graphicsBackend);
			System.setProperty("freej2me.m3g.backend", graphicsBackend);
			systemSettings.put("sound", sound ? "on" : "off");
		}

		private static int boundedInt(String value, int fallback, int minimum, int maximum)
		{
			try
			{
				int parsed = Integer.parseInt(value);
				return parsed >= minimum && parsed <= maximum ? parsed : fallback;
			}
			catch (Exception ignored) { return fallback; }
		}

		private static int allowedRotation(String value)
		{
			int rotation = boundedInt(value, 0, 0, 270);
			return rotation == 0 || rotation == 90 || rotation == 180 || rotation == 270 ? rotation : 0;
		}

		private static String allowed(String value, String[] allowed, String fallback)
		{
			for (int index = 0; index < allowed.length; index++)
			{
				if (allowed[index].equals(value)) { return value; }
			}
			return fallback;
		}

		private static final String[] PHONE_VALUES = {
			"Standard", "Nokia", "NokiaKeyboard", "KDDI", "LG", "Motorola",
			"MotoTriplets", "MotoV8", "MotoA1000", "Sagem", "Siemens", "SKT"
		};
		private static final String[] BACKEND_VALUES = { "auto", "software", "webgl2" };
	}

	private static String normalizeDataPath(String path)
	{
		if (path == null || path.length() == 0) { return ""; }
		return path.endsWith("/") ? path : path + "/";
	}

	private static String normalizeJarLocation(String location)
	{
		if (location.startsWith("file:") || location.startsWith("http:") || location.startsWith("https:"))
		{
			return location;
		}
		return "file:" + location;
	}

	private void applySettings()
	{
		boolean rotated = Mobile.updateSettings();
		if (rotated || platform.lcdWidth != Mobile.lcdWidth || platform.lcdHeight != Mobile.lcdHeight)
		{
			platform.resizeLCD(Mobile.lcdWidth, Mobile.lcdHeight);
		}
	}

	private int mobileKeyIndex(int awtKeyCode)
	{
		for (int i = 0; i < Config.inputKeycodes.length; i++)
		{
			if (Config.inputKeycodes[i] == awtKeyCode)
			{
				return Mobile.getMobileKey(Mobile.convertAWTKeycode(i));
			}
		}
		return Integer.MIN_VALUE;
	}

	private void installInputHandlers()
	{
		lcd.addKeyListener(new KeyListener()
		{
			public void keyPressed(KeyEvent event)
			{
				int key = mobileKeyIndex(event.getKeyCode());
				if (key != Integer.MIN_VALUE) { MobilePlatform.keyPressed(key); }
			}

			public void keyReleased(KeyEvent event)
			{
				int key = mobileKeyIndex(event.getKeyCode());
				if (key != Integer.MIN_VALUE) { MobilePlatform.keyReleased(key); }
			}

			public void keyTyped(KeyEvent event) { }
		});

		lcd.addMouseListener(new MouseListener()
		{
			public void mousePressed(MouseEvent event) { MobilePlatform.pointerPressed(pointerX(event), pointerY(event)); }
			public void mouseReleased(MouseEvent event) { MobilePlatform.pointerReleased(pointerX(event), pointerY(event)); }
			public void mouseClicked(MouseEvent event) { }
			public void mouseEntered(MouseEvent event) { }
			public void mouseExited(MouseEvent event) { }
		});

		lcd.addMouseMotionListener(new MouseMotionListener()
		{
			public void mouseDragged(MouseEvent event) { MobilePlatform.pointerDragged(pointerX(event), pointerY(event)); }
			public void mouseMoved(MouseEvent event) { }
		});
	}

	private int pointerX(MouseEvent event)
	{
		return event.getX() * Mobile.lcdWidth / Math.max(1, lcd.getWidth());
	}

	private int pointerY(MouseEvent event)
	{
		return event.getY() * Mobile.lcdHeight / Math.max(1, lcd.getHeight());
	}

	private final class LcdCanvas extends Canvas
	{
		public void paint(Graphics graphics)
		{
			if (platform.getLcdFrontbuffer() != null)
			{
				graphics.drawImage(platform.getLcdFrontbuffer().getCanvas(), 0, 0, getWidth(), getHeight(), null);
			}
		}

		public void update(Graphics graphics) { paint(graphics); }
	}
}
