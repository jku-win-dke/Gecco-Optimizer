package optimizer.optimization.jenetics.jeneticsSO;

import optimizer.optimization.jenetics.JeneticsOptimizationStatistics;

public class SOJeneticsOptimizationStatistics extends JeneticsOptimizationStatistics<Double> {
    private long solutionGeneration;

    public void setSolutionGeneration(long generation) {
        this.solutionGeneration = generation;
    }

    public long getSolutionGeneration() {
        return this.solutionGeneration;
    }
}
