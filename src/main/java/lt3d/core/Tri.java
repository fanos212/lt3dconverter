package lt3d.core;

/**
 * A single triangle of a model: three points, optional texture coordinates and the name of its material.
 * Pure data, no Minecraft classes, so the converters can be tested standalone.
 */
public final class Tri {

    /** a, b, c as {x, y, z} */
    public final double[] a;
    public final double[] b;
    public final double[] c;
    /** {u_a, v_a, u_b, v_b, u_c, v_c} or null when the face has no texture coordinates */
    public final double[] uv;
    public final String material;

    public Tri(double[] a, double[] b, double[] c, double[] uv, String material) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.uv = uv;
        this.material = material;
    }

    public double[] vertex(int i) {
        return i == 0 ? a : i == 1 ? b : c;
    }

    /** UV of the triangle centre or null when there are no texture coordinates */
    public double[] centroidUv() {
        if (uv == null)
            return null;
        return new double[] { (uv[0] + uv[2] + uv[4]) / 3D, (uv[1] + uv[3] + uv[5]) / 3D };
    }
}
