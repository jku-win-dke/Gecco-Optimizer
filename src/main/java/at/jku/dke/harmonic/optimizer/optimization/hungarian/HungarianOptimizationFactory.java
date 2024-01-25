package at.jku.dke.harmonic.optimizer.optimization.hungarian;

import at.jku.dke.harmonic.optimizer.domain.Flight;
import at.jku.dke.harmonic.optimizer.domain.Slot;
import at.jku.dke.harmonic.optimizer.optimization.InvalidOptimizationParameterTypeException;
import at.jku.dke.harmonic.optimizer.optimization.OptimizationFactory;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HungarianOptimizationFactory extends OptimizationFactory<Flight> {
	private static final Logger logger = LogManager.getLogger();
	
    @Override
    public HungarianOptimization createOptimization(Flight[] flights, Slot[] slots) {
        return new HungarianOptimization(flights, slots);
    }

    @Override
    public HungarianOptimization createOptimization(Flight[] flights, Slot[] slots, Map<String, Object> parameters) {
    	return this.createOptimization(flights, slots);
    }
}
