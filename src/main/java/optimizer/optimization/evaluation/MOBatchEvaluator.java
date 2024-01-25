package optimizer.optimization.evaluation;

import com.optimization.data.privacyEngine.dto.PopulationOrderDTO;
import optimizer.domain.Flight;
import optimizer.domain.Slot;
import optimizer.optimization.OptimizationMode;
import optimizer.optimization.jenetics.FitnessEvolutionStep;
import optimizer.optimization.jenetics.jeneticsMO.MOJeneticsOptimization;
import optimizer.optimization.jenetics.jeneticsMO.MOSlotAllocationProblem;
import io.jenetics.EnumGene;
import io.jenetics.Phenotype;
import io.jenetics.ext.moea.Vec;
import io.jenetics.util.ISeq;
import io.jenetics.util.Seq;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public abstract class MOBatchEvaluator extends BatchEvaluator<Vec<int[]>, double[], MOJeneticsOptimization, MOSlotAllocationProblem> {

    private static final Logger logger = LogManager.getLogger();

    protected boolean secondObfuscated = false;

    /**
     * Used in NON_PRIVACY_PRESERVING mode when useActualFitnessValues is false, to verify if fitness has been improved in a given generation.
     */
    protected long actualMaxFitness;
    /**
     * Used in conjunction with useActualFitnessValues = false and holds the current increment of the obfuscated base fitness value.
     */
    protected long fitnessIncrement;

    public MOBatchEvaluator(MOSlotAllocationProblem problem, MOJeneticsOptimization optimization) {
        super(problem, optimization);
        this.actualMaxFitness = Integer.MIN_VALUE;
        this.fitnessIncrement = 1;

    }

    @Override
    protected abstract List<Phenotype<EnumGene<Integer>, Vec<int[]>>> estimatePopulation(
            Seq<Phenotype<EnumGene<Integer>, Vec<int[]>>> population,
            FitnessEvolutionStep<double[]> fitnessEvolutionStep,
            PopulationEvaluation<Vec<int[]>> evaluation);


    @Override
    public ISeq<Phenotype<EnumGene<Integer>, Vec<int[]>>> eval(Seq<Phenotype<EnumGene<Integer>, Vec<int[]>>> population) {
        logger.debug("Starting population evaluation in the multi batch evaluator ...");
        this.noGenerations++;
        Optional<Long> generation = population.stream().map(Phenotype::generation).max(Long::compareTo);

        if(isDeduplicate){
            if(deduplicate(population, generation.get())){
                return ISeq.of(population
                        .stream()
                        .map(p -> p.withFitness(Vec.of(-1, -1)))
                        .collect(Collectors.toList()));
            }
        }
        noGenerationsEvaluated++;


        FitnessEvolutionStep<double[]> fitnessEvolutionStep = null;
        List<Phenotype<EnumGene<Integer>, Vec<int[]>>> estimatedPopulation = null;

        logger.debug("Number of distinct solutions in population: " + population.stream().distinct().count());

        if(this.optimization.isTraceFitnessEvolution()) {
            fitnessEvolutionStep = new FitnessEvolutionStep<>();

            logger.debug("Adding fitness evolution to statistics");
            this.optimization.getStatistics().getFitnessEvolution().add(fitnessEvolutionStep);

            if(generation.isPresent()) {
                fitnessEvolutionStep.setGeneration(generation.get().intValue());

                logger.debug("Tracing fitness evolution. Generation: " + fitnessEvolutionStep.getGeneration());
            }
        }

        PopulationEvaluation<Vec<int[]>> evaluation = evaluatePopulation(population, fitnessEvolutionStep);
        estimatedPopulation = estimatePopulation(population, fitnessEvolutionStep, evaluation);
        setFitnessEstimationStep(estimatedPopulation, fitnessEvolutionStep, evaluation.evaluatedPopulation);

        logger.debug("Devaluing invalid solutions.");
        estimatedPopulation = estimatedPopulation.stream().map(p -> {
            Map<? extends Flight, Slot> phenotypeMap = this.problem.decode(p.genotype()); // decode phenotype
            this.noPhenotypes++;
            long invalidAssignments = // determine how many invalid assignments the phenotype has
                    phenotypeMap.entrySet().stream().filter(e ->
                            e.getKey().getScheduledTime() != null &&
                                    e.getKey().getScheduledTime().isAfter(e.getValue().getTime())).count();

            Phenotype<EnumGene<Integer>, Vec<int[]>> phenotype = p;

            // if there are violations of the constraint, devalue the individual accordingly
            if(invalidAssignments > 0) {
                this.noInvalidPhenotypes++;
                this.noInvalidAssignments += invalidAssignments;
                phenotype = p.withFitness(Vec.of(
                        (int) invalidAssignments * DEVALUATOR, (int) invalidAssignments *DEVALUATOR
                ));
            }

            return phenotype;
        }).collect(Collectors.toList());

        logger.debug("Finished evaluation");

        logger.debug("Update statistics.");
        int[][] paretoFrontStats = getParetoFrontStats(evaluation.evaluatedPopulation);
        this.optimization.getStatistics().setFitnessFunctionInvocations(problem.getFitnessFunctionApplications());
        this.optimization.getStatistics().setParetoFront(paretoFrontStats);

        return ISeq.of(estimatedPopulation);
    }

    private int[][] getParetoFrontStats(List<Phenotype<EnumGene<Integer>, Vec<int[]>>> evaluation) {
        List<int[]> paretoFront = new LinkedList<>();
        for (Phenotype<EnumGene<Integer>, Vec<int[]>> solution: evaluation) {
            boolean dominated = false;
            for(Phenotype<EnumGene<Integer>, Vec<int[]>> other: evaluation) {
                if(other.fitness().data()[0] > solution.fitness().data()[0] && other.fitness().data()[1] > solution.fitness().data()[1]) {
                    dominated = true;
                }
            }
            if(!dominated) {
                paretoFront.add(new int[]{solution.fitness().data()[0], solution.fitness().data()[1]});
            }
        }
        return paretoFront.stream()
                .map(Arrays::toString)
                .distinct()
                .map(str -> Arrays.stream(str.substring(1, str.length() - 1).split(", "))
                        .mapToInt(Integer::parseInt)
                        .toArray())
                .toArray(int[][]::new);
    }

    @Override
    protected abstract PopulationEvaluation<Vec<int[]>> evaluatePopulation(Seq<Phenotype<EnumGene<Integer>, Vec<int[]>>> population, FitnessEvolutionStep<double[]> fitnessEvolutionStep);

    /**
     * Convert the population from the Jenetics native representation to the array format required by the
     * Privacy Engine.
     * @param population the population in Jenetics representation
     * @return the population in array format required by Privacy Engine
     */
    public Integer[][] convertPopulationToArray(Seq<Phenotype<EnumGene<Integer>, Vec<int[]>>> population) {
        return population.asList().stream()
                .map(phenotype -> this.problem.decode(phenotype.genotype()))
                .map(map ->{
                            // 1. Get the slots ordered ascending by time
                            // 2. Replace the flights in the flight sequence by the index of their assigned slot
                            var orderedSlots = map.entrySet().stream().sorted(Map.Entry.comparingByValue()).map(Map.Entry::getValue).toList();
                            return this.problem.getFlights().stream().map(flight -> orderedSlots.indexOf(map.get(flight))).toArray(Integer[]::new);
                        }
                ).toArray(Integer[][]::new);
    }

    /**
     * Takes the unevaluated population and returns the ordererd candidates and the maximum fitness
     * @param population the unevaluated population
     * @param fitnessEvolutionStep the evolution step of this generation
     * @return the ordered population
     */
    protected PopulationEvaluation<Vec<int[]>> evaluatePopulationOrder(Seq<Phenotype<EnumGene<Integer>, Vec<int[]>>> population, FitnessEvolutionStep<double[]> fitnessEvolutionStep){
        List<Phenotype<EnumGene<Integer>, Vec<int[]>>> evaluatedPopulation;
        Phenotype<EnumGene<Integer>, Vec<int[]>> firstBest;
        Phenotype<EnumGene<Integer>, Vec<int[]>> secondBest;

        if(this.optimization.getMode() == OptimizationMode.PRIVACY_PRESERVING) {
            logger.debug("Running in privacy-preserving mode: Evaluate the population using the Privacy Engine.");

            logger.debug("Convert population to format required by Privacy Engine.");
            Integer[][] input = this.convertPopulationToArray(population);

            // TODO: generation needs to be ordered twice for the different fitness values for now same call twice
            logger.debug("Invoke the Privacy Engine service to evaluate population.");
            PopulationOrderDTO orderFirstAttribute =
                    this.optimization.getPrivacyEngineService().computePopulationOrder(this.optimization, input);

            int[] firstOrder = orderFirstAttribute.getOrder();

            if(secondObfuscated) {
                PopulationOrderDTO orderSecondAttribute =
                        this.optimization.getPrivacyEngineService().computePopulationOrder(this.optimization, input);

                int[] secondOrder = orderSecondAttribute.getOrder();

                evaluatedPopulation = IntStream.range(0, firstOrder.length)
                        .mapToObj(i -> population.get(i).withFitness(Vec.of(firstOrder[i], secondOrder[i])))
                        .collect(Collectors.toList());
            } else {
                evaluatedPopulation = IntStream.range(0, firstOrder.length)
                        .mapToObj(i -> population.get(i).withFitness(Vec.of(firstOrder[i], population.get(i).fitness().data()[0])))
                        .collect(Collectors.toList());
            }

            firstBest = evaluatedPopulation.stream()
                    .max(Comparator.comparingInt(p -> p.fitness().data()[0])).get();

            secondBest = evaluatedPopulation.stream()
                    .max(Comparator.comparingInt(p -> p.fitness().data()[1])).get();

            logger.debug("Convert the population order received from the Privacy Engine to the format required by Jenetics.");

            logger.debug("Maximum fitness in generation according to Privacy Engine is " + firstBest.fitness().data()[0] + ".");
            logger.debug("Maximum fitness in generation according to Privacy Engine is " + secondBest.fitness().data()[1] + ".");
        } else {
            logger.debug("Running in non-privacy-preserving mode: Evaluate the population using the submitted weights.");
            long start = System.currentTimeMillis();
            evaluatedPopulation =
                    population.stream()
                            .map(phenotype -> phenotype.withFitness(problem.fitness(phenotype.genotype())))
                            .sorted((p1, p2) -> Integer.compare(p2.fitness().data()[0], p1.fitness().data()[0]))
                            .toList();

            firstBest = evaluatedPopulation.stream()
                    .max(Comparator.comparingInt(p -> p.fitness().data()[0])).get();

            secondBest = evaluatedPopulation.stream()
                    .max(Comparator.comparingInt(p -> p.fitness().data()[1])).get();

            logger.debug("Actual minimum fitness of the population: " + evaluatedPopulation.get(evaluatedPopulation.size() - 1).fitness());

            setEvolutionStep(fitnessEvolutionStep, evaluatedPopulation);
        }

        PopulationEvaluation<Vec<int[]>> evaluation = new PopulationEvaluation<>();
        evaluation.evaluatedPopulation = evaluatedPopulation;
        evaluation.maxFitness = firstBest.fitness().data()[0];
        evaluation.maxFitnessTwo = secondBest.fitness().data()[1];
        evaluation.bestGenotype = firstBest.genotype();
        evaluation.bestGenotypeTwo = secondBest.genotype();
        return evaluation;
    }

    protected void setFitnessEstimationStep(List<Phenotype<EnumGene<Integer>, Vec<int[]>>> estimatedPopulation,
                                            FitnessEvolutionStep<double[]> fitnessEvolutionStep,
                                            List<Phenotype<EnumGene<Integer>, Vec<int[]>>> evaluatedPopulation){
        // only include the Pareto front in the fitness step
        if(fitnessEvolutionStep != null) {
            fitnessEvolutionStep.setEstimatedPopulation(
                    estimatedPopulation.stream()
                            .map(phenotype -> {
                                double[] fitness = new double[2];
                                fitness[0] = phenotype.fitness().data()[0];
                                fitness[1] = phenotype.fitness().data()[1];
                                return fitness;
                            }) // Convert to strings for distinct comparison
                            .map(Arrays::toString)
                            .distinct()
                            .map(s -> Arrays.stream(s.substring(1, s.length() - 1).split(", "))
                                    .mapToDouble(Double::parseDouble)
                                    .toArray())
                            .filter(p -> isDominated(p, estimatedPopulation)).toArray(double[][]::new)
            );

            setEvolutionStep(fitnessEvolutionStep, evaluatedPopulation);
            logger.debug("Size of estimated population: " + fitnessEvolutionStep.getEstimatedPopulation().length);
        }
    }

    protected void setEvolutionStep(FitnessEvolutionStep<double[]> fitnessEvolutionStep,
                                      List<Phenotype<EnumGene<Integer>, Vec<int[]>>> evaluatedPopulation) {
        fitnessEvolutionStep.setEvaluatedPopulation(
                evaluatedPopulation.stream()
                        .map(p -> p.fitness().data())
                        .map(f -> {
                            double[] newValues = new double[2];
                            newValues[0] = f[0];
                            newValues[1] = f[1];
                            return newValues;
                        })
                        .map(Arrays::toString)
                        .distinct()
                        .map(s -> Arrays.stream(s.substring(1, s.length() - 1).split(", "))
                                .mapToDouble(Double::parseDouble)
                                .toArray())
                        .filter(p -> isDominated(p, evaluatedPopulation)).toArray(double[][]::new)
        );
    }

    private static boolean isDominated(double[] p, List<Phenotype<EnumGene<Integer>, Vec<int[]>>> population) {
        boolean dominated = false;
        for(Phenotype<EnumGene<Integer>, Vec<int[]>> gen : population) {
            if((gen.fitness().data()[0] > p[0] && gen.fitness().data()[1] >= p[1])
                    || (gen.fitness().data()[0] >= p[0] && gen.fitness().data()[1] > p[1])){
                dominated = true;
            }
        }
        return !dominated;
    }

    public void setSecondObfuscated(boolean secondObfuscated) {
        this.secondObfuscated = secondObfuscated;
    }
}
