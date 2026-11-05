package lt3d.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Boxes grouped by colour. Every box is an int array in the LittleTiles box format:
 * {minX, minY, minZ, maxX, maxY, maxZ} for a normal box, or the same six numbers followed by the corner indicator and the packed 16 bit
 * corner offsets for a transformable box (see {@link SliceConverter}).
 */
public final class ImportResult {

    public final Map<Integer, List<int[]>> boxesByColor = new LinkedHashMap<>();
    public int boxCount;
    public int skippedTriangles;

    void add(int color, int[] box) {
        boxesByColor.computeIfAbsent(color, x -> new ArrayList<>()).add(box);
        boxCount++;
    }
}
