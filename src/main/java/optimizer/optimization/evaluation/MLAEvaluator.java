package optimizer.optimization.evaluation;

import optimizer.optimization.OptimizationMode;
import optimizer.optimization.jenetics.FitnessEvolutionStep;
import optimizer.optimization.jenetics.jeneticsMO.MOJeneticsOptimization;
import optimizer.optimization.jenetics.jeneticsMO.MOSlotAllocationProblem;
import io.jenetics.EnumGene;
import io.jenetics.Phenotype;
import io.jenetics.ext.moea.Vec;
import io.jenetics.util.Seq;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MLAEvaluator extends MOBatchEvaluator {

    private static final Logger logger = LogManager.getLogger();

    public MLAEvaluator(MOSlotAllocationProblem problem, MOJeneticsOptimization optimization) {
        super(problem, optimization);
    }

    /**
     * When using the actual fitness values, the evaluated population is used for the estimation step.
     *
     * @param population the unevaluated population
     * @param fitnessEvolutionStep the evolution step of this generation
     * @param evaluation result of the evaluation
     * @return the estimated population
     */
    @Override
    protected List<Phenotype<EnumGene<Integer>, Vec<int[]>>> estimatePopulation(Seq<Phenotype<EnumGene<Integer>, Vec<int[]>>> population, FitnessEvolutionStep<double[]> fitnessEvolutionStep, PopulationEvaluation<Vec<int[]>> evaluation) {

        List<Phenotype<EnumGene<Integer>, Vec<int[]>>> estimatedPopulation = new LinkedList<>();

        if(this.optimization.getFitnessEstimator() != null) {
            final int quantiles = this.optimization.getFitnessPrecision();

            logger.debug("Actual maximum fitness of the population: " + evaluation.maxFitness);

            List<Phenotype<EnumGene<Integer>, Vec<int[]>>> sortedPopulation = evaluation.evaluatedPopulation.stream()
                    .sorted(Comparator.comparingInt(p -> p.fitness().data()[0])).toList();

            double[] estimatedFitnessValues =
                    this.optimization.getFitnessEstimator().estimateFitnessDistribution(
                            population.size(), sortedPopulation.get(population.size()-1).fitness().data()[0]);

            Map<Integer, List<Phenotype<EnumGene<Integer>, Vec<int[]>>>> assignedQuantiles = evaluation.evaluatedPopulation.stream()
                    .collect(Collectors.groupingBy(
                            p -> (sortedPopulation.indexOf(p) * quantiles) / sortedPopulation.size()
                    ));

            List<Phenotype<EnumGene<Integer>, Vec<int[]>>> reorderedPhenotypes = assignedQuantiles.entrySet()
                    .stream().peek(e -> e.setValue(e.getValue().stream()
                            .sorted((p1, p2) -> p2.fitness().data()[1] - p1.fitness().data()[1])
                            .toList()))
                    .sorted((e1, e2) -> e2.getKey().compareTo(e1.getKey()))
                    .flatMap(e -> e.getValue().stream())
                    .toList();

            // TODO set the maximum fitness values properly
            this.optimization.getStatistics().setMaximumFitness(reorderedPhenotypes.get(0).fitness().data()[0]);
            this.optimization.getStatistics().setMaximumFitnessTwo(reorderedPhenotypes.get(0).fitness().data()[1]);

                for(int i = 0; i < population.size(); i++) {
                estimatedPopulation.add(reorderedPhenotypes.get(i)
                        .withFitness(Vec.of((int)Math.round(estimatedFitnessValues[i]))));
            }
        } else {
            logger.debug("No estimator specified. Using exact fitness (if available).");

            if(this.optimization.getMode() == OptimizationMode.NON_PRIVACY_PRESERVING){
                logger.debug("Running in non-privacy-preserving mode. Exact fitness values available.");
                estimatedPopulation = evaluation.evaluatedPopulation;
            }
        }
        return estimatedPopulation;
    }

    @Override
    protected PopulationEvaluation<Vec<int[]>> evaluatePopulation(Seq<Phenotype<EnumGene<Integer>, Vec<int[]>>> population, FitnessEvolutionStep<double[]> fitnessEvolutionStep) {
        return evaluatePopulationOrder(population, fitnessEvolutionStep);
    }

    @Override
    protected void setFitnessEstimationStep(List<Phenotype<EnumGene<Integer>, Vec<int[]>>> estimatedPopulation,
                                            FitnessEvolutionStep<double[]> fitnessEvolutionStep,
                                            List<Phenotype<EnumGene<Integer>, Vec<int[]>>> evaluatedPopulation){
        if(fitnessEvolutionStep != null) {
            double estimatedBestValue = estimatedPopulation.get(0).fitness().data()[0];
            fitnessEvolutionStep.setEstimatedPopulation(new double[][]{{estimatedBestValue}});

            setEvolutionStep(fitnessEvolutionStep, evaluatedPopulation);
            logger.debug("Size of estimated population: " + fitnessEvolutionStep.getEstimatedPopulation().length);
        }
    }
}
