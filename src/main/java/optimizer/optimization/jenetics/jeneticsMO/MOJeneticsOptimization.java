package optimizer.optimization.jenetics.jeneticsMO;

import optimizer.domain.FlightMO;
import optimizer.domain.Slot;
import optimizer.optimization.evaluation.BatchEvaluatorFactory;
import optimizer.optimization.evaluation.MOBatchEvaluator;
import optimizer.optimization.jenetics.JeneticsOptimization;
import optimizer.optimization.jenetics.jeneticsMLA.MLAJeneticsOptimization;
import com.optimization.data.optimizer.service.dto.OptimizationResultDTO;
import io.jenetics.*;
import io.jenetics.engine.*;
import io.jenetics.ext.moea.MOEA;
import io.jenetics.ext.moea.Vec;
import io.jenetics.util.ISeq;
import io.jenetics.util.IntRange;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class MOJeneticsOptimization extends JeneticsOptimization<Map<FlightMO, Slot>,
        MOSlotAllocationProblem,
        MOJeneticsOptimizationStatistics,
        MOJeneticsOptimizationConfiguration,
        FlightMO> {

    private static final Logger logger = LogManager.getLogger();
    private Map<Map<FlightMO, Slot>, int[]> paretoFront = null;
    public MOJeneticsOptimization(FlightMO[] flights, Slot[] slots) {
        super(flights, slots);
        this.statistics = new MOJeneticsOptimizationStatistics();
        this.problem = new MOSlotAllocationProblem(
                ISeq.of(this.getFlights()),
                ISeq.of(this.getSlots())
        );
        logger.info("Multi Objective slot allocation problem initialized");
    }

    @Override
    protected MOJeneticsOptimizationConfiguration createNewConfig() {
        return new MOJeneticsOptimizationConfiguration();
    }

    @Override
    public OptimizationResultDTO[] getResultDTO(int noOfSolutions) {
        List<OptimizationResultDTO> resultDTOs = new LinkedList<>();
        if(this.getParetoFront() != null) {
            this.getParetoFront()
                    .forEach((key, value) -> {
                        OptimizationResultDTO newResult = convertResultMapToOptimizationResultMapDto(this.getOptId(), key);
                        newResult.setFitness((double) value[0]);
                        if(!(this instanceof MLAJeneticsOptimization)){
                            newResult.setSecondFitness((double) value[1]);
                        }
                        resultDTOs.add(newResult);

                    });
        }
        return resultDTOs.toArray(new OptimizationResultDTO[0]);
    }

    @Override
    public Map<FlightMO, Slot> run() {

        InternConfig<Vec<int[]>> config = configurationWithoutMissingValues();

        if(this.statistics.getFitnessEvolution() != null)  {
            this.statistics.getFitnessEvolution().clear();
            logger.info("Cleared fitness evolution.");
        }

        logger.info("Initial population consists of " + config.initialPopulation.length() + " individuals.");
        logger.info("Initial population consists of " + config.initialPopulation.stream().distinct().toList().size() + " distinct individuals.");

        logger.info("Build the genetic algorithm engine.");

        MOBatchEvaluator evaluator = createEvaluator();

        evaluator.setSecondObfuscated(configuration.isSecondObfuscated());

        Engine.Builder<EnumGene<Integer>, Vec<int[]>> builder = new Engine.Builder<>(evaluator, this.problem.codec().encoding());

        builder = deduplicate(builder);

        EvolutionStatistics<Vec<int[]>, ?> statistics = EvolutionStatistics.ofComparable();

        EvolutionStream<EnumGene<Integer>, Vec<int[]>> stream = buildEvolutionStream(config, builder);

        this.getStatistics().setTimeStarted(LocalDateTime.now());
        final ISeq<Phenotype<EnumGene<Integer>, Vec<int[]>>> paretoFrontResult = stream
                    .peek(statistics)
                    .collect(MOEA.toParetoSet(IntRange.of(1, 100)));

        // TODO evaluate if a checking for valid solutions is required --> depends from the weight map

        Map<Map<FlightMO, Slot>, int[]> resultMap = getFitnessValues(paretoFrontResult, problem);

        this.setParetoFront(resultMap);

        int[][] fitnessValues = resultMap.values().toArray(new int[0][]);

        Map.Entry<Map<FlightMO, Slot>, int[]> selectedPoint = selectPointOnParetoFront(resultMap, fitnessValues);

        this.getStatistics().setSelectedPoint(selectedPoint.getValue());

        logParetoFront(fitnessValues, selectedPoint.getValue());

        logger.info("Statistics: \n" + statistics);
        logger.info("Printing statistics from BatchEvaluator");
        evaluator.printLogs();

        setAndPrintStatistics(statistics, paretoFrontResult, problem, selectedPoint.getValue());
        changeTheoreticalMaxValues(this.getStatistics().getEstimatedParetoFront(), selectedPoint.getValue());

        List<Map<FlightMO, Slot>> resultRepresentation = convertParetoFront(paretoFrontResult, this.problem);
        this.setResult(selectedPoint.getKey());

        logger.info("Converting result population to the format required by the PE.");
        Integer[][] resultListConverted = evaluator.convertPopulationToArray(ISeq.of(paretoFrontResult.stream()
                .filter(distinctByAttribute(Phenotype::genotype))
                .sorted(Comparator.comparingInt(p -> p.fitness().data()[0]))
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList())));

        return selectedPoint.getKey();
    }

    protected void setAndPrintStatistics(EvolutionStatistics<Vec<int[]>,?> statistics,
                                         ISeq<Phenotype<EnumGene<Integer>, Vec<int[]>>> paretoFrontResult,
                                         MOSlotAllocationProblem problem ,
                                         int[] selectedPoint) {

        logger.info("Setting statistics for this optimization."); // already initialized in constructor
        this.getStatistics().setTimeFinished(LocalDateTime.now());
        this.getStatistics().setMaximumFitness(selectedPoint[0]);
        this.getStatistics().setMaximumFitnessTwo(selectedPoint[1]);

        this.getStatistics().setIterations((int) statistics.altered().count());
        this.getStatistics().setFitnessFunctionInvocations(problem.getFitnessFunctionApplications());
        double balanceRatio = Math.abs((double)selectedPoint[0]/this.statistics.getTheoreticalMaxFitness() -
                (double)selectedPoint[1]/this.statistics.getTheoreticalMaxFitnessTwo());
        this.statistics.setBalanceRatio(balanceRatio);

        logger.info("Fitness of best solution: " + this.getStatistics().getResultFitness());
        logger.info("Number of generations: " + this.getStatistics().getIterations());
        logger.info("Number of fitness function invocations: " + this.getStatistics().getFitnessFunctionInvocations());

    }

    protected void changeTheoreticalMaxValues(double[][] estimatedParetoFront, int[] value) {
        double[] nearestPoint = null;
        double maxDistance = Double.MAX_VALUE;
        for(double[] point: estimatedParetoFront) {
            double distancePoint = Math.pow(point[0]-value[0], 2) + Math.pow(point[1]-value[1], 2);
            if(distancePoint < maxDistance &&
                    point[0] > value[0] &&
                    point[1] > value[1]){
                nearestPoint = point;
                maxDistance = distancePoint;
            }
        }
        if(nearestPoint != null) {
            this.getStatistics().setTheoreticalMaxFitness((int) nearestPoint[0]);
            this.getStatistics().setTheoreticalMaxFitnessTwo((int) nearestPoint[1]);
        } else {
            // optimization result is on the pareto front
            this.getStatistics().setTheoreticalMaxFitness(value[0]);
            this.getStatistics().setTheoreticalMaxFitnessTwo(value[1]);
        }

    }

    private Map<Map<FlightMO, Slot>, int[]> getFitnessValues(ISeq<Phenotype<EnumGene<Integer>, Vec<int[]>>> paretoFrontResult, MOSlotAllocationProblem problem) {
         Map<Map<FlightMO, Slot>, int[]> resultMap = new HashMap<>();
        paretoFrontResult.stream()
                .forEach(p -> resultMap.put(
                        problem.decode(p.genotype()),
                        p.fitness().data())
                );
         return resultMap;
    }

    private List<Map<FlightMO, Slot>> convertParetoFront(ISeq<Phenotype<EnumGene<Integer>, Vec<int[]>>> paretoFrontResult, MOSlotAllocationProblem problem) {
        return paretoFrontResult.stream()
                .sorted(Comparator.comparingInt(p -> p.fitness().data()[0]))
                .map(Phenotype::genotype)
                .map(problem::decode)
                .toList();
    }

    private EvolutionStream<EnumGene<Integer>,Vec<int[]>> buildEvolutionStream(InternConfig<Vec<int[]>> config, Engine.Builder<EnumGene<Integer>, Vec<int[]>> builder) {
        Engine<EnumGene<Integer>, Vec<int[]>> engine = builder
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

        EvolutionStream<EnumGene<Integer>, Vec<int[]>> stream = engine.stream(config.initialPopulation);

        for(Predicate<? super EvolutionResult<EnumGene<Integer>, Vec<int[]>>> terminationCondition: config.terminationConditions) {
            stream = stream.limit(terminationCondition);
        }
        logger.info("Current thread: " + Thread.currentThread());

        // add a termination condition that truncates the result if the current thread was interrupted
        stream = stream.limit(result -> !Thread.currentThread().isInterrupted());
        return stream;
    }

    protected void logParetoFront(int[][] paretoFrontResult, int[] selectedPoint) {
        logger.info("Finished optimization");
        logger.info(Thread.currentThread() + " was interrupted: " + Thread.currentThread().isInterrupted());

        Arrays.stream(paretoFrontResult)
                .sorted(Comparator.comparingInt(p -> p[1]))
                .map(Arrays::toString)
                .distinct()
                .map(str -> Arrays.stream(str.substring(1, str.length() - 1).split(", "))
                        .mapToInt(Integer::parseInt)
                        .toArray())
                .forEach(p -> logger.info("Solution on the pareto front:\t" + p[0] + "\t" + p[1]));

        System.out.println();


        // TODO: remove the code below only used for the faster copying to R
        Arrays.stream(paretoFrontResult)
                .sorted(Comparator.comparingInt(p -> p[1]))
                .map(Arrays::toString)
                .distinct()
                .map(str -> Arrays.stream(str.substring(1, str.length() - 1).split(", "))
                        .mapToInt(Integer::parseInt)
                        .toArray())
                .forEach(p -> System.out.print(", " + p[0]));

        System.out.println();

        Arrays.stream(paretoFrontResult)
                .sorted(Comparator.comparingInt(p -> p[1]))
                .map(Arrays::toString)
                .distinct()
                .map(str -> Arrays.stream(str.substring(1, str.length() - 1).split(", "))
                        .mapToInt(Integer::parseInt)
                        .toArray())
                .forEach(p -> System.out.print(", " + p[1]));

        System.out.println();

        logger.info("The point:\t" + selectedPoint[0] + "\\t" + selectedPoint[1] +" was chosen from the pareto front");
    }

    protected Map.Entry<Map<FlightMO, Slot>, int[]> selectPointOnParetoFront(
            Map<Map<FlightMO, Slot>, int[]> paretoFrontResult,
            int[][] fitnessValues) {
        Map.Entry<Map<FlightMO, Slot>, int[]> bestScoringPoint = null;
        double bestScore = Integer.MIN_VALUE;
        int[] bestFirstObj = Arrays.stream(fitnessValues)
                .max(Comparator.comparingInt(v -> v[0]))
                .get();
        int[] bestSecondObj = Arrays.stream(fitnessValues)
                .max(Comparator.comparingInt(v -> v[0]))
                .get();
        double firstMax = bestFirstObj[0];
        double firstMin = bestSecondObj[0];
        double secondMax = bestSecondObj[1];
        double secondMin = bestFirstObj[1];
        for(Map.Entry<Map<FlightMO, Slot>, int[]> point : paretoFrontResult.entrySet()) {
            double score = (point.getValue()[0]-firstMin+1)/(firstMax-firstMin+1) +
                    (point.getValue()[1]-secondMin+1)/(secondMax-secondMin+1);
            if(score > bestScore) {
                bestScoringPoint = point;
                bestScore = score;
            }
        }
        return bestScoringPoint;
    }

    @Override
    public Map<FlightMO, Slot> getResult() {
        return this.result;
    }

    @Override
    public MOJeneticsOptimizationConfiguration getConfiguration() {
        return this.configuration;
    }


    public Map<Map<FlightMO, Slot>, int[]> getParetoFront() {
        return paretoFront;
    }

    public void setParetoFront(Map<Map<FlightMO, Slot>, int[]> paretoFront) {
        this.paretoFront = paretoFront;
    }

    public int computeInitialFitness() {
        // TODO Think if there should be an initial fitness
        return 0;
    }

    protected MOBatchEvaluator createEvaluator() {
        return BatchEvaluatorFactory.getMOEvaluator(getFitnessMethod(), problem, this);
    }
}
