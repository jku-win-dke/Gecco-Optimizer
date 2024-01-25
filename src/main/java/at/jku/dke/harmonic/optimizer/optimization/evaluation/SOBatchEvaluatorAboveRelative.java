package at.jku.dke.harmonic.optimizer.optimization.evaluation;

import at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsSO.SOJeneticsOptimization;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsSO.SOSlotAllocationProblem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * BatchEvaluator for the fitness-method ABOVE_RELATIVE_THRESHOLD
 */
public class SOBatchEvaluatorAboveRelative extends SOBatchEvaluatorAbove {
    /**
     * @param problem      the slot allocation problem
     * @param optimization the Jenetics optimization run
     */
    public SOBatchEvaluatorAboveRelative(SOSlotAllocationProblem problem, SOJeneticsOptimization optimization) {
        super(problem, optimization);
    }

    /**
     * Returns the threshold for the evaluation as percentile of the fitness-values according to the configured fitness-precision
     * @param evaluation the evaluated population
     * @return the percentile
     */
    @Override
    protected double getThreshold(PopulationEvaluation<Integer> evaluation) {
        return percentile(evaluation.evaluatedPopulation.stream().map(ph -> (double) ph.fitness()).toList(), (100 - this.optimization.getFitnessPrecision()));
    }

    /**
     * Utility method that calculates a percentile
     * @param values the values
     * @param percentile the desired percentile
     * @return the percentile
     */
    protected static double percentile(List<Double> values, double percentile) {
        values = new ArrayList<>(values);
        Collections.sort(values);
        int index = (int) Math.ceil((percentile / 100) * values.size());
        return values.get(index);
    }
}
