package optimizer.optimization.jenetics;

import com.optimization.data.optimizer.service.dto.OptimizationResultDTO;
import optimizer.domain.Flight;
import optimizer.domain.Slot;
import optimizer.optimization.*;
import optimizer.optimization.fitnessEstimation.FitnessEstimator;
import optimizer.service.PrivacyEngineService;
import io.jenetics.*;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.util.ISeq;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class JeneticsOptimization<R,
        P extends SlotAllocationProblem,
        S extends JeneticsOptimizationStatistics,
        C extends JeneticsOptimizationConfiguration,
        F extends Flight> extends Optimization<R, F> {

    private static final Logger logger = LogManager.getLogger();
    private FitnessEstimator fitnessEstimator;
    private OptimizationMode mode = OptimizationMode.NON_PRIVACY_PRESERVING;
    private OptimizationStatus status = OptimizationStatus.CREATED;
    private String privacyEngineEndpoint = null;
    private PrivacyEngineService privacyEngineService;
    private FitnessMethod fitnessMethod = null;
    private int fitnessPrecision = Integer.MIN_VALUE; // TODO: only maybe only for SOO
    private boolean traceFitnessEvolution = false;
    private String[] initialFlightSequence = null;
    private Integer[][] convertedResults = null;
    private boolean secondObfuscated = false;

    protected C configuration = null;
    protected S statistics;
    protected P problem;

    public JeneticsOptimization(F[] flights, Slot[] slots) {
        super(flights, slots);
    }

    public static <T> Predicate<T> distinctByAttribute(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    public FitnessEstimator getFitnessEstimator() {
        return fitnessEstimator;
    }

    public void setFitnessEstimator(FitnessEstimator fitnessEstimator) {
        this.fitnessEstimator = fitnessEstimator;
    }

    public OptimizationMode getMode() {
        return mode;
    }

    public void setMode(OptimizationMode mode) {
        this.mode = mode;
    }

    public OptimizationStatus getStatus() {
        return status;
    }

    public void setStatus(OptimizationStatus status) {
        this.status = status;
    }

    public String getPrivacyEngineEndpoint() {
        return privacyEngineEndpoint;
    }

    public void setPrivacyEngineEndpoint(String privacyEngineEndpoint) {
        this.privacyEngineEndpoint = privacyEngineEndpoint;
    }

    public PrivacyEngineService getPrivacyEngineService() {
        return privacyEngineService;
    }

    public void setPrivacyEngineService(PrivacyEngineService privacyEngineService) {
        this.privacyEngineService = privacyEngineService;
    }

    public FitnessMethod getFitnessMethod() {
        return fitnessMethod;
    }

    public void setFitnessMethod(FitnessMethod fitnessMethod) {
        this.fitnessMethod = fitnessMethod;
    }

    public int getFitnessPrecision() {
        return fitnessPrecision;
    }

    public void setFitnessPrecision(int fitnessPrecision) {
        this.fitnessPrecision = fitnessPrecision;
    }

    public boolean isTraceFitnessEvolution() {
        return traceFitnessEvolution;
    }

    public void setTraceFitnessEvolution(boolean traceFitnessEvolution) {
        this.traceFitnessEvolution = traceFitnessEvolution;
    }

    public String[] getInitialFlightSequence() {
        return initialFlightSequence;
    }

    public void setInitialFlightSequence(String[] initialFlightSequence) {
        this.initialFlightSequence = initialFlightSequence;
    }

    public void setConfiguration(C configuration) {
        this.configuration = configuration;
    }

    public S getStatistics() {
        return statistics;
    }

    public void setStatistics(S s) {
        this.statistics = s;
    }

    public C getDefaultConfiguration(){
        C defaultConfiguration = createNewConfig();

        Map<String, Object> terminationConditionParameters = new HashMap<>();
        terminationConditionParameters.put("BY_EXECUTION_TIME", 60);

        defaultConfiguration.setMaximalPhenotypeAge(70);
        defaultConfiguration.setPopulationSize(50);
        defaultConfiguration.setOffspringFraction(0.6);
        defaultConfiguration.setMutator("SWAP_MUTATOR");
        defaultConfiguration.setMutatorAlterProbability(0.2);
        defaultConfiguration.setCrossover("PARTIALLY_MATCHED_CROSSOVER");
        defaultConfiguration.setCrossoverAlterProbability(0.35);
        defaultConfiguration.setSurvivorsSelector("TOURNAMENT_SELECTOR");
        defaultConfiguration.setOffspringSelector("TOURNAMENT_SELECTOR");
        defaultConfiguration.setTerminationConditions(terminationConditionParameters);
        defaultConfiguration.setDeduplicate(false);

        return defaultConfiguration;
    }

    protected abstract C createNewConfig();

    public C getConfiguration() {
        return this.configuration;
    }

    public void newConfiguration(Map<String, Object> parameters) throws InvalidOptimizationParameterTypeException {
        C newConfiguration = createNewConfig();

        Object maximalPhenotypeAge = parameters.get("maximalPhenotypeAge");
        Object populationSize = parameters.get("populationSize");
        Object offspringFraction = parameters.get("offspringFraction");
        Object mutator = parameters.get("mutator");
        Object mutatorAlterProbability = parameters.get("mutatorAlterProbability");
        Object crossover = parameters.get("crossover");
        Object crossoverAlterProbability = parameters.get("crossoverAlterProbability");
        Object offspringSelector = parameters.get("offspringSelector");
        Object offspringSelectorParameter = parameters.get("offspringSelectorParameter");
        Object survivorsSelector = parameters.get("survivorsSelector");
        Object survivorsSelectorParameter = parameters.get("survivorsSelectorParameter");
        Object terminationConditions = parameters.get("terminationConditions");
        Object deduplicate = parameters.get("deduplicate");
        Object deduplicateMaxRetries = parameters.get("deduplicateMaxRetries");
        Object secondObfuscated = parameters.get("secondObfuscated");

        // set the parameters
        try {
            if (maximalPhenotypeAge != null) {
                newConfiguration.setMaximalPhenotypeAge((int) maximalPhenotypeAge);
            }
        } catch (Exception e) {
            throw new InvalidOptimizationParameterTypeException("maximalPhenotypeAge", Integer.class);
        }

        try {
            if (populationSize != null) {
                newConfiguration.setPopulationSize((int) populationSize);
            }
        } catch (Exception e) {
            throw new InvalidOptimizationParameterTypeException("populationSize", Integer.class);
        }

        try {
            if (offspringSelector != null) {
                newConfiguration.setOffspringSelector((String) offspringSelector);
            }
        } catch (Exception e) {
            throw new InvalidOptimizationParameterTypeException("offspringSelector", String.class);
        }

        try {
            if (offspringSelectorParameter != null) {
                newConfiguration.setOffspringSelectorParameter((Number) offspringSelectorParameter);
            }
        } catch (Exception e) {
            throw new InvalidOptimizationParameterTypeException("offspringSelectorParameter", Number.class);
        }

        try {
            if (offspringFraction != null) {
                newConfiguration.setOffspringFraction((double) offspringFraction);
            }
        } catch (Exception e) {
            throw new InvalidOptimizationParameterTypeException("offspringFraction", Double.class);
        }

        try {
            if (mutator != null) {
                newConfiguration.setMutator((String) mutator);
            }
        } catch (Exception e) {
            throw new InvalidOptimizationParameterTypeException("mutator", String.class);
        }

        try {
            if (mutatorAlterProbability != null) {
                newConfiguration.setMutatorAlterProbability((double) mutatorAlterProbability);
            }
        } catch (Exception e) {
            throw new InvalidOptimizationParameterTypeException("mutatorAlterProbability", Double.class);
        }

        try {
            if (crossover != null) {
                newConfiguration.setCrossover((String) crossover);
            }
        } catch (Exception e) {
            throw new InvalidOptimizationParameterTypeException("crossover", String.class);
        }

        try {
            if (crossoverAlterProbability != null) {
                newConfiguration.setCrossoverAlterProbability((double) crossoverAlterProbability);
            }
        } catch (Exception e) {
            throw new InvalidOptimizationParameterTypeException("crossoverAlterProbability", Double.class);
        }

        try {
            if (terminationConditions != null) {
                // if cast throws error, the error is caught
                @SuppressWarnings("unchecked")
                Map<String,Object> terminationConditionsMap = (Map<String,Object>) terminationConditions;
                newConfiguration.setTerminationConditions(terminationConditionsMap);
            }
        } catch (Exception e) {
            throw new InvalidOptimizationParameterTypeException("terminationConditions", Map.class);
        }

        try {
            if(survivorsSelector != null) {
                newConfiguration.setSurvivorsSelector((String) survivorsSelector);
            }
        } catch (Exception e) {
            throw new InvalidOptimizationParameterTypeException("survivorsSelector", String.class);
        }

        try {
            if (survivorsSelectorParameter != null) {
                logger.info("Submitted survivors selector parameter: " + survivorsSelectorParameter);
                newConfiguration.setSurvivorsSelectorParameter((Number) survivorsSelectorParameter);
            }
        } catch (Exception e) {
            throw new InvalidOptimizationParameterTypeException("survivorsSelectorParameter", Number.class);
        }


        try {
            if (deduplicate != null) {
                newConfiguration.setDeduplicate((boolean) deduplicate);
            }
        } catch (Exception e) {
            throw new InvalidOptimizationParameterTypeException("deduplicate", Boolean.class);
        }

        try {
            if (deduplicateMaxRetries != null) {
                newConfiguration.setDeduplicateMaxRetries((int) deduplicateMaxRetries);
            }
        } catch (Exception e) {
            throw new InvalidOptimizationParameterTypeException("deduplicateMaxRetries", Integer.class);
        }

        try {
            if(secondObfuscated != null) {
                newConfiguration.setSecondObfuscated((boolean) secondObfuscated);
            }
        } catch (Exception e) {
            throw new InvalidOptimizationParameterTypeException("secondObfuscated", Boolean.class);
        }


        // replace the configuration if no error was thrown
        this.configuration = newConfiguration;
    }

    protected InternConfig configurationWithoutMissingValues() {
        InternConfig internConfig = new InternConfig();
        if(this.getConfiguration() != null) {
            internConfig.populationSize = this.getConfiguration().getPopulationSize();
            if (internConfig.populationSize < 0) {
                internConfig.populationSize = this.getDefaultConfiguration().getPopulationSize();
            }

            internConfig.mutator = this.getConfiguration().getMutator();
            if (internConfig.mutator == null) {
                internConfig.mutator = this.getDefaultConfiguration().getMutator();
            }

            internConfig.crossover = this.getConfiguration().getCrossover();
            if (internConfig.crossover == null) {
                internConfig.crossover = this.getDefaultConfiguration().getCrossover();
            }

            internConfig.offspringSelector = this.getConfiguration().getOffspringSelector();
            if (internConfig.offspringSelector == null) {
                internConfig.offspringSelector = this.getDefaultConfiguration().getOffspringSelector();
            }

            internConfig.survivorsSelector = this.getConfiguration().getSurvivorsSelector();
            if (internConfig.survivorsSelector == null) {
                internConfig.survivorsSelector = this.getDefaultConfiguration().getSurvivorsSelector();
            }

            internConfig.maximalPhenotypeAge = this.getConfiguration().getMaximalPhenotypeAge();
            if (internConfig.maximalPhenotypeAge < 0) {
                internConfig.maximalPhenotypeAge = this.getDefaultConfiguration().getMaximalPhenotypeAge();
            }

            internConfig.offspringFraction = this.getConfiguration().getOffspringFraction();
            if (internConfig.offspringFraction < 0) {
                internConfig.offspringFraction = this.getDefaultConfiguration().getOffspringFraction();
            }

            internConfig.initialPopulation = this.getConfiguration().getInitialPopulation(this.problem, internConfig.populationSize);
            if(internConfig.initialPopulation == null) {
                internConfig.initialPopulation = this.getDefaultConfiguration().getInitialPopulation(this.problem, internConfig.populationSize);
            }

            internConfig.terminationConditions = this.getConfiguration().getTerminationConditions();
            if(internConfig.terminationConditions == null) {
                internConfig.terminationConditions = this.getDefaultConfiguration().getTerminationConditions();
            }
        } else {
            internConfig.populationSize = this.getDefaultConfiguration().getPopulationSize();
            internConfig.mutator = this.getDefaultConfiguration().getMutator();
            internConfig.crossover = this.getDefaultConfiguration().getCrossover();
            internConfig.offspringSelector = this.getDefaultConfiguration().getOffspringSelector();
            internConfig.survivorsSelector = this.getDefaultConfiguration().getSurvivorsSelector();
            internConfig.maximalPhenotypeAge = this.getDefaultConfiguration().getMaximalPhenotypeAge();
            internConfig.offspringFraction = this.getDefaultConfiguration().getOffspringFraction();
            internConfig.initialPopulation = this.getDefaultConfiguration().getInitialPopulation(this.problem, internConfig.populationSize);
            internConfig.terminationConditions = this.getDefaultConfiguration().getTerminationConditions();
        }

        return internConfig;
    }

    public abstract OptimizationResultDTO[] getResultDTO(int noOfSolutions);

    public Integer[][] getConvertedResults() {
        return convertedResults;
    }

    public void setConvertedResults(Integer[][] convertedResults) {
        this.convertedResults = convertedResults;
    }

    protected static class InternConfig<T extends Comparable<? super T>> {
        public int populationSize;
        public Mutator<EnumGene<Integer>, T> mutator;
        public Crossover<EnumGene<Integer>, T> crossover;
        public Selector<EnumGene<Integer>, T> offspringSelector;
        public Selector<EnumGene<Integer>, T> survivorsSelector;
        public int maximalPhenotypeAge;
        public double offspringFraction;
        public ISeq<Genotype<EnumGene<Integer>>> initialPopulation;
        public Predicate<? super EvolutionResult<EnumGene<Integer>, T>>[] terminationConditions;

    }

    /**
     * Create a new instance from a result map between flights and slots.
     * @param optId the optimization identifier
     * @param resultMap a mapping between flights and slots
     * @return an OptimizationResultDTO based on the input mapping
     */
    public OptimizationResultDTO convertResultMapToOptimizationResultMapDto(UUID optId, Map<? extends Flight, Slot> resultMap) {
        // sort the flights by slot instant
        String[] optimizedFlightSequence = resultMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .map(Flight::getFlightId)
                .toArray(String[]::new);

        LocalDateTime[] slots = resultMap.values().stream().sorted().map(Slot::getTime).toArray(LocalDateTime[]::new);

        return new OptimizationResultDTO(optId, optimizedFlightSequence, slots);
    }

    protected Engine.Builder deduplicate(Engine.Builder builder) {
        if(this.getConfiguration().isDeduplicate()){
            int maxRetries = this.getConfiguration().getDeduplicateMaxRetries();

            if (maxRetries > 0) {
                logger.debug("The engine should deduplicate the population; maxRetries: " + maxRetries);
                return builder.interceptor(EvolutionResult.toUniquePopulation(maxRetries));
            } else {
                logger.debug("The engine should deduplicate the population");
                return builder.interceptor(EvolutionResult.toUniquePopulation());
            }
        }
        return builder;
    }
}
