package lt3d.core;

import java.util.List;
import java.util.Map;

/**
 * Writes an {@link ImportResult} as the text the LittleTiles "Import" window accepts: the SNBT form of the blueprint data, exactly what the "data" type of
 * the LittleTiles export window produces (see LittleGroup#save and LittleCollection#save).
 * <pre>
 * {t:{"littletiles:colored_clean":[[I;color],[I;box...],[I;box...],[I;color2],...]},size:[I;x,y,z],min:[I;x,y,z],tiles:N,boxes:M}
 * </pre>
 * A one number array starts a new colour, every following array is one box. {@code grid} is left out for the default grid 16, like LittleTiles does.
 */
public final class LittleTilesFormat {

    private LittleTilesFormat() {}

    public static final class Output {

        public final String text;
        public final int tiles;
        public final int boxes;
        public final int[] size;

        Output(String text, int tiles, int boxes, int[] size) {
            this.text = text;
            this.tiles = tiles;
            this.boxes = boxes;
            this.size = size;
        }
    }

    public static Output toSnbt(ImportResult result, ImportSettings settings) throws ImportException {
        String block = settings.baseBlock == null ? "" : settings.baseBlock.trim();
        if (block.isEmpty())
            throw new ImportException("The block name is empty!");

        int[] min = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE };
        int[] max = { Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE };
        boolean translucent = false;
        for (Map.Entry<Integer, List<int[]>> entry : result.boxesByColor.entrySet()) {
            if ((entry.getKey() >>> 24) < 255)
                translucent = true;
            for (int[] box : entry.getValue())
                for (int k = 0; k < 3; k++) {
                    min[k] = Math.min(min[k], box[k]);
                    max[k] = Math.max(max[k], box[k + 3]);
                }
        }
        if (result.boxCount == 0)
            throw new ImportException("Nothing to export!");

        StringBuilder out = new StringBuilder(result.boxCount * 40 + 256);
        out.append("{t:{");
        quote(out, block);
        out.append(":[");
        boolean first = true;
        for (Map.Entry<Integer, List<int[]>> entry : result.boxesByColor.entrySet()) {
            if (!first)
                out.append(',');
            first = false;
            out.append("[I;").append(entry.getKey()).append(']');
            for (int[] box : entry.getValue()) {
                out.append(",[I;");
                for (int i = 0; i < box.length; i++) {
                    if (i > 0)
                        out.append(',');
                    out.append(box[i]);
                }
                out.append(']');
            }
        }
        out.append("]}");
        if (settings.grid != 16)
            out.append(",grid:").append(settings.grid);
        int[] size = { max[0] - min[0], max[1] - min[1], max[2] - min[2] };
        out.append(",size:[I;").append(size[0]).append(',').append(size[1]).append(',').append(size[2]).append(']');
        out.append(",min:[I;").append(min[0]).append(',').append(min[1]).append(',').append(min[2]).append(']');
        out.append(",tiles:").append(result.boxesByColor.size());
        out.append(",boxes:").append(result.boxCount);
        if (translucent)
            out.append(",trans:1b");
        out.append('}');
        return new Output(out.toString(), result.boxesByColor.size(), result.boxCount, size);
    }

    private static void quote(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\')
                out.append('\\');
            out.append(c);
        }
        out.append('"');
    }
}
