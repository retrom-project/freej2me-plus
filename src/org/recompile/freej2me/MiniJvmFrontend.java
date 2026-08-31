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
		this(jarLocation, dataPath, 240, 320, 60);
	}

	public MiniJvmFrontend(String jarLocation, String dataPath, int width, int height, int frameRate)
	{
		if (jarLocation == null) { throw new NullPointerException("jarLocation"); }

		this.width = width > 0 ? width : 240;
		this.height = height > 0 ? height : 320;

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

		Mobile.config.settings.put("scrwidth", Integer.toString(this.width));
		Mobile.config.settings.put("scrheight", Integer.toString(this.height));
		Mobile.config.settings.put("fps", Integer.toString(frameRate > 0 ? frameRate : 60));
		Mobile.config.sysSettings.put("sound", "on");
		applySettings();
		platform.runJar();
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
