package at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsMO;

import at.jku.dke.harmonic.optimizer.domain.Flight;
import at.jku.dke.harmonic.optimizer.domain.FlightMO;
import at.jku.dke.harmonic.optimizer.domain.Slot;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.SlotAllocationProblem;
import io.jenetics.EnumGene;
import io.jenetics.engine.Codecs;
import io.jenetics.engine.InvertibleCodec;
import io.jenetics.ext.moea.Vec;

import java.util.Map;
import java.util.function.Function;

import io.jenetics.util.ISeq;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class MOSlotAllocationProblem extends SlotAllocationProblem<Vec<int[]>, FlightMO> {

    private static final Logger logger = LogManager.getLogger();

    public MOSlotAllocationProblem(ISeq<FlightMO> flights,
                                   ISeq<Slot> availableSlots) {
        super(flights, availableSlots);
        logger.debug("Compute weight map for each flight.");
        Slot[] slotArray = availableSlots.toArray(Slot[]::new);
        for(FlightMO f : flights) {
            f.computeWeightMap(slotArray);
            f.computeSecondWeightMap(slotArray);
        }
    }

    @Override
    public Function<Map<FlightMO, Slot>, Vec<int[]>> fitness() {
        return slotAllocation -> {
            fitnessFunctionApplications++;
            int fitnessAirline = slotAllocation.entrySet().stream()
                    .map(e -> e.getKey().getWeight(e.getValue()))
                    .mapToInt(Integer::intValue)
                    .sum();
            int fitnessAirport = slotAllocation.entrySet().stream()
                    .map(e -> e.getKey().getScondWeight(e.getValue()))
                    .mapToInt(Integer::intValue)
                    .sum();
            return Vec.of(fitnessAirline, fitnessAirport);
        };
    }

    @Override
    public InvertibleCodec<Map<FlightMO, Slot>, EnumGene<Integer>> codec() {
        return Codecs.ofMapping(this.getFlights(), this.getAvailableSlots());
    }

}
