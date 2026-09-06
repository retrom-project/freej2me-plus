package org.recompile.mobile;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

public final class MiniJvmPixelBlitterTest {
    public static void main(String[] args) {
        PlatformFont.setScreenSize(240, 320);
        Image target = Image.createImage(4, 3);
        Graphics graphics = target.getGraphics();
        graphics.setClip(1, 1, 2, 2);
        final int[] calls = {0};
        final int[] source = new int[12];
        for (int i = 0; i < source.length; i++) source[i] = 0xff000000 | i;
        PlatformGraphics.pixelBlitter = new PixelBlitter() {
            public boolean draw(int[] src, int offset, int stride, int[] dst, int destOffset,
                                int destStride, int width, int height, boolean alpha) {
                if (src != source || offset != 5 || stride != -3 || destOffset != 5 ||
                    destStride != 4 || width != 2 || height != 2 || !alpha)
                    throw new AssertionError("clipped bulk rectangle");
                calls[0]++;
                return false;
            }
        };
        try {
            graphics.drawRGB(source, 6, -3, -1, 0, 4, 3, true);
            if (calls[0] != 1) throw new AssertionError("bulk hook was not offered the clipped rectangle");
            int[] pixels = target.getDataBuffer();
            if (pixels[5] != source[5] || pixels[6] != source[6] || pixels[9] != source[2] || pixels[10] != source[3])
                throw new AssertionError("declined bulk operation must use the Java fallback");
        } finally { PlatformGraphics.pixelBlitter = null; }
        System.out.println("MIDP bulk clipping and fallback passed.");
    }
}
