package org.recompile.mobile;

/** Straight ARGB compositing shared by the software MIDP drawing paths. */
public final class AlphaCompositor {
    private AlphaCompositor() {}

    /** Source-over on straight (unpremultiplied) ARGB pixels. */
    public static int sourceOver(int source, int destination) {
        int alpha = source >>> 24;
        if (alpha == 0) return destination;
        int destinationAlpha = destination >>> 24;
        if (alpha == 255 || destinationAlpha == 0) return source;
        int inverse = 255 - alpha;
        int destinationWeight = destinationAlpha * inverse;
        int sourceWeight = alpha * 255;
        int weight = sourceWeight + destinationWeight;
        int red = (((source >>> 16) & 255) * sourceWeight + ((destination >>> 16) & 255) * destinationWeight + weight / 2) / weight;
        int green = (((source >>> 8) & 255) * sourceWeight + ((destination >>> 8) & 255) * destinationWeight + weight / 2) / weight;
        int blue = ((source & 255) * sourceWeight + (destination & 255) * destinationWeight + weight / 2) / weight;
        int resultAlpha = (weight + 127) / 255;
        return (resultAlpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
