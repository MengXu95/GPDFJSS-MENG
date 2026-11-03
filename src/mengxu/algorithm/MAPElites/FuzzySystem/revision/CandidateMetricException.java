package mengxu.algorithm.MAPElites.FuzzySystem.revision;

/** A rule's numeric output is undefined on valid, frozen metric inputs. */
public final class CandidateMetricException extends IllegalArgumentException {
    public CandidateMetricException(String message) { super(message); }
}
