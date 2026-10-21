package mengxu.algorithm.multiobjective.MPSLGP;

import yimei.jss.gp.PreferenceStateAccess;

/** Checks the actual loaded GP implementation without training or simulation. */
public final class MPSLGPPreferenceRuntimeCheck {
    private MPSLGPPreferenceRuntimeCheck() { }

    public static void main(String[] args) {
        if (args.length != 0) { throw new IllegalArgumentException("The preference runtime check takes no arguments."); }
        PreferenceStateAccess.verifyRuntime();
        System.out.println("PASS: preference runtime " + PreferenceStateAccess.RUNTIME_VERSION);
        System.out.println("Verified both PSLGP and MPSLGP GP nodes: Pone/Ptwo = 20/80 and 80/20.");
        System.out.println("Missing preference contexts are rejected, not replaced with constants.");
        System.out.println("No training or scheduling simulations were started.");
        System.out.println("AttributeGPNode loaded from "
                + yimei.jss.gp.terminal.AttributeGPNode.class.getProtectionDomain().getCodeSource().getLocation());
        for (java.util.Map.Entry<String, String> entry : PreferenceStateAccess.runtimeProvenance().entrySet()) {
            System.out.println(entry.getKey() + "=" + entry.getValue());
        }
    }
}
