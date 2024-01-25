package optimizer.optimization.hungarian;

import optimizer.domain.Flight;
import optimizer.domain.Slot;
import optimizer.optimization.OptimizationFactory;

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
