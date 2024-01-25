package at.jku.dke.harmonic.optimizer.optimization;

import at.jku.dke.harmonic.optimizer.domain.Flight;
import at.jku.dke.harmonic.optimizer.domain.Slot;
import at.jku.dke.harmonic.optimizer.optimization.InvalidOptimizationParameterTypeException;

import java.util.Map;

public abstract class OptimizationFactory<F extends Flight> {
    public abstract Optimization createOptimization(F[] flights, Slot[] slots);
    public abstract Optimization createOptimization(F[] flights, Slot[] slots, Map<String, Object> parameters) throws InvalidOptimizationParameterTypeException;
}
