package at.jku.dke.harmonic.optimizer.optimization.evaluation;

import at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsSO.SOJeneticsOptimization;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsSO.SOSlotAllocationProblem;
import io.jenetics.Phenotype;

import java.util.Comparator;

/**
 * BatchEvaluator for the fitness-method ABOVE_ABSOLUTE_THRESHOLD
 */
public class SOBatchEvaluatorAboveAbsolute extends SOBatchEvaluatorAbove {
    /**
     * @param problem      the slot allocation problem
     * @param optimization the Jenetics optimization run
     */
    public SOBatchEvaluatorAboveAbsolute(SOSlotAllocationProblem problem, SOJeneticsOptimization optimization) {
        super(problem, optimization);
    }

    /**
     * Returns the threshold according to the fitness-precision and the maximum fitness.
     * @param evaluation the evaluated population
     * @return the threshold
     */
    @Override
    protected double getThreshold(PopulationEvaluation<Integer> evaluation) {
        int maximum;
        var phenotype = evaluation.evaluatedPopulation.stream().max(Comparator.comparingInt(Phenotype::fitness));
        maximum = phenotype.isPresent() ? phenotype.get().fitness() : (int) evaluation.maxFitness;
        if(maximum < 0){
            return maximum * (1 + (1 - this.optimization.getFitnessPrecision() / 100.0));
        }
        return maximum * (this.optimization.getFitnessPrecision() / 100.0);
    }
}
