package optimizer.optimization.jenetics;

import optimizer.domain.Flight;
import io.jenetics.*;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.util.ISeq;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.function.Predicate;

public abstract class JeneticsOptimizationConfiguration<T extends Comparable<? super T>> {
    public Map<String,Object> parameters = new HashMap<>();

    public abstract ISeq<Genotype<EnumGene<Integer>>> getInitialPopulation(SlotAllocationProblem<T, Flight> problem, int populationSize);

    public Object getParameter(String key) {
        return parameters.get(key);
    }

    public void setParameter(String key, Object value) {
        parameters.put(key, value);
    }

    public String getStringParameter(String param) {
        Object value = this.getParameter(param);

        String stringValue = null;

        if(value != null) {
            stringValue = (String) value;
        }

        return stringValue;
    }

    public double getDoubleParameter(String param) {
        double doubleValue = Double.MIN_VALUE;
        Object value = this.getParameter(param);

        if(value != null) doubleValue = (double) value;

        return doubleValue;
    }

    public int getIntegerParameter(String param) {
        int integerValue = Integer.MIN_VALUE;
        Object value = this.getParameter(param);

        if(value != null) integerValue = (int) value;

        return integerValue;
    }

    protected Number getNumberParameter(String param) {
        Object value = this.getParameter(param);

        Number numberValue = null;

        if(value != null) {
            numberValue = (Number) value;
        }

        return numberValue;
    }

    protected Long getLongParameter(String param) {
        Object value = this.getParameter(param);

        Long longValue = null;

        if(value != null) {
            longValue = (Long) value;
        }

        return longValue;
    }

    protected boolean getBooleanParameter(String param) {
        Object value = this.getParameter(param);

        boolean booleanValue = false;

        if(value != null) {
            booleanValue = (boolean) value;
        }

        return booleanValue;
    }

    private static final Logger logger = LogManager.getLogger();

    /**
     * Returns the maximal phenotype age, or Integer.MIN_VALUE if the parameter is not set.
     * @return the maximal phenotype age
     */
    public int getMaximalPhenotypeAge() {
        return this.getIntegerParameter("maximalPhenotypeAge");
    }

    public void setMaximalPhenotypeAge(int maximalPhenotypeAge) {
        this.setParameter("maximalPhenotypeAge", maximalPhenotypeAge);
    }


    public int getPopulationSize() {
        return this.getIntegerParameter("populationSize");
    }

    public void setPopulationSize(int populationSize) {
        this.setParameter("populationSize", populationSize);
    }

    public abstract Mutator<EnumGene<Integer>, T> getMutator();

    protected double getMutatorAlterProbability() {
        return this.getDoubleParameter("mutatorAlterProbability");
    }


    public abstract Crossover<EnumGene<Integer>, T> getCrossover();

    protected double getCrossoverAlterProbability() {
        return this.getDoubleParameter("crossoverAlterProbability");
    }

    public double getOffspringFraction() {
        return this.getDoubleParameter("offspringFraction");
    }

    public abstract Predicate<? super EvolutionResult<EnumGene<Integer>, T>>[] getTerminationConditions();

    protected Map<String,Object> getMapParameter(String param) {
        Map<String,Object> mapValue = null;
        Object value = this.getParameter(param);

        if(value != null) mapValue = (Map<String,Object>) value;

        return mapValue;
    }

    public boolean isDeduplicate() {
        return this.getBooleanParameter("deduplicate");
    }


    public int getDeduplicateMaxRetries() {
        return this.getIntegerParameter("deduplicateMaxRetries");
    }

    public boolean isSecondObfuscated() {
        return this.getBooleanParameter("secondObfuscated");
    }

    public void setTerminationConditions(Map<String,Object> terminationConditionParameters) {
        this.setParameter("terminationConditions", terminationConditionParameters);
    }

    public void setOffspringFraction(double offspringFraction) {
        this.setParameter("offspringFraction", offspringFraction);
    }

    public void setMutator(String mutator) {
        this.setParameter("mutator", mutator);
    }

    public void setCrossover(String crossover) {
        this.setParameter("crossover", crossover);
    }

    public void setMutatorAlterProbability(double mutatorAlterProbability) {
        this.setParameter("mutatorAlterProbability", mutatorAlterProbability);
    }

    public void setCrossoverAlterProbability(double crossoverAlterProbability) {
        this.setParameter("crossoverAlterProbability", crossoverAlterProbability);
    }

    public void setOffspringSelector(String offspringSelector) {
        this.setParameter("offspringSelector", offspringSelector);
    }

    public void setSurvivorsSelector(String survivorsSelector) {
        this.setParameter("survivorsSelector", survivorsSelector);
    }

    public void setOffspringSelectorParameter(Number offspringSelectorParameter) {
        this.setParameter("offspringSelectorParameter", offspringSelectorParameter);
    }

    public void setSurvivorsSelectorParameter(Number survivorsSelectorParameter) {
        this.setParameter("survivorsSelectorParameter", survivorsSelectorParameter);
    }

    public void setDeduplicate(boolean deduplicate) {
        this.setParameter("deduplicate", deduplicate);
    }

    public void setDeduplicateMaxRetries(int maxRetries) {
        this.setParameter("deduplicateMaxRetries", maxRetries);
    }

    public void setSecondObfuscated(boolean secondObfuscated) {
        this.setParameter("secondObfuscated", secondObfuscated);
    }

    public abstract Selector<EnumGene<Integer>, T> getOffspringSelector();

    public abstract Selector<EnumGene<Integer>, T> getSurvivorsSelector();

    public abstract Selector<EnumGene<Integer>, T> getSelector(String selectorType, Number selectorParameter);

}
