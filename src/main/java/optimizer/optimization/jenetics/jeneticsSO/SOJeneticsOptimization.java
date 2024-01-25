package optimizer.optimization.jenetics.jeneticsSO;

import com.optimization.data.optimizer.service.dto.OptimizationResultDTO;
import optimizer.domain.Flight;
import optimizer.domain.Slot;
import optimizer.optimization.FitnessMethod;
import optimizer.optimization.jenetics.JeneticsOptimization;
import optimizer.optimization.OptimizationMode;
import optimizer.optimization.evaluation.SOBatchEvaluator;
import optimizer.optimization.evaluation.BatchEvaluatorFactory;
import io.jenetics.EnumGene;
import io.jenetics.Optimize;
import io.jenetics.Phenotype;
import io.jenetics.engine.*;
import io.jenetics.util.ISeq;
import io.jenetics.util.Seq;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SOJeneticsOptimization extends JeneticsOptimization<Map<Flight, Slot>,
        SOSlotAllocationProblem,
        SOJeneticsOptimizationStatistics,
        SOJeneticsOptimizationConfiguration,
        Flight> {

    private static final Logger logger = LogManager.getLogger();

    private int fitnessPrecision = Integer.MIN_VALUE;
    private List<Map<Flight, Slot>> results;

    private List<Integer> fitnessValuesResults = null;

    public SOJeneticsOptimization(Flight[] flights, Slot[] slots) {
        super(flights, slots);
        this.statistics = new SOJeneticsOptimizationStatistics();
        this.problem = new SOSlotAllocationProblem(
                ISeq.of(this.getFlights()),
                ISeq.of(this.getSlots())
        );
        logger.info("Slot allocation problem initialized.");
    }

    @Override
    protected SOJeneticsOptimizationConfiguration createNewConfig() {
        return new SOJeneticsOptimizationConfiguration();
    }

    @Override
    public OptimizationResultDTO[] getResultDTO(int noOfSolutions) {
        Map<Flight, Slot>[] resultMaps = this.getResults().toArray(Map[]::new);
        List<OptimizationResultDTO> resultsDTOs = new LinkedList<>();

        if(resultMaps != null) {
            for(int i = 0; i < resultMaps.length && i < noOfSolutions; i++) {
                Map<Flight, Slot> resultMap = resultMaps[i];
                resultsDTOs.add(this.convertResultMapToOptimizationResultMapDto(this.getOptId(), resultMap));

                logger.info("Checking if result " + i + " is invalid ...");
                int invalidCount = 0;
                for (Flight f : resultMap.keySet()) {
                    if (f.getScheduledTime() != null && f.getScheduledTime().isAfter(resultMap.get(f).getTime())) {
                        invalidCount++;
                        logger.info("Flight " + f.getFlightId() + " with scheduled time " + f.getScheduledTime() +" at Slot " + resultMap.get(f).getTime());
                    }
                }

                if(invalidCount > 0) {
                    logger.info("Solution " + i + " is invalid. Number of invalid assignments: " + invalidCount);
                } else {
                    logger.info("Solution " + i + " is valid.");
                }


                if(i == 0) {
                    // For the best result, we know the fitness
                    logger.info("Set fitness of solution " + i + " to " + statistics.getMaximumFitness());
                    resultsDTOs.get(i).setFitness(getStatistics().getResultFitness());
                } else{
                    if(i == 1) logger.info("Setting fitness values of all returned solutions.");
                    resultsDTOs.get(i).setFitness(getFitnessValuesResults() != null && getFitnessValuesResults().size() > i ?
                            getFitnessValuesResults().get(i)
                            : 0.0);
                }
                if(getConvertedResults() != null){
                    resultsDTOs.get(i).setOptimizedFlightSequenceIndexes(getConvertedResults()[i]);
                }
            }
        }
        return resultsDTOs.toArray(OptimizationResultDTO[]::new);
    }

    @Override
    public Map<Flight, Slot> run() {

        InternConfig<Integer> config = configurationWithoutMissingValues();

        if(this.statistics.getFitnessEvolution() != null)  {
            this.statistics.getFitnessEvolution().clear();
            logger.info("Cleared fitness evolution.");
        }

        logger.info("Initial population consists of " + config.initialPopulation.length() + " individuals.");
        logger.info("Initial population consists of " + config.initialPopulation.stream().distinct().toList().size() + " distinct individuals.");

        logger.info("Build the genetic algorithm engine.");

        SOBatchEvaluator evaluator = createEvaluator();

        Engine.Builder<EnumGene<Integer>, Integer> builder = new Engine.Builder<>(evaluator, problem.codec().encoding());

        builder = deduplicate(builder);

        EvolutionStatistics<Integer, ?> statistics = EvolutionStatistics.ofNumber();

        EvolutionStream<EnumGene<Integer>, Integer> stream = buildEvolutionStream(config, builder);

        this.getStatistics().setTimeStarted(LocalDateTime.now()); // set the begin time in the statistics

        EvolutionResult<EnumGene<Integer>, Integer> result = stream
                .peek(statistics)
                .collect(EvolutionResult.toBestEvolutionResult());

        logResult(result);

        result = removeInvalidSolutions(result);

        if(result.bestFitness() > 0) { // for invalid solutions, the devalued fitness will be returned
            if (this.getMode() == OptimizationMode.NON_PRIVACY_PRESERVING ||
                    this.getMode() == OptimizationMode.DEMONSTRATION ||
                    this.getMode() == OptimizationMode.BENCHMARKING) {
                List<Phenotype<EnumGene<Integer>, Integer>> evaluatedResultGeneration = result.population()
                        .stream()
                        .map(phenotype -> phenotype.withFitness(problem.fitness(phenotype.genotype())))
                        .sorted(Comparator.comparingInt(Phenotype::fitness))
                        .sorted(Comparator.reverseOrder())
                        .collect(Collectors.toList());

                result = setEvaluatedResult(result, evaluatedResultGeneration);

                logger.info("Setting fitness values of distinct, evaluated population.");
                var distinctIndividualFitnessValues = result.population()
                        .stream()
                        .map(Phenotype::genotype)
                        .distinct()
                        .map(problem::fitness)
                        .sorted(Comparator.reverseOrder())
                        .collect(Collectors.toList());

                this.setFitnessValuesResults(distinctIndividualFitnessValues);
            } else{
                if(getFitnessMethod() != FitnessMethod.ACTUAL_VALUES){
                    logger.debug("Running in privacy-preserving mode. Evaluating the last generation with actual values.");
                    var seq = Seq.of(result.population());
                    Integer[] fitnessValues = this.getPrivacyEngineService().computeActualFitnessValues(this, evaluator.convertPopulationToArray(seq));

                    EvolutionResult<EnumGene<Integer>, Integer> finalResult = result;
                    List<Phenotype<EnumGene<Integer>, Integer>> evaluatedResultGeneration = IntStream
                            .range(0, fitnessValues.length)
                            .mapToObj(i -> finalResult.population().get(i).withFitness(fitnessValues[i]))
                            .collect(Collectors.toList());

                    result = setEvaluatedResult(result, evaluatedResultGeneration);
                }
                logger.info("Setting fitness values of distinct, evaluated population.");
                var fitnessValueResults = result.population()
                        .stream()
                        .filter(distinctByAttribute(Phenotype::genotype))
                        .map(Phenotype::fitness)
                        .sorted(Comparator.reverseOrder())
                        .toList();

                this.setFitnessValuesResults(fitnessValueResults);
            }
        } else{
            logger.info("No actual fitness values will be calculated, as the result contains no valid solutions");
        }

        Map<Flight, Slot> resultMap = problem.decode(result.bestPhenotype().genotype());

        if(logger.isDebugEnabled()) {
            logger.debug("Checking if solution is valid ...");

            int invalidCount = 0;
            for (Flight f : resultMap.keySet()) {
                if (f.getScheduledTime() != null && f.getScheduledTime().isAfter(resultMap.get(f).getTime())) {
                    invalidCount++;
                    logger.debug("Flight " + f.getFlightId() + " with scheduled time " + f.getScheduledTime() +" at Slot " + resultMap.get(f).getTime());
                }

                if(invalidCount > 0) {
                    logger.debug("Solution is invalid. Number of invalid assignments: " + invalidCount);
                } else {
                    logger.debug("Solution is valid.");
                }
            }
        }

        logger.info("Statistics: \n" + statistics);
        logger.info("Printing statistics from BatchEvaluator");
        evaluator.printLogs();

        setAndPrintStatistics(statistics, result, problem);

        // set the results
        List<Map<Flight, Slot>> resultList =
                result.population().stream()
                        .sorted(Comparator.comparingInt(Phenotype::fitness))
                        .sorted(Comparator.reverseOrder())
                        .map(Phenotype::genotype)
                        .distinct()
                        .map(problem::decode)
                        .toList();

        logger.info("Saving {} distinct results.", resultList.size());
        this.setResults(resultList);

        logger.info("Converting result population to the format required by the PE.");
        Integer[][] resultListConverted = evaluator.convertPopulationToArray(ISeq.of(result.population().stream()
                .filter(distinctByAttribute(Phenotype::genotype))
                .sorted(Comparator.comparingInt(Phenotype::fitness))
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList())));

        return resultMap;
    }

    @Override
    public Map<Flight, Slot> getResult() {
        return null;
    }

    private EvolutionStream<EnumGene<Integer>, Integer> buildEvolutionStream(InternConfig<Integer> config, Engine.Builder<EnumGene<Integer>, Integer> builder) {
        Engine<EnumGene<Integer>, Integer> engine = builder
                .optimize(Optimize.MAXIMUM)
                .populationSize(config.populationSize)
                .alterers(config.mutator, config.crossover)
                .offspringSelector(config.offspringSelector)
                .survivorsSelector(config.survivorsSelector)
                .maximalPhenotypeAge(config.maximalPhenotypeAge)
                .offspringFraction(config.offspringFraction)
                .constraint(problem.constraint().isPresent()? problem.constraint().get() :null)
                .build();

        logger.info("Engine population size: " + engine.populationSize());

        logger.info("Running optimization using Jenetics framework as slot allocation problem ...");

        EvolutionStream<EnumGene<Integer>, Integer> stream = engine.stream(config.initialPopulation);

        for(Predicate<? super EvolutionResult<EnumGene<Integer>, Integer>> terminationCondition: config.terminationConditions) {
            stream = stream.limit(terminationCondition);
        }
        logger.info("Current thread: " + Thread.currentThread());

        // add a termination condition that truncates the result if the current thread was interrupted
        stream = stream.limit(result -> !Thread.currentThread().isInterrupted());
        return stream;
    }

    private void logResult(EvolutionResult<EnumGene<Integer>, Integer> result) {
        logger.info("Finished optimization");

        logger.info(Thread.currentThread() + " was interrupted: " + Thread.currentThread().isInterrupted());

        logger.info("Result fitness after optimization: {}.", result.bestFitness());
        logger.info("Removing invalid solutions from result generation");
        logger.info("Result population contains {} invalid solutions.", result.invalidCount());

    }

    private EvolutionResult<EnumGene<Integer>, Integer> removeInvalidSolutions(EvolutionResult<EnumGene<Integer>, Integer> result) {
        AtomicInteger invalidPhenotypeCount = new AtomicInteger();
        List<Phenotype<EnumGene<Integer>, Integer>> validSolutions = result.population()
                .stream()
                .filter(phenotype -> {
                    Map<Flight, Slot> decodedGenotype = problem.decode(phenotype.genotype());
                    for(Map.Entry<Flight, Slot> entry : decodedGenotype.entrySet()){
                        if(entry.getKey().getScheduledTime() != null && entry.getKey().getScheduledTime().isAfter(entry.getValue().getTime())){
                            invalidPhenotypeCount.getAndIncrement();
                            return false;
                        }
                    }
                    return true;
                })
                .toList();
        boolean hasValidSolutions = !validSolutions.isEmpty();
        if(!hasValidSolutions){
            logger.warn("There are no valid solutions left.");
            logger.warn("Optimization will return an invalid solution.");
            validSolutions = new ArrayList<>();
            validSolutions.add(result.bestPhenotype());
        }

        logger.info("Removed {} invalid solutions.", invalidPhenotypeCount.get());
        logger.info("Result has {} remaining solutions.", validSolutions.size());
        result = EvolutionResult.of(
                Optimize.MAXIMUM,
                ISeq.of(validSolutions),
                result.generation(),
                result.totalGenerations(),
                result.durations(),
                invalidPhenotypeCount.get(),
                result.invalidCount() - invalidPhenotypeCount.get(),
                result.alterCount()
        );
        logger.info("Result fitness after invalid solutions have been removed: {}.", result.bestFitness());
        return result;
    }

    public List<Integer> getFitnessValuesResults() {
        return fitnessValuesResults;
    }

    public int computeInitialFitness() {
        logger.info("Calculating fitness of initial flight sequence.");
        Map<Flight, Slot> initialAllocation = new HashMap<>();

        int initialFitness = Integer.MIN_VALUE;

        List<Flight> initialFlightSequence =
                Arrays.stream(Objects.requireNonNullElse(this.getInitialFlightSequence(), new String[]{""}))
                        .map(flightId -> {
                            // return flight with same id (should find flight)
                            for(Flight flight : this.getFlights()) {
                                if(flight.getFlightId().equals(flightId)) {
                                    return flight;
                                }
                            }
                            // return null (shouldn't happen)
                            return null;
                        })
                        .collect(Collectors.toList());

        for(int i = 0; i < initialFlightSequence.size(); i++){
            if(initialFlightSequence.get(i) != null) {
                initialAllocation.put(initialFlightSequence.get(i), this.getSlots()[i]);
            }
        }

        long initialFlightSequenceNotNullCount = Arrays.stream(this.getFlights()).filter(Objects::nonNull).count();

        if(initialAllocation.size() < initialFlightSequenceNotNullCount){
            logger.info("Could not calculate initial fitness as not all initial flight IDs have been mapped to a slot.");
        }else{
            initialFitness = problem.fitness(initialAllocation);
            logger.info("Initial fitness: {}.", initialFitness);
        }

        return initialFitness;
    }

    public void setFitnessValuesResults(List<Integer> fitnessValuesResults) {
        this.fitnessValuesResults = fitnessValuesResults;
    }

    public List<Map<Flight, Slot>> getResults() {
        return results;
    }

    public void setResults(List<Map<Flight, Slot>> results) {
        this.results = results;
    }

    private void setAndPrintStatistics(EvolutionStatistics<Integer,?> statistics, EvolutionResult<EnumGene<Integer>, Integer> result, SOSlotAllocationProblem problem) {

        int resultFitness = result.bestPhenotype().fitness();
        logger.info("Setting statistics for this optimization."); // already initialized in constructor
        this.getStatistics().setTimeFinished(LocalDateTime.now());
        this.getStatistics().setResultFitness(result.bestPhenotype().fitness());
        this.getStatistics().setIterations((int) statistics.altered().count());
        this.getStatistics().setFitnessFunctionInvocations(problem.getFitnessFunctionApplications());
        this.getStatistics().setSolutionGeneration(resultFitness);
        if(resultFitness > this.statistics.getMaximumFitness()) {
            this.statistics.setMaximumFitness(resultFitness);
        }

        logger.info("Fitness of best solution: " + this.getStatistics().getResultFitness());
        logger.info("Number of generations: " + this.getStatistics().getIterations());
        logger.info("Number of fitness function invocations: " + this.getStatistics().getFitnessFunctionInvocations());
        logger.info("Generation of best solution: " + this.getStatistics().getSolutionGeneration());
    }

    private EvolutionResult<EnumGene<Integer>, Integer> setEvaluatedResult(EvolutionResult<EnumGene<Integer>, Integer> result,
                                                                           List<Phenotype<EnumGene<Integer>, Integer>> evaluatedResultGeneration) {
        logger.info("Setting evaluated population as new result population.");
        return EvolutionResult.of(
                Optimize.MAXIMUM,
                ISeq.of(evaluatedResultGeneration),
                result.generation(),
                result.totalGenerations(),
                result.durations(),
                result.killCount(),
                result.invalidCount(),
                result.alterCount()
        );
    }

    protected SOBatchEvaluator createEvaluator() {
        return BatchEvaluatorFactory.getSOEvaluator(getFitnessMethod(), problem, this);
    }
}
