package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import java.io.IOException;
import java.io.PrintStream;

public interface EManagedState {
    void setProgress(PrintStream progress);
    void closeResources() throws IOException;
}
