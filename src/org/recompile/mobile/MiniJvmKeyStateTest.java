package org.recompile.mobile;

import java.lang.reflect.Method;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.game.GameCanvas;

public final class MiniJvmKeyStateTest
{
	private MiniJvmKeyStateTest() { }

	public static void main(String[] args) throws Exception
	{
		Method update = MobilePlatform.class.getDeclaredMethod(
				"updateKeyState", Integer.TYPE, Boolean.TYPE);
		update.setAccessible(true);
		MobilePlatform.keyState = 0;

		update.invoke(null, Canvas.FIRE, Boolean.TRUE);
		assertState(GameCanvas.FIRE_PRESSED, "FIRE press");
		update.invoke(null, Canvas.FIRE, Boolean.FALSE);
		assertState(0, "FIRE release");
		update.invoke(null, Canvas.FIRE, Boolean.FALSE);
		assertState(0, "duplicate FIRE release");

		update.invoke(null, Canvas.LEFT, Boolean.TRUE);
		update.invoke(null, Canvas.FIRE, Boolean.TRUE);
		assertState(GameCanvas.LEFT_PRESSED | GameCanvas.FIRE_PRESSED, "multi-key press");
		update.invoke(null, Canvas.LEFT, Boolean.FALSE);
		assertState(GameCanvas.FIRE_PRESSED, "independent key release");
		System.out.println("GameCanvas key-state transitions verified.");
	}

	private static void assertState(int expected, String operation)
	{
		if (MobilePlatform.keyState != expected)
			throw new AssertionError(operation + ": expected " + expected +
					", got " + MobilePlatform.keyState);
	}
}
