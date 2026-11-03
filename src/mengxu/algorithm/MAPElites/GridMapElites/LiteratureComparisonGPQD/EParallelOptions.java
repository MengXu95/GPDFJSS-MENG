package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Execution-only switches; these never enter the scientific configuration. */
public final class EParallelOptions {
    public final EConfiguration config;
    public final int workers;
    public final String workerHeap;

    private EParallelOptions(EConfiguration config, int workers, String workerHeap) {
        this.config = config;
        this.workers = workers;
        this.workerHeap = workerHeap;
    }

    public static EParallelOptions parse(String[] args) throws IOException {
        int workers = 5;
        String heap = "2g";
        boolean seenWorkers = false, seenHeap = false;
        List<String> scientific = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String argument = args[i];
            if ("--workers".equals(argument)) {
                if (seenWorkers || i + 1 == args.length) {
                    throw new IllegalArgumentException("Use --workers N once (1..5)");
                }
                seenWorkers = true;
                String value = args[++i];
                if (!value.matches("[1-5]")) {
                    throw new IllegalArgumentException("--workers must be between 1 and 5: " + value);
                }
                workers = Integer.parseInt(value);
            } else if ("--worker-heap".equals(argument)) {
                if (seenHeap || i + 1 == args.length) {
                    throw new IllegalArgumentException("Use --worker-heap Xm or Xg once");
                }
                seenHeap = true;
                heap = args[++i].toLowerCase(Locale.ROOT);
                if (!heap.matches("[1-9][0-9]*[mg]")) {
                    throw new IllegalArgumentException("--worker-heap must be Xm or Xg: " + heap);
                }
                try {
                    Math.multiplyExact(Long.parseLong(heap.substring(0, heap.length() - 1)),
                            heap.endsWith("g") ? 1L << 30 : 1L << 20);
                } catch (ArithmeticException | NumberFormatException invalid) {
                    throw new IllegalArgumentException("--worker-heap is too large: " + heap, invalid);
                }
            } else {
                scientific.add(argument);
                if (("--config".equals(argument) || "--set".equals(argument)) && i + 1 < args.length) {
                    scientific.add(args[++i]);
                }
            }
        }
        return new EParallelOptions(EConfiguration.parse(scientific.toArray(new String[0])), workers, heap);
    }
}
