package mengxu.algorithm.multitaskHeuristicLearning.SVRrelationmodel;

import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.classifiers.functions.SMOreg;
import weka.classifiers.functions.supportVector.RBFKernel;
import weka.core.converters.ConverterUtils.DataSource;

import java.util.ArrayList;

public class SVRmodel{

    public SMOreg svr;
    public int numPC;

    public SVRmodel(){
        this.svr = new SMOreg();
        this.numPC = 40;
    }

    public void SVRmodelTrain(ArrayList<double[]> values) throws Exception {
        // Step 1: Define Attributes
        ArrayList<Attribute> attributes = new ArrayList<>();

        // Single attribute u_i
        attributes.add(new Attribute("u"));

        // Attributes for PC_i (list of features)
        for(int i=0; i<this.numPC; i++){
            attributes.add(new Attribute("PC" + i));
        }

        // Attribute for the target fit_i
        Attribute targetAttribute = new Attribute("fit");
        attributes.add(targetAttribute);

        // Step 2: Create Instances object with these attributes
        Instances dataset = new Instances("FitData", attributes, 0);

        // Set the last attribute as the target attribute
        dataset.setClassIndex(dataset.numAttributes() - 1);

        // Step 3: Add data points and instances to dataset
        for(int i=0; i<values.size(); i++){
            dataset.add(new DenseInstance(1.0, values.get(i)));
        }

        // Configure RBF Kernel
        RBFKernel rbfKernel = new RBFKernel();
        rbfKernel.setGamma(0.01);  // Gamma parameter for the RBF kernel
        svr.setKernel(rbfKernel);

        // Set the regularization parameter C (default is 1.0)
        svr.setC(1.0);

        // Step 5: Train the model
        svr.buildClassifier(dataset);
    }

    public double predict(double[] value) throws Exception {
        // Step 1: Define Attributes
        ArrayList<Attribute> attributes = new ArrayList<>();

        // Single attribute u_i
        attributes.add(new Attribute("u"));

        // Attributes for PC_i (list of features)
        for(int i=0; i<this.numPC; i++){
            attributes.add(new Attribute("PC" + i));
        }

        // Attribute for the target fit_i
        Attribute targetAttribute = new Attribute("fit");
        attributes.add(targetAttribute);

        // Step 2: Create Instances object with these attributes
        Instances dataset = new Instances("FitData", attributes, 0);

        // Set the last attribute as the target attribute
        dataset.setClassIndex(dataset.numAttributes() - 1);

        DenseInstance instance = new DenseInstance(1.0, value);

        dataset.add(instance);


        double predictedFit = svr.classifyInstance(dataset.instance(0));
//        System.out.println("Predicted fit for instance: " + predictedFit);
        return predictedFit;
    }

    public static void main(String[] args) throws Exception {
        // Step 1: Define Attributes
        ArrayList<Attribute> attributes = new ArrayList<>();

        // Single attribute u_i
        attributes.add(new Attribute("u"));

        // Attributes for PC_i (list of features)
        attributes.add(new Attribute("PC1"));
        attributes.add(new Attribute("PC2"));

        // Attribute for the target fit_i
        Attribute targetAttribute = new Attribute("fit");
        attributes.add(targetAttribute);

        // Step 2: Create Instances object with these attributes
        Instances dataset = new Instances("FitData", attributes, 0);

        // Set the last attribute as the target attribute
        dataset.setClassIndex(dataset.numAttributes() - 1);

        // Step 3: Add data points
        double[] values1 = {1.2, 0.5, 0.8, 10.2};  // u_i, PC_i(0.5, 0.8), fit_i(10.2)
        double[] values2 = {2.5, 1.1, 0.9, 20.1};  // u_i, PC_i(1.1, 0.9), fit_i(20.1)
        double[] values3 = {3.1, 0.3, 1.5, 30.5};  // u_i, PC_i(0.3, 1.5), fit_i(30.5)
        double[] values4 = {4.0, 2.0, 0.7, 40.0};  // u_i, PC_i(2.0, 0.7), fit_i(40.0)

        // Add instances to dataset
        dataset.add(new DenseInstance(1.0, values1));
        dataset.add(new DenseInstance(1.0, values2));
        dataset.add(new DenseInstance(1.0, values3));
        dataset.add(new DenseInstance(1.0, values4));

        // Step 4: Configure SMOreg (SVR in Weka) with RBF Kernel
        SMOreg svr = new SMOreg();

        // Configure RBF Kernel
        RBFKernel rbfKernel = new RBFKernel();
        rbfKernel.setGamma(0.01);  // Gamma parameter for the RBF kernel
        svr.setKernel(rbfKernel);

        // Set the regularization parameter C (default is 1.0)
        svr.setC(1.0);

        // Step 5: Train the model
        svr.buildClassifier(dataset);

        // Step 6: Make predictions
        for (int i = 0; i < dataset.numInstances(); i++) {
            double predictedFit = svr.classifyInstance(dataset.instance(i));
            System.out.println("Predicted fit_i for instance " + (i+1) + ": " + predictedFit);
        }
    }
}
