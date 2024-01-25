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

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

public class MOBatchEvaluatorActualValues extends MOBatchEvaluator{

    private static final Logger logger = LogManager.getLogger();


    public MOBatchEvaluatorActualValues(MOSlotAllocationProblem problem, MOJeneticsOptimization optimization) {
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
        return evaluation.evaluatedPopulation;
    }

    @Override
    protected PopulationEvaluation<Vec<int[]>> evaluatePopulation(Seq<Phenotype<EnumGene<Integer>, Vec<int[]>>> population, FitnessEvolutionStep<double[]> fitnessEvolutionStep) {
        List<Phenotype<EnumGene<Integer>, Vec<int[]>>> evaluatedPopulation;

        if(this.optimization.getMode() == OptimizationMode.PRIVACY_PRESERVING) {
            logger.debug("Running in privacy-preserving mode: Evaluate the population using the Privacy Engine.");

            logger.debug("Convert population to format required by Privacy Engine.");
            Integer[][] input = this.convertPopulationToArray(population);

            logger.debug("Invoke the Privacy Engine service to evaluate population.");
            Integer[] fitnessValues = this.optimization.getPrivacyEngineService().computeActualFitnessValues(optimization, input);

            logger.debug("Convert the evaluated population received from the Privacy Engine to the format required by Jenetics.");
            evaluatedPopulation = IntStream
                    .range(0, fitnessValues.length)
                    // TODO: new interface of the privacy engine to get two fitness values
                    .mapToObj(i -> population.get(i).withFitness(Vec.of(fitnessValues[i], fitnessValues[i])))
                    .sorted((p1, p2) -> Integer.compare(p2.fitness().data()[0], p1.fitness().data()[0]))
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
