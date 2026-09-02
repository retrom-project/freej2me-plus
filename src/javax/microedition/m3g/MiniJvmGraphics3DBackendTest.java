package javax.microedition.m3g;

public final class MiniJvmGraphics3DBackendTest
{
	private static int creates;
	private static int binds;
	private static int clears;
	private static int releases;

	public static void main(String[] args)
	{
		String oldMode = System.getProperty("freej2me.m3g.backend");
		String oldFactory = System.getProperty("freej2me.m3g.backendFactory");
		try
		{
			System.setProperty("freej2me.m3g.backend", "webgl2");
			System.setProperty("freej2me.m3g.backendFactory", RecordingFactory.class.getName());
			Graphics3D graphics = new Graphics3D();
			Image2D target = new Image2D(Image2D.RGBA, 2, 2);
			graphics.bindTarget(target);
			graphics.clear(null);
			graphics.releaseTarget();
			check(creates == 1, "factory creation");
			check(binds == 1, "target binding");
			check(clears == 1, "clear dispatch");
			check(releases == 1, "target release");
		}
		finally
		{
			restore("freej2me.m3g.backend", oldMode);
			restore("freej2me.m3g.backendFactory", oldFactory);
		}
	}

	private static void restore(String name, String value)
	{
		if (value == null) { System.clearProperty(name); }
		else { System.setProperty(name, value); }
	}

	private static void check(boolean value, String label)
	{
		if (!value) { throw new AssertionError(label); }
	}

	public static final class RecordingFactory implements Graphics3D.BackendFactory
	{
		public Graphics3D.Backend create(Graphics3D owner, Graphics3D.Backend softwareFallback)
		{
			creates++;
			return new RecordingBackend();
		}
	}

	private static final class RecordingBackend implements Graphics3D.Backend
	{
		public void bindTarget(Object target, boolean depthBuffer, int hints) { binds++; }
		public void clear(Background background) { clears++; }
		public void render(Mesh mesh, int submeshIndex, VertexBuffer vertices,
			TriangleStripArray triangles, Appearance appearance, Transform transform) { }
		public void releaseTarget() { releases++; }
	}
}
