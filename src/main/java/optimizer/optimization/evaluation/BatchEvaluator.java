package optimizer.optimization.evaluation;

import optimizer.optimization.FitnessMethod;
import optimizer.optimization.jenetics.FitnessEvolutionStep;
import optimizer.optimization.jenetics.JeneticsOptimization;
import optimizer.optimization.jenetics.SlotAllocationProblem;
import io.jenetics.EnumGene;
import io.jenetics.Genotype;
import io.jenetics.Phenotype;
import io.jenetics.engine.Evaluator;
import io.jenetics.util.Seq;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

public abstract class BatchEvaluator<T extends Comparable<? super T>, V,
        O extends JeneticsOptimization,
        P extends SlotAllocationProblem> implements Evaluator<EnumGene<Integer>, T> {

    private static final Logger logger = LogManager.getLogger();

    protected static final int DEVALUATOR = -10000000;

    /**
     * Fields for deduplicate and duplicate-statistics.
     */

    protected long noGenerations;
    protected long latestUnevaluatedGeneration;
    protected long noGenerationsUnevaluated;
    protected long noInitialDuplicates;
    protected long noRemainingDuplicates;
    protected long noGenerationsDuplicatesNotEliminated;
    protected long noGenerationsEvaluated;

    protected final O optimization;
    protected final P problem;

    /**
     * Devaluation statistics
     */
    protected long noPhenotypes;
    protected long noInvalidPhenotypes;
    protected long noInvalidAssignments;

    /**
     * If true, evaluation is only triggered for generations encountered for the second time, or if no duplicates are present in the population.
     */
    protected final boolean isDeduplicate;
    /**
     * If true, duplicate statistics are gathered, even if isDeduplicate is false.
     */
    protected final boolean trackDuplicates;
    /**
     * If true, the actual fitness values are used. Otherwise, the fitness is obfuscated for the GA.
     */
    protected final boolean useActualFitnessValues;

    public BatchEvaluator(P problem, O optimization) {
        this.problem = problem;
        this.optimization = optimization;
        this.isDeduplicate = optimization.getConfiguration().isDeduplicate();
        this.noGenerations = 0;
        this.noGenerationsUnevaluated = 0;
        this.noInitialDuplicates = 0;
        this.noRemainingDuplicates = 0;
        this.noGenerationsDuplicatesNotEliminated = 0;

        this.noPhenotypes = 0;
        this.noInvalidAssignments = 0;
        this.noInvalidPhenotypes = 0;

        this.trackDuplicates = Boolean.parseBoolean(System.getenv("TRACK_DUPLICATES"));
        this.useActualFitnessValues = Boolean.parseBoolean(System.getenv("USE_ACTUAL_FITNESS"))
                || this.optimization.getFitnessMethod() == FitnessMethod.ACTUAL_VALUES;
        logger.info("Using actual fitness values: {}.", useActualFitnessValues);
    }

    /**
     * Takes the evaluated population and configuration and estimates missing fitness-values accordingly
     * @param population the unevaluated population
     * @param fitnessEvolutionStep the evolution step of this generation
     * @param evaluation the result of the evaluation step
     * @return the estimated generation
     */
    protected abstract List<Phenotype<EnumGene<Integer>, T>> estimatePopulation(Seq<Phenotype<EnumGene<Integer>, T>> population, FitnessEvolutionStep<V> fitnessEvolutionStep, PopulationEvaluation<T> evaluation);

    /**
     * Takes the unevaluated population and returns the evaluation according to the configuration
     * @param population the unevaluated population
     * @param fitnessEvolutionStep the evolution step for this generation
     * @return the evaluated population
     */
    protected abstract PopulationEvaluation<T> evaluatePopulation(Seq<Phenotype<EnumGene<Integer>, T>> population, FitnessEvolutionStep<V> fitnessEvolutionStep);


    protected boolean deduplicate( Seq<Phenotype<EnumGene<Integer>, T>> population, Long generation) {
        if(generation != latestUnevaluatedGeneration || trackDuplicates){
            logger.debug("Checking for duplicates.");
            final Map<Genotype<EnumGene<Integer>>, Phenotype<EnumGene<Integer>, T>> elements =
                    population.stream()
                            .collect(toMap(
                                    Phenotype::genotype,
                                    Function.identity(),
                                    (a, b) -> a));

            if(elements.size() < population.size() && generation != latestUnevaluatedGeneration){
                logger.debug("Generation " + generation + " contains duplicates and is encountered for the first time.");
                logger.debug("Returning unevaluated population with dummy fitness-values.");
                this.noGenerationsUnevaluated++;
                this.noInitialDuplicates = noInitialDuplicates + population.size() - elements.size();

                latestUnevaluatedGeneration = generation;
                return true;
            }
            this.noRemainingDuplicates = noRemainingDuplicates + population.size() - elements.size();
            if(elements.size() < population.size()) this.noGenerationsDuplicatesNotEliminated++;
            return false;
        }
        return false;
    }


    public void printLogs(){
        logger.info("--------------- Statistics Batch Evaluator --------------------");
        logger.info("Deduplication: " + this.isDeduplicate + ".");
        logger.info("Tracking duplicates " + this.trackDuplicates + ".");
        logger.info("Number of populations that entered evaluation: "+ this.noGenerations);
        logger.info("Number of populations that have been evaluated: " + this.noGenerationsEvaluated);
        logger.info("Number of populations that have been rejected because of duplicates: " + this.noGenerationsUnevaluated);
        logger.info("Number of initial duplicates encountered: " + this.noInitialDuplicates);
        logger.info("Number of remaining duplicates after deduplication: " + this.noRemainingDuplicates);
        logger.info("Number of phenotypes checked for validness: " + this.noPhenotypes);
        logger.info("Number of invalid phenotypes found: " + this.noInvalidPhenotypes);
        logger.info("Number of invalid assignments: " + this.noInvalidAssignments);
        logger.info("----------------------------------------------------------------");
    }

    /**
     * Represents the evaluation of a population
     */
    static class PopulationEvaluation<T extends Comparable<? super T>>{
        protected List<Phenotype<EnumGene<Integer>, T>> evaluatedPopulation;
        protected Map<Phenotype<EnumGene<Integer>, T>, T> fitnessQuantilesPopulation;
        protected Genotype<EnumGene<Integer>> bestGenotype;
        protected Genotype<EnumGene<Integer>> bestGenotypeTwo;
        protected double maxFitness;
        protected double maxFitnessTwo;
    }
}
