package org.recompile.mobile;

/** Verifies the public MIDP draw path, including the output alpha channel. */
public final class MiniJvmAlphaCompositingTest {
    public static void main(String[] args) {
        PlatformFont.setScreenSize(240, 320);
        check(0x80ff0000, 0xff0000ff, 0xff80007f, "translucent on opaque");
        check(0x8040a0f0, 0x00000000, 0x8040a0f0, "straight ARGB on transparent");
        check(0x80ff0000, 0x800000ff, 0xc0aa0055, "two translucent layers");
        check(0x00ff00ff, 0x8040a0f0, 0x8040a0f0, "transparent source");
        check(0xff123456, 0x80567890, 0xff123456, "opaque source");
        System.out.println("MIDP source-over color and alpha verified.");
    }
    private static void check(int source, int destination, int expected, String name) {
        PlatformImage image = new PlatformImage(1, 1, destination);
        PlatformGraphics graphics = image.getMIDPGraphics();
        graphics.drawRGB(new int[]{source}, 0, 1, 0, 0, 1, 1, true);
        int actual = image.getDataBuffer()[0];
        if (actual != expected) throw new AssertionError(name + ": expected " + Integer.toHexString(expected) + ", got " + Integer.toHexString(actual));
    }
}
