package optimizer.optimization.evaluation;

import com.optimization.data.privacyEngine.dto.FitnessQuantilesDTO;
import optimizer.optimization.OptimizationMode;
import optimizer.optimization.jenetics.FitnessEvolutionStep;
import optimizer.optimization.jenetics.jeneticsMO.MOJeneticsOptimization;
import optimizer.optimization.jenetics.jeneticsMO.MOSlotAllocationProblem;
import io.jenetics.EnumGene;
import io.jenetics.Phenotype;
import io.jenetics.ext.moea.Vec;
import io.jenetics.util.Seq;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

public class MOBatchEvaluatorFitnessRangeQuantiles extends MOBatchEvaluator{

    private static final Logger logger = LogManager.getLogger();

    public MOBatchEvaluatorFitnessRangeQuantiles(MOSlotAllocationProblem problem, MOJeneticsOptimization optimization) {
        super(problem, optimization);
    }

    @Override
    protected List<Phenotype<EnumGene<Integer>, Vec<int[]>>> estimatePopulation(Seq<Phenotype<EnumGene<Integer>, Vec<int[]>>> population, FitnessEvolutionStep<double[]> fitnessEvolutionStep, PopulationEvaluation<Vec<int[]>> evaluation) {
        List<Phenotype<EnumGene<Integer>, Vec<int[]>>> estimatedPopulation = null;
        List<Phenotype<EnumGene<Integer>, Vec<int[]>>> estimatedPopulationStream;

        logger.debug("Actual maximum fitness of the population: " + evaluation.maxFitness);

        double minFitness = evaluation.maxFitness - (2 * Math.abs(evaluation.maxFitness)) - (Math.abs(evaluation.maxFitness) * 0.0001); // 0.0001 to avoid division by zero when calculating delta in linear estimator
        double minFitnessTwo = evaluation.maxFitnessTwo - (2 * Math.abs(evaluation.maxFitnessTwo)) - (Math.abs(evaluation.maxFitnessTwo) * 0.0001); // 0.0001 to avoid division by zero when calculating delta in linear estimator


        logger.debug("Estimated minimum fitness of the population: " + minFitness);

        if(this.optimization.getFitnessEstimator() != null) {

            int estimatedPopulationSize = this.optimization.getFitnessPrecision();
            logger.debug("Estimated population size: " + estimatedPopulationSize);

            // for this we probably need a change of the Privacy Engine interface when running in privacy-preserving mode
            logger.debug("Getting estimated fitness value from estimator: " + this.optimization.getFitnessEstimator().getClass());
            double[] estimatedFitnessValues =
                    this.optimization.getFitnessEstimator().estimateFitnessDistribution(estimatedPopulationSize, evaluation.maxFitness, minFitness);

            logger.debug("Assign the estimated fitness of the phenotype's fitness quantile");
            final Map<Phenotype<EnumGene<Integer>, Vec<int[]>>, Vec<int[]>> finalFitnessQuantilesPopulation = evaluation.fitnessQuantilesPopulation;

            if(secondObfuscated) {
                double[] estimatedFitnessValuesTwo =
                        this.optimization.getFitnessEstimator().estimateFitnessDistribution(estimatedPopulationSize, evaluation.maxFitnessTwo, minFitnessTwo);
                estimatedPopulationStream = finalFitnessQuantilesPopulation.entrySet().stream()
                        .map(e -> {
                            int fitnessOne = (int) estimatedFitnessValues[e.getValue().data()[0]];
                            int fitnessTwo = (int) estimatedFitnessValuesTwo[e.getValue().data()[1]];
                            return e.getKey().withFitness(Vec.of(fitnessOne, fitnessTwo));
                        }).toList();
            } else {
                estimatedPopulationStream = finalFitnessQuantilesPopulation.entrySet().stream()
                        .map(e -> {
                            int fitnessOne = (int) estimatedFitnessValues[e.getValue().data()[0]];
                            int fitnessTwo = e.getKey().fitness().data()[1];
                            return e.getKey().withFitness(Vec.of(fitnessOne, fitnessTwo));
                        }).toList();
            }

            logger.debug("Assigned the fitness quantiles");

            // Fill up the population stream if it contains to few elements
            int i = 0;
            while(estimatedPopulationStream.size() < evaluation.evaluatedPopulation.size()) {
                estimatedPopulationStream = new ArrayList<>(estimatedPopulationStream);
                estimatedPopulationStream.add(estimatedPopulationStream.get(i));
                i++;
            }

            estimatedPopulation = estimatedPopulationStream.stream()
                    .sorted((p1, p2) -> Integer.compare(p2.fitness().data()[0], p1.fitness().data()[0]))
                    .toList();

            logger.debug("Assigned estimated fitness values.");
        } else {
            logger.debug("No estimator specified. Using exact fitness (if available).");

            if(this.optimization.getMode() == OptimizationMode.NON_PRIVACY_PRESERVING){
                logger.debug("Running in non-privacy-preserving mode. Exact fitness values available.");
                estimatedPopulation = evaluation.evaluatedPopulation;
            }
        }
        return estimatedPopulation;
    }

    @Override
    protected PopulationEvaluation<Vec<int[]>> evaluatePopulation(Seq<Phenotype<EnumGene<Integer>, Vec<int[]>>> population, FitnessEvolutionStep<double[]> fitnessEvolutionStep) {
        return evaluatePopulationFitnessQuantiles(population);
    }

    protected PopulationEvaluation<Vec<int[]>> evaluatePopulationFitnessQuantiles(Seq<Phenotype<EnumGene<Integer>, Vec<int[]>>> population){
        final List<Phenotype<EnumGene<Integer>, Vec<int[]>>> evaluatedPopulation;
        Map<Phenotype<EnumGene<Integer>, Vec<int[]>>, Vec<int[]>> fitnessQuantilesPopulation = new HashMap<>(750);
        Phenotype<EnumGene<Integer>, Vec<int[]>> firstBest;
        Phenotype<EnumGene<Integer>, Vec<int[]>> secondBest;

        if(this.optimization.getMode() == OptimizationMode.PRIVACY_PRESERVING) {
            // TODO implement privacy engine setting
            logger.debug("Running in privacy-preserving mode: Evaluate the population using the Privacy Engine.");

            logger.debug("Convert population to format required by Privacy Engine.");
            Integer[][] input = this.convertPopulationToArray(population);

            logger.debug("Invoke the Privacy Engine service to get fitness quantiles of population.");
            FitnessQuantilesDTO fitnessQuantiles =
                    this.optimization.getPrivacyEngineService().computeFitnessQuantiles(this.optimization, input);

            // TODO convert between Privacy Engine's return format and format required by Optimizer
            evaluatedPopulation = null;

            firstBest = population.get(0);
            secondBest = population.get(0);

        } else {
            evaluatedPopulation =
                    population.stream()
                            .map(phenotype -> phenotype.withFitness(problem.fitness(phenotype.genotype())))
                            .sorted((p1, p2) -> Integer.compare(p2.fitness().data()[0], p1.fitness().data()[0]))
                            .toList();

            firstBest = evaluatedPopulation.stream().max(Comparator.comparingInt(p -> p.fitness().data()[0])).get();

            double firstMin = evaluatedPopulation.stream().map(p -> p.fitness().data()[0]).min(Comparator.comparingInt(p -> p)).get();

            double firstDifference = firstBest.fitness().data()[0] - firstMin;

            double firstWindowLength = (firstDifference / this.optimization.getFitnessPrecision()) + 0.01;

            logger.debug("first Diff: " + firstDifference + ", first windowLength: " + firstWindowLength);

            Map<Integer, List<Phenotype<EnumGene<Integer>, Vec<int[]>>>> quantilePopulationsOne = evaluatedPopulation.stream()
                    .collect(Collectors.groupingBy(phenotype -> (int) ((firstBest.fitness().data()[0] - (double) phenotype.fitness().data()[0]) / firstWindowLength)));

            logger.debug("Map phenotype to quantile");

            for(Map.Entry<Integer, List<Phenotype<EnumGene<Integer>, Vec<int[]>>>> e : quantilePopulationsOne.entrySet()) {
                for(Phenotype<EnumGene<Integer>, Vec<int[]>> p : e.getValue()) {
                    fitnessQuantilesPopulation.put(p, Vec.of(e.getKey()));
                }
            }

            secondBest = evaluatedPopulation.stream()
                    .max(Comparator.comparingInt(p -> p.fitness().data()[1])).get();

            if(secondObfuscated) {
                double secondMin = evaluatedPopulation.stream()
                        .map(p -> p.fitness().data()[1])
                        .min(Integer::compareTo).get();
                double secondDifference = secondBest.fitness().data()[1] - secondMin;
                double secondWindowLength = (secondDifference / this.optimization.getFitnessPrecision()) + 0.01;
                logger.debug("second Diff: " + secondDifference + ", second windowLength: " + secondWindowLength);
                Map<Integer, List<Phenotype<EnumGene<Integer>, Vec<int[]>>>> quantilePopulationsTwo = evaluatedPopulation.stream()
                        .collect(Collectors.groupingBy(phenotype -> (int) ((secondBest.fitness().data()[1] - (double) phenotype.fitness().data()[1]) / secondWindowLength)));
                for(Map.Entry<Integer, List<Phenotype<EnumGene<Integer>, Vec<int[]>>>> entry : quantilePopulationsTwo.entrySet()) {
                    for(Phenotype<EnumGene<Integer>, Vec<int[]>> p : entry.getValue()) {
                        Vec<int[]> data = Vec.of(fitnessQuantilesPopulation.get(p).data()[0],entry.getKey());
                        fitnessQuantilesPopulation.put(p, data);
                    }
                }
            }

            logger.debug("Mapped phenotypes to quantile");
        }

        PopulationEvaluation<Vec<int[]>> evaluation = new PopulationEvaluation<>();
        evaluation.evaluatedPopulation = evaluatedPopulation;
        evaluation.fitnessQuantilesPopulation = fitnessQuantilesPopulation;
        evaluation.bestGenotype = firstBest.genotype();
        evaluation.bestGenotypeTwo = secondBest.genotype();
        evaluation.maxFitness = firstBest.fitness().data()[0];
        evaluation.maxFitnessTwo = secondBest.fitness().data()[1];
        return evaluation;
    }
}
