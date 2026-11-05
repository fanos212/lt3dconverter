package lt3d.core;

import java.awt.image.BufferedImage;

/** Gives the colour (ARGB, same packing LittleTiles uses) for a point of the model. */
public interface ColorSource {

    /** @param uv texture coordinates {u, v} or null when the face has none */
    int colorAt(double[] uv);

    final class Solid implements ColorSource {

        private final int color;

        public Solid(int argb) {
            this.color = argb;
        }

        @Override
        public int colorAt(double[] uv) {
            return color;
        }
    }

    /** Image sampled at the given uv, optionally multiplied by a tint (the diffuse colour and opacity of an MTL material) */
    final class Image implements ColorSource {

        private final BufferedImage image;
        private final float tintR, tintG, tintB, opacity;
        private final int fallback;

        public Image(BufferedImage image, float r, float g, float b, float opacity, int fallback) {
            this.image = image;
            this.tintR = r;
            this.tintG = g;
            this.tintB = b;
            this.opacity = opacity;
            this.fallback = fallback;
        }

        @Override
        public int colorAt(double[] uv) {
            if (uv == null)
                return fallback; // can happen when some faces don't have texture coordinates

            double u = uv[0] % 1D;
            double v = uv[1] % 1D;
            if (u < 0)
                u += 1D;
            if (v < 0)
                v += 1D;
            int x = (int) (u * (image.getWidth() - 1));
            int y = (int) ((1D - v) * (image.getHeight() - 1));
            int argb = image.getRGB(x, y);
            int a = (int) (((argb >>> 24) & 255) * opacity);
            int r = (int) (((argb >> 16) & 255) * tintR);
            int g = (int) (((argb >> 8) & 255) * tintG);
            int b = (int) ((argb & 255) * tintB);
            return a << 24 | r << 16 | g << 8 | b;
        }
    }

    static int argb(int r, int g, int b, int a) {
        return (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
    }

    static int argb(float r, float g, float b, float a) {
        return argb((int) (r * 255), (int) (g * 255), (int) (b * 255), (int) (a * 255));
    }
}
