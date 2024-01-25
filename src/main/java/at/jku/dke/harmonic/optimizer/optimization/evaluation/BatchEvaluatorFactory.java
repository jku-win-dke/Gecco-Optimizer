package at.jku.dke.harmonic.optimizer.optimization.evaluation;

import at.jku.dke.harmonic.optimizer.optimization.FitnessMethod;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsMO.MOJeneticsOptimization;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsMO.MOSlotAllocationProblem;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsSO.SOJeneticsOptimization;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsSO.SOSlotAllocationProblem;

/**
 * Utility class with the factory-method returning the {@link SOBatchEvaluator} according to the {@link FitnessMethod}.
 */
public class BatchEvaluatorFactory {
    private BatchEvaluatorFactory(){
        // utility class
    }

    /**
     * Factory method that returns the {@link SOBatchEvaluator} according to the {@link FitnessMethod}.
     * @param fitnessMethod the fitness method
     * @param problem the problem definition
     * @param optimization the optimization
     * @return the evaluator
     */
    public static SOBatchEvaluator getSOEvaluator(FitnessMethod fitnessMethod, SOSlotAllocationProblem problem, SOJeneticsOptimization optimization){
        return switch(fitnessMethod){
            case ORDER_QUANTILES -> new SOBatchEvaluatorOrderQuantiles(problem, optimization);
            case FITNESS_RANGE_QUANTILES -> new SOBatchEvaluatorFitnessRangeQuantiles(problem, optimization);
            case ABOVE_ABSOLUTE_THRESHOLD -> new SOBatchEvaluatorAboveAbsolute(problem, optimization);
            case ABOVE_RELATIVE_THRESHOLD -> new SOBatchEvaluatorAboveRelative(problem, optimization);
            case ACTUAL_VALUES -> new SOBatchEvaluatorActualValues(problem, optimization);
            default -> new SOBatchEvaluatorOrder(problem, optimization);
        };
    }

    public static MOBatchEvaluator getMOEvaluator(FitnessMethod fitnessMethod, MOSlotAllocationProblem problem, MOJeneticsOptimization optimization){
        return switch(fitnessMethod){
            case ACTUAL_VALUES -> new MOBatchEvaluatorActualValues(problem, optimization);
            case ORDER_QUANTILES ->  new MOBatchEvaluatorOrderQuantiles(problem, optimization);
            case FITNESS_RANGE_QUANTILES -> new MOBatchEvaluatorFitnessRangeQuantiles(problem, optimization);
            case ABOVE_ABSOLUTE_THRESHOLD -> new MOBatchEvaluatorAboveAbsolute(problem, optimization);
            case ABOVE_RELATIVE_THRESHOLD -> new MOBatchEvaluatorAboveRelative(problem, optimization);
            default -> new MOBatchEvaluatorOrder(problem, optimization);
        };
    }

    public static MLAEvaluator getMLAEvaluator(MOSlotAllocationProblem problem, MOJeneticsOptimization optimization) {
        return new MLAEvaluator(problem, optimization);
    }
}
