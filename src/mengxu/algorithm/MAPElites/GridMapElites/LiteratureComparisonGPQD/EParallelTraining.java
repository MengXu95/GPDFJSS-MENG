package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import java.io.IOException;

public final class EParallelTraining {
    private EParallelTraining() { }
    public static void main(String[] args) throws IOException {
        if (args.length == 0 || (args.length == 1 && args[0].equals("--help"))) {
            System.out.println("EParallelTraining --config FILE --resume [--workers 5] [--worker-heap 2g]"
                    + " [--dry-run] [--set key=value ...]");
            return;
        }
        ETraining.runParallel(EParallelOptions.parse(args));
    }
}
