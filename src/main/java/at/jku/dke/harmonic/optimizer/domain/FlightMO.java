package at.jku.dke.harmonic.optimizer.domain;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FlightMO extends Flight{

    private int[] secondWeights;
    protected Map<Slot, Integer> secondWeightMap;

    public FlightMO(String flightId, LocalDateTime scheduledTime, int[] weights) {
        super(flightId, scheduledTime, weights);
    }

    public FlightMO(String flightId, LocalDateTime scheduledTime, int[] weights, int[] secondWeight) {
        this(flightId, scheduledTime, weights);
        this.secondWeights = secondWeight;
    }

    public void computeSecondWeightMap(Slot[] slots) {
        super.computeWeightMap(slots);
        if(secondWeightMap != null) {
            secondWeightMap.clear();
        } else {
            secondWeightMap = new HashMap<>();
        }

        if (secondWeightMap == null) {
            // in SECRET mode no weights are stored in Flight and the
            // weight map cannot be computed
            return;
        }

        // sort the slots by their time
        List<Slot> slotList = Arrays.stream(slots).sorted().toList();

        // for each slot in the sorted slot list get the weight from the weights array
        for(Slot s : slotList){
            secondWeightMap.put(s, secondWeights[slotList.indexOf(s)]);
        }
    }

    public int getScondWeight(Slot s) {
        int weight = Integer.MIN_VALUE;

        if(secondWeightMap != null && secondWeightMap.containsKey(s)) {
            weight = secondWeightMap.get(s);
        }

        return weight;
    }
    public int[] getSecondWeights() {
        return secondWeights;
    }

    public void setSecondWeights(int[] secondWeights) {
        this.secondWeights = secondWeights;
    }

    public Map<Slot, Integer> getSecondWeightMap() {
        return secondWeightMap;
    }
}
