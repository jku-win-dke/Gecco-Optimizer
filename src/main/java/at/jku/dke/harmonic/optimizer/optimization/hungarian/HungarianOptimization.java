package at.jku.dke.harmonic.optimizer.optimization.hungarian;

import at.jku.dke.harmonic.optimizer.domain.Flight;
import at.jku.dke.harmonic.optimizer.domain.Slot;

import java.util.HashMap;
import java.util.Map;

import at.jku.dke.harmonic.optimizer.optimization.Optimization;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HungarianOptimization extends Optimization<Map<Flight, Slot>, Flight> {
	private static final Logger logger = LogManager.getLogger();
	protected static final int DEVALUATION = -10000000;
	protected static final boolean DEVALUE_SOBT_CONSTRAINT = true;
	private double optimalFitness;

	
    public HungarianOptimization(Flight[] flights, Slot[] slots) {
        super(flights, slots);
    }

	// TODO: check if the weight map must somehow be adapted bcs of the TTAs
    @Override
    public Map<Flight, Slot> run() {
    	logger.info("Running optimization using Hungarian Algorithm ...");
    	Flight[] flights = this.getFlights();
    	Slot[] slots = this.getSlots();
    	logger.debug("Optimization flights: " + flights.length + " | slots: " + slots.length);
    	
    	// create cost matrix
    	// slots are with index i (so some slots can be unassigned)
    	// flights are with index j
    	//  -> at [i][j] is the weight to assign flight j to slot i
    	double[][] costMatrix = computeCostMatrix(flights, slots);

    	printCostMatrix(costMatrix);
    	costMatrix = adjustCostMatrix(costMatrix);

    	HungarianAlgorithm ha = new HungarianAlgorithm(costMatrix);
    	int[] result = ha.execute();
    	
    	if (logger.isDebugEnabled()) {
    		printResult(result);
    	}

    	double sumOfWeights = 0;
    	Map<Flight, Slot> resultMap = new HashMap<>();
    	for (int i = 0; i < result.length; i++) {
    		resultMap.put(flights[result[i]], slots[i]);
    		sumOfWeights += getWeight(flights[result[i]], slots[i]);
    	}
    	logger.info("Finished optimization using Hungarian algorithm for " + this.getOptId() + " with a fitness value of " + sumOfWeights);

    	this.optimalFitness = sumOfWeights;
		this.setResult(resultMap);
        return resultMap;
    }

	@Override
	public Map<Flight, Slot> getResult() {
		return this.getResult();
	}


	public double getOptimalFitness() {
		return optimalFitness;
	}

	protected double[][] computeCostMatrix(Flight[] flights, Slot[] slots) {
		double[][] costMatrix = new double[slots.length][flights.length];
		for (int i = 0; i < slots.length; i++) {
			for (int j = 0; j < flights.length; j++) {
				if(i == 0) flights[j].computeWeightMap(slots);

				if(DEVALUE_SOBT_CONSTRAINT &&
						flights[j].getScheduledTime() != null &&
						slots[i].getTime().isBefore(flights[j].getScheduledTime())){
					costMatrix[i][j] = DEVALUATION;
				}else{
					costMatrix[i][j] = flights[j].getWeight(slots[i]);
				}
			}
		}
		return costMatrix;
	}

	protected void printCostMatrix(double[][] costMatrix) {
		logger.debug("costMatrix[" + costMatrix.length + "][" + costMatrix[0].length + "]");
		if (logger.isDebugEnabled()) {
			String out = "";
			for (int k = 0; k < costMatrix.length; k++) {
				for (int l = 0; l < costMatrix[k].length; l++) {
					out = out + ("[" + costMatrix[k][l] + "]");
				}
				out = out + "\n";
			}
			logger.debug(out);
		}
	}

	static double[][] adjustCostMatrix(double[][] costMatrix) {
		// Hungarian algorithm cannot work with negative values
		// See https://math.stackexchange.com/q/2036640 & https://en.wikipedia.org/wiki/Hungarian_algorithm

		// adjusting cost matrix
		// find minimum value
		double minValue = Double.MAX_VALUE;
		for (int i = 0; i < costMatrix.length; i++) {
			for (int j = 0; j < costMatrix[i].length; j++) {
				if (minValue > costMatrix[i][j]) {
					minValue = costMatrix[i][j];
				}
			}
		}

		for (int i = 0; i < costMatrix.length; i++) {
			for (int j = 0; j < costMatrix[i].length; j++) {
				if (minValue < costMatrix[i][j]) {
					costMatrix[i][j] = Math.abs(minValue) + costMatrix[i][j];
				}
			}
		}

		// (Hungarian algorithm tries to minimize cost, but here the goal is to maximize utility)
		// https://stackoverflow.com/a/17520780

		// Adjusting cost matrix
		// find maximum value
		double maxValue = Double.MIN_VALUE;
		for (int i = 0; i < costMatrix.length; i++) {
			for (int j = 0; j < costMatrix[i].length; j++) {
				if (maxValue < costMatrix[i][j]) {
					maxValue = costMatrix[i][j];
				}
			}
		}

		for (int i = 0; i < costMatrix.length; i++) {
			for (int j = 0; j < costMatrix[i].length; j++) {
				costMatrix[i][j] = maxValue - costMatrix[i][j];
			}
		}
		return costMatrix;
	}

	protected void printResult(int[] result) {
		String out = "";
		for (int i = 0; i < result.length; i++) {
			out = out + "[" + result[i] + "]";
		}
		logger.debug(out);
	}

	protected int getWeight(Flight flight, Slot slot){
		int weight = 0;
		if(DEVALUE_SOBT_CONSTRAINT &&
				flight.getScheduledTime() != null &&
				slot.getTime().isBefore(flight.getScheduledTime())){
			weight = DEVALUATION;
		}else{
			weight = flight.getWeight(slot);
		}
		logger.debug("Slot " + slot.getTime().toString() + ": " + flight.getFlightId()
				+ " | weight: " + flight.getWeight(slot));
		return weight;
	}
}
