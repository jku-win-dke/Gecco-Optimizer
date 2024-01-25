package optimizer.optimization.evaluation;

import com.optimization.data.privacyEngine.dto.FitnessQuantilesDTO;
import optimizer.optimization.OptimizationMode;
import optimizer.optimization.jenetics.FitnessEvolutionStep;
import optimizer.optimization.jenetics.jeneticsSO.SOJeneticsOptimization;
import optimizer.optimization.jenetics.jeneticsSO.SOSlotAllocationProblem;
import io.jenetics.EnumGene;
import io.jenetics.Genotype;
import io.jenetics.Phenotype;
import io.jenetics.util.Seq;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * BatchEvaluator for the fitness-method FITNESS_RANGE_QUANTILES
 */
public class SOBatchEvaluatorFitnessRangeQuantiles extends SOBatchEvaluator {
    private static final Logger logger = LogManager.getLogger();

    /**
     * @param problem      the slot allocation problem
     * @param optimization the Jenetics optimization run
     */
    public SOBatchEvaluatorFitnessRangeQuantiles(SOSlotAllocationProblem problem, SOJeneticsOptimization optimization) {
        super(problem, optimization);
    }

    @Override
    protected List<Phenotype<EnumGene<Integer>, Integer>> estimatePopulation(Seq<Phenotype<EnumGene<Integer>, Integer>> population, FitnessEvolutionStep<Double> fitnessEvolutionStep, PopulationEvaluation<Integer> evaluation) {
        List<Phenotype<EnumGene<Integer>, Integer>> estimatedPopulation = null;
        List<Phenotype<EnumGene<Integer>, Integer>> estimatedPopulationStream;

        logger.debug("Actual maximum fitness of the population: " + evaluation.maxFitness);

        double minFitness = evaluation.maxFitness - (2 * Math.abs(evaluation.maxFitness)) - (Math.abs(evaluation.maxFitness) * 0.0001); // 0.0001 to avoid division by zero when calculating delta in linear estimator

        logger.debug("Estimated minimum fitness of the population: " + minFitness);

        if(this.optimization.getFitnessEstimator() != null) {
            int estimatedPopulationSize = this.optimization.getFitnessPrecision();
            logger.debug("Estimated population size: " + estimatedPopulationSize);

            // for this we probably need a change of the Privacy Engine interface when running in privacy-preserving mode
            logger.debug("Getting estimated fitness value from estimator: " + this.optimization.getFitnessEstimator().getClass());
            double[] estimatedFitnessValues =
                    this.optimization.getFitnessEstimator().estimateFitnessDistribution(estimatedPopulationSize, evaluation.maxFitness, minFitness);

            logger.debug("Assign the estimated fitness of the phenotype's fitness quantile");
            final Map<Phenotype<EnumGene<Integer>, Integer>, Integer> finalFitnessQuantilesPopulation = evaluation.fitnessQuantilesPopulation;
            estimatedPopulationStream = evaluation.evaluatedPopulation.stream()
                    .map(phenotype -> {
                                int fitness = (int) estimatedFitnessValues[finalFitnessQuantilesPopulation.get(phenotype)];
                                return phenotype.withFitness(fitness);
                            }
                    ).collect(Collectors.toList());
            logger.debug("Assigned the fitness quantiles");

            estimatedPopulation = estimatedPopulationStream.stream()
                    .sorted(Comparator.comparingInt(Phenotype::fitness))
                    .sorted(Comparator.reverseOrder())
                    .toList();

            if(!useActualFitnessValues && evaluation.maxFitness < this.optimization.getStatistics().getTheoreticalMaxFitness()){
                estimatedPopulation = estimatedPopulation.stream()
                        .map(phenotype -> phenotype.genotype().equals(evaluation.bestGenotype) ? phenotype.withFitness( (int) evaluation.maxFitness + 1) : phenotype)
                        .sorted(Comparator.comparingInt(Phenotype::fitness))
                        .sorted(Comparator.reverseOrder())
                        .collect(Collectors.toList());
            }

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

    @Override
    protected PopulationEvaluation<Integer> evaluatePopulation(Seq<Phenotype<EnumGene<Integer>, Integer>> population, FitnessEvolutionStep<Double> fitnessEvolutionStep) {
        return evaluatePopulationFitnessQuantiles(population, fitnessEvolutionStep);
    }


    /**
     * Takes the unevaluated population and assigns them to fitness-range-quantiles according to the fitness-precision
     * @param population the unevaluated population
     * @param fitnessEvolutionStep the evolution step of this generation
     * @return the mapping of the candidates to the fitness-range-quantiles
     */
    protected PopulationEvaluation<Integer> evaluatePopulationFitnessQuantiles(Seq<Phenotype<EnumGene<Integer>, Integer>> population, FitnessEvolutionStep<Double> fitnessEvolutionStep){
        final List<Phenotype<EnumGene<Integer>, Integer>> evaluatedPopulation;
        Map<Phenotype<EnumGene<Integer>, Integer>, Integer> fitnessQuantilesPopulation = null;
        Genotype<EnumGene<Integer>> bestGenotype = null;
        double maxFitness;

        if(this.optimization.getMode() == OptimizationMode.PRIVACY_PRESERVING) {
            logger.debug("Running in privacy-preserving mode: Evaluate the population using the Privacy Engine.");

            logger.debug("Convert population to format required by Privacy Engine.");
            Integer[][] input = this.convertPopulationToArray(population);

            logger.debug("Invoke the Privacy Engine service to get fitness quantiles of population.");
            FitnessQuantilesDTO fitnessQuantiles =
                    this.optimization.getPrivacyEngineService().computeFitnessQuantiles(this.optimization, input);

            // TODO convert between Privacy Engine's return format and format required by Optimizer
            evaluatedPopulation = null;

            maxFitness = fitnessQuantiles.getMaximum();

        } else {
            evaluatedPopulation = evaluatePopulationOrderNonPrivacy(population, fitnessEvolutionStep);
            maxFitness = evaluatedPopulation.get(0).fitness();
            bestGenotype = evaluatedPopulation.get(0).genotype();

            double actualMinFitness = evaluatedPopulation.get(evaluatedPopulation.size()-1).fitness();

            double difference = evaluatedPopulation.get(0).fitness() - actualMinFitness;

            double windowLength = (difference / this.optimization.getFitnessPrecision()) + 0.01;

            logger.debug("Diff: " + difference + ", windowLength: " + windowLength);

            Map<Integer, List<Phenotype<EnumGene<Integer>, Integer>>> quantilePopulations = evaluatedPopulation.stream()
                    .collect(Collectors.groupingBy(phenotype -> (int) ((evaluatedPopulation.get(0).fitness() - (double) phenotype.fitness()) / windowLength)));

            logger.debug("Map phenotype to quantile");
            fitnessQuantilesPopulation = new HashMap<>();

            for(int quantile : quantilePopulations.keySet()) {
                List<Phenotype<EnumGene<Integer>, Integer>> quantilePopulation = quantilePopulations.get(quantile);

                for(Phenotype<EnumGene<Integer>, Integer> phenotype : quantilePopulation) {
                    fitnessQuantilesPopulation.put(phenotype, quantile);
                }
            }

            logger.debug("Mapped phenotypes to quantile");
        }

        PopulationEvaluation evaluation = new PopulationEvaluation();
        evaluation.evaluatedPopulation = evaluatedPopulation;
        evaluation.fitnessQuantilesPopulation = fitnessQuantilesPopulation;
        evaluation.bestGenotype = bestGenotype;
        evaluation.maxFitness = maxFitness;
        return evaluation;
    }
}
