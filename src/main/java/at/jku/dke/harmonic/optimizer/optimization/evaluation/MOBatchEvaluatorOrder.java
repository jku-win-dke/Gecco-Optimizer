package at.jku.dke.harmonic.optimizer.optimization.evaluation;

import at.jku.dke.harmonic.optimizer.optimization.OptimizationMode;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.FitnessEvolutionStep;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsMO.MOJeneticsOptimization;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsMO.MOSlotAllocationProblem;
import io.jenetics.EnumGene;
import io.jenetics.Phenotype;
import io.jenetics.ext.moea.Vec;
import io.jenetics.util.Seq;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.stream.Collectors;

public class MOBatchEvaluatorOrder extends MOBatchEvaluator{

    private static final Logger logger = LogManager.getLogger();

    /**
     * @param problem      the slot allocation problem
     * @param optimization the Jenetics optimization run
     */
    public MOBatchEvaluatorOrder(MOSlotAllocationProblem problem, MOJeneticsOptimization optimization) {
        super(problem, optimization);
    }

    /**
     * Estimates the population according to the order of the candidates and the estimated fitness-values according to the Estimator-type and the maximum and minimum fitness
     *
     * @param population the unevaluated population
     * @param fitnessEvolutionStep the evolution step of this generation
     * @param evaluation the result of the evaluation
     * @return the estimated population
     */
    @Override
    protected List<Phenotype<EnumGene<Integer>, Vec<int[]>>> estimatePopulation(Seq<Phenotype<EnumGene<Integer>, Vec<int[]>>> population, FitnessEvolutionStep<double[]> fitnessEvolutionStep, PopulationEvaluation<Vec<int[]>> evaluation) {
        List<Phenotype<EnumGene<Integer>, Vec<int[]>>> estimatedPopulation = null;
        List<Phenotype<EnumGene<Integer>, Vec<int[]>>> estimatedPopulationStream;

        logger.debug("Actual maximum fitness of the population: " + evaluation.maxFitness);

        if(this.optimization.getFitnessEstimator() != null) {
            // Order and Order-Quantiles estimation only differ regarding the estimated population size
            int estimatedPopulationSize = getEstimatedPopulationSize(population);

            logger.debug("Getting estimated fitness value from estimator: " + this.optimization.getFitnessEstimator().getClass());
            double[] estimatedFitnessValuesOne =
                    this.optimization.getFitnessEstimator().estimateFitnessDistribution(estimatedPopulationSize, evaluation.maxFitness);


            logger.debug("Assign each solution in the population an estimated fitness value.");
            final int finalEstimatedPopulationSize = estimatedPopulationSize;

            if(secondObfuscated){
                double[] estimatedFitnessValuesTwo =
                        this.optimization.getFitnessEstimator().estimateFitnessDistribution(estimatedPopulationSize, evaluation.maxFitnessTwo);

                List<Phenotype<EnumGene<Integer>, Vec<int[]>>> evaluationSortedBySecond = evaluation.evaluatedPopulation.stream()
                        .sorted((p1, p2) -> Integer.compare(p2.fitness().data()[1], p1.fitness().data()[1]))
                        .toList();
                estimatedPopulationStream = evaluation.evaluatedPopulation.stream()
                        .map(phenotype -> {
                            int positionOne = (int)((double) (evaluation.evaluatedPopulation.indexOf(phenotype)) / (double) (population.size()) * finalEstimatedPopulationSize);
                            int positionTwo = (int)((double) (evaluationSortedBySecond.indexOf(phenotype)) / (double) (population.size()) * finalEstimatedPopulationSize);
                            Vec<int[]> fitness = Vec.of(
                                    (int) estimatedFitnessValuesOne[positionOne],
                                    (int) estimatedFitnessValuesTwo[positionTwo]
                            );
                            return phenotype.withFitness(fitness);
                        }).collect(Collectors.toList());
            } else {
                estimatedPopulationStream = evaluation.evaluatedPopulation.stream()
                        .map(phenotype -> {
                            int positionOne = (int)((double) (evaluation.evaluatedPopulation.indexOf(phenotype)) / (double) (population.size()) * finalEstimatedPopulationSize);
                            Vec<int[]> fitness = Vec.of(
                                    (int) estimatedFitnessValuesOne[positionOne],
                                    phenotype.fitness().data()[1]
                            );
                            return phenotype.withFitness(fitness);
                        }).collect(Collectors.toList());
            }

            estimatedPopulation = estimatedPopulationStream.stream()
                    .sorted((p1, p2) -> Integer.compare(p2.fitness().data()[0], p1.fitness().data()[0]))
                    .toList();

            logger.debug("Assigned estimated fitness values.");
        } else {
            logger.debug("No estimator specified. Using exact fitness (if available).");

            if(this.optimization.getMode() == OptimizationMode.NON_PRIVACY_PRESERVING){
                logger.debug("Running in non-privacy-preserving mode. Exact fitness values available.");
                estimatedPopulation = evaluation.evaluatedPopulation;
            }
        }

        return estimatedPopulation;
    }

    /**
     * Evaluates the population
     *
     * @param population the unevaluated population
     * @param fitnessEvolutionStep the evolution step for this generation
     * @return the ordered population
     */
    @Override
    protected PopulationEvaluation<Vec<int[]>> evaluatePopulation(Seq<Phenotype<EnumGene<Integer>, Vec<int[]>>> population, FitnessEvolutionStep<double[]> fitnessEvolutionStep) {
        return evaluatePopulationOrder(population, fitnessEvolutionStep);
    }

    /**
     * Returns the estimated population size
     * @param population the unevaluated population
     * @return the size
     */
    protected int getEstimatedPopulationSize(Seq<Phenotype<EnumGene<Integer>, Vec<int[]>>> population) {
        return population.size();
    }
}
