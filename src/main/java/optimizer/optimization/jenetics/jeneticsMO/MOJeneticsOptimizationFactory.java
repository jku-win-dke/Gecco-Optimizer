package optimizer.optimization.jenetics.jeneticsMO;

import optimizer.domain.FlightMO;
import optimizer.domain.Slot;
import optimizer.optimization.InvalidOptimizationParameterTypeException;
import optimizer.optimization.OptimizationFactory;

import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MOJeneticsOptimizationFactory extends OptimizationFactory<FlightMO> {

    private static final Logger logger = LogManager.getLogger();
    @Override
    public MOJeneticsOptimization createOptimization(FlightMO[] flights, Slot[] slots) {
        return new MOJeneticsOptimization(flights, slots);
    }

    @Override
    public MOJeneticsOptimization createOptimization(FlightMO[] flights, Slot[] slots, Map<String, Object> parameters) throws InvalidOptimizationParameterTypeException {
        MOJeneticsOptimization optimization = new MOJeneticsOptimization(flights, slots);

        try {
            optimization.newConfiguration(parameters);
        } catch (InvalidOptimizationParameterTypeException e) {
            logger.error("Wrong parameter for Jenetics configuration.", e);
            throw e;
        }

        return optimization;
    }
}
