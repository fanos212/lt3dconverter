package lt3d.core;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Checks the multi threading, the CPU limit, cancelling and the exported text. Run: java -cp out lt3d.core.ParallelAndFormatTest */
public class ParallelAndFormatTest {

    static int failures;

    public static void main(String[] args) throws Exception {
        ObjModel model = randomSphere(60000);
        for (ImportSettings.Mode mode : ImportSettings.Mode.values()) {
            ImportSettings s = new ImportSettings();
            s.mode = mode;
            s.maxSize = mode == ImportSettings.Mode.VOXELS ? 48 : 200;
            s.color = 0xFF3366AA;

            String single = run(model, s, Job.single());
            Job many = new Job(6);
            String parallel = run(model, s, many);
            check(single.equals(parallel), mode + ": 1 thread and 6 threads give identical text (" + single.length() + " chars)");
            check(many.done() > 0, mode + ": progress is reported");
        }

        throttle(model);
        cancel(model);
        formatRoundTrip();

        if (failures > 0) {
            System.out.println("FAILED: " + failures);
            System.exit(1);
        }
        System.out.println("All checks passed");
    }

    static String run(ObjModel model, ImportSettings s, Job job) throws Exception {
        ImportResult r = ModelConverter.convert(model, s, null, job);
        return LittleTilesFormat.toSnbt(r, s).text;
    }

    static void throttle(ObjModel model) throws Exception {
        ImportSettings s = new ImportSettings();
        s.mode = ImportSettings.Mode.VOXELS;
        s.maxSize = 48;
        long t0 = System.nanoTime();
        ModelConverter.convert(model, s, null, new Job(2));
        double full = (System.nanoTime() - t0) / 1e9;

        Job slow = new Job(2);
        slow.cpuFraction = 0.25;
        t0 = System.nanoTime();
        ModelConverter.convert(model, s, null, slow);
        double limited = (System.nanoTime() - t0) / 1e9;
        System.out.printf("  full speed %.2fs, 25%% %.2fs%n", full, limited);
        check(limited > full * 2, "25% takes clearly longer than 100%");
    }

    static void cancel(ObjModel model) throws Exception {
        ImportSettings s = new ImportSettings();
        s.mode = ImportSettings.Mode.VOXELS;
        s.maxSize = 48;
        Job job = new Job(2);
        job.cpuFraction = 0.05;
        new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {}
            job.cancel();
        }).start();
        long t0 = System.nanoTime();
        try {
            ModelConverter.convert(model, s, null, job);
            check(false, "cancelled job must throw");
        } catch (Job.Cancelled expected) {}
        check((System.nanoTime() - t0) / 1e9 < 3, "cancel returns quickly");
    }

    static void formatRoundTrip() throws Exception {
        String obj = "v 0 0 0\nv 4 0 0\nv 0 4 0\nv 0 0 4\nusemtl a\nf 1 2 3\nf 1 2 4\nf 1 3 4\nf 2 3 4\n";
        ObjModel m = ObjParser.parse(new ByteArrayInputStream(obj.getBytes(StandardCharsets.UTF_8)));
        ImportSettings s = new ImportSettings();
        s.maxSize = 16;
        s.color = 0x80FF0000; // translucent red
        s.grid = 32;
        ImportResult r = ModelConverter.convert(m, s, null);
        LittleTilesFormat.Output out = LittleTilesFormat.toSnbt(r, s);
        System.out.println("  " + out.text);

        Object parsed = new Snbt(out.text).value();
        check(parsed instanceof java.util.Map, "root is a compound");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> root = (java.util.Map<String, Object>) parsed;
        check(root.containsKey("grid") && root.get("grid").equals(32), "grid 32 is written");
        check(root.get("trans") != null, "translucent flag set for alpha < 255");
        check(root.get("boxes").equals(out.boxes) && root.get("tiles").equals(1), "counts match");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> t = (java.util.Map<String, Object>) root.get("t");
        @SuppressWarnings("unchecked")
        List<int[]> list = (List<int[]>) t.get("littletiles:colored_clean");
        check(list.size() == out.boxes + 1, "one colour entry + boxes");
        check(list.get(0).length == 1 && list.get(0)[0] == 0x80FF0000, "colour entry is the ARGB int");
        for (int i = 1; i < list.size(); i++)
            check(list.get(i).length >= 6, "box " + i + " has at least 6 ints");

        // default grid is not written
        s.grid = 16;
        check(!new Snbt(LittleTilesFormat.toSnbt(r, s).text).value().toString().contains("grid"), "grid 16 is omitted");
    }

    static ObjModel randomSphere(int triangles) throws Exception {
        Random rnd = new Random(7);
        StringBuilder sb = new StringBuilder();
        List<double[]> pts = new ArrayList<>();
        for (int i = 0; i < triangles * 3; i++) {
            double[] p = { rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian() };
            double l = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
            sb.append(String.format(java.util.Locale.ROOT, "v %f %f %f%n", p[0] / l * 10, p[1] / l * 10, p[2] / l * 10));
        }
        for (int i = 0; i < triangles; i++)
            sb.append("f ").append(i * 3 + 1).append(' ').append(i * 3 + 2).append(' ').append(i * 3 + 3).append('\n');
        return ObjParser.parse(new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8)));
    }

    static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        } else
            System.out.println("ok: " + what);
    }

    /** Minimal SNBT reader, enough for the subset we write: compounds, lists, int arrays, ints, 1b */
    static final class Snbt {

        final String s;
        int p;

        Snbt(String s) {
            this.s = s;
        }

        Object value() {
            char c = s.charAt(p);
            if (c == '{')
                return compound();
            if (c == '[')
                return s.startsWith("[I;", p) ? intArray() : list();
            int start = p;
            while (p < s.length() && ",}]".indexOf(s.charAt(p)) < 0)
                p++;
            String token = s.substring(start, p);
            if (token.endsWith("b"))
                return token;
            return Integer.parseInt(token);
        }

        java.util.Map<String, Object> compound() {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            p++;
            while (s.charAt(p) != '}') {
                String key;
                if (s.charAt(p) == '"') {
                    p++;
                    int start = p;
                    while (s.charAt(p) != '"')
                        p++;
                    key = s.substring(start, p++);
                } else {
                    int start = p;
                    while (s.charAt(p) != ':')
                        p++;
                    key = s.substring(start, p);
                }
                if (s.charAt(p++) != ':')
                    throw new IllegalStateException("expected : at " + p);
                map.put(key, value());
                if (s.charAt(p) == ',')
                    p++;
            }
            p++;
            return map;
        }

        List<Object> list() {
            List<Object> l = new ArrayList<>();
            p++;
            while (s.charAt(p) != ']') {
                l.add(value());
                if (s.charAt(p) == ',')
                    p++;
            }
            p++;
            return l;
        }

        int[] intArray() {
            p += 3;
            List<Integer> l = new ArrayList<>();
            while (s.charAt(p) != ']') {
                int start = p;
                while (",]".indexOf(s.charAt(p)) < 0)
                    p++;
                l.add(Integer.parseInt(s.substring(start, p)));
                if (s.charAt(p) == ',')
                    p++;
            }
            p++;
            return l.stream().mapToInt(i -> i).toArray();
        }
    }
}
