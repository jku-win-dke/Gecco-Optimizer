package at.jku.dke.harmonic.optimizer.optimization.evaluation;

import at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsMO.MOJeneticsOptimization;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsMO.MOSlotAllocationProblem;
import io.jenetics.EnumGene;
import io.jenetics.Phenotype;
import io.jenetics.ext.moea.Vec;
import io.jenetics.util.Seq;

public class MOBatchEvaluatorOrderQuantiles extends MOBatchEvaluatorOrder{
    /**
     * @param problem      the slot allocation problem
     * @param optimization the Jenetics optimization run
     */
    public MOBatchEvaluatorOrderQuantiles(MOSlotAllocationProblem problem, MOJeneticsOptimization optimization) {
        super(problem, optimization);
    }

    /**
     * Returns the estimated population size according to the fitness precision
     * @param population the unevaluated population
     * @return the size
     */
    @Override
    protected int getEstimatedPopulationSize(Seq<Phenotype<EnumGene<Integer>, Vec<int[]>>> population) {
        return this.optimization.getFitnessPrecision();
    }
}
