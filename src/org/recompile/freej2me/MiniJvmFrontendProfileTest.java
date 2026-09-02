package org.recompile.freej2me;

import java.util.HashMap;
import java.util.Map;

public final class MiniJvmFrontendProfileTest
{
	public static void main(String[] args)
	{
		Map<String, String> source = new HashMap<String, String>();
		source.put("width", "128");
		source.put("height", "144");
		source.put("fps", "30");
		source.put("phone", "Nokia");
		source.put("rotation", "90");
		source.put("sound", "off");
		source.put("m3g.backend", "webgl2");
		source.put("m3g.halfResolution", "on");

		MiniJvmFrontend.Profile profile = MiniJvmFrontend.Profile.from(source, 240, 320, 60);
		Map<String, String> game = new HashMap<String, String>();
		Map<String, String> system = new HashMap<String, String>();
		profile.applyTo(game, system);

		check("128".equals(game.get("scrwidth")), "width");
		check("144".equals(game.get("scrheight")), "height");
		check("30".equals(game.get("fps")), "fps");
		check("Standard".equals(game.get("phone")), "Nokia alias");
		check("90".equals(game.get("rotate")), "rotation");
		check("off".equals(system.get("sound")), "sound");
		check("webgl2".equals(game.get("j2mewebm3gbackend")), "M3G backend");
		check("on".equals(game.get("spdhackm3ghalfres")), "M3G half resolution");

		source.put("width", "0");
		source.put("phone", "invalid");
		profile = MiniJvmFrontend.Profile.from(source, 240, 320, 60);
		check(profile.width == 240, "invalid width fallback");
		check("Standard".equals(profile.phone), "invalid phone fallback");
	}

	private static void check(boolean value, String label)
	{
		if (!value) { throw new AssertionError(label); }
	}
}
