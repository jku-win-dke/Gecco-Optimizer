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
import java.util.stream.IntStream;

public class SOBatchEvaluatorActualValues extends SOBatchEvaluator {
    private static final Logger logger = LogManager.getLogger();

    /**
     * @param problem      the slot allocation problem
     * @param optimization the Jenetics optimization run
     */
    public SOBatchEvaluatorActualValues(SOSlotAllocationProblem problem, SOJeneticsOptimization optimization) {
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
    protected List<Phenotype<EnumGene<Integer>, Integer>> estimatePopulation(Seq<Phenotype<EnumGene<Integer>, Integer>> population, FitnessEvolutionStep<Double> fitnessEvolutionStep, PopulationEvaluation<Integer> evaluation) {
        return evaluation.evaluatedPopulation;
    }

    /**
     * Evaluates the population using actual fitness values for each individual.
     * When running in {@link OptimizationMode#NON_PRIVACY_PRESERVING} mode, {@link SOBatchEvaluator#evaluatePopulationOrder(Seq, FitnessEvolutionStep)} is used.
     *
     * @param population the unevaluated population
     * @param fitnessEvolutionStep the evolution step for this generation
     * @return the evaluated population
     */
    @Override
    protected PopulationEvaluation<Integer> evaluatePopulation(Seq<Phenotype<EnumGene<Integer>, Integer>> population, FitnessEvolutionStep<Double> fitnessEvolutionStep) {
        List<Phenotype<EnumGene<Integer>, Integer>> evaluatedPopulation;

        if(this.optimization.getMode() == OptimizationMode.PRIVACY_PRESERVING) {
            logger.debug("Running in privacy-preserving mode: Evaluate the population using the Privacy Engine.");

            logger.debug("Convert population to format required by Privacy Engine.");
            Integer[][] input = this.convertPopulationToArray(population);

            logger.debug("Invoke the Privacy Engine service to evaluate population.");
            Integer[] fitnessValues = this.optimization.getPrivacyEngineService().computeActualFitnessValues(optimization, input);

            logger.debug("Convert the evaluated population received from the Privacy Engine to the format required by Jenetics.");
            evaluatedPopulation = IntStream
                    .range(0, fitnessValues.length)
                    .mapToObj(i -> population.get(i).withFitness(fitnessValues[i]))
                    .sorted(Comparator.comparingInt(Phenotype::fitness))
                    .sorted(Comparator.reverseOrder())
                    .toList();

            logger.debug("Maximum fitness in generation according to Privacy Engine is " + evaluatedPopulation.get(0).fitness() + ".");
        } else {
            // order of population is used as it provides all fitness values in NON_PRIVACY_PRESERVING mode
            return evaluatePopulationOrder(population, fitnessEvolutionStep);
        }

        PopulationEvaluation evaluation = new PopulationEvaluation();
        evaluation.evaluatedPopulation = evaluatedPopulation;
        return evaluation;
    }
}
