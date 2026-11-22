package yimei.jss.niching;

public final class SurrogateSamples {
    private SurrogateSamples() { }

    public static double[][][][] copy(double[][][][] samples) {
        double[][][][] copy = new double[samples.length][][][];
        for (int subpop = 0; subpop < samples.length; subpop++) {
            copy[subpop] = new double[samples[subpop].length][][];
            for (int individual = 0; individual < samples[subpop].length; individual++) {
                copy[subpop][individual] = new double[samples[subpop][individual].length][];
                for (int preference = 0; preference < samples[subpop][individual].length; preference++) {
                    copy[subpop][individual][preference] = samples[subpop][individual][preference].clone();
                }
            }
        }
        return copy;
    }
}
