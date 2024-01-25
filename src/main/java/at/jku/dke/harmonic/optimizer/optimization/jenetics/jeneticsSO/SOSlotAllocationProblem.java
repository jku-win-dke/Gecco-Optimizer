package at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsSO;

import at.jku.dke.harmonic.optimizer.domain.Flight;
import at.jku.dke.harmonic.optimizer.domain.Slot;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.SlotAllocationProblem;
import io.jenetics.EnumGene;
import io.jenetics.engine.Constraint;
import io.jenetics.engine.RetryConstraint;
import io.jenetics.util.ISeq;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SOSlotAllocationProblem extends SlotAllocationProblem<Integer, Flight> {
	private static final Logger logger = LogManager.getLogger();

	public SOSlotAllocationProblem(ISeq<Flight> flights, ISeq<Slot> availableSlots) {
		super(flights, availableSlots);

		logger.debug("Compute weight map for each flight.");
		Slot[] slotArray = availableSlots.toArray(Slot[]::new);
		for(Flight f : flights) {
			try {
				f.computeWeightMap(slotArray);
			} catch (Exception e) {
				e.getMessage();
			}
		}
	}
	
    @Override
    public Function<Map<Flight, Slot>, Integer> fitness() {
        return slotAllocation -> {
			fitnessFunctionApplications++;

			return slotAllocation.keySet().stream()
					.map(f -> f.getWeight(slotAllocation.get(f)))
					.mapToInt(Integer::intValue)
					.sum();
		};
    }
    
    @Override
    public Optional<Constraint<EnumGene<Integer>, Integer>> constraint() {
		return Optional.of(
				RetryConstraint.of(
						codec(),
						flightSlotMap -> {
							for(Map.Entry<Flight, Slot> e : flightSlotMap.entrySet()) {
								if(e.getKey().getScheduledTime() != null &&
										e.getKey().getScheduledTime().isAfter(e.getValue().getTime())) {
									return false;
								}
							}

							return true;
						}
				)
		);
    }

}
