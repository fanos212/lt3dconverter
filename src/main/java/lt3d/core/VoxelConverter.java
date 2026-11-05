package lt3d.core;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Rasterises the surface of the model into 1x1x1 tiles of the grid, coloured from the texture. Port of the original convertModelToRecipe / Triangle.calcBlocks
 * (the textured mode of version 0.4). Compared to the original the model is moved to the origin first and the cells containing the triangle corners are always
 * added, so very small triangles no longer leave holes.
 */
public final class VoxelConverter {

    private VoxelConverter() {}

    public static ImportResult convert(ObjModel model, ImportSettings settings, Map<String, ColorSource> colors, ColorSource fallback) throws ImportException {
        return convert(model, settings, colors, fallback, Job.single());
    }

    public static ImportResult convert(ObjModel model, ImportSettings settings, Map<String, ColorSource> colors, ColorSource fallback, Job job) throws ImportException {
        double longest = model.longestSide();
        if (!(longest > 0))
            throw new ImportException("The model has no size!");
        if (!(settings.precision > 0))
            throw new ImportException("The precision must be greater than zero!");

        double ratio = settings.maxSize / longest;
        double[] min = model.min();
        double precision = settings.precision;
        java.util.List<Tri> triangles = model.triangles;

        job.phase("Converting triangles", triangles.size());
        java.util.List<Piece> pieces = Parallel.map(job, triangles.size(), 256, (from, to) -> {
            Piece piece = new Piece();
            Set<Long> cells = new HashSet<>(); // only removes duplicates inside the chunk, the real first-wins check happens in the merge
            double[][] p = new double[3][3];
            for (int n = from; n < to; n++) {
                Tri tri = triangles.get(n);
                for (int i = 0; i < 3; i++) {
                    double[] v = tri.vertex(i);
                    for (int k = 0; k < 3; k++)
                        p[i][k] = (v[k] - min[k]) * ratio;
                }

                Sink sink = new Sink(cells, piece, colors.getOrDefault(tri.material, fallback));
                for (int i = 0; i < 3; i++)
                    sink.add(floor(p[i][0]), floor(p[i][1]), floor(p[i][2]), tri.uv == null ? null : new double[] { tri.uv[i * 2], tri.uv[i * 2 + 1] });
                rasterise(tri, p, precision, sink);
            }
            return piece;
        });

        ImportResult result = Merge.merge(pieces, job, true);
        if (result.boxCount == 0)
            throw new ImportException("The model is too small for this size, nothing was generated. Increase the max size or the grid.");
        return result;
    }

    static long key(int x, int y, int z) {
        return ((long) (x + 0x100000) & 0x1FFFFF) << 42 | ((long) (y + 0x100000) & 0x1FFFFF) << 21 | ((long) (z + 0x100000) & 0x1FFFFF);
    }

    private static final class Sink {

        private final Set<Long> cells;
        private final Piece piece;
        private final ColorSource source;

        Sink(Set<Long> cells, Piece piece, ColorSource source) {
            this.cells = cells;
            this.piece = piece;
            this.source = source;
        }

        void add(int x, int y, int z, double[] uv) {
            long key = key(x, y, z);
            if (!cells.add(key))
                return; // cell is already taken (maybe with a different colour, first one wins)
            piece.add(key, source.colorAt(uv), new int[] { x, y, z, x + 1, y + 1, z + 1 });
        }
    }

    /** Samples lines from edge AC to edge BC and records every cell those lines cross. */
    private static void rasterise(Tri tri, double[][] p, double precision, Sink sink) {
        double[] a = p[0], b = p[1], c = p[2];
        double[] ac = { c[0] - a[0], c[1] - a[1], c[2] - a[2] };
        double[] bc = { c[0] - b[0], c[1] - b[1], c[2] - b[2] };
        double slices = Math.sqrt(ac[0] * ac[0] + ac[1] * ac[1] + ac[2] * ac[2]) / precision + 2D;
        double[] uv = tri.uv;

        for (int i = 0; i <= slices; i++) {
            double t = i / slices;
            double[] p1 = { a[0] + ac[0] * t, a[1] + ac[1] * t, a[2] + ac[2] * t };
            double[] p2 = { b[0] + bc[0] * t, b[1] + bc[1] * t, b[2] + bc[2] * t };
            double[] uv1 = uv == null ? null : new double[] { uv[0] + (uv[4] - uv[0]) * t, uv[1] + (uv[5] - uv[1]) * t };
            double[] uv2 = uv == null ? null : new double[] { uv[2] + (uv[4] - uv[2]) * t, uv[3] + (uv[5] - uv[3]) * t };

            for (int axis = 0; axis < 3; axis++) {
                int c1 = (int) Math.ceil(p1[axis]);
                int c2 = (int) Math.ceil(p2[axis]);
                int from = Math.min(c1, c2);
                int to = Math.max(c1, c2);
                double delta = p2[axis] - p1[axis];

                for (int k = from; k < to; k++) {
                    double s = delta == 0 ? 0 : (k - p1[axis]) / delta;
                    s = Math.max(0D, Math.min(1D, s)); // guards against rounding errors
                    double x = p1[0] + (p2[0] - p1[0]) * s;
                    double y = p1[1] + (p2[1] - p1[1]) * s;
                    double z = p1[2] + (p2[2] - p1[2]) * s;
                    double[] uvHere = uv == null ? null : new double[] { uv1[0] + (uv2[0] - uv1[0]) * s, uv1[1] + (uv2[1] - uv1[1]) * s };
                    // the crossed plane itself belongs to the cell on its upper side, floor() of the exact coordinate does exactly that
                    sink.add(floor(axis == 0 ? k : x), floor(axis == 1 ? k : y), floor(axis == 2 ? k : z), uvHere);
                }
            }
        }
    }

    private static int floor(double d) {
        return (int) Math.floor(d);
    }
}
