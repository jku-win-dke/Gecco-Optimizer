package at.jku.dke.harmonic.optimizer.optimization.evaluation;

import at.jku.dke.harmonic.optimizer.optimization.OptimizationMode;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.FitnessEvolutionStep;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsSO.SOJeneticsOptimization;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsSO.SOSlotAllocationProblem;
import io.jenetics.EnumGene;
import io.jenetics.Genotype;
import io.jenetics.Phenotype;
import io.jenetics.util.Seq;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * BatchEvaluator for the fitness-method ORDER
 */
public class SOBatchEvaluatorOrder extends SOBatchEvaluator {
    private static final Logger logger = LogManager.getLogger();

    /**
     * @param problem      the slot allocation problem
     * @param optimization the Jenetics optimization run
     */
    public SOBatchEvaluatorOrder(SOSlotAllocationProblem problem, SOJeneticsOptimization optimization) {
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
    protected List<Phenotype<EnumGene<Integer>, Integer>> estimatePopulation(Seq<Phenotype<EnumGene<Integer>, Integer>> population, FitnessEvolutionStep<Double> fitnessEvolutionStep, PopulationEvaluation<Integer> evaluation) {
        List<Phenotype<EnumGene<Integer>, Integer>> estimatedPopulation = null;
        List<Phenotype<EnumGene<Integer>, Integer>> estimatedPopulationStream = null;

        logger.debug("Actual maximum fitness of the population: " + evaluation.maxFitness);

        if(this.optimization.getFitnessEstimator() != null){
            // Order and Order-Quantiles estimation only differ regarding the estimated population size
            int estimatedPopulationSize = getEstimatedPopulationSize(population);

            logger.debug("Getting estimated fitness value from estimator: " + this.optimization.getFitnessEstimator().getClass());
            double[] estimatedFitnessValues =
                    this.optimization.getFitnessEstimator().estimateFitnessDistribution(estimatedPopulationSize, evaluation.maxFitness);

            logger.debug("Assign each solution in the population an estimated fitness value.");
            final int finalEstimatedPopulationSize = estimatedPopulationSize;

            // get the fitness value at the candidate's position
            estimatedPopulationStream = evaluation.evaluatedPopulation.stream()
                    .map(phenotype -> phenotype.withFitness((int) estimatedFitnessValues[
                            (int)((double) (evaluation.evaluatedPopulation.indexOf(phenotype)) / (double) (population.size()) * finalEstimatedPopulationSize)
                            ])).collect(Collectors.toList());

            estimatedPopulation = estimatedPopulationStream.stream()
                    .sorted(Comparator.comparingInt(Phenotype::fitness))
                    .sorted(Comparator.reverseOrder())
                    .toList();

            logger.debug("Assigned estimated fitness values.");
        } else {
            logger.debug("No estimator specified. Using exact fitness (if available).");

            if(this.optimization.getMode() == OptimizationMode.NON_PRIVACY_PRESERVING){
                logger.debug("Running in non-privacy-preserving mode. Exact fitness values available.");
                estimatedPopulation = evaluation.evaluatedPopulation;
            }
        }
        return  estimatedPopulation;
    }

    /**
     * Returns the estimated population size
     * @param population the unevaluated population
     * @return the size
     */
    protected int getEstimatedPopulationSize(Seq<Phenotype<EnumGene<Integer>, Integer>> population) {
        return population.size();
    }

    /**
     * Evaluates the population
     *
     * @param population the unevaluated population
     * @param fitnessEvolutionStep the evolution step for this generation
     * @return the ordered population
     */
    @Override
    protected PopulationEvaluation<Integer> evaluatePopulation(Seq<Phenotype<EnumGene<Integer>, Integer>> population, FitnessEvolutionStep<Double> fitnessEvolutionStep) {
       return evaluatePopulationOrder(population, fitnessEvolutionStep);
    }
}
