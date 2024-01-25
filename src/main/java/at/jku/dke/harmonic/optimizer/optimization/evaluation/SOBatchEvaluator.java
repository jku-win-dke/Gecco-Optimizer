package at.jku.dke.harmonic.optimizer.optimization.evaluation;

import at.jku.dke.harmonic.optimizer.domain.Flight;
import at.jku.dke.harmonic.optimizer.domain.Slot;
import at.jku.dke.harmonic.optimizer.optimization.OptimizationMode;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.FitnessEvolutionStep;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsSO.SOJeneticsOptimization;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsSO.SOSlotAllocationProblem;
import at.jku.dke.harmonic.privacyEngine.dto.PopulationOrderDTO;
import io.jenetics.EnumGene;
import io.jenetics.Genotype;
import io.jenetics.Phenotype;
import io.jenetics.util.ISeq;
import io.jenetics.util.Seq;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Abstract super-class of all batch evaluators
 */
public abstract class SOBatchEvaluator extends BatchEvaluator<Integer, Double, SOJeneticsOptimization, SOSlotAllocationProblem> {
    private static final Logger logger = LogManager.getLogger();

    /**
     * Used in NON_PRIVACY_PRESERVING mode when useActualFitnessValues is false, to verify if fitness has been improved in a given generation.
     */
    protected long actualMaxFitness;
    /**
     * Used in conjunction with useActualFitnessValues = false and holds the current increment of the obfuscated base fitness value.
     */
    protected long fitnessIncrement;

    /**
     *
     * @param problem the slot allocation problem
     * @param optimization the Jenetics optimization run
     */
    public SOBatchEvaluator(SOSlotAllocationProblem problem, SOJeneticsOptimization optimization) {
        super(problem, optimization);
        this.actualMaxFitness = Integer.MIN_VALUE;
        this.fitnessIncrement = 1;
    }

    /**
     * Takes a population of (unevaluated) candidate solutions and returns a sequence of evaluated solutions.
     * @param population the population of candidate solutions
     * @return an evaluated population
     */
    @Override
    public ISeq<Phenotype<EnumGene<Integer>, Integer>> eval(Seq<Phenotype<EnumGene<Integer>, Integer>> population) {
        logger.debug("Starting population evaluation ...");
        this.noGenerations++;
        Optional<Long> generation = population.stream().map(Phenotype::generation).max(Long::compareTo);
        if(isDeduplicate){
            if(deduplicate(population, generation.get())) {
                return ISeq.of(population
                        .stream()
                        .map(p -> p.withFitness(-1))
                        .collect(Collectors.toList()));
            }
        }
        noGenerationsEvaluated++;

        List<Phenotype<EnumGene<Integer>, Integer>> estimatedPopulation;

        FitnessEvolutionStep<Double> fitnessEvolutionStep = null;

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

        PopulationEvaluation<Integer> evaluation = evaluatePopulation(population, fitnessEvolutionStep);

        estimatedPopulation = estimatePopulation(population, fitnessEvolutionStep, evaluation);

        if(fitnessEvolutionStep != null) {
            fitnessEvolutionStep.setEstimatedPopulation(
                    estimatedPopulation.stream()
                            .map(phenotype -> (double) phenotype.fitness())
                            .toArray(Double[]::new)
            );
            logger.debug("Size of estimated population: " + fitnessEvolutionStep.getEstimatedPopulation().length);
        }

        logger.debug("Devaluing invalid solutions.");
        estimatedPopulation = estimatedPopulation.stream().map(p -> {
            Map<Flight, Slot> phenotypeMap = this.problem.decode(p.genotype()); // decode phenotype
            this.noPhenotypes++;
            long invalidAssignments = // determine how many invalid assignments the phenotype has
                    phenotypeMap.entrySet().stream().filter(e ->
                            e.getKey().getScheduledTime() != null &&
                                    e.getKey().getScheduledTime().isAfter(e.getValue().getTime())).count();

            Phenotype<EnumGene<Integer>, Integer> phenotype = p;

            // if there are violations of the constraint, devalue the individual accordingly
            if(invalidAssignments > 0) {
                this.noInvalidPhenotypes++;
                this.noInvalidAssignments += invalidAssignments;
                phenotype = p.withFitness((int) invalidAssignments * DEVALUATOR);
            }

            return phenotype;
        }).collect(Collectors.toList());

        if(evaluation.maxFitness >= this.optimization.getStatistics().getMaximumFitness() && estimatedPopulation != null) {
            logger.debug("Best fitness of current generation better than current best fitness. Attaching intermediate result to the optimization run.");
            this.optimization.setResults(
                    estimatedPopulation.stream().distinct().map(phenotype -> this.problem.decode(phenotype.genotype())).toList()
            );

            // set the optimization's maximum fitness to this generation's maximum fitness
            this.optimization.getStatistics().setMaximumFitness((int) evaluation.maxFitness);
        }

        logger.debug("Finished evaluation");

        logger.debug("Update statistics.");
        this.optimization.getStatistics().setFitnessFunctionInvocations(problem.getFitnessFunctionApplications());
        this.optimization.getStatistics().setResultFitness(this.optimization.getStatistics().getMaximumFitness());

        return ISeq.of(estimatedPopulation);
    }

    /**
     * Takes the unevaluated population and returns the ordererd candidates and the maximum fitness
     * @param population the unevaluated population
     * @param fitnessEvolutionStep the evolution step of this generation
     * @return the ordered population
     */
    protected PopulationEvaluation<Integer> evaluatePopulationOrder(Seq<Phenotype<EnumGene<Integer>, Integer>> population, FitnessEvolutionStep<Double> fitnessEvolutionStep){
        List<Phenotype<EnumGene<Integer>, Integer>> evaluatedPopulation;
        Genotype<EnumGene<Integer>> bestGenotype = null;
        double maxFitness;

        if(this.optimization.getMode() == OptimizationMode.PRIVACY_PRESERVING) {
            logger.debug("Running in privacy-preserving mode: Evaluate the population using the Privacy Engine.");

            logger.debug("Convert population to format required by Privacy Engine.");
            Integer[][] input = this.convertPopulationToArray(population);

            logger.debug("Invoke the Privacy Engine service to evaluate population.");
            PopulationOrderDTO populationOrder =
                    this.optimization.getPrivacyEngineService().computePopulationOrder(this.optimization, input);

            int[] order = populationOrder.getOrder();

            logger.debug("Convert the population order received from the Privacy Engine to the format required by Jenetics.");

            evaluatedPopulation =
                    Arrays.stream(order).mapToObj(population::get)
                            .collect(Collectors.toList());

            maxFitness = populationOrder.getMaximum();
            logger.debug("Maximum fitness in generation according to Privacy Engine is " + maxFitness + ".");
        } else {
            evaluatedPopulation = evaluatePopulationOrderNonPrivacy(population, fitnessEvolutionStep);
            maxFitness = evaluatedPopulation.get(0).fitness();
            bestGenotype = evaluatedPopulation.get(0).genotype();
        }

        PopulationEvaluation<Integer> evaluation = new PopulationEvaluation<>();
        evaluation.evaluatedPopulation = evaluatedPopulation;
        evaluation.maxFitness = maxFitness;
        evaluation.bestGenotype = bestGenotype;
        return evaluation;
    }

    /**
     * Convert the population from the Jenetics native representation to the array format required by the
     * Privacy Engine.
     * @param population the population in Jenetics representation
     * @return the population in array format required by Privacy Engine
     */
    public Integer[][] convertPopulationToArray(Seq<Phenotype<EnumGene<Integer>, Integer>> population) {
        return population.asList().stream()
                  .map(phenotype -> this.problem.decode(phenotype.genotype()))
                  .map(map ->{
                              // 1. Get the slots ordered ascending by time
                              // 2. Replace the flights in the flight sequence by the index of their assigned slot
                              var orderedSlots = map.entrySet().stream().sorted(Entry.comparingByValue()).map(Entry::getValue).toList();
                              return this.problem.getFlights().stream().map(flight -> orderedSlots.indexOf(map.get(flight))).toArray(Integer[]::new);
                          }
                  ).toArray(Integer[][]::new);
    }

    protected List<Phenotype<EnumGene<Integer>, Integer>>evaluatePopulationOrderNonPrivacy(Seq<Phenotype<EnumGene<Integer>, Integer>> population, FitnessEvolutionStep<Double> fitnessEvolutionStep) {
        List<Phenotype<EnumGene<Integer>, Integer>> evaluatedPopulation;
        logger.debug("Running in non-privacy-preserving mode: Evaluate the population using the submitted weights.");
        evaluatedPopulation =
                population.stream()
                        .map(phenotype -> phenotype.withFitness(problem.fitness(phenotype.genotype())))
                        .sorted(Comparator.comparingInt(Phenotype::fitness))
                        .sorted(Comparator.reverseOrder())
                        .toList();

        double maxFitness = evaluatedPopulation.get(0).fitness();
        Genotype<EnumGene<Integer>> bestGenotype = evaluatedPopulation.get(0).genotype();

        setImprovement(maxFitness, population, evaluatedPopulation);

        logger.debug("Actual minimum fitness of the population: " + evaluatedPopulation.get(evaluatedPopulation.size() - 1).fitness());

        if(fitnessEvolutionStep != null) {
            fitnessEvolutionStep.setEvaluatedPopulation(
                    evaluatedPopulation.stream().map(phenotype -> (double) phenotype.fitness()).toArray(Double[]::new)
            );
            logger.debug("Tracing fitness evolution. Size of evaluated population: " + fitnessEvolutionStep.getEvaluatedPopulation().length);
        }
        return evaluatedPopulation;
    }

    protected void setImprovement(double maxFitness, Seq<Phenotype<EnumGene<Integer>, Integer>> population, List<Phenotype<EnumGene<Integer>, Integer>> evaluatedPopulation) {
        if(!useActualFitnessValues && maxFitness < this.optimization.getStatistics().getTheoreticalMaxFitness()){
            maxFitness = population.size();

            // Check if fitness has improved compared to current maximum.
            boolean isMaxFitnessIncreased = evaluatedPopulation.get(0).fitness() > actualMaxFitness;

            // Override max fitness used for estimation/optimization with dummy-value (first Evaluation (noGenerations == 1) is ignored by Jenetics)
            if(isMaxFitnessIncreased && noGenerations > 1){
                actualMaxFitness = evaluatedPopulation.get(0).fitness();
                // Add increment to dummy maxFitness to indicate improvement.
                maxFitness = maxFitness + fitnessIncrement;
                fitnessIncrement ++;
            }
        }
    }
}
