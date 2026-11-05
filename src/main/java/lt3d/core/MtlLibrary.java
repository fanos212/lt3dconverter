package lt3d.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reads the parts of .mtl files that matter here: diffuse colour (Kd), opacity (d / Tr) and the diffuse texture (map_Kd). */
public final class MtlLibrary {

    public static final class Material {

        public final String name;
        public float r = 1, g = 1, b = 1;
        public float opacity = 1;
        /** texture path exactly as written in the file, may be null */
        public String diffuseMap;

        Material(String name) {
            this.name = name;
        }
    }

    public final Map<String, Material> materials = new LinkedHashMap<>();

    public static MtlLibrary load(Path directory, Iterable<String> fileNames) throws ImportException {
        MtlLibrary library = new MtlLibrary();
        boolean any = false;
        for (String name : fileNames) {
            Path file = directory.resolve(name);
            if (!Files.isRegularFile(file))
                throw new ImportException("One or more MTL files declared in the obj file do not exist! (" + name + ")");
            any = true;
            try (InputStream in = Files.newInputStream(file)) {
                library.read(in);
            } catch (IOException e) {
                throw new ImportException("There was a problem reading an MTL file (" + name + ")", e);
            }
        }
        if (!any)
            throw new ImportException("No MTL entry declared in this .obj file!");
        return library;
    }

    void read(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        Material current = null;
        String line;
        while ((line = reader.readLine()) != null) {
            int hash = line.indexOf('#');
            if (hash >= 0)
                line = line.substring(0, hash);
            line = line.trim();
            if (line.isEmpty())
                continue;
            String[] t = line.split("\\s+");
            try {
                switch (t[0].toLowerCase()) {
                    case "newmtl":
                        current = new Material(line.substring(6).trim());
                        materials.put(current.name, current);
                        break;
                    case "kd":
                        if (current != null && t.length >= 4) {
                            current.r = clamp(Float.parseFloat(t[1]));
                            current.g = clamp(Float.parseFloat(t[2]));
                            current.b = clamp(Float.parseFloat(t[3]));
                        }
                        break;
                    case "d":
                        if (current != null && t.length >= 2)
                            current.opacity = clamp(Float.parseFloat(t[t.length - 1]));
                        break;
                    case "tr":
                        if (current != null && t.length >= 2)
                            current.opacity = clamp(1F - Float.parseFloat(t[t.length - 1]));
                        break;
                    case "map_kd":
                        if (current != null)
                            current.diffuseMap = stripOptions(t, line);
                        break;
                    default:
                        break;
                }
            } catch (NumberFormatException e) {
                // a broken value in a material is not worth failing the whole import, keep the defaults
            }
        }
    }

    /** map_Kd may carry options like "-s 1 1 1" before the file name, which itself may contain spaces */
    private static String stripOptions(String[] tokens, String line) {
        int i = 1;
        while (i < tokens.length && tokens[i].startsWith("-")) {
            i++; // the option itself
            while (i < tokens.length && isNumber(tokens[i]))
                i++; // its numeric arguments
        }
        if (i >= tokens.length)
            return null;
        int start = line.indexOf(tokens[i], tokens[0].length());
        return line.substring(start).trim();
    }

    private static boolean isNumber(String s) {
        try {
            Float.parseFloat(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static float clamp(float f) {
        return f < 0 ? 0 : f > 1 ? 1 : f;
    }
}
