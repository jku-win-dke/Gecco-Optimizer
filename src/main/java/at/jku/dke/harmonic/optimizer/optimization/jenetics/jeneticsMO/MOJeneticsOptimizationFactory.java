package at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsMO;

import at.jku.dke.harmonic.optimizer.domain.Flight;
import at.jku.dke.harmonic.optimizer.domain.FlightMO;
import at.jku.dke.harmonic.optimizer.domain.Slot;
import at.jku.dke.harmonic.optimizer.optimization.InvalidOptimizationParameterTypeException;
import at.jku.dke.harmonic.optimizer.optimization.Optimization;
import at.jku.dke.harmonic.optimizer.optimization.OptimizationFactory;

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
