package at.jku.dke.harmonic.optimizer.optimization.hungarian;

import at.jku.dke.harmonic.optimizer.domain.Flight;
import at.jku.dke.harmonic.optimizer.domain.FlightMO;
import at.jku.dke.harmonic.optimizer.domain.Slot;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

public class ParetoFrontEstimator {

    private static final Logger logger = LogManager.getLogger();

    private final int PARETO_FRONT_GRANULARITY = 100;
    final private FlightMO[] flights;
    final private Slot[] slots;

    public ParetoFrontEstimator(FlightMO[] flights, Slot[] slots) {
        this.slots = slots;
        this.flights = flights;
        for(Flight flight: this.flights) flight.computeWeightMap(this.slots);
    }

    public double[][] calculateParetoFront() {

        double[][] pointsOnFront = new double[PARETO_FRONT_GRANULARITY+1][2];

        for(int i = 0; i <= PARETO_FRONT_GRANULARITY; i++) {
            // get the weight map
            double[][] weights = getWeightMap((double) i/PARETO_FRONT_GRANULARITY);
            // transform weight map
            weights = HungarianOptimization.adjustCostMatrix(weights);
            // optimize the weight map
            HungarianAlgorithm ha = new HungarianAlgorithm(weights);
            int[] result = ha.execute();
            // calculate the weights based on the result
            int firstValue = 0;
            int secondValue = 0;
            for(int j = 0; j < result.length; j++) {
                firstValue += flights[result[j]].getWeight(slots[j]);
                secondValue += flights[result[j]].getScondWeight(slots[j]);
            }
            // set the point
            pointsOnFront[i][0] = firstValue;
            pointsOnFront[i][1] = secondValue;
        }

        // check if border points are relevant
        if(pointsOnFront[0][1] == pointsOnFront[1][1]) {
            pointsOnFront[0][0] = pointsOnFront[1][0];
        }
        if(pointsOnFront[10][0] == pointsOnFront[9][0]) {
            pointsOnFront[10][1] = pointsOnFront[9][1];
        }

        return Arrays.stream(pointsOnFront)
                .map(Arrays::toString)
                .distinct()
                .map(str -> Arrays.stream(str.substring(1, str.length() - 1).split(", "))
                        .mapToDouble(Double::parseDouble)
                        .toArray())
                .peek(p -> logger.info("Point on estimated Pareto Front: " + p[0] + " " + p[1]))
                .toArray(double[][]::new);
    }

    private double[][] getWeightMap(double composition) {
        double[][] costMatrix = new double[slots.length][flights.length];
        for (int i = 0; i < slots.length; i++) {
            for (int j = 0; j < flights.length; j++) {
                costMatrix[i][j] = flights[j].getWeight(slots[i]) * composition +
                                    flights[j].getScondWeight(slots[i]) * (1-composition);
            }
        }
        return costMatrix;
    }
}
