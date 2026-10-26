package mengxu.algorithm.MAPElites.FuzzySystem.revision;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;

public final class ArchiveCandidate {
    private final Path source;
    private final int ordinal;
    private final double storedFitness;
    private final String sequencing;
    private final String routing;
    private final String treeHash;

    public ArchiveCandidate(Path source, int ordinal, double storedFitness,
                            String sequencing, String routing) {
        if (source == null || ordinal < 0 || Double.isNaN(storedFitness)
                || storedFitness < 0 || sequencing == null || routing == null
                || sequencing.trim().isEmpty() || routing.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid archive candidate");
        }
        this.source = source.toAbsolutePath().normalize();
        this.ordinal = ordinal;
        this.storedFitness = storedFitness;
        this.sequencing = sequencing.trim().replaceAll("\\s+", " ");
        this.routing = routing.trim().replaceAll("\\s+", " ");
        this.treeHash = sha256(this.sequencing + "\n" + this.routing);
    }

    public Path source() { return source; }
    public int ordinal() { return ordinal; }
    public double storedFitness() { return storedFitness; }
    public String sequencing() { return sequencing; }
    public String routing() { return routing; }
    public String treeHash() { return treeHash; }
    public String id() { return ordinal + "-" + treeHash.substring(0, 16); }
    public boolean hasFiniteStoredFitness() {
        return Double.isFinite(storedFitness) && storedFitness < Double.MAX_VALUE;
    }

    public GPRule[] rules() {
        GPRule[] rules = {
            PriorityExpression.parseRule(RuleType.SEQUENCING, sequencing),
            PriorityExpression.parseRule(RuleType.ROUTING, routing)
        };
        PriorityExpression.terminals(rules[0].getGPTree().child);
        PriorityExpression.terminals(rules[1].getGPTree().child);
        return rules;
    }

    public static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) {
                result.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
