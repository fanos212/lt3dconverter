package lt3d.core;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

/** Entry point of the conversion: reads the files named in the settings and runs the chosen algorithm. Contains no Minecraft code. */
public final class ModelConverter {

    private ModelConverter() {}

    public static ImportResult convert(ImportSettings settings) throws ImportException {
        return convert(settings, Job.single());
    }

    public static ImportResult convert(ImportSettings settings, Job job) throws ImportException {
        job.phase("Reading model", 0);
        Path modelPath = path(settings.modelFile);
        if (settings.modelFile.isBlank() || !Files.isRegularFile(modelPath))
            throw new ImportException("Cannot read model file! Does it exist?");

        ObjModel model;
        try (InputStream in = Files.newInputStream(modelPath)) {
            model = ObjParser.parse(in);
        } catch (IOException e) {
            throw new ImportException("Unreadable model file! Is it in .obj format?", e);
        }

        return convert(model, settings, modelPath.toAbsolutePath().getParent(), job);
    }

    public static ImportResult convert(ObjModel model, ImportSettings settings, Path modelDirectory) throws ImportException {
        return convert(model, settings, modelDirectory, Job.single());
    }

    public static ImportResult convert(ObjModel model, ImportSettings settings, Path modelDirectory, Job job) throws ImportException {
        job.phase("Loading textures", 0);
        if (settings.maxSize < 1)
            throw new ImportException("The max size must be at least 1!");

        Map<String, ColorSource> colors = new HashMap<>();
        ColorSource fallback = new ColorSource.Solid(settings.color);

        if (settings.useTexture) {
            if (settings.useMtl)
                loadMtl(model, modelDirectory, colors);
            else {
                if (settings.textureFile.isBlank())
                    throw new ImportException("Empty texture field!");
                ColorSource texture = new ColorSource.Image(readImage(path(settings.textureFile)), 1, 1, 1, 1, -1);
                for (String material : model.materials)
                    colors.put(material, texture);
                fallback = texture;
            }
        } else {
            for (String material : model.materials)
                colors.put(material, fallback);
        }

        switch (settings.mode) {
            case VOXELS:
                return VoxelConverter.convert(model, settings, colors, fallback, job);
            default:
                return SliceConverter.convert(model, settings, colors, fallback, job);
        }
    }

    private static void loadMtl(ObjModel model, Path directory, Map<String, ColorSource> colors) throws ImportException {
        MtlLibrary library = MtlLibrary.load(directory, model.mtlFiles);
        Map<String, ColorSource> loaded = new HashMap<>(); // one image per file, many materials may share it

        for (String name : model.materials) {
            MtlLibrary.Material material = library.materials.get(name);
            if (material == null) {
                colors.put(name, new ColorSource.Solid(-1));
                continue;
            }
            int flat = ColorSource.argb(material.r, material.g, material.b, material.opacity);
            if (material.diffuseMap == null) {
                colors.put(name, new ColorSource.Solid(flat));
                continue;
            }

            Path file = material.diffuseMap.contains(":") ? Paths.get(material.diffuseMap) : directory.resolve(material.diffuseMap);
            String key = file + "|" + material.r + "|" + material.g + "|" + material.b + "|" + material.opacity;
            ColorSource source = loaded.get(key);
            if (source == null)
                loaded.put(key, source = new ColorSource.Image(readImage(file), material.r, material.g, material.b, material.opacity, flat));
            colors.put(name, source);
        }
    }

    private static BufferedImage readImage(Path file) throws ImportException {
        try {
            BufferedImage image = ImageIO.read(file.toFile());
            if (image == null)
                throw new ImportException("Error loading texture file! Is it in supported format? (JPEG, PNG, BMP)");
            return image;
        } catch (IOException e) {
            throw new ImportException("Error loading texture file '" + file + "'! Does it exist?", e);
        }
    }

    /** Players tend to paste paths with quotation marks (Windows "Copy as path") */
    public static Path path(String text) {
        String cleaned = text == null ? "" : text.trim().replace("\"", "");
        return Paths.get(cleaned);
    }
}
