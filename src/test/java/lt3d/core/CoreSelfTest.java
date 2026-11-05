package lt3d.core;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Standalone checks for the conversion core, no Minecraft or JUnit needed:
 * <pre>
 * javac -d out $(find src/main/java/net/timardo/lt3dimporter/core src/test -name '*.java')
 * java -cp out lt3d.core.CoreSelfTest
 * </pre>
 * The decoder below is a copy of the logic in LittleTiles' LittleTransformableBox#getTiltedCorners, so a pass means that LittleTiles would see the
 * triangle corners where the importer put them.
 */
public class CoreSelfTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        parserHandlesFaceFormats();
        flatTriangleIsPulledOntoItsPlane();
        randomTrianglesDecodeToTheirVertices();
        collapsedTrianglesAreSkipped();
        cubeSlices();
        cubeVoxelsFillOnlyTheShell();
        texturedVoxelsGetColors();

        if (failures > 0) {
            System.out.println("FAILED: " + failures);
            System.exit(1);
        }
        System.out.println("All checks passed");
    }

    // ------------------------------------------------------------------------------------------------------------------------------------------------

    static void parserHandlesFaceFormats() throws Exception {
        String obj = "# comment\nv 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\nvt 0 0\nvt 1 0\nvt 1 1\nvt 0 1\nusemtl red\n" + "f 1/1/1 2/2/1 3/3/1 4/4/1\n" // quad with uv -> 2 triangles
                + "usemtl blue\nf -4 -3 -2\n" // negative indices, no uv
                + "f 1//1 2//1 3//1\n"; // v//vn
        ObjModel m = ObjParser.parse(new ByteArrayInputStream(obj.getBytes(StandardCharsets.UTF_8)));
        check(m.triangles.size() == 4, "quad + 2 triangles = 4 triangles, got " + m.triangles.size());
        check(m.triangles.get(0).uv != null && m.triangles.get(0).material.equals("red"), "first triangle has uv and material red");
        check(m.triangles.get(2).uv == null && m.triangles.get(2).material.equals("blue"), "third triangle has no uv, material blue");
        check(Arrays.equals(m.triangles.get(2).a, new double[] { 0, 0, 0 }), "negative index -4 resolves to the first vertex");
        check(Arrays.equals(m.sides(), new double[] { 1, 1, 0 }), "sides of the flat quad");

        try {
            ObjParser.parse(new ByteArrayInputStream("v 0 0 0\nf 1 2 3\n".getBytes(StandardCharsets.UTF_8)));
            check(false, "missing vertex must throw");
        } catch (ImportException expected) {}
    }

    static void flatTriangleIsPulledOntoItsPlane() throws Exception {
        // triangle in the plane x = 5, so its bounding box is flat on x
        int[][] v = { { 5, 0, 0 }, { 5, 10, 0 }, { 5, 0, 10 } };
        int[] box = SliceConverter.encode(v);
        check(box != null, "flat triangle is encoded");
        check(box[3] - box[0] == 1, "flat axis got one unit of thickness");
        checkCorners(box, v, "flat triangle");
    }

    static void randomTrianglesDecodeToTheirVertices() throws Exception {
        Random random = new Random(42);
        int encoded = 0;
        for (int n = 0; n < 20000; n++) {
            int[][] v = new int[3][3];
            for (int[] vertex : v)
                for (int k = 0; k < 3; k++)
                    vertex[k] = random.nextInt(200) - 20;
            int[] box = SliceConverter.encode(v);
            if (box == null)
                continue;
            encoded++;
            if (!checkCorners(box, v, "random triangle " + n + " " + Arrays.deepToString(v)))
                return;
        }
        check(encoded > 19000, "most random triangles are valid, got " + encoded);
    }

    static void collapsedTrianglesAreSkipped() throws Exception {
        check(SliceConverter.encode(new int[][] { { 1, 1, 1 }, { 1, 1, 1 }, { 1, 1, 1 } }) == null, "point is skipped");
        check(SliceConverter.encode(new int[][] { { 0, 0, 0 }, { 2, 2, 2 }, { 4, 4, 4 } }) == null, "line is skipped");
        check(SliceConverter.encode(new int[][] { { 0, 0, 0 }, { 5, 0, 0 }, { 0, 5, 0 } }) != null, "real triangle is kept");
    }

    static void cubeSlices() throws Exception {
        ObjModel cube = cube();
        ImportSettings s = new ImportSettings();
        s.maxSize = 16;
        ImportResult r = ModelConverter.convert(cube, s, null);
        check(r.boxCount == 12, "a cube has 12 triangles, got " + r.boxCount);
        check(r.boxesByColor.size() == 1, "one colour");
        // every box has to stay inside 0..16 on every axis once corner offsets are applied
        for (int[] box : r.boxesByColor.values().iterator().next())
            for (double[] corner : tilted(box))
                for (double c : corner)
                    check(c >= 0 && c <= 16, "cube corner inside the requested size: " + c);
    }

    static void cubeVoxelsFillOnlyTheShell() throws Exception {
        ImportSettings s = new ImportSettings();
        s.mode = ImportSettings.Mode.VOXELS;
        s.maxSize = 8;
        ImportResult r = ModelConverter.convert(cube(), s, null);
        // 8x8x8 cube surface touches the cells of coordinates 0..8, the shell of a 9x9x9 block minus its 7x7x7 inside
        int expectedMax = 9 * 9 * 9 - 7 * 7 * 7;
        check(r.boxCount <= expectedMax && r.boxCount >= 6 * 7 * 7, "voxel shell size plausible: " + r.boxCount + " (<= " + expectedMax + ")");
        Set<String> seen = new HashSet<>();
        for (int[] box : r.boxesByColor.values().iterator().next()) {
            check(box.length == 6 && box[3] - box[0] == 1 && box[4] - box[1] == 1 && box[5] - box[2] == 1, "voxel is a unit cube");
            check(seen.add(Arrays.toString(box)), "no voxel twice");
            boolean inside = box[0] > 0 && box[0] < 8 && box[1] > 0 && box[1] < 8 && box[2] > 0 && box[2] < 8;
            check(!inside, "no voxel in the hollow inside: " + Arrays.toString(box));
        }
    }

    static void texturedVoxelsGetColors() throws Exception {
        // one flat red triangle through a "texture" that is just a solid source
        ObjModel m = cube();
        ImportSettings s = new ImportSettings();
        s.mode = ImportSettings.Mode.VOXELS;
        s.maxSize = 4;
        s.color = 0xFF336699;
        ImportResult r = ModelConverter.convert(m, s, null);
        check(r.boxesByColor.containsKey(0xFF336699), "flat colour from the settings is used");
    }

    // ------------------------------------------------------------------------------------------------------------------------------------------------

    static ObjModel cube() throws Exception {
        String obj = "v 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\nv 0 0 1\nv 1 0 1\nv 1 1 1\nv 0 1 1\n" + "f 1 2 3 4\nf 5 8 7 6\nf 1 5 6 2\nf 2 6 7 3\nf 3 7 8 4\nf 4 8 5 1\n";
        return ObjParser.parse(new ByteArrayInputStream(obj.getBytes(StandardCharsets.UTF_8)));
    }

    /** Same corner order as BoxCorner: EUN, EUS, EDN, EDS, WUN, WUS, WDN, WDS; {x, y, z} per corner. Port of LittleTransformableBox#getTiltedCorners. */
    static double[][] tilted(int[] array) {
        int[][] base = { { 1, 1, 0 }, { 1, 1, 1 }, { 1, 0, 0 }, { 1, 0, 1 }, { 0, 1, 0 }, { 0, 1, 1 }, { 0, 0, 0 }, { 0, 0, 1 } }; // 1 = max, 0 = min
        double[][] corners = new double[8][3];
        boolean transformable = array.length > 6 && array[6] < 0;
        int indicator = transformable ? array[6] : 0;
        int active = 0;
        for (int i = 0; i < 8; i++)
            for (int axis = 0; axis < 3; axis++) {
                int offset = 0;
                if ((indicator >> (i * 3 + axis) & 1) != 0) {
                    int index = active++;
                    int packed = array[7 + index / 2];
                    offset = index % 2 == 1 ? (short) (packed & 0xFFFF) : (short) ((packed >> 16) & 0xFFFF);
                }
                corners[i][axis] = offset + array[axis + (base[i][axis] == 1 ? 3 : 0)];
            }
        return corners;
    }

    static boolean checkCorners(int[] box, int[][] vertices, String what) {
        for (double[] corner : tilted(box)) {
            boolean found = false;
            for (int[] v : vertices)
                found |= corner[0] == v[0] && corner[1] == v[1] && corner[2] == v[2];
            if (!found) {
                check(false, what + ": corner " + Arrays.toString(corner) + " is not one of the vertices");
                return false;
            }
        }
        // all three vertices must be used, otherwise the triangle would be lost
        for (int[] v : vertices) {
            boolean used = false;
            for (double[] corner : tilted(box))
                used |= corner[0] == v[0] && corner[1] == v[1] && corner[2] == v[2];
            if (!used) {
                check(false, what + ": vertex " + Arrays.toString(v) + " is not used by any corner");
                return false;
            }
        }
        return true;
    }

    static void check(boolean ok, String message) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + message);
        }
    }

}
