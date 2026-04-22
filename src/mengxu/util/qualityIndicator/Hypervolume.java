//  Hypervolume.java
//
//  Author:
//       Antonio J. Nebro <antonio@lcc.uma.es>
//       Juan J. Durillo <durillo@lcc.uma.es>
//
//  Copyright (c) 2011 Antonio J. Nebro, Juan J. Durillo
//
//  This program is free software: you can redistribute it and/or modify
//  it under the terms of the GNU Lesser General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.
//
//  This program is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU Lesser General Public License for more details.
// 
//  You should have received a copy of the GNU Lesser General Public License
//  along with this program.  If not, see <http://www.gnu.org/licenses/>.

package mengxu.util.qualityIndicator;

import java.util.Arrays;
import java.util.Comparator;

/**
 * This class implements the hypervolume indicator. The code is the a Java version
 * of the original metric implementation by Eckart Zitzler.
 * It can be used also as a command line program just by typing
 * $java jmetal.qualityIndicator.Hypervolume <solutionFrontFile> <trueFrontFile> <numberOfOjbectives>
 * Reference: E. Zitzler and L. Thiele
 *           Multiobjective Evolutionary Algorithms: A Comparative Case Study 
 *           and the Strength Pareto Approach,
 *           IEEE Transactions on Evolutionary Computation, vol. 3, no. 4, 
 *           pp. 257-271, 1999.
 */
public class Hypervolume {

  public mengxu.util.qualityIndicator.util.MetricsUtil utils_;
  
  /**
  * Constructor
  * Creates a new instance of MultiDelta
  */
  public Hypervolume() {
    utils_ = new mengxu.util.qualityIndicator.util.MetricsUtil();
  } // Hypervolume
  
  /* 
   returns true if 'point1' dominates 'points2' with respect to the 
   to the first 'noObjectives' objectives 
   */
  boolean  dominates(double  point1[], double  point2[], int  noObjectives) {
    int  i;
    int  betterInAnyObjective;

    betterInAnyObjective = 0;
    for (i = 0; i < noObjectives && point1[i] >= point2[i]; i++)
      if (point1[i] > point2[i]) 
      	betterInAnyObjective = 1;
    
    return ((i >= noObjectives) && (betterInAnyObjective>0));
  } //Dominates

  void  swap(double [][] front, int  i, int  j){
    double  [] temp;
    
    temp = front[i];
    front[i] = front[j];
    front[j] = temp;
  } // Swap 

  
  /* all nondominated points regarding the first 'noObjectives' dimensions
  are collected; the points referenced by 'front[0..noPoints-1]' are
  considered; 'front' is resorted, such that 'front[0..n-1]' contains
  the nondominated points; n is returned */
  int  filterNondominatedSet(double [][] front, int  noPoints, int  noObjectives){
    int  i, j;
    int  n;

    n = noPoints;
    i = 0;
    while (i < n) {
      j = i + 1;
      while (j < n) {
        if (dominates(front[i], front[j], noObjectives)) {
	/* remove point 'j' */
	  n--;
	  swap(front, j, n);
        } else if (dominates(front[j], front[i], noObjectives)) {
	/* remove point 'i'; ensure that the point copied to index 'i'
	   is considered in the next outer loop (thus, decrement i) */
	  n--;
	  swap(front, i, n);
	  i--;
	  break;
        } else
	  j++;
      }
      i++;
    }
    return n;
  } // FilterNondominatedSet 


  /* calculate next value regarding dimension 'objective'; consider
     points referenced in 'front[0..noPoints-1]' */
  double  surfaceUnchangedTo(double [][] front, int  noPoints, int  objective) {
    int     i;
    double  minValue, value;

    if (noPoints < 1)  
      System.err.println("run-time error");
    
    minValue = front[0][objective];
    for (i = 1; i < noPoints; i++) {
      value = front[i][objective];
      if (value < minValue)
        minValue = value;
    }
    return minValue;
  } // SurfaceUnchangedTo 

  /* remove all points which have a value <= 'threshold' regarding the
     dimension 'objective'; the points referenced by
     'front[0..noPoints-1]' are considered; 'front' is resorted, such that
     'front[0..n-1]' contains the remaining points; 'n' is returned */
  int  reduceNondominatedSet(double [][] front, int  noPoints, int  objective,
			   double  threshold){
    int  n;
    int  i;

    n = noPoints;
    for (i = 0; i < n; i++)
      if (front[i][objective] <= threshold) {
        n--;
        swap(front, i, n);
      }
  
    return n;
  } // ReduceNondominatedSet

  public double calculateHypervolumeNew(double[][] paretoFront, double[] referencePoint) {//modified by mengxu 2024.5.21
    // Sort the Pareto front by the first objective in ascending order.
    Arrays.sort(paretoFront, Comparator.comparingDouble((double[] o) -> o[1]).reversed());

    double hypervolume = 0.0;
    double previousY = referencePoint[1];

    for (int i = 0; i < paretoFront.length; i++) {
      double currentX = paretoFront[i][0];
      double currentY = paretoFront[i][1];

      // The width is the difference in the first objective values.
      double width = referencePoint[0] - currentX;
      // The height is the difference between the reference point and the current point in the second objective.
      double height = previousY - currentY;

      // Add the volume of the current rectangle.
      hypervolume += width * height;

      // Update the previous X value.
      previousY = currentY;
    }

    return hypervolume;
  }

//  public double calculateHypervolumeNew(double[][] paretoFront, double[] referencePoint) {
//    // Sort the Pareto front by the first objective in ascending order.
//    Arrays.sort(paretoFront, Comparator.comparingDouble(o -> o[0]));
//
//    double hypervolume = 0.0;
//    double previousX = 0.0;
//
//    for (int i = 0; i < paretoFront.length; i++) {
//      double currentX = paretoFront[i][0];
//      double currentY = paretoFront[i][1];
//
//      // The width is the difference in the first objective values.
//      double width = currentX - previousX;
//      // The height is the difference between the reference point and the current point in the second objective.
//      double height = referencePoint[1] - currentY;
//
//      // Add the volume of the current rectangle.
//      hypervolume += width * height;
//
//      // Update the previous X value.
//      previousX = currentX;
//    }
//
//    // Add the last rectangle to the reference point.
//    double width = referencePoint[0] - previousX;
//    double height = referencePoint[1];
//    hypervolume += width * height;
//
//    return hypervolume;
//  }

  public double calculateHypervolume(double [][] front, int  noPoints,int  noObjectives){
    int     n;
    double  volume, distance;

    volume = 0;
    distance = 0;
    n = noPoints;
    while (n > 0) {
      int     noNondominatedPoints;
      double  tempVolume, tempDistance;

      noNondominatedPoints = filterNondominatedSet(front, n, noObjectives - 1);
      //noNondominatedPoints = front.length;
      if (noObjectives < 3) {
        if (noNondominatedPoints < 1)  
          System.err.println("run-time error");
      
        tempVolume = front[0][0];
      } else{
        tempVolume = calculateHypervolume(front,
                noNondominatedPoints,
                noObjectives - 1);
      }

      tempDistance = surfaceUnchangedTo(front, n, noObjectives - 1);
      volume += tempVolume * (tempDistance - distance);
      distance = tempDistance;
      n = reduceNondominatedSet(front, n, noObjectives - 1, distance);
    }
    return volume;
  } // CalculateHypervolume

   
  /* merge two fronts */
  double [][] mergeFronts(double [][] front1, int  sizeFront1,
		 double [][] front2, int  sizeFront2, int  noObjectives)
  {
    int  i, j;
    int  noPoints;
    double [][] frontPtr;

    /* allocate memory */
    noPoints = sizeFront1 + sizeFront2;
    frontPtr = new double[noPoints][noObjectives];
    /* copy points */
    noPoints = 0;
    for (i = 0; i < sizeFront1; i++) {
      for (j = 0; j < noObjectives; j++)
        frontPtr[noPoints][j] = front1[i][j];
      noPoints++;
    }
    for (i = 0; i < sizeFront2; i++) {
      for (j = 0; j < noObjectives; j++)
        frontPtr[noPoints][j] = front2[i][j];
      noPoints++;
    }

    return frontPtr;
  } // MergeFronts

  /** 
   * Returns the hypevolume value of the paretoFront. This method call to the
   * calculate hipervolume one
   * @param paretoFront The pareto front
   * @param paretoTrueFront The true pareto front
   * @param numberOfObjectives Number of objectives of the pareto front
   */
  public double hypervolume_MaxMin(double [][] paretoFront,
                            double [][] paretoTrueFront,
                            int numberOfObjectives) {
    
    /**
     * Stores the maximum values of true pareto front.
     */
    double[] maximumValues;
    
    /**
     * Stores the minimum values of the true pareto front.
     */
    double[] minimumValues;
    
    /**
     * Stores the normalized front.
     */
    double [][] normalizedFront;
    
    /**
     * Stores the inverted front. Needed for minimization problems
     */
    double [][] invertedFront;
    
    // STEP 1. Obtain the maximum and minimum values of the Pareto front
    maximumValues = utils_.getMaximumValues(paretoTrueFront,numberOfObjectives);
    minimumValues = utils_.getMinimumValues(paretoTrueFront,numberOfObjectives);

    //todo: need double check, modified by mengxu 2024.5.20
    //as the true Pareto front is estimated. This way can avoid only one point in the estimated true Paret front
    for(int i=0; i<maximumValues.length; i++){
      maximumValues[i] = maximumValues[i]+1;
      minimumValues[i] = minimumValues[i]-1;
    }
    
    // STEP 2. Get the normalized front
    normalizedFront = utils_.getNormalizedFront(paretoFront,
                                                maximumValues,
                                                minimumValues);

    double[] referencePoint = {1.0, 1.0};
    return this.calculateHypervolumeNew(normalizedFront,referencePoint);
    
    // STEP 3. Inverse the pareto front. This is needed because of the original
    //metric by Zitzler is for maximization problems
    //todo: double check whether this is needed by mengxu 2024.5.20
//    invertedFront = utils_.invertedFront(normalizedFront);
//    // STEP4. The hypervolumen (control is passed to java version of Zitzler code)
//    return this.calculateHypervolume(invertedFront,invertedFront.length,numberOfObjectives);
  }// hypervolume


  public double hypervolume_MaxMin(double [][] paretoFront,
                            double [] maxObj,
                            double [] minObj) {

    /**
     * Stores the maximum values of true pareto front.
     */
    double[] maximumValues;

    /**
     * Stores the minimum values of the true pareto front.
     */
    double[] minimumValues;

    /**
     * Stores the normalized front.
     */
    double [][] normalizedFront;

    /**
     * Stores the inverted front. Needed for minimization problems
     */
    double [][] invertedFront;

    // STEP 1. Obtain the maximum and minimum values of the Pareto front
    maximumValues = maxObj;
    minimumValues = minObj;

    //todo: need double check, modified by mengxu 2024.5.20
    //as the true Pareto front is estimated. This way can avoid only one point in the estimated true Paret front
    for(int i=0; i<maximumValues.length; i++){
      maximumValues[i] = maximumValues[i]+1;
      minimumValues[i] = minimumValues[i]-1;
    }

    // STEP 2. Get the normalized front
    normalizedFront = utils_.getNormalizedFront(paretoFront,
            maximumValues,
            minimumValues);

//    if(normalizedFront.length>1){
//      System.out.println("Number in Pareto front: " + normalizedFront.length);
//    }

//    double[] referencePoint = {1.0, 1.0};//by mengxu
//    return this.calculateHypervolumeNew(normalizedFront,referencePoint);

    // STEP 3. Inverse the pareto front. This is needed because of the original
    //metric by Zitzler is for maximization problems
    //todo: double check whether this is needed by mengxu 2024.5.20
    invertedFront = utils_.invertedFront(normalizedFront);
    // STEP4. The hypervolumen (control is passed to java version of Zitzler code)
    int numberOfObjectives = maximumValues.length;
    return this.calculateHypervolume(invertedFront,invertedFront.length,numberOfObjectives);
  }// hypervolume


  public double hypervolume_Manual(double [][] paretoFront,
                                   double [] manualObj) {

    /**
     * Stores the maximum values of true pareto front.
     */
    double[] maximumValues = new double[manualObj.length];

    /**
     * Stores the normalized front.
     */
    double [][] normalizedFront;

    // STEP 1. Obtain the maximum and minimum values of the Pareto front
    for(int i=0; i<manualObj.length; i++){
      maximumValues[i] = manualObj[i]*10;
    }

    // STEP 2. Get the normalized front
    normalizedFront = utils_.getNormalizedFront_Manual(paretoFront,maximumValues);

    double[] referencePoint = {1.0, 1.0};
    double HV = this.calculateHypervolumeNew(normalizedFront,referencePoint);
    if(HV<0){
      HV = Double.POSITIVE_INFINITY;
    }
    return HV;
  }// hypervolume
  
  /**
   * This class can be invoqued from the command line. Three params are required:
   * 1) the name of the file containing the front,  
   * 2) the name of the file containig the true Pareto front
   * 3) the number of objectives
   */
  public static void main(String args[]) {
    if (args.length < 2) {
      System.err.println("Error using Hypervolume. Usage: \n java jmetal.qualityIndicator.Hypervolume " +
                         "<SolutionFrontFile> " +
                         "<TrueFrontFile> " + "<getNumberOfObjectives>");
      System.exit(1);
    }
    
    //Create a new instance of the metric
    Hypervolume qualityIndicator = new Hypervolume();
    //Read the front from the files
    double [][] solutionFront = qualityIndicator.utils_.readFront(args[0]);
    double [][] trueFront     = qualityIndicator.utils_.readFront(args[1]);

//    double [][] solutionFront = qualityIndicator.utils_.readFront(args[0]);
//    double [][] trueFront     = qualityIndicator.utils_.readFront(args[1]);
    
    //Obtain delta value
    double value = qualityIndicator.hypervolume_MaxMin(solutionFront, trueFront, Integer.parseInt(args[2]));
    
    System.out.println(value);  
  } // main
} // Hypervolume
