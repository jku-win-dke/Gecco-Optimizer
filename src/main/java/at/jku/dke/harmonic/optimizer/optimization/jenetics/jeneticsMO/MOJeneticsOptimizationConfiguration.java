package at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsMO;

import at.jku.dke.harmonic.optimizer.domain.Flight;
import at.jku.dke.harmonic.optimizer.domain.Slot;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.JeneticsOptimizationConfiguration;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.SlotAllocationProblem;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsMO.jeneticsExtensions.*;
import io.jenetics.*;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.engine.Limits;
import io.jenetics.ext.HPRMutator;
import io.jenetics.ext.RSMutator;
import io.jenetics.ext.moea.NSGA2Selector;
import io.jenetics.ext.moea.UFTournamentSelector;
import io.jenetics.ext.moea.Vec;
import io.jenetics.util.ISeq;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.*;
import java.util.function.Predicate;

public class MOJeneticsOptimizationConfiguration extends JeneticsOptimizationConfiguration<Vec<int[]>> {

    private static final Logger logger = LogManager.getLogger();

    // TODO: think about a good structure to make getInitialPopulation different in the two classes (probably with superclass)
    public ISeq<Genotype<EnumGene<Integer>>> getInitialPopulation(SlotAllocationProblem<Vec<int[]>, Flight> problem, int populationSize) {
        Map<Flight, Slot> initialAllocation = new HashMap<>();
        Flight[] flights;
        Slot[] slots;

        logger.info("-- Initial Population --");

        logger.info("Sort flights and available slots to get initial population.");
        flights = problem.getFlights().stream().sorted().toArray(Flight[]::new);
        slots = problem.getAvailableSlots().stream().sorted().toArray(Slot[]::new);

        logger.info("Allocate slots according to scheduled time.");
        for(int i = 0; i < flights.length; i++) {
            initialAllocation.put(flights[i], slots[i]);
        }

        Genotype<EnumGene<Integer>> genotype = problem.codec().encode(initialAllocation);

        Genotype[] genotypes = new Genotype[populationSize];

        genotypes[0] = genotype;

        logger.info("Swap flights for initial allocation.");
        int ratioGenotypesToFlights = (int) Math.ceil(genotypes.length / flights.length);
        logger.debug("ratioGenotypesToFlights = " + ratioGenotypesToFlights);
        for (int i = 0; i < genotypes.length; i++) {
            Map<Flight, Slot> swappedAllocation = new HashMap<>(initialAllocation);

            int ratioCurrentGenotypeIndexToFlights = (int) Math.ceil(i / flights.length);
            logger.debug("ratioCurrentGenotypeIndexToFlights = " + ratioCurrentGenotypeIndexToFlights);
            for(int k = 0; k <= ratioCurrentGenotypeIndexToFlights; k++) {
                int swap1 = i - (ratioCurrentGenotypeIndexToFlights * flights.length) + (2 * k);
                int swap2 = swap1 + 1;

                if(swap2 >= flights.length) {
                    swap1 = swap1 - flights.length + 1;
                    swap2 = swap2 - flights.length + 2;
                }

                if(swap2 < flights.length) {
                    logger.debug("Swapping " + swap1 + " with " + swap2);
                    Slot s1 = swappedAllocation.get(flights[swap1]);
                    Slot s2 = swappedAllocation.get(flights[swap2]);

                    swappedAllocation.put(flights[swap1], s2);
                    swappedAllocation.put(flights[swap2], s1);
                }
            }

            genotype = problem.codec().encode(swappedAllocation);
            genotypes[i] = genotype;
        }

        return ISeq.of(genotypes);
    }

    @Override
    public Selector<EnumGene<Integer>, Vec<int[]>> getOffspringSelector() {
        String selectorType = this.getStringParameter("offspringSelector");

        Number selectorParameter = this.getOffspringSelectorParameter();

        logger.info("-- Offspring Selector --");
        return this.getSelector(selectorType, selectorParameter);
    }

    private Number getOffspringSelectorParameter() {
        return this.getNumberParameter("offspringSelectorParameter");
    }

    @Override
    public Selector<EnumGene<Integer>, Vec<int[]>> getSurvivorsSelector() {
        String selectorType = this.getStringParameter("survivorsSelector");

        Number selectorParameter = this.getSurvivorsSelectorParameter();

        logger.info("-- Survivors Selector --");
        logger.info("Found survivors selector parameter: " + selectorParameter);
        return this.getSelector(selectorType, selectorParameter);
    }

    // TODO: maybe add subfunctions in the JeneticsOptimizationConfiguration to reduce the code reduction
    private Number getSurvivorsSelectorParameter() {
        return this.getNumberParameter("survivorsSelectorParameter");
    }
    @Override
    public Selector<EnumGene<Integer>, Vec<int[]>> getSelector(String selectorType, Number selectorParameter) {
        Selector<EnumGene<Integer>, Vec<int[]>> selector = null;

        if(selectorType != null) {
            switch(selectorType) {
                case "TOURNAMENT_SELECTOR":
                    if(selectorParameter != null) {
                        logger.info("Using tournament selector with parameter " + selectorParameter.intValue() + ".");
                        selector = new TournamentSelector<>(selectorParameter.intValue());
                    } else {
                        logger.info("Using tournament selector with default parameter.");
                        selector = new TournamentSelector<>();
                    }
                    break;
                case "NSGA2_SELECTOR":
                    logger.info("Using NSGA2 selector with default parameter.");
                    selector = NSGA2Selector.ofVec();
                    break;
                case "SPEA2_SELECTOR":
                    if(selectorParameter != null) {
                        logger.info("Using tournament selector with archive size " + selectorParameter.intValue() + ".");
                        selector = new SPEA2Selector<>(Vec::dominance, Vec::distance, selectorParameter.intValue());
                    } else {
                        logger.info("Using tournament selector with default archive size.");
                        selector = new SPEA2Selector<>(Vec::dominance, Vec::distance);
                    }
                    break;
                case "UT_TOURNAMENT_SELECTOR":
                    // TODO check why selector is not working properly
                    logger.info("Using unique fitness tournament selector with default parameter.");
                    selector = UFTournamentSelector.ofVec();
                    break;
                default:
                    if(selectorParameter != null) {
                        logger.info("No valid selector for multi-objective optimization provided. Using tournament selector with parameter " + selectorParameter.intValue() + ".");
                        selector = new TournamentSelector<>(selectorParameter.intValue());
                    } else {
                        logger.info("No valid selector for multi-objective optimization provided. Using tournament selector with default parameter.");
                        selector = new TournamentSelector<>();
                    }
            }
        }

        return selector;
    }

    @Override
    public Crossover<EnumGene<Integer>, Vec<int[]>> getCrossover() {
        String crossoverType = this.getStringParameter("crossover");

        Crossover<EnumGene<Integer>, Vec<int[]>> crossover = null;

        double alterProbability = this.getCrossoverAlterProbability();

        logger.info("-- Crossover --");

        if(crossoverType != null) {
            switch (crossoverType) {
                case "PARTIALLY_MATCHED_CROSSOVER":
                    if(alterProbability >= 0) {
                        logger.info("Use partially matched crossover with " + alterProbability + " alter probability.");
                        crossover = new PartiallyMatchedCrossover<>(alterProbability);
                    } else {
                        logger.info("No alter probability for partially matched crossover.");
                        crossover = new PartiallyMatchedCrossover<>(0);
                    }
                    break;
                case "UNIFORM_ORDER_BASED_CROSSOVER":
                    if(alterProbability >= 0) {
                        logger.info("Use uniform order based crossover with " + alterProbability + " alter probability.");
                        crossover = new UniformOderBasedCrossover<>(alterProbability);
                    } else {
                        logger.info("No alter probability for uniform order based crossover.");
                        crossover = new UniformOderBasedCrossover<>(0);
                    }
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + crossoverType);
            }
        }

        return crossover;
    }

    @Override
    public Predicate<? super EvolutionResult<EnumGene<Integer>, Vec<int[]>>>[] getTerminationConditions() {
        List<Predicate<? super EvolutionResult<EnumGene<Integer>, Integer>>> predicates = new LinkedList<>();

        Map<String,Object> terminationConditionParameters = this.getMapParameter("terminationConditions");

        if(terminationConditionParameters != null) {
            Set<String> terminationConditionTypes = terminationConditionParameters.keySet();

            for(String terminationConditionType : terminationConditionTypes) {
                Predicate<? super EvolutionResult<EnumGene<Integer>, Integer>> nextPredicate = null;

                switch(terminationConditionType) {
                    case "WORST_FITNESS": {
                        int threshold = (int) terminationConditionParameters.get("WORST_FITNESS");

                        nextPredicate = result -> result.worstFitness() > threshold;
                        break;
                    }
                    case "BY_FITNESS_THRESHOLD": {
                        int threshold = (int) terminationConditionParameters.get("BY_FITNESS_THRESHOLD");

                        nextPredicate = Limits.byFitnessThreshold(threshold);
                        break;
                    }
                    case "BY_STEADY_FITNESS": {
                        int generations = (int) terminationConditionParameters.get("BY_STEADY_FITNESS");

                        nextPredicate = Limits.bySteadyFitness(generations);
                        break;
                    }
                    case "BY_FIXED_GENERATION": {
                        int generation = (int) terminationConditionParameters.get("BY_FIXED_GENERATION");

                        nextPredicate = Limits.byFixedGeneration(generation);
                        break;
                    }
                    case "BY_EXECUTION_TIME": {
                        int duration = (int) terminationConditionParameters.get("BY_EXECUTION_TIME");

                        nextPredicate = Limits.byExecutionTime(Duration.ofSeconds(duration));
                        break;
                    }
                    case "BY_POPULATION_CONVERGENCE": {
                        double epsilon = (double) terminationConditionParameters.get("BY_POPULATION_CONVERGENCE");

                        nextPredicate = Limits.byPopulationConvergence(epsilon);
                        break;
                    }
                    case "BY_FITNESS_CONVERGENCE": {
                        Map<String, Object> fitnessConvergenceParameters =
                                (Map<String, Object>) terminationConditionParameters.get("BY_FITNESS_CONVERGENCE");

                        int shortFilterSize = (int) fitnessConvergenceParameters.get("shortFilterSize");
                        int longFilterSize = (int) fitnessConvergenceParameters.get("longFilterSize");
                        double epsilon = (double) fitnessConvergenceParameters.get("epsilon");

                        nextPredicate = Limits.byFitnessConvergence(shortFilterSize, longFilterSize, epsilon);
                        break;
                    }
                }

                predicates.add(nextPredicate);
            }
        }

        return predicates.toArray(Predicate[]::new);
    }

    @Override
    public Mutator<EnumGene<Integer>, Vec<int[]>> getMutator() {
        String mutatorType = this.getStringParameter("mutator");

        Mutator<EnumGene<Integer>, Vec<int[]>> mutator = null;

        logger.info("-- Mutator --");

        if(mutatorType != null) {
            double alterProbability = this.getMutatorAlterProbability();

            switch (mutatorType) {
                case "SWAP_MUTATOR":
                    if(alterProbability >= 0) {
                        logger.info("Use swap mutator with alter probability: " + alterProbability);
                        mutator = new SwapMutator<>(alterProbability);
                    } else {
                        logger.info("Use swap mutator with default alter probability.");
                        mutator = new SwapMutator<>();
                    }
                    break;
                case "RS_MUTATOR":
                    if(alterProbability >= 0) {
                        logger.info("Use swap mutator with alter probability: " + alterProbability);
                        mutator = new RSMutator<>(alterProbability);
                    } else {
                        logger.info("Use swap mutator with default alter probability.");
                        mutator = new RSMutator<>();
                    }
                    break;
                case "ARBITRARY_MUTATOR":
                    if(alterProbability >= 0) {
                        logger.info("Use swap mutator with alter probability: " + alterProbability);
                        mutator = new ArbitraryMutator<>(alterProbability);
                    } else {
                        logger.info("Use swap mutator with default alter probability.");
                        mutator = new ArbitraryMutator<>();
                    }
                    break;
                case "SHIFT_MUTATOR":
                    if(alterProbability >= 0) {
                        logger.info("Use swap mutator with alter probability: " + alterProbability);
                        mutator = new ShiftMutator<>(alterProbability);
                    } else {
                        logger.info("Use swap mutator with default alter probability.");
                        mutator = new ShiftMutator<>();
                    }
                    break;
                case "HYBRID_SWAP_REVERSE_SEQUENCE_MUTATOR":
                    if(alterProbability >= 0) {
                        logger.info("Use a hybrid between swap mutator and reverse sequence mutator with alter probability: " + alterProbability);
                        mutator = new HPRMutator<>(alterProbability);
                    } else {
                        logger.info("Use a hybrid between swap mutator and reverse sequence mutator with default alter probability.");
                        mutator = new HPRMutator<>();
                    }
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + mutatorType);
            }
        }

        return mutator;
    }

}
