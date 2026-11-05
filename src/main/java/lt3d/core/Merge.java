package lt3d.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Joins the chunk results in order. In voxel mode the first box of a cell wins, like in the single threaded version. */
final class Merge {

    private Merge() {}

    static ImportResult merge(List<Piece> pieces, Job job, boolean dedupe) throws ImportException {
        job.phase("Merging", pieces.size());
        ImportResult result = new ImportResult();
        Set<Long> cells = dedupe ? new HashSet<>() : null;
        for (Piece piece : pieces) {
            job.check();
            result.skippedTriangles += piece.skipped;
            for (int i = 0; i < piece.size; i++) {
                if (dedupe && !cells.add(piece.keys[i]))
                    continue;
                result.add(piece.colors[i], piece.boxes[i]);
            }
            job.advance(1);
        }
        return result;
    }
}
