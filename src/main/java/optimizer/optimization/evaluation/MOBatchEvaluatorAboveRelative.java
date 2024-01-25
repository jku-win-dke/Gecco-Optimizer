package optimizer.optimization.evaluation;

import optimizer.optimization.jenetics.jeneticsMO.MOJeneticsOptimization;
import optimizer.optimization.jenetics.jeneticsMO.MOSlotAllocationProblem;
import io.jenetics.ext.moea.Vec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MOBatchEvaluatorAboveRelative extends MOBatchEvaluatorAbove{
    public MOBatchEvaluatorAboveRelative(MOSlotAllocationProblem problem, MOJeneticsOptimization optimization) {
        super(problem, optimization);
    }

    @Override
    protected double[] getThreshold(PopulationEvaluation<Vec<int[]>> evaluation) {
        List<Double> valuesFirst = evaluation.evaluatedPopulation.stream()
                .map(p->(double)p.fitness().data()[0])
                .sorted(Comparator.reverseOrder())
                .toList();
        List<Double> valuesSecond = evaluation.evaluatedPopulation.stream()
                .map(p->(double)p.fitness().data()[1])
                .sorted(Comparator.reverseOrder())
                .toList();
        double percentile = (100 - this.optimization.getFitnessPrecision());
        valuesFirst = new ArrayList<>(valuesFirst);
        valuesSecond = new ArrayList<>(valuesSecond);
        int index = (int) Math.ceil((percentile / 100) * valuesFirst.size());
        return new double[]{valuesFirst.get(index), valuesSecond.get(index)};
    }
}
