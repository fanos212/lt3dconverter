package lt3d;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import lt3d.core.ImportSettings;
import lt3d.core.Job;
import lt3d.core.LittleTilesFormat;
import lt3d.core.ModelConverter;

/** Command line mode: {@code java -jar lt3dconverter.jar model.obj out.txt [options]} */
final class Cli {

    private Cli() {}

    static void usage() {
        System.out.println("Usage: lt3dconverter <model.obj> <out.txt> [options]\n" + "  --mode slices|voxels   (default slices)\n" + "  --size N               longest side in tiles (default 64)\n"
                + "  --grid N               tiles per block: 1,2,4,8,16,32,64,128 (default 16)\n" + "  --cpu P                share of the PC in percent, 5..100 (default 100)\n"
                + "  --color RRGGBB         flat colour (default FFFFFF)\n" + "  --texture file.png     take colours from an image\n"
                + "  --mtl                  take colours/textures from the .mtl files\n" + "  --precision X          voxel mode sampling step (default 0.5)\n"
                + "  --block name           block id (default littletiles:colored_clean)");
    }

    static int run(String[] args) {
        try {
            if (args.length < 2) {
                usage();
                return 2;
            }
            ImportSettings s = new ImportSettings();
            s.modelFile = args[0];
            s.maxSize = 64;
            int cpu = 100;
            for (int i = 2; i < args.length; i++) {
                String a = args[i];
                switch (a) {
                    case "--mode":
                        s.mode = ImportSettings.Mode.byName(args[++i]);
                        break;
                    case "--size":
                        s.maxSize = Integer.parseInt(args[++i]);
                        break;
                    case "--grid":
                        s.grid = Integer.parseInt(args[++i]);
                        break;
                    case "--cpu":
                        cpu = Integer.parseInt(args[++i]);
                        break;
                    case "--color":
                        s.color = 0xFF000000 | Integer.parseInt(args[++i].replace("#", ""), 16);
                        break;
                    case "--texture":
                        s.useTexture = true;
                        s.textureFile = args[++i];
                        break;
                    case "--mtl":
                        s.useTexture = true;
                        s.useMtl = true;
                        break;
                    case "--precision":
                        s.precision = Float.parseFloat(args[++i]);
                        break;
                    case "--block":
                        s.baseBlock = args[++i];
                        break;
                    default:
                        System.out.println("Unknown option " + a);
                        usage();
                        return 2;
                }
            }

            Job job = Job.allCores();
            job.cpuFraction = Math.max(5, Math.min(100, cpu)) / 100D;
            Thread printer = new Thread(() -> {
                try {
                    while (true) {
                        Thread.sleep(1000);
                        long total = job.total();
                        System.out.printf("%s: %d / %d%n", job.phase(), job.done(), total);
                    }
                } catch (InterruptedException e) {}
            });
            printer.setDaemon(true);
            printer.start();

            var result = ModelConverter.convert(s, job);
            var out = LittleTilesFormat.toSnbt(result, s);
            Files.write(Paths.get(args[1]), out.text.getBytes(StandardCharsets.UTF_8));
            System.out.printf("Done in %.1fs: %d boxes, %d colours, %d skipped triangles, size %dx%dx%d tiles, %.1f KB -> %s%n", job.totalSeconds(), out.boxes, out.tiles,
                result.skippedTriangles, out.size[0], out.size[1], out.size[2], out.text.length() / 1024D, args[1]);
            return 0;
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            return 1;
        }
    }
}
