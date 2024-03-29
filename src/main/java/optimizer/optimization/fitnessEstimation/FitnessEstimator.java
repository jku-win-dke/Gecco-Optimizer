package optimizer.optimization.fitnessEstimation;

public abstract class FitnessEstimator {
    public double[] estimateFitnessDistribution(int populationSize, double maxFitness) {
        return estimateFitnessDistribution(populationSize, maxFitness, 0);
    }

    public abstract double[] estimateFitnessDistribution(int populationSize, double maxFitness, double minFitness);
}
