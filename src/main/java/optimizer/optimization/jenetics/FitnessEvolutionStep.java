package optimizer.optimization.jenetics;

public class FitnessEvolutionStep<FitnessStep> {

    private int generation;

    private FitnessStep[] evaluatedPopulation = null;
    private FitnessStep[] estimatedPopulation = null;

    public int getGeneration() {
        return generation;
    }

    public void setGeneration(int generation) {
        this.generation = generation;
    }

    public FitnessStep[] getEvaluatedPopulation() {
        return evaluatedPopulation;
    }

    public void setEvaluatedPopulation(FitnessStep[] evaluatedPopulation) {
        this.evaluatedPopulation = evaluatedPopulation;
    }

    public FitnessStep[] getEstimatedPopulation() {
        return estimatedPopulation;
    }

    public void setEstimatedPopulation(FitnessStep[] estimatedPopulation) {
        this.estimatedPopulation = estimatedPopulation;
    }
}
