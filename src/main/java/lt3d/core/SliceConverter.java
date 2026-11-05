package lt3d.core;

import java.util.Map;

/**
 * Turns every triangle into one transformable LittleTiles box (a "slice"): the box is the bounding box of the triangle's rounded vertices and its
 * eight corners are then moved onto the three vertices. This is the algorithm of the 1.12.2 importer (convertModelToRecipeUseSlices), ported 1:1 with a few
 * safety fixes: degenerate triangles are skipped, flat bounding boxes get a thickness of one unit and offsets are range checked.
 * <p>
 * Box format (identical in LittleTiles 1.12 and 1.21): six ints for min/max, then the indicator and the packed offsets.
 * Indicator bit {@code 3 * corner + axis} says that the corner is moved along that axis, the corners are ordered like the BoxCorner enum
 * (EUN, EUS, EDN, EDS, WUN, WUS, WDN, WDS) and the axes x, y, z. Bit 31 marks the box as transformable. The offsets are 16 bit values, packed two per int,
 * the first one into the upper half, in the order of the set indicator bits.
 */
public final class SliceConverter {

    public static final int TRANSFORMABLE_MARKER = Integer.MIN_VALUE;

    private SliceConverter() {}

    public static ImportResult convert(ObjModel model, ImportSettings settings, Map<String, ColorSource> colors, ColorSource fallback) throws ImportException {
        return convert(model, settings, colors, fallback, Job.single());
    }

    public static ImportResult convert(ObjModel model, ImportSettings settings, Map<String, ColorSource> colors, ColorSource fallback, Job job) throws ImportException {
        double longest = model.longestSide();
        if (!(longest > 0))
            throw new ImportException("The model has no size!");

        double ratio = settings.maxSize / longest;
        double[] min = model.min();
        java.util.List<Tri> triangles = model.triangles;

        job.phase("Converting triangles", triangles.size());
        java.util.List<Piece> pieces = Parallel.map(job, triangles.size(), 4096, (from, to) -> {
            Piece piece = new Piece();
            int[][] rounded = new int[3][3];
            for (int n = from; n < to; n++) {
                Tri tri = triangles.get(n);
                for (int i = 0; i < 3; i++) {
                    double[] v = tri.vertex(i);
                    for (int k = 0; k < 3; k++)
                        rounded[i][k] = (int) Math.round((v[k] - min[k]) * ratio);
                }

                int[] box = encode(rounded);
                if (box == null) {
                    piece.skipped++;
                    continue;
                }
                ColorSource source = colors.getOrDefault(tri.material, fallback);
                piece.add(0, source.colorAt(tri.centroidUv()), box);
            }
            return piece;
        });

        ImportResult result = Merge.merge(pieces, job, false);
        if (result.boxCount == 0)
            throw new ImportException("The model is too small for this size, nothing was generated. Increase the max size or the grid.");
        return result;
    }

    /**
     * @param v the three rounded vertices
     * @return the box array or null if the triangle collapsed into a point or a line at this resolution
     */
    static int[] encode(int[][] v) throws ImportException {
        if (collapsed(v))
            return null;

        int[] minMax = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE };
        for (int[] vertex : v)
            for (int k = 0; k < 3; k++) {
                minMax[k] = Math.min(minMax[k], vertex[k]);
                minMax[k + 3] = Math.max(minMax[k + 3], vertex[k]);
            }
        // A box needs a volume. A triangle lying in a plane gets one unit of thickness, its corners are pulled back onto the plane by the offsets below.
        for (int k = 0; k < 3; k++)
            if (minMax[k + 3] == minMax[k])
                minMax[k + 3]++;

        // corners in BoxCorner order: E = max x, W = min x, U = max y, D = min y, S = max z, N = min z
        int[][] corners = { { minMax[3], minMax[4], minMax[2] }, // EUN
                { minMax[3], minMax[4], minMax[5] }, // EUS
                { minMax[3], minMax[1], minMax[2] }, // EDN
                { minMax[3], minMax[1], minMax[5] }, // EDS
                { minMax[0], minMax[4], minMax[2] }, // WUN
                { minMax[0], minMax[4], minMax[5] }, // WUS
                { minMax[0], minMax[1], minMax[2] }, // WDN
                { minMax[0], minMax[1], minMax[5] } }; // WDS

        int indicator = TRANSFORMABLE_MARKER;
        int[] offsets = new int[24];
        int offsetCount = 0;
        int[] cornersAtVertex = new int[3]; // a vertex may take at most three corners, this prevents tiles without faces

        for (int j = 0; j < corners.length; j++) {
            int[] corner = corners[j];
            int closest = 0;
            double smallest = Double.MAX_VALUE;
            for (int k = 0; k < 3; k++) {
                double dist = distSq(corner, v[k]);
                if (dist < smallest && cornersAtVertex[k] < 3) {
                    closest = k;
                    smallest = dist;
                }
            }
            cornersAtVertex[closest]++;

            for (int axis = 0; axis < 3; axis++) {
                int offset = v[closest][axis] - corner[axis];
                if (offset == 0)
                    continue;
                if (offset < Short.MIN_VALUE || offset > Short.MAX_VALUE)
                    throw new ImportException("The model is too big for this grid, choose a smaller max size!");
                indicator |= 1 << (3 * j + axis);
                offsets[offsetCount++] = offset;
            }
        }

        if (offsetCount == 0) // the triangle already is a straight box
            return new int[] { minMax[0], minMax[1], minMax[2], minMax[3], minMax[4], minMax[5] };

        int packed = (offsetCount + 1) / 2;
        int[] box = new int[7 + packed];
        System.arraycopy(minMax, 0, box, 0, 6);
        box[6] = indicator;
        for (int i = 0; i < offsetCount; i++) {
            int slot = 7 + i / 2;
            if (i % 2 == 0)
                box[slot] = offsets[i] << 16;
            else
                box[slot] |= offsets[i] & 0xFFFF;
        }
        return box;
    }

    private static boolean collapsed(int[][] v) {
        long ax = v[1][0] - v[0][0], ay = v[1][1] - v[0][1], az = v[1][2] - v[0][2];
        long bx = v[2][0] - v[0][0], by = v[2][1] - v[0][1], bz = v[2][2] - v[0][2];
        long cx = ay * bz - az * by;
        long cy = az * bx - ax * bz;
        long cz = ax * by - ay * bx;
        return cx == 0 && cy == 0 && cz == 0; // zero area: points coincide or lie on one line
    }

    private static double distSq(int[] a, int[] b) {
        double x = a[0] - b[0], y = a[1] - b[1], z = a[2] - b[2];
        return x * x + y * y + z * z;
    }
}
