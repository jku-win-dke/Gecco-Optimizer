package optimizer.optimization.jenetics;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public abstract class JeneticsOptimizationStatistics<FitnessValue> {

    private double initialFitness = Integer.MIN_VALUE;
    private double resultFitness = Integer.MIN_VALUE;
    private int iterations = Integer.MIN_VALUE;
    private int fitnessFunctionInvocations = Integer.MIN_VALUE;
    private int maximumFitness = Integer.MIN_VALUE;
    private int theoreticalMaxFitness  = Integer.MIN_VALUE;

    private LocalDateTime timeCreated;
    private LocalDateTime timeStarted;
    private LocalDateTime timeAborted;
    private LocalDateTime timeFinished;

    private List<FitnessEvolutionStep<FitnessValue>> fitnessEvolution = null;

    public double getInitialFitness() {
        return initialFitness;
    }

    public void setInitialFitness(double initialFitness) {
        this.initialFitness = initialFitness;
    }

    public double getResultFitness() {
        return resultFitness;
    }

    public void setResultFitness(double resultFitness) {
        this.resultFitness = resultFitness;
    }

    public int getIterations() {
        return iterations;
    }

    public void setIterations(int iterations) {
        this.iterations = iterations;
    }

    public LocalDateTime getTimeCreated() {
        return timeCreated;
    }

    public void setTimeCreated(LocalDateTime timeCreated) {
        this.timeCreated = timeCreated;
    }

    public LocalDateTime getTimeStarted() {
        return timeStarted;
    }

    public void setTimeStarted(LocalDateTime timeStarted) {
        this.timeStarted = timeStarted;
    }

    public LocalDateTime getTimeAborted() {
        return timeAborted;
    }

    public void setTimeAborted(LocalDateTime timeAborted) {
        this.timeAborted = timeAborted;
    }

    public LocalDateTime getTimeFinished() {
        return timeFinished;
    }

    public void setTimeFinished(LocalDateTime timeFinished) {
        this.timeFinished = timeFinished;
    }

    public Duration getDuration() {
        Duration duration = null;

        if(this.timeStarted != null && this.timeFinished != null) {
            duration = Duration.between(this.getTimeStarted(), this.getTimeFinished());
        }

        return duration;
    }

    public int getFitnessFunctionInvocations() {
        return fitnessFunctionInvocations;
    }

    public void setFitnessFunctionInvocations(int fitnessFunctionInvocations) {
        this.fitnessFunctionInvocations = fitnessFunctionInvocations;
    }

    public List<FitnessEvolutionStep<FitnessValue>> getFitnessEvolution() {
        return fitnessEvolution;
    }

    public void setFitnessEvolution(List<FitnessEvolutionStep<FitnessValue>> fitnessEvolution) {
        this.fitnessEvolution = fitnessEvolution;
    }

    public int getMaximumFitness() {
        return maximumFitness;
    }

    public void setMaximumFitness(int maximumFitness) {
        this.maximumFitness = maximumFitness;
    }

    public int getTheoreticalMaxFitness() {
        return theoreticalMaxFitness;
    }

    public void setTheoreticalMaxFitness(int theoreticalMaxFitness) {
        this.theoreticalMaxFitness = theoreticalMaxFitness;
    }
}
