package at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsMO;

import at.jku.dke.harmonic.optimizer.optimization.jenetics.JeneticsOptimizationStatistics;

public class MOJeneticsOptimizationStatistics extends JeneticsOptimizationStatistics<double[]> {
    private int maximumFitnessTwo = Integer.MIN_VALUE;
    private int theoreticalMaxFitnessTwo;

    private double[][] estimatedParetoFront;

    private double balanceRatio;

    private int[][] paretoFront;

    private int[] selectedPoint;

    public int getMaximumFitnessTwo() {
        return maximumFitnessTwo;
    }

    public void setMaximumFitnessTwo(int maximumFitnessTwo) {
        this.maximumFitnessTwo = maximumFitnessTwo;
    }

    public int[][] getParetoFront() {
        return paretoFront;
    }

    public void setParetoFront(int[][] paretoFront) {
        this.paretoFront = paretoFront;
    }

    public int getTheoreticalMaxFitnessTwo() {
        return theoreticalMaxFitnessTwo;
    }

    public void setTheoreticalMaxFitnessTwo(int theoreticalMaxFitnessTwo) {
        this.theoreticalMaxFitnessTwo = theoreticalMaxFitnessTwo;
    }

    public double[][] getEstimatedParetoFront() {
        return estimatedParetoFront;
    }

    public void setEstimatedParetoFront(double[][] estimatedParetoFront) {
        this.estimatedParetoFront = estimatedParetoFront;
    }

    public int[] getSelectedPoint() {
        return selectedPoint;
    }

    public void setSelectedPoint(int[] selectedPoint) {
        this.selectedPoint = selectedPoint;
    }

    public double getBalanceRatio() {
        return balanceRatio;
    }

    public void setBalanceRatio(double balanceRatio) {
        this.balanceRatio = balanceRatio;
    }
}
