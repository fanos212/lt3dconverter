package lt3d.core;

import java.util.Arrays;

/** Boxes produced by one chunk of triangles, in the order they were produced */
final class Piece {

    int size;
    int skipped;
    int[] colors = new int[16];
    int[][] boxes = new int[16][];
    /** cell keys, only used by the voxel mode */
    long[] keys = new long[16];

    void add(long key, int color, int[] box) {
        if (size == colors.length) {
            int n = size * 2;
            colors = Arrays.copyOf(colors, n);
            boxes = Arrays.copyOf(boxes, n);
            keys = Arrays.copyOf(keys, n);
        }
        colors[size] = color;
        boxes[size] = box;
        keys[size] = key;
        size++;
    }
}
