package org.recompile.mobile;

/** Optional frontend acceleration. False must leave the destination unchanged. */
public interface PixelBlitter {
    boolean draw(int[] source, int sourceOffset, int sourceStride,
                 int[] destination, int destinationOffset, int destinationStride,
                 int width, int height, boolean processAlpha);
}
