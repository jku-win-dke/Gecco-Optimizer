package at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsMLA;

import at.jku.dke.harmonic.optimizer.domain.FlightMO;
import at.jku.dke.harmonic.optimizer.domain.Slot;
import at.jku.dke.harmonic.optimizer.optimization.evaluation.BatchEvaluatorFactory;
import at.jku.dke.harmonic.optimizer.optimization.evaluation.MOBatchEvaluator;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsMO.MOJeneticsOptimization;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsMO.MOJeneticsOptimizationConfiguration;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsMO.MOSlotAllocationProblem;
import io.jenetics.EnumGene;
import io.jenetics.Phenotype;
import io.jenetics.engine.EvolutionStatistics;
import io.jenetics.ext.moea.Vec;
import io.jenetics.util.ISeq;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Map;

public class MLAJeneticsOptimization extends MOJeneticsOptimization {

    private static final Logger logger = LogManager.getLogger();

    public MLAJeneticsOptimization(FlightMO[] flights, Slot[] slots) {
        super(flights, slots);
    }

    @Override
    protected MOBatchEvaluator createEvaluator(){
        return BatchEvaluatorFactory.getMLAEvaluator(problem, this);
    }

    @Override
    protected MOJeneticsOptimizationConfiguration createNewConfig() {
        return new MLAJeneticsOptimizationConfiguration();
    }

    @Override
    protected Map.Entry<Map<FlightMO, Slot>, int[]> selectPointOnParetoFront(Map<Map<FlightMO, Slot>, int[]> paretoFrontResult, int[][] fitnessValues) {
        return paretoFrontResult.entrySet().stream()
                .sorted(Comparator.comparingInt(p -> p.getValue()[0]))
                .findFirst()
                .orElseThrow();
    }

    @Override
    protected void logParetoFront(int[][] paretoFrontResult, int[] selectedPoint) {
        logger.info("Best Performance: \t" + selectedPoint[0]);
    }

    @Override
    protected void setAndPrintStatistics(EvolutionStatistics<Vec<int[]>,?> statistics,
                                         ISeq<Phenotype<EnumGene<Integer>, Vec<int[]>>> paretoFrontResult,
                                         MOSlotAllocationProblem problem,
                                         int[] selectedPoint) {
        logger.info("Setting statistics for this optimization."); // already initialized in constructor
        this.getStatistics().setTimeFinished(LocalDateTime.now());
        this.getStatistics().setIterations((int) statistics.altered().count());
        this.getStatistics().setFitnessFunctionInvocations(problem.getFitnessFunctionApplications());

        double balanceRatio = Math.abs((double) this.statistics.getMaximumFitness()/this.statistics.getTheoreticalMaxFitness() -
                (double) this.statistics.getMaximumFitnessTwo()/this.statistics.getTheoreticalMaxFitnessTwo());
        this.statistics.setBalanceRatio(balanceRatio);

        logger.info("Fitness of best solution: " + this.getStatistics().getResultFitness());
        logger.info("Number of generations: " + this.getStatistics().getIterations());
        logger.info("Number of fitness function invocations: " + this.getStatistics().getFitnessFunctionInvocations());
    }



    @Override
    protected void changeTheoreticalMaxValues(double[][] estimatedParetoFront, int[] value) {
        double[] nearestPoint = null;
        double maxDistance = Double.MAX_VALUE;
        for(double[] point: estimatedParetoFront) {
            double distancePoint = Math.pow(point[0]-this.statistics.getMaximumFitness(), 2)
                    + Math.pow(point[1]-this.statistics.getMaximumFitnessTwo(), 2);
            if(distancePoint < maxDistance &&
                    point[0] > this.statistics.getMaximumFitness() &&
                    point[1] > this.statistics.getMaximumFitnessTwo()){
                nearestPoint = point;
                maxDistance = distancePoint;
            }
        }
        if(nearestPoint != null) {
            this.getStatistics().setTheoreticalMaxFitness((int) nearestPoint[0]);
            this.getStatistics().setTheoreticalMaxFitnessTwo((int) nearestPoint[1]);
        } else {
            // optimization result is on the pareto front
            this.getStatistics().setTheoreticalMaxFitness(this.statistics.getMaximumFitness());
            this.getStatistics().setTheoreticalMaxFitnessTwo(this.statistics.getMaximumFitnessTwo());
        }

    }
}
