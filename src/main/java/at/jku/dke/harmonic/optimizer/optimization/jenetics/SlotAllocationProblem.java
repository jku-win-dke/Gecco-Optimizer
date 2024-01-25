package at.jku.dke.harmonic.optimizer.optimization.jenetics;

import at.jku.dke.harmonic.optimizer.domain.Flight;
import at.jku.dke.harmonic.optimizer.domain.Slot;
import io.jenetics.EnumGene;
import io.jenetics.Gene;
import io.jenetics.engine.Codecs;
import io.jenetics.engine.InvertibleCodec;
import io.jenetics.engine.Problem;
import io.jenetics.util.ISeq;

import java.util.Map;

public abstract class SlotAllocationProblem<C extends Comparable<? super C>, F extends Flight> implements Problem<Map<F, Slot>, EnumGene<Integer>, C> {

    private final ISeq<F> flights;
    private final ISeq<Slot> availableSlots;
    protected int fitnessFunctionApplications = 0;

    public SlotAllocationProblem(ISeq<F> flights, ISeq<Slot> availableSlots) {
        this.flights = flights;
        this.availableSlots = availableSlots;
    }

    public ISeq<F> getFlights() {
        return flights;
    }

    public ISeq<Slot> getAvailableSlots() {
        return availableSlots;
    }

    @Override
    public InvertibleCodec<Map<F, Slot>, EnumGene<Integer>> codec() {
        return Codecs.ofMapping(flights, availableSlots);
    }

    public int getFitnessFunctionApplications() {
        return fitnessFunctionApplications;
    }

}
