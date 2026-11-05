package lt3d.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Control and progress of one conversion. The UI creates it, hands it to the converter and reads the progress from it while the workers run.
 * <ul>
 * <li>{@link #cpuFraction} may be changed at any time (the workers look at it after every chunk of work).</li>
 * <li>{@link #cancel()} stops the work as soon as every worker finished its current chunk.</li>
 * </ul>
 */
public final class Job {

    /** thrown inside the workers when the job was cancelled */
    public static final class Cancelled extends ImportException {

        public Cancelled() {
            super("Cancelled");
        }
    }

    /** number of worker threads */
    public final int threads;
    /** Share of the whole machine the workers may use, 0.02 .. 1. Every worker sleeps after each chunk so that it is busy only this fraction of the time. */
    public volatile double cpuFraction = 1D;
    private volatile boolean cancelled;

    private volatile String phase = "";
    private volatile long total;
    private final AtomicLong done = new AtomicLong();
    private volatile long phaseStart = System.nanoTime();
    private final long jobStart = System.nanoTime();

    public Job(int threads) {
        this.threads = Math.max(1, threads);
    }

    /** One thread, full speed: what the tests and the old single threaded entry points use */
    public static Job single() {
        return new Job(1);
    }

    public static Job allCores() {
        return new Job(Runtime.getRuntime().availableProcessors());
    }

    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void check() throws Cancelled {
        if (cancelled)
            throw new Cancelled();
    }

    /** @param total number of work items of the phase, 0 if unknown */
    public void phase(String name, long total) {
        this.phase = name;
        this.total = total;
        this.done.set(0);
        this.phaseStart = System.nanoTime();
    }

    public String phase() {
        return phase;
    }

    public long total() {
        return total;
    }

    public long done() {
        return done.get();
    }

    void advance(long amount) {
        done.addAndGet(amount);
    }

    /** seconds since the current phase started */
    public double phaseSeconds() {
        return (System.nanoTime() - phaseStart) / 1e9;
    }

    public double totalSeconds() {
        return (System.nanoTime() - jobStart) / 1e9;
    }

    /** Sleeps {@code nanos}, in short steps so that cancelling and a changed {@link #cpuFraction} take effect quickly */
    void sleep(long nanos) throws Cancelled {
        long end = System.nanoTime() + nanos;
        while (true) {
            check();
            long left = end - System.nanoTime();
            if (left <= 0)
                return;
            try {
                Thread.sleep(Math.min(left / 1_000_000L, 40L), (int) Math.min(left % 1_000_000L, 999_999L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancel();
                throw new Cancelled();
            }
        }
    }
}
