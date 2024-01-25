package optimizer.optimization;

import optimizer.domain.Flight;
import optimizer.domain.Slot;

import java.util.UUID;

public abstract class Optimization<R, F extends Flight> {

    private F[] flights;
    private Slot[] slots;
    private UUID optId;
    protected R result;

    public Optimization(F[] flights, Slot[] slots) {
        this.flights = flights;
        this.slots = slots;
    }

    public abstract R run();

    public abstract R getResult();

    public F[] getFlights() {
        return flights;
    }

    public void setFlights(F[] flights) {
        this.flights = flights;
    }

    public Slot[] getSlots() {
        return slots;
    }

    public void setSlots(Slot[] slots) {
        this.slots = slots;
    }

    public UUID getOptId() {
        return optId;
    }

    public void setOptId(UUID optId) {
        this.optId = optId;
    }

    protected void setResult(R result) {
        this.result = result;
    }
}
