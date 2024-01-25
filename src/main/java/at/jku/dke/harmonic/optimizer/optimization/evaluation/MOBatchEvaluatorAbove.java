package at.jku.dke.harmonic.optimizer.optimization.evaluation;

import at.jku.dke.harmonic.optimizer.optimization.OptimizationMode;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.FitnessEvolutionStep;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsMO.MOJeneticsOptimization;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsMO.MOSlotAllocationProblem;
import at.jku.dke.harmonic.privacyEngine.dto.AboveIndividualsDTO;
import io.jenetics.EnumGene;
import io.jenetics.Genotype;
import io.jenetics.Phenotype;
import io.jenetics.ext.moea.Vec;
import io.jenetics.util.Seq;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class MOBatchEvaluatorAbove extends MOBatchEvaluator{

    private static final Logger logger = LogManager.getLogger();

    public MOBatchEvaluatorAbove(MOSlotAllocationProblem problem, MOJeneticsOptimization optimization) {

        super(problem, optimization);
    }

    /**
     * Estimates the population by assigning the maximum fitness to each evaluated phenotype.
     * All phenotypes that are not evaluated are assigned the minimum fitness.
     *
     * @param population the unevaluated population
     * @param fitnessEvolutionStep the evolution step of this generation
     * @param evaluation the result of the evaluation step
     * @return the estimated population
     */
    @Override
    protected List<Phenotype<EnumGene<Integer>, Vec<int[]>>> estimatePopulation(Seq<Phenotype<EnumGene<Integer>, Vec<int[]>>> population, FitnessEvolutionStep<double[]> fitnessEvolutionStep, PopulationEvaluation<Vec<int[]>> evaluation) {

        logger.debug("Actual maximum fitness of the population: " + evaluation.maxFitness);
        int secondMax = evaluation.evaluatedPopulation.stream()
                        .map(p -> p.fitness().data()[1]).min(Integer::compareTo).get();
        logger.debug("Actual maximum fitness of the population: " + secondMax);

        List<Phenotype<EnumGene<Integer>, Vec<int[]>>> estimatedPopulation = null;

        List<Genotype<EnumGene<Integer>>> evaluatedGenotypes =
                evaluation.evaluatedPopulation.stream().map(Phenotype::genotype).toList();

        if(secondObfuscated) {
            logger.debug("Assign each solution returned by the Privacy Engine the maximum fitness: {} and \t {}", evaluation.maxFitness, secondMax);
            // Add all evaluated individuals to the estimated population with the max fitness
            estimatedPopulation = population.stream()
                    .filter(phenotype -> evaluatedGenotypes.contains(phenotype.genotype()))
                    .map(phenotype -> phenotype.withFitness(Vec.of((int) evaluation.maxFitness, secondMax)))
                    .collect(Collectors.toList());

            // Increase the fitness of the best genotype in the population if possible to improve selection process
            if(evaluation.bestGenotype != null && evaluation.maxFitness < this.optimization.getStatistics().getTheoreticalMaxFitness()){
                logger.debug("Assigning higher fitness value to best genotype(s).");
                estimatedPopulation = estimatedPopulation.stream()
                        .map(phenotype -> phenotype.genotype().equals(evaluation.bestGenotype) ?
                                phenotype.withFitness(Vec.of((int) evaluation.maxFitness + 1, secondMax)) : phenotype)
                        .collect(Collectors.toList());
            }

            if(evaluation.bestGenotype != null && secondMax < this.optimization.getStatistics().getTheoreticalMaxFitnessTwo()){
                logger.debug("Assigning higher fitness value to best genotype(s).");
                estimatedPopulation = estimatedPopulation.stream()
                        .map(phenotype -> phenotype.genotype().equals(evaluation.bestGenotypeTwo) ?
                                phenotype.withFitness(Vec.of((int) evaluation.maxFitness, secondMax+1)) : phenotype)
                        .collect(Collectors.toList());
            }
        } else {
            logger.debug("Assign each solution returned by the Privacy Engine the maximum fitness for the first optimization target: {}", evaluation.maxFitness);
            // Add all evaluated individuals to the estimated population with the max fitness
            try{
                estimatedPopulation = evaluation.evaluatedPopulation.stream()
                                .map(p -> p.withFitness(Vec.of((int) evaluation.maxFitness, p.fitness().data()[1])))
                                .collect(Collectors.toList());
            } catch (Exception e) {
                e.getStackTrace();
            }


            // Increase the fitness of the best genotype in the population if possible to improve selection process
            if(evaluation.bestGenotype != null && evaluation.maxFitness < this.optimization.getStatistics().getTheoreticalMaxFitness()){
                logger.debug("Assigning higher fitness value to best genotype(s).");
                estimatedPopulation = estimatedPopulation.stream()
                        .map(phenotype -> phenotype.genotype().equals(evaluation.bestGenotype) ?
                                phenotype.withFitness(Vec.of((int) evaluation.maxFitness + 1, phenotype.fitness().data()[1])) : phenotype)
                        .collect(Collectors.toList());
            }
        }



        Optional<Genotype<EnumGene<Integer>>> bestGenotypeSecond = estimatedPopulation.stream()
                .sorted((p1, p2) -> Integer.compare(p2.fitness().data()[1], p1.fitness().data()[1]))
                .map(Phenotype::genotype)
                .findFirst();


        if(bestGenotypeSecond.isPresent() && secondMax < this.optimization.getStatistics().getTheoreticalMaxFitnessTwo()){
            logger.debug("Assigning higher fitness value to best genotype(s).");
            estimatedPopulation = estimatedPopulation.stream()
                    .map(phenotype -> phenotype.genotype().equals(bestGenotypeSecond.get()) ?
                            phenotype.withFitness(Vec.of((int) evaluation.maxFitness, secondMax + 1)) : phenotype)
                    .collect(Collectors.toList());
        }

        // Add estimated individuals to the collection until the size of the estimated population equals the size of the population (Jenetics requirement)
        while(estimatedPopulation.size() < population.size()){
            estimatedPopulation.addAll(estimatedPopulation);
        }

        estimatedPopulation = estimatedPopulation
                .stream()
                .limit(population.size())
                .sorted((p1, p2) -> Integer.compare(p2.fitness().data()[0], p1.fitness().data()[0]))
                .collect(Collectors.toList());

        logger.debug("Assigned estimated fitness values.");
        return  estimatedPopulation;
    }

    /**
     * Evaluates all phenotypes according to the threshold
     *
     * @param population the unevaluated population
     * @param fitnessEvolutionStep the evolution step for this generation
     * @return the evaluated population
     */
    @Override
    protected PopulationEvaluation<Vec<int[]>> evaluatePopulation(Seq<Phenotype<EnumGene<Integer>, Vec<int[]>>> population, FitnessEvolutionStep<double[]> fitnessEvolutionStep) {
        return evaluatePopulationAbove(population, fitnessEvolutionStep);
    }

    /**
     * Takes the unevaluated population and returns all individuals exceeding a defined threshold
     * @param population the unevaluated population
     * @param fitnessEvolutionStep the evolution step of this generation
     * @return the evaluated population
     */
    protected PopulationEvaluation<Vec<int[]>> evaluatePopulationAbove(Seq<Phenotype<EnumGene<Integer>, Vec<int[]>>> population, FitnessEvolutionStep<double[]> fitnessEvolutionStep){
        final List<Phenotype<EnumGene<Integer>, Vec<int[]>>> evaluatedPopulation;
        Phenotype<EnumGene<Integer>, Vec<int[]>> firstBest;
        Phenotype<EnumGene<Integer>, Vec<int[]>> secondBest;
        PopulationEvaluation<Vec<int[]>> evaluation = null;
        double maxFitness;

        if(this.optimization.getMode() == OptimizationMode.PRIVACY_PRESERVING) {
            logger.debug("Running in privacy-preserving mode: Evaluate the population using the Privacy Engine.");

            logger.debug("Convert population to format required by Privacy Engine.");
            Integer[][] input = this.convertPopulationToArray(population);

            logger.debug("Invoke the Privacy Engine service to get phenotypes exceeding threshold.");
            // TODO: distinguish ABOVE from TOP
            AboveIndividualsDTO individualsAboveFirst =
                    this.optimization.getPrivacyEngineService().computeIndividualsAbove(this.optimization, input);

            if(secondObfuscated) {
                // TODO: for now same call
                AboveIndividualsDTO individualsAboveSecond =
                        this.optimization.getPrivacyEngineService().computeIndividualsAbove(this.optimization, input);
            }



            // convert between Privacy Engine's return format and format required by Optimizer
            logger.debug("Convert returned population to format required by Jenetics.");
            logger.debug(" Using size of population as base for the maximum fitness in generation.");
            logger.debug("Returned index of best genotype is {}",  (individualsAboveFirst.getHighest() != null ? individualsAboveFirst.getHighest() : "NULL") + ".");
            logger.debug("Has max fitness improved: {}",  (individualsAboveFirst.getBest() != null ? individualsAboveFirst.getBest() : "NULL") + ".");

            maxFitness = population.size();

            if(Boolean.TRUE.equals(individualsAboveFirst.getBest())){
                maxFitness += this.fitnessIncrement;
                this.fitnessIncrement++;
                logger.debug("Increased max fitness to {}{}",  maxFitness, ".");
            }

            evaluatedPopulation = Arrays.stream(individualsAboveFirst.getIndices())
                    .map(population::get)
                    .map(phenotype -> phenotype.withFitness(problem.fitness(phenotype.genotype())))
                    .toList();

            firstBest = evaluatedPopulation.stream()
                    .max(Comparator.comparingInt(p -> p.fitness().data()[0])).get();

            secondBest = evaluatedPopulation.stream()
                    .max(Comparator.comparingInt(p -> p.fitness().data()[1])).get();

            evaluation = new PopulationEvaluation<>();
            evaluation.evaluatedPopulation = evaluatedPopulation;
            evaluation.bestGenotype = firstBest.genotype();
            evaluation.bestGenotypeTwo = secondBest.genotype();
            evaluation.maxFitness = maxFitness;
            evaluation.maxFitnessTwo = secondBest.fitness().data()[1];
        }else{
            evaluation = evaluatePopulationOrder(population, fitnessEvolutionStep);
            double[] thresholds = getThreshold(evaluation);
            if(secondObfuscated) {
                evaluation.evaluatedPopulation = evaluation.evaluatedPopulation.stream()
                        .filter(phenotype -> phenotype.fitness().data()[0] >= thresholds[0] ||
                                phenotype.fitness().data()[1] >= thresholds[1]).toList();
            } else {
                evaluation.evaluatedPopulation = evaluation.evaluatedPopulation.stream()
                        .filter(phenotype -> phenotype.fitness().data()[0] >= thresholds[0])
                        .map(phenotype -> phenotype.withFitness(problem.fitness(phenotype.genotype())))
                        .toList();
            }

        }

        return evaluation;
    }
    /**
     * Returns the threshold for the evaluation
     * @param evaluation the evaluated population
     * @return the threshold
     */
    protected abstract double[] getThreshold(PopulationEvaluation<Vec<int[]>> evaluation);

}
