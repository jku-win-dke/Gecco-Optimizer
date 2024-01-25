package at.jku.dke.harmonic.optimizer.optimization.hungarian;

import at.jku.dke.harmonic.optimizer.domain.Flight;
import at.jku.dke.harmonic.optimizer.domain.FlightMO;
import at.jku.dke.harmonic.optimizer.domain.Slot;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MOHungarianOptimization extends HungarianOptimization {

    private static final Logger logger = LogManager.getLogger();
    public MOHungarianOptimization(Flight[] flights, Slot[] slots) {
        super(flights, slots);
    }

    @Override
    protected double[][] computeCostMatrix(Flight[] flights, Slot[] slots) {
        double[][] costMatrix = new double[slots.length][flights.length];
        for (int i = 0; i < slots.length; i++) {
            for (int j = 0; j < flights.length; j++) {
                FlightMO flight = (FlightMO) flights[j];
                if(i == 0) flight.computeSecondWeightMap(slots);

                if(DEVALUE_SOBT_CONSTRAINT &&
                        flight.getScheduledTime() != null &&
                        slots[i].getTime().isBefore(flight.getScheduledTime())){
                    costMatrix[i][j] = DEVALUATION;
                }else{
                    costMatrix[i][j] = flight.getScondWeight(slots[i]);
                }
            }
        }
        return costMatrix;
    }

    @Override
    protected int getWeight(Flight flight, Slot slot) {
        FlightMO castedFlight = (FlightMO) flight;
        int weight;
        if(DEVALUE_SOBT_CONSTRAINT &&
                castedFlight.getScheduledTime() != null &&
                slot.getTime().isBefore(castedFlight.getScheduledTime())){
            weight = DEVALUATION;
        }else{
            weight = castedFlight.getScondWeight(slot);
        }
        logger.debug("Slot " + slot.getTime().toString() + ": " + castedFlight.getFlightId()
                + " | weight: " + castedFlight.getWeight(slot));
        return weight;
    }
}
