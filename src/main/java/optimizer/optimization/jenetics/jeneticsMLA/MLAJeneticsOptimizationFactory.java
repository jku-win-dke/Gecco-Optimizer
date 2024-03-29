package optimizer.optimization.jenetics.jeneticsMLA;

import optimizer.domain.FlightMO;
import optimizer.domain.Slot;
import optimizer.optimization.InvalidOptimizationParameterTypeException;
import optimizer.optimization.OptimizationFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class MLAJeneticsOptimizationFactory extends OptimizationFactory<FlightMO> {

    private static final Logger logger = LogManager.getLogger();
    @Override
    public MLAJeneticsOptimization createOptimization(FlightMO[] flights, Slot[] slots) {
        return new MLAJeneticsOptimization(flights, slots);
    }

    @Override
    public MLAJeneticsOptimization createOptimization(FlightMO[] flights, Slot[] slots, Map<String, Object> parameters) throws InvalidOptimizationParameterTypeException {
        MLAJeneticsOptimization optimization = new MLAJeneticsOptimization(flights, slots);

        try {
            optimization.newConfiguration(parameters);
        } catch (InvalidOptimizationParameterTypeException e) {
            logger.error("Wrong parameter for Jenetics configuration.", e);
            throw e;
        }

        return optimization;
    }
}
