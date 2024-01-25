package optimizer.optimization;

import optimizer.domain.Flight;
import optimizer.domain.Slot;

import java.util.Map;

public abstract class OptimizationFactory<F extends Flight> {
    public abstract Optimization createOptimization(F[] flights, Slot[] slots);
    public abstract Optimization createOptimization(F[] flights, Slot[] slots, Map<String, Object> parameters) throws InvalidOptimizationParameterTypeException;
}
