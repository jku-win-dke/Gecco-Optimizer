package at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsSO;

import at.jku.dke.harmonic.optimizer.domain.Flight;
import at.jku.dke.harmonic.optimizer.domain.Slot;
import at.jku.dke.harmonic.optimizer.optimization.InvalidOptimizationParameterTypeException;
import at.jku.dke.harmonic.optimizer.optimization.OptimizationFactory;
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
