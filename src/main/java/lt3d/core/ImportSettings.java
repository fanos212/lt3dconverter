package lt3d.core;

/** Everything the converters need to know. Plain data so it can be stored in the structure and sent around easily. */
public final class ImportSettings {

    public enum Mode {
        /** Every triangle becomes one transformable box (a thin surface). Fast, smooth, the original 0.7 behaviour. */
        SLICES,
        /** The surface is rasterised into 1x1x1 tiles of the grid. Slower and blockier, but gives per-tile texture colours. */
        VOXELS;

        public static Mode byName(String name) {
            for (Mode mode : values())
                if (mode.name().equalsIgnoreCase(name))
                    return mode;
            return SLICES;
        }
    }

    public String modelFile = "";
    public String textureFile = "";
    public Mode mode = Mode.SLICES;
    /** take colours from the texture (and/or the MTL file) instead of one flat colour */
    public boolean useTexture = false;
    /** read materials and textures from the .mtl files declared in the .obj */
    public boolean useMtl = false;
    /** ARGB, used when no texture is wanted */
    public int color = -1;
    /** length of the longest side of the model, in tiles of the chosen grid */
    public int maxSize = 16;
    /** tiles per block, one of the grids LittleTiles knows */
    public int grid = 16;
    /** distance between sample lines in {@link Mode#VOXELS}, smaller = fewer holes but slower */
    public float precision = 0.5F;
    /** registry name of the block the tiles are made of */
    public String baseBlock = "littletiles:colored_clean";

    public ImportSettings copy() {
        ImportSettings s = new ImportSettings();
        s.modelFile = modelFile;
        s.textureFile = textureFile;
        s.mode = mode;
        s.useTexture = useTexture;
        s.useMtl = useMtl;
        s.color = color;
        s.maxSize = maxSize;
        s.grid = grid;
        s.precision = precision;
        s.baseBlock = baseBlock;
        return s;
    }
}
