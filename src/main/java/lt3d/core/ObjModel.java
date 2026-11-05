package lt3d.core;

import java.util.ArrayList;
import java.util.List;

/** Triangulated Wavefront OBJ model. */
public final class ObjModel {

    public final List<Tri> triangles = new ArrayList<>();
    public final List<String> mtlFiles = new ArrayList<>();
    public final List<String> materials = new ArrayList<>();

    /** {minX, minY, minZ, maxX, maxY, maxZ} of all triangle vertices */
    public final double[] bounds = { Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
            Double.NEGATIVE_INFINITY };

    void add(Tri tri) {
        triangles.add(tri);
        for (int i = 0; i < 3; i++) {
            double[] v = tri.vertex(i);
            for (int k = 0; k < 3; k++) {
                if (v[k] < bounds[k])
                    bounds[k] = v[k];
                if (v[k] > bounds[k + 3])
                    bounds[k + 3] = v[k];
            }
        }
    }

    public double[] min() {
        return new double[] { bounds[0], bounds[1], bounds[2] };
    }

    /** Length of the model along x, y and z */
    public double[] sides() {
        return new double[] { bounds[3] - bounds[0], bounds[4] - bounds[1], bounds[5] - bounds[2] };
    }

    public double longestSide() {
        double[] s = sides();
        return Math.max(s[0], Math.max(s[1], s[2]));
    }
}
