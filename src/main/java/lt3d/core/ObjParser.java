package lt3d.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Small tolerant Wavefront OBJ reader. Understands v, vt, f, usemtl and mtllib; everything else is ignored.
 * Polygons are triangulated as a fan, which is what the original (1.12.2) importer did through ObjUtils.triangulate.
 */
public final class ObjParser {

    private static final String NO_MATERIAL = "";

    private ObjParser() {}

    public static ObjModel parse(InputStream stream) throws IOException, ImportException {
        ObjModel model = new ObjModel();
        List<double[]> verts = new ArrayList<>();
        List<double[]> uvs = new ArrayList<>();
        String material = NO_MATERIAL;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            StringBuilder pending = null;
            while ((line = reader.readLine()) != null) {
                if (pending != null) {
                    line = pending.append(line).toString();
                    pending = null;
                }
                if (line.endsWith("\\")) {
                    pending = new StringBuilder(line.substring(0, line.length() - 1)).append(' ');
                    continue;
                }

                int hash = line.indexOf('#');
                if (hash >= 0)
                    line = line.substring(0, hash);
                line = line.trim();
                if (line.isEmpty())
                    continue;

                String[] t = line.split("\\s+");
                switch (t[0]) {
                    case "v":
                        if (t.length < 4)
                            throw new ImportException("Broken vertex line: " + line);
                        verts.add(new double[] { num(t[1]), num(t[2]), num(t[3]) });
                        break;
                    case "vt":
                        if (t.length < 2)
                            throw new ImportException("Broken texture coordinate line: " + line);
                        uvs.add(new double[] { num(t[1]), t.length > 2 ? num(t[2]) : 0D });
                        break;
                    case "usemtl":
                        material = line.length() > 6 ? line.substring(6).trim() : NO_MATERIAL;
                        if (!model.materials.contains(material))
                            model.materials.add(material);
                        break;
                    case "mtllib":
                        if (line.length() > 6) {
                            String rest = line.substring(6).trim();
                            // several libraries may be listed, but file names can contain spaces, so only split when every part looks like a .mtl file
                            String[] parts = rest.split("\\s+");
                            boolean allMtl = parts.length > 1;
                            for (String part : parts)
                                allMtl &= part.toLowerCase().endsWith(".mtl");
                            model.mtlFiles.addAll(allMtl ? Arrays.asList(parts) : Arrays.asList(rest));
                        }
                        break;
                    case "f":
                        readFace(model, t, verts, uvs, material);
                        break;
                    default:
                        break;
                }
            }
        }

        if (model.triangles.isEmpty())
            throw new ImportException("The model contains no faces!");
        if (!model.materials.contains(NO_MATERIAL) && model.triangles.stream().anyMatch(x -> x.material.isEmpty()))
            model.materials.add(NO_MATERIAL);
        return model;
    }

    private static void readFace(ObjModel model, String[] t, List<double[]> verts, List<double[]> uvs, String material) throws ImportException {
        int count = t.length - 1;
        if (count < 3)
            return; // lines and points are not surfaces

        int[] vi = new int[count];
        int[] ti = new int[count];
        boolean allUv = true;
        for (int i = 0; i < count; i++) {
            String[] parts = t[i + 1].split("/", -1);
            vi[i] = resolve(parts[0], verts.size(), "vertex");
            if (parts.length > 1 && !parts[1].isEmpty())
                ti[i] = resolve(parts[1], uvs.size(), "texture coordinate");
            else {
                ti[i] = -1;
                allUv = false;
            }
        }

        for (int i = 1; i < count - 1; i++) {
            int[] idx = { 0, i, i + 1 };
            double[][] p = new double[3][];
            double[] uv = allUv ? new double[6] : null;
            for (int k = 0; k < 3; k++) {
                p[k] = verts.get(vi[idx[k]]);
                if (allUv) {
                    double[] c = uvs.get(ti[idx[k]]);
                    uv[k * 2] = c[0];
                    uv[k * 2 + 1] = c[1];
                }
            }
            model.add(new Tri(p[0], p[1], p[2], uv, material));
        }
    }

    private static int resolve(String token, int size, String what) throws ImportException {
        int index;
        try {
            index = Integer.parseInt(token);
        } catch (NumberFormatException e) {
            throw new ImportException("Broken face, cannot read " + what + " index '" + token + "'");
        }
        index = index < 0 ? size + index : index - 1;
        if (index < 0 || index >= size)
            throw new ImportException("Face references a missing " + what + " (index " + token + ")");
        return index;
    }

    private static double num(String s) throws ImportException {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new ImportException("Cannot read number '" + s + "'");
        }
    }
}
