package optimizer.optimization.jenetics.jeneticsSO;

import optimizer.domain.Flight;
import optimizer.domain.Slot;
import optimizer.optimization.InvalidOptimizationParameterTypeException;
import optimizer.optimization.OptimizationFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class SOJeneticsOptimizationFactory extends OptimizationFactory<Flight> {
    private static final Logger logger = LogManager.getLogger();

    @Override
    public SOJeneticsOptimization createOptimization(Flight[] flights, Slot[] slots) {
        return new SOJeneticsOptimization(flights, slots);
    }

    @Override
    public SOJeneticsOptimization createOptimization(Flight[] flights, Slot[] slots, Map<String, Object> parameters) throws InvalidOptimizationParameterTypeException {
        SOJeneticsOptimization optimization = new SOJeneticsOptimization(flights, slots);

        try {
            optimization.newConfiguration(parameters);
        } catch (InvalidOptimizationParameterTypeException e) {
            logger.error("Wrong parameter for Jenetics configuration.", e);
            throw e;
        }

        return optimization;
    }
}
