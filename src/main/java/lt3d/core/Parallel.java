package lt3d.core;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Runs a function over [0, count) in chunks on several threads. The results come back in chunk order, so the output does not depend on the thread count. */
final class Parallel {

    interface ChunkFunction<T> {

        T run(int from, int to) throws ImportException;
    }

    private Parallel() {}

    @SuppressWarnings("unchecked")
    static <T> java.util.List<T> map(Job job, int count, int chunkSize, ChunkFunction<T> function) throws ImportException {
        int chunks = (count + chunkSize - 1) / chunkSize;
        Object[] results = new Object[chunks];
        AtomicInteger next = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Runnable worker = () -> {
            try {
                while (true) {
                    job.check();
                    int index = next.getAndIncrement();
                    if (index >= chunks)
                        return;
                    int from = index * chunkSize;
                    int to = Math.min(count, from + chunkSize);

                    long start = System.nanoTime();
                    results[index] = function.run(from, to);
                    long work = System.nanoTime() - start;
                    job.advance(to - from);

                    double fraction = job.cpuFraction;
                    if (fraction < 0.995D) {
                        fraction = Math.max(fraction, 0.01D);
                        job.sleep((long) (work * (1D - fraction) / fraction));
                    }
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
                job.cancel(); // stop the other workers
            }
        };

        if (job.threads == 1)
            worker.run();
        else {
            Thread[] pool = new Thread[job.threads];
            for (int i = 0; i < pool.length; i++) {
                pool[i] = new Thread(worker, "lt3d-worker-" + i);
                pool[i].setDaemon(true);
                pool[i].start();
            }
            for (Thread thread : pool)
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    job.cancel();
                }
        }

        Throwable t = failure.get();
        if (t != null) {
            if (t instanceof ImportException && !(t instanceof Job.Cancelled))
                throw (ImportException) t;
            if (t instanceof OutOfMemoryError)
                throw new ImportException("Not enough memory! Lower the max size or the grid, or use the Slices mode.", t);
            if (t instanceof Job.Cancelled)
                throw (Job.Cancelled) t;
            throw new ImportException("Unexpected error: " + t, t);
        }
        job.check();
        return (java.util.List<T>) (java.util.List<?>) java.util.Arrays.asList(results);
    }
}
