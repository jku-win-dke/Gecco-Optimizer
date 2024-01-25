package optimizer.rest.legacy;

import org.springframework.lang.Nullable;

import java.time.Instant;

public class FlightDTO {
    private String flightId;

    private Instant scheduledTime;

    private Instant timeNotAfter;

    @Nullable
    private int[] weightMap; // List of weights for slots; only used in mode NON_PRIVACY_PRESERVING

    @Nullable
    private int[] secondWeightMap;

    public FlightDTO(String flightId, Instant scheduledTime, Instant timeNotAfter, @Nullable int[] weightMap, @Nullable int[] secondWeightMap) {
        this(flightId, scheduledTime, timeNotAfter, weightMap);
        this.secondWeightMap = secondWeightMap;
    }

    public FlightDTO(String flightId, Instant scheduledTime, Instant timeNotAfter, @Nullable int[] weightMap) {
        this.flightId = flightId;
        this.scheduledTime = scheduledTime;
        this.timeNotAfter = timeNotAfter;
        this.weightMap = weightMap;
    }

    public FlightDTO(String flightId, Instant scheduledTime, int[] weightMap) {
        this.flightId = flightId;
        this.scheduledTime = scheduledTime;
        this.weightMap = weightMap;
    }

    public FlightDTO(String flightId, Instant scheduledTime) {
        this.flightId = flightId;
        this.scheduledTime = scheduledTime;
    }

    public FlightDTO() { }

    public String getFlightId() {
        return flightId;
    }

    public void setFlightId(String flightId) {
        this.flightId = flightId;
    }

    public Instant getScheduledTime() {
        return scheduledTime;
    }

    public Instant getTimeNotAfter() {
        return timeNotAfter;
    }

    public void setTimeNotAfter(Instant timeNotAfter) {
        this.timeNotAfter = timeNotAfter;
    }

    public void setScheduledTime(Instant scheduledTime) {
        this.scheduledTime = scheduledTime;
    }
    
	public int[] getWeightMap() {
		return weightMap;
	}
	
	public void setWeightMap(int[] weightMap) {
		this.weightMap = weightMap;
	}

    @Nullable
    public int[] getSecondWeightMap() {
        return secondWeightMap;
    }

    public void setSecondWeightMap(@Nullable int[] secondWeightMap) {
        this.secondWeightMap = secondWeightMap;
    }
}
