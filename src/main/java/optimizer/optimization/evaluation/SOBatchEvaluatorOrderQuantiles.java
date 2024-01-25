package optimizer.optimization.evaluation;

import optimizer.optimization.jenetics.jeneticsSO.SOJeneticsOptimization;
import optimizer.optimization.jenetics.jeneticsSO.SOSlotAllocationProblem;
import io.jenetics.EnumGene;
import io.jenetics.Phenotype;
import io.jenetics.util.Seq;

/**
 * BatchEvaluator for the fitness-methode ORDER_QUANTILES
 */
public class SOBatchEvaluatorOrderQuantiles extends SOBatchEvaluatorOrder {
    /**
     * @param problem      the slot allocation problem
     * @param optimization the Jenetics optimization run
     */
    public SOBatchEvaluatorOrderQuantiles(SOSlotAllocationProblem problem, SOJeneticsOptimization optimization) {
        super(problem, optimization);
    }

    /**
     * Returns the estimated population size according to the fitness precision
     * @param population the unevaluated population
     * @return the size
     */
    @Override
    protected int getEstimatedPopulationSize(Seq<Phenotype<EnumGene<Integer>, Integer>> population) {
        return this.optimization.getFitnessPrecision();
    }
}
