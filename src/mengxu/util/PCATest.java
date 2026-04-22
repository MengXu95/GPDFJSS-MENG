package mengxu.util;

public class PCATest{

    public static void show(double[][] v) {
        for (double[] aV : v) {
            for (int column = 0; column < v[0].length; column++) {
                System.out.print(aV[column] + "    ");
            }
            System.out.println();
        }
    }

    public static double[][] reduced(double[][] allOriginal){
        double[][] reducedAll = new double[allOriginal.length][1];
        for(int i=0; i<allOriginal.length; i++){
            reducedAll[i][0] = oneValue(allOriginal[i]);
        }
        return reducedAll;
    }

    public static double oneValue(double[] original){
        double sum = 0;
        for(int i=0; i<original.length; i++){
            sum = sum + original[i]*Math.pow(10,i);
        }
        return sum;
    }

    public static void main(String[] args) {
        PCA pca = new PCA();

//        double[][] v = {{1.0, 2.0, 3.0}, {3.0, 4.0, 3.0}, {5.0, 6.0, 3.0}};
        //test for sequencing decisions
        double[][] v = {{1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0},
                        {7.0, 6.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0},
                        {7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 6.0, 7.0, 7.0, 7.0, 7.0, 7.0},
                        {7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0}};

        show(pca.pcaNormalized(v));

        show(pca.pca(v));

        show(reduced(v));

    }
}