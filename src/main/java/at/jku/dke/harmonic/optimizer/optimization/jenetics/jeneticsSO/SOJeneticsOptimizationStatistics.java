package at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsSO;

import at.jku.dke.harmonic.optimizer.optimization.jenetics.JeneticsOptimizationStatistics;

public class SOJeneticsOptimizationStatistics extends JeneticsOptimizationStatistics<Double> {
    private long solutionGeneration;

    public void setSolutionGeneration(long generation) {
        this.solutionGeneration = generation;
    }

    public long getSolutionGeneration() {
        return this.solutionGeneration;
    }
}
