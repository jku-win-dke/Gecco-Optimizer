package optimizer.optimization.evaluation;

import optimizer.optimization.jenetics.jeneticsMO.MOJeneticsOptimization;
import optimizer.optimization.jenetics.jeneticsMO.MOSlotAllocationProblem;
import io.jenetics.ext.moea.Vec;


public class MOBatchEvaluatorAboveAbsolute extends MOBatchEvaluatorAbove{

    public MOBatchEvaluatorAboveAbsolute(MOSlotAllocationProblem problem, MOJeneticsOptimization optimization) {
        super(problem, optimization);
    }

    @Override
    protected double[] getThreshold(PopulationEvaluation<Vec<int[]>> evaluation) {
        double[] maxima = new double[2];
        double factor = this.optimization.getFitnessPrecision() / 100.0;
        if(evaluation.maxFitness < 0){
            maxima[0] = evaluation.maxFitness * (1 + (1 - factor));
        } else {
            maxima[0] = evaluation.maxFitness * factor;
        }

        if(evaluation.maxFitnessTwo < 0){
            maxima[1] = evaluation.maxFitnessTwo * (1 + (1 - factor));
        } else {
            maxima[1] = evaluation.maxFitnessTwo * factor;
        }
        return maxima;

    }
}
