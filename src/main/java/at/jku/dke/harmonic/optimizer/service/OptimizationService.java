package at.jku.dke.harmonic.optimizer.service;

import at.jku.dke.harmonic.optimizer.OptimizerApplication;
import at.jku.dke.harmonic.optimizer.Utils;
import at.jku.dke.harmonic.optimizer.domain.Flight;
import at.jku.dke.harmonic.optimizer.domain.FlightMO;
import at.jku.dke.harmonic.optimizer.domain.Slot;
import at.jku.dke.harmonic.optimizer.optimization.*;
import at.jku.dke.harmonic.optimizer.optimization.fitnessEstimation.FitnessEstimator;
import at.jku.dke.harmonic.optimizer.optimization.hungarian.HungarianOptimization;
import at.jku.dke.harmonic.optimizer.optimization.hungarian.MOHungarianOptimization;
import at.jku.dke.harmonic.optimizer.optimization.hungarian.ParetoFrontEstimator;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.JeneticsOptimization;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsMLA.MLAJeneticsOptimizationFactory;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsMLA.MLAJeneticsOptimization;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsMO.MOJeneticsOptimization;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsMO.MOJeneticsOptimizationFactory;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsSO.SOJeneticsOptimization;
import at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsSO.SOJeneticsOptimizationFactory;
import at.jku.dke.harmonic.optimizer.service.dto.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

@Service
public class OptimizationService {
	private static final Logger logger = LogManager.getLogger();

	private final Map<UUID, OptimizationDTO> optimizationDTOs;
	private final Map<UUID, JeneticsOptimization> optimizations;
	private final Map<UUID, Future<OptimizationResultDTO>> threads;

	private final PrivacyEngineService privacyEngineService;

	public OptimizationService(PrivacyEngineService privacyEngineService) {
		this.privacyEngineService = privacyEngineService;

		this.optimizationDTOs = new ConcurrentHashMap<>();
		this.optimizations = new ConcurrentHashMap<>();
		this.threads = new ConcurrentHashMap<>();
	}

	/**
	 * Create the optimization and initialize it with the given data.
	 * @param optimizationDto data for the optimization session
	 * @return information about the optimization
	 */
	public OptimizationDTO createAndInitializeOptimization(final OptimizationDTO optimizationDto)
			throws ClassNotFoundException, InvocationTargetException,
				   InstantiationException, IllegalAccessException,
			       NoSuchMethodException, InvalidOptimizationParameterTypeException {
		logger.info("Starting process to initialize optimization session.");

		UUID optId = optimizationDto.getOptId();

		// remove existing optimization if same optimization id is used twice
		if(this.optimizations.containsKey(optId)) {
			logger.info("Found duplicate optimization entry for optimization with id " + optId + ". Deleting old entry.");
			optimizationDTOs.remove(optId);
			optimizations.remove(optId);
		}

		try {
			logger.info("Read the factory class from the JSON properties file.");
			String factoryClasses = System.getProperty(OptimizerApplication.FACTORY_PROPERTY);

			String optimizationFramework = optimizationDto.getOptimizationFramework();
			if(optimizationDto.getOptimizationType().equals("MULTI_OBJECTIVE")){
				optimizationFramework += "_MO";
			} else if(optimizationDto.getOptimizationType().equals("MLA")){
				optimizationFramework += "_MLA";
			}
			String className =
				Utils.getMapFromJson(factoryClasses).get(optimizationFramework);



			// get flights array from the DTO
			// preserve original order (important to receive correct results from Privacy Engine)
			Flight[] flights;
			if(optimizationDto.getOptimizationType().equals("MULTI_OBJECTIVE") || optimizationDto.getOptimizationType().equals("MLA")) {
				flights = Arrays.stream(optimizationDto.getFlights())
						.map(f -> new FlightMO(f.getFlightId(), f.getScheduledTime(), f.getWeightMap(), f.getSecondWeightMap()))
						.toArray(FlightMO[]::new);
			} else {
				flights  = Arrays.stream(optimizationDto.getFlights())
						.map(f -> new Flight(f.getFlightId(), f.getScheduledTime(), f.getWeightMap()))
						.toArray(Flight[]::new);
			}
			// get slots array from the DTO
			Slot[] slots = Arrays.stream(optimizationDto.getSlots())
					.map(s -> new Slot(s.getTime()))
					.toArray(Slot[]::new);

			logger.info("Checking if flights can be assigned to slots without violating SOBT constraint..");
			var flightsWithScheduledTime = Arrays.stream(flights)
					.filter(f -> f.getScheduledTime() != null)
					.sorted().toList();
			for(int i = 1; i <= flightsWithScheduledTime.size(); i++){
				var flight = flightsWithScheduledTime.get(flightsWithScheduledTime.size() - i);
				long availableSlots = Arrays.stream(slots).filter(slot -> slot.getTime().compareTo(flight.getScheduledTime()) >= 0).count();
				if(availableSlots < i){
					logger.error("It is not possible to construct a valid solution.");
					logger.error("{} flights require a slot after or equal to {}, but there are only {} slots available.", i, flight.getScheduledTime().toString(), availableSlots);
					logger.warn("Initialize the optimization with a feasible input.");
					break;
				}
			}
			logger.info("Finished validating SOBT constraint.");

			if(optimizationDto.getOptimizationType().equals("MULTI_OBJECTIVE")) {
				MOJeneticsOptimizationFactory factory = createMOFactory(className, optimizationDto.getOptimizationFramework());
				createMOJenetics(optimizationDto, factory, (FlightMO[]) flights, slots, optId);
			} else if(optimizationDto.getOptimizationType().equals("MLA")){
				MLAJeneticsOptimizationFactory factory = createMLAFactory(className, optimizationDto.getOptimizationFramework());
				createMLAJenetics(optimizationDto, factory, (FlightMO[]) flights, slots, optId);
			} else {
				SOJeneticsOptimizationFactory factory = createSOFactory(className, optimizationDto.getOptimizationFramework());
				createSOJenetics(optimizationDto, factory, flights, slots, optId);
			}



		} catch (ClassNotFoundException|
				InvocationTargetException |
				InstantiationException |
				IllegalAccessException |
				NoSuchMethodException e) {
			logger.info("Could not create optimization with id " + optId);
			throw e;
		}

		// keep the DTO for later
		optimizationDTOs.put(optId, optimizationDto);

		if(logger.isDebugEnabled()) {
			logger.debug("Listing available optimization sessions ...");
			for (Optimization o : optimizations.values()) {
				logger.debug(o.getOptId().toString());
			}
		}
		return optimizationDto;
	}
	
	/**
	 * Start the optimization run. The method runs asynchronously in a separate thread.
	 * @param optId optId of the optimization session
	 */
	@Async("threadPoolTaskExecutor")
	public Future<OptimizationResultDTO> runOptimizationAsynchronously(UUID optId) {
		logger.info("Current thread: " + Thread.currentThread());

		OptimizationResultDTO optimizationResultDto = this.runOptimization(optId);

		return new AsyncResult<>(optimizationResultDto);
	}

	/**
	 * Returns the result of the optimization, if already available.
	 * @param optId the optimization identifier
	 * @param noOfSolutions the number of solutions to be retrieved
	 * @return the result of the optimization
	 */
	public OptimizationResultDTO[] getOptimizationResult(UUID optId, int noOfSolutions) {
		JeneticsOptimization optimization = this.optimizations.get(optId);
		return optimization.getResultDTO(noOfSolutions);
	}
	
	/**
	 * Deletes an optimization and all its associated data. If the optimization is currently running, the optimization
	 * will be aborted.
	 * @param optId the optimization identifier
	 */
	public OptimizationDTO deleteOptimization(UUID optId) {
		OptimizationDTO optimizationDto = optimizationDTOs.remove(optId);
		JeneticsOptimization optimization = optimizations.remove(optId);
		
		if(optimization.getStatus() == OptimizationStatus.RUNNING) {
			this.abortOptimization(optId);
		}

		return optimizationDto;
	}

	/**
	 * Determines whether an optimization with the argument optimization identifier already exists.
	 * @param optId the optimization identifier
	 * @return true if the optimization exists; false otherwise.
	 */
	public boolean existsOptimization(UUID optId) {
		return optimizations.get(optId) != null;
	}

	/**
	 * Returns an optimization DTO with the specified identifier.
	 * @param optId the identifier of the optimization
	 * @return the optimization DTO with the specified identifier, if it exists; null otherwise.
	 */
	public OptimizationDTO getOptimization(UUID optId) {
		OptimizationDTO optimizationDto = optimizationDTOs.get(optId);
		JeneticsOptimization optimization = optimizations.get(optId);

		if(optimization != null && optimizationDto != null) {
			switch (optimization.getStatus()) {
				case CREATED -> optimizationDto.setOptimizationStatus(OptimizationStatusEnum.CREATED);
				case INITIALIZED -> optimizationDto.setOptimizationStatus(OptimizationStatusEnum.INITIALIZED);
				case RUNNING -> optimizationDto.setOptimizationStatus(OptimizationStatusEnum.RUNNING);
				case CANCELLED -> optimizationDto.setOptimizationStatus(OptimizationStatusEnum.CANCELLED);
				case DONE -> optimizationDto.setOptimizationStatus(OptimizationStatusEnum.DONE);
			}

			optimizationDto.setTimestamp(LocalDateTime.now());
		} else{
			optimizationDto = null;
			logger.info("Optimization with id " + optId + " not found.");
		}

		return optimizationDto;
	}

	/**
	 * Get the current statistics for an optimization. Statistics are updated constantly during the optimization run.
	 * @param optId the optimization id
	 * @return the current optimization statistics
	 */
	public OptimizationStatisticsDTO getOptimizationStatistics(UUID optId) {
		// search for optId
		JeneticsOptimization optimization = this.optimizations.get(optId);
		if (optimization == null) {
			logger.info("Optimization with id " + optId + " not found.");
			return null;
		}


		if(optimization instanceof MOJeneticsOptimization) {
			MOOptimizationStatisticsDTO stats = new MOOptimizationStatisticsDTO();
			if(optimization.getMode() == OptimizationMode.BENCHMARKING ||
					optimization.getMode() == OptimizationMode.DEMONSTRATION) {
				MOJeneticsOptimization castedOptimization = (MOJeneticsOptimization) optimization;
				stats.setFirstTheoreticalFitness(castedOptimization.getStatistics().getTheoreticalMaxFitness());
				stats.setSecondTheoreticalFitness(castedOptimization.getStatistics().getTheoreticalMaxFitnessTwo());

				stats.setFirstFitnessResult(castedOptimization.getStatistics().getMaximumFitness());
				stats.setSecondFitnessResult(castedOptimization.getStatistics().getMaximumFitnessTwo());

				stats.setBalanceRatio(castedOptimization.getStatistics().getBalanceRatio());
				setBasicStats(optimization, stats);
				if(optimization.isTraceFitnessEvolution()) {
					logger.debug("Tracing fitness evolution: include fitness evolution in statistics.");
					stats.setFitnessEvolution(getMOEvolution(castedOptimization));
				}
				stats.setEstimatedParetoFront(castedOptimization.getStatistics().getEstimatedParetoFront());
				if(!(optimization instanceof MLAJeneticsOptimization)) {
					int[][] paretoFrontInt = castedOptimization.getStatistics().getParetoFront();
					double[][] paretoFront = new double[paretoFrontInt.length][paretoFrontInt[0].length];
					for(int i = 0; i < paretoFrontInt.length; i++) {
						for(int j = 0; j < paretoFrontInt[i].length; j++){
							paretoFront[i][j] = paretoFrontInt[i][j];
						}
					}
					stats.setOptimizedParetoFront(paretoFront);
				}
			}
			return stats;
		} else {
			OptimizationStatisticsDTO stats = new OptimizationStatisticsDTO();
			if(optimization.getMode() == OptimizationMode.BENCHMARKING ||
					optimization.getMode() == OptimizationMode.DEMONSTRATION) {
				SOJeneticsOptimization castedOptimization = (SOJeneticsOptimization) optimization;
				stats.setTheoreticalMaximumFitness(castedOptimization.getStatistics().getTheoreticalMaxFitness());
				stats.setResultFitness(optimization.getStatistics().getResultFitness());
				setBasicStats(optimization, stats);
				if(optimization.isTraceFitnessEvolution()) {
					logger.debug("Tracing fitness evolution: include fitness evolution in statistics.");
					stats.setFitnessEvolution(getSOEvolution(castedOptimization));
				}
			}
			return stats;
		}
	}

	private void setBasicStats(JeneticsOptimization optimization, OptimizationStatisticsDTO stats) {

		stats.setOptId(optimization.getOptId().toString());

		stats.setRequestTime(LocalDateTime.now());

		switch(optimization.getStatus()) {
			case CREATED -> stats.setStatus(OptimizationStatusEnum.CREATED);
			case INITIALIZED -> stats.setStatus(OptimizationStatusEnum.INITIALIZED);
			case RUNNING -> stats.setStatus(OptimizationStatusEnum.RUNNING);
			case CANCELLED -> stats.setStatus(OptimizationStatusEnum.CANCELLED);
			case DONE -> stats.setStatus(OptimizationStatusEnum.DONE);
		}

		stats.setTimeCreated(optimization.getStatistics().getTimeCreated());
		stats.setTimeStarted(optimization.getStatistics().getTimeStarted());
		stats.setTimeFinished(optimization.getStatistics().getTimeFinished());
		stats.setTimeAborted(optimization.getStatistics().getTimeAborted());
		stats.setDuration(optimization.getStatistics().getDuration());

		stats.setIterations(optimization.getStatistics().getIterations());
		stats.setInitialFitness(optimization.getStatistics().getInitialFitness());
	}

	private FitnessEvolutionStepDTO[] getSOEvolution(SOJeneticsOptimization optimization) {
		return optimization.getStatistics().getFitnessEvolution().stream()
				.map(fitnessEvolutionStep -> {
					FitnessEvolutionStepDTO<Double> newStep = new FitnessEvolutionStepDTO<>();

					newStep.setGeneration(fitnessEvolutionStep.getGeneration());

					if(fitnessEvolutionStep.getEstimatedPopulation() != null) newStep.setEstimatedPopulation(
							Arrays.stream(fitnessEvolutionStep.getEstimatedPopulation()).toArray(Double[]::new)
					);

					if(fitnessEvolutionStep.getEvaluatedPopulation() != null) newStep.setEvaluatedPopulation(
							Arrays.stream(fitnessEvolutionStep.getEvaluatedPopulation()).toArray(Double[]::new)
					);

					return newStep;
				}).toArray(FitnessEvolutionStepDTO[]::new);
	}

	private FitnessEvolutionStepDTO[] getMOEvolution(MOJeneticsOptimization optimization) {
		return optimization.getStatistics().getFitnessEvolution().stream()
				.map(fitnessEvolutionStep -> {
					FitnessEvolutionStepDTO<double[]> newStep = new FitnessEvolutionStepDTO<>();

					newStep.setGeneration(fitnessEvolutionStep.getGeneration());

					if(fitnessEvolutionStep.getEstimatedPopulation() != null) {
						newStep.setEstimatedPopulation(
								Arrays.stream(fitnessEvolutionStep.getEstimatedPopulation()).toArray(double[][]::new)
						);
					}

					if(fitnessEvolutionStep.getEvaluatedPopulation() != null) {
						newStep.setEvaluatedPopulation(
								Arrays.stream(fitnessEvolutionStep.getEvaluatedPopulation()).toArray(double[][]::new)
						);
					}

					return newStep;
				}).toArray(FitnessEvolutionStepDTO[]::new);
	}

	/**
	 * Runs the optimization with the specified framework and parameters.
	 * @param optId the optimization identifier
	 * @return the best solution found by the optimization
	 */
 	public OptimizationResultDTO runOptimization(UUID optId) {
		// TODO Optimization is not running bcs the problem is not set
		logger.info("Current thread: " + Thread.currentThread());

		// search for optId
		JeneticsOptimization optimization = this.optimizations.get(optId);

		OptimizationResultDTO optimizationResultDto = null;

		if(optimization != null) {
			// set optimization status to running
			optimization.setStatus(OptimizationStatus.RUNNING);

			logger.info("Starting optimization " + optId + " and running optimization algorithm.");

			Object resultMap;
			resultMap = optimization.run();

			logger.info("Optimization " + optId + " has finished.");


			if(optimization.getStatus() != OptimizationStatus.CANCELLED) {
				optimization.setStatus(OptimizationStatus.DONE);
			}

			logger.info("Convert the result map into the required format.");
			optimizationResultDto = optimization.convertResultMapToOptimizationResultMapDto(optId, (Map<? extends Flight, Slot>) resultMap);

			// get the fitness and fitness function invocations from the statistics and include it in the results
			logger.info("Including basic statistics in the response.");
			optimizationResultDto.setFitness(optimization.getStatistics().getResultFitness());
		} else {
			logger.info("Optimization " + optId + " not found.");
		}

		return optimizationResultDto;
	}

	/**
	 * Abort the optimization with the specified identifier
	 * @param optId the optimization identifier
	 */
	public void abortOptimization(UUID optId) {
		Future<OptimizationResultDTO> future = this.threads.get(optId);
        JeneticsOptimization optimization = this.optimizations.get(optId);

		logger.info("Cancel the running optimization " + optId);

		if(future != null && future.cancel(true)) {
            optimization.setStatus(OptimizationStatus.CANCELLED);
			logger.info("Cancellation successfully triggered.");

			optimization.getStatistics().setTimeAborted(LocalDateTime.now()); // set the abort time in the statistics
		} else {
			logger.info("Could not cancel.");
		}
	}

	/**
	 * Register the future of an optimization; this allows to abort the thread later.
	 * @param optId the optimization identifier
	 * @param future the future obtained from an asynchronous run
	 */
	public void registerThread(UUID optId, Future<OptimizationResultDTO> future) {
		logger.info("Registering future for optimization with id " + optId);
		this.threads.put(optId, future);
	}

	/**
	 * Return all the optimizations currently known to the Optimizer
	 * @return a list of optimizations
	 */
    public OptimizationDTO[] getOptimizations() {
		return this.optimizationDTOs.values().toArray(OptimizationDTO[]::new);
    }

	private void createSOJenetics(OptimizationDTO optimizationDto, SOJeneticsOptimizationFactory factory, Flight[] flights, Slot[] slots, UUID optId) {
		SOJeneticsOptimization newOptimization;
		try {
			logger.info("Create a new optimization with the specified characteristics");
			if (optimizationDto.getParameters() != null) {
				newOptimization = factory.createOptimization(flights, slots, optimizationDto.getParameters());
			} else {
				newOptimization = factory.createOptimization(flights, slots);
			}

			instantiateAndDefineOptimization(optimizationDto, newOptimization, optId);

			// set initial solution's fitness in optimization statistics; only available in non-privacy-preserving mode
			logger.info("Initial flight sequence: {}.", Arrays.toString(newOptimization.getInitialFlightSequence()));

			if(optimizationDto.getOptimizationMode() == OptimizationModeEnum.BENCHMARKING ||
					optimizationDto.getOptimizationMode() == OptimizationModeEnum.DEMONSTRATION ||
					optimizationDto.getOptimizationMode() == OptimizationModeEnum.NON_PRIVACY_PRESERVING
			) {
				int initialFitness = newOptimization.computeInitialFitness();

				newOptimization.getStatistics().setInitialFitness(initialFitness);
			}

			if(optimizationDto.getOptimizationMode() == OptimizationModeEnum.BENCHMARKING ||
					optimizationDto.getOptimizationMode() == OptimizationModeEnum.DEMONSTRATION) {
				logger.info("Get theoretical maximum fitness by running the Hungarian algorithm before the actual optimization.");

				double theoreticalMaximumFitness = applyHungarian(new HungarianOptimization(flights, slots));

				newOptimization.getStatistics().setTheoreticalMaxFitness((int)theoreticalMaximumFitness);
				newOptimization.getConfiguration().setParameter("theoreticalMaximumFitness", theoreticalMaximumFitness);
			}

			if(optimizationDto.getFitnessPrecision() != null) {
				logger.info("Set fitness precision: " + optimizationDto.getFitnessPrecision());
				newOptimization.setFitnessPrecision(optimizationDto.getFitnessPrecision());
			}

			setOptimizationToInitialized(newOptimization, optimizationDto);
		} catch (InvalidOptimizationParameterTypeException e) {
			logger.error("Could not create optimization due to error in parameters.", e);
		}
	}

	private void createMLAJenetics(OptimizationDTO optimizationDto, MLAJeneticsOptimizationFactory factory, FlightMO[] flights, Slot[] slots, UUID optId) {
		MLAJeneticsOptimization newOptimization;
		try {
			logger.info("Create a new optimization with the specified characteristics");
			if (optimizationDto.getParameters() != null) {
				newOptimization = factory.createOptimization(flights, slots, optimizationDto.getParameters());
			} else {
				newOptimization = factory.createOptimization(flights, slots);
			}

			instantiateAndDefineOptimization(optimizationDto, newOptimization, optId);

			// set initial solution's fitness in optimization statistics; only available in non-privacy-preserving mode
			logger.info("Initial flight sequence: {}.", Arrays.toString(newOptimization.getInitialFlightSequence()));

			if(optimizationDto.getOptimizationMode() == OptimizationModeEnum.BENCHMARKING ||
					optimizationDto.getOptimizationMode() == OptimizationModeEnum.DEMONSTRATION ||
					optimizationDto.getOptimizationMode() == OptimizationModeEnum.NON_PRIVACY_PRESERVING
			) {
				int initialFitness = newOptimization.computeInitialFitness();
				newOptimization.getStatistics().setInitialFitness(initialFitness);
			}

			if(optimizationDto.getOptimizationMode() == OptimizationModeEnum.BENCHMARKING ||
					optimizationDto.getOptimizationMode() == OptimizationModeEnum.DEMONSTRATION) {
				setTwoTheoreticalFitness(flights, slots, newOptimization);

				ParetoFrontEstimator paretoFrontEstimator = new ParetoFrontEstimator(flights, slots);
				double[][] estimatedParetoFront = paretoFrontEstimator.calculateParetoFront();
				newOptimization.getStatistics().setEstimatedParetoFront(estimatedParetoFront);
				newOptimization.getConfiguration().setParameter("estimatedParetoFront", estimatedParetoFront);
			}

			if(optimizationDto.getFitnessPrecision() != null) {
				logger.info("Set fitness precision: " + optimizationDto.getFitnessPrecision());
				newOptimization.setFitnessPrecision(optimizationDto.getFitnessPrecision());
			}

			setOptimizationToInitialized(newOptimization, optimizationDto);
		} catch (InvalidOptimizationParameterTypeException e) {
			logger.error("Could not create optimization due to error in parameters.", e);
		}
	}

	private void createMOJenetics(OptimizationDTO optimizationDto, MOJeneticsOptimizationFactory factory, FlightMO[] flights, Slot[] slots, UUID optId) {
		MOJeneticsOptimization newOptimization;
		try {
			logger.info("Create a new optimization with the specified characteristics");
			if (optimizationDto.getParameters() != null) {
				newOptimization = factory.createOptimization(flights, slots, optimizationDto.getParameters());
			} else {
				newOptimization = factory.createOptimization(flights, slots);
			}

			instantiateAndDefineOptimization(optimizationDto, newOptimization, optId);

			// set initial solution's fitness in optimization statistics; only available in non-privacy-preserving mode
			logger.info("Initial flight sequence: {}.", Arrays.toString(newOptimization.getInitialFlightSequence()));

			if(optimizationDto.getOptimizationMode() == OptimizationModeEnum.BENCHMARKING ||
					optimizationDto.getOptimizationMode() == OptimizationModeEnum.DEMONSTRATION ||
					optimizationDto.getOptimizationMode() == OptimizationModeEnum.NON_PRIVACY_PRESERVING
			) {
				int initialFitness = newOptimization.computeInitialFitness();

				newOptimization.getStatistics().setInitialFitness(initialFitness);
			}

			if(optimizationDto.getOptimizationMode() == OptimizationModeEnum.BENCHMARKING ||
					optimizationDto.getOptimizationMode() == OptimizationModeEnum.DEMONSTRATION) {
				setTwoTheoreticalFitness(flights, slots, newOptimization);

				ParetoFrontEstimator paretoFrontEstimator = new ParetoFrontEstimator(flights, slots);
				double[][] estimatedParetoFront = paretoFrontEstimator.calculateParetoFront();
				newOptimization.getStatistics().setEstimatedParetoFront(estimatedParetoFront);
				newOptimization.getConfiguration().setParameter("estimatedParetoFront", estimatedParetoFront);
			}

			if(optimizationDto.getFitnessPrecision() != null) {
				logger.info("Set fitness precision: " + optimizationDto.getFitnessPrecision());
				newOptimization.setFitnessPrecision(optimizationDto.getFitnessPrecision());
			}

			setOptimizationToInitialized(newOptimization, optimizationDto);
		} catch (InvalidOptimizationParameterTypeException e) {
			logger.error("Could not create optimization due to error in parameters.", e);
		}
	}

	private void instantiateAndDefineOptimization(OptimizationDTO optimizationDto, JeneticsOptimization newOptimization, UUID optId) {
		instantiateNewOptimization(optimizationDto, newOptimization, optId);

		setFitnessEstimator(optimizationDto, newOptimization);

		setPrivacyEngineEndpoint(optimizationDto, newOptimization);

		setFitnessMethod(optimizationDto, newOptimization);
	}

	private SOJeneticsOptimizationFactory createSOFactory(String className, String optimizationFramework)
			throws ClassNotFoundException, InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException {
		SOJeneticsOptimizationFactory factory;

		try {
			logger.info("Instantiate " + className + " for optimization framework " + optimizationFramework);
			factory = (SOJeneticsOptimizationFactory) Class.forName(className).getDeclaredConstructor().newInstance();
		} catch (ClassNotFoundException |
				 InvocationTargetException |
				 InstantiationException |
				 IllegalAccessException |
				 NoSuchMethodException e) {
			logger.error("Could not instantiate optimization factory.", e);
			throw e;
		}
		return factory;
	}

	private MOJeneticsOptimizationFactory createMOFactory(String className, String optimizationFramework)
			throws ClassNotFoundException, InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException {
		MOJeneticsOptimizationFactory factory;

		try {
			logger.info("Instantiate " + className + " for optimization framework " + optimizationFramework);
			factory = (MOJeneticsOptimizationFactory) Class.forName(className).getDeclaredConstructor().newInstance();
		} catch (ClassNotFoundException |
				 InvocationTargetException |
				 InstantiationException |
				 IllegalAccessException |
				 NoSuchMethodException e) {
			logger.error("Could not instantiate optimization factory.", e);
			throw e;
		}
		return factory;
	}

	private MLAJeneticsOptimizationFactory createMLAFactory(String className, String optimizationFramework)
			throws ClassNotFoundException, InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException {
		MLAJeneticsOptimizationFactory factory;

		try {
			logger.info("Instantiate " + className + " for optimization framework " + optimizationFramework);
			factory = (MLAJeneticsOptimizationFactory) Class.forName(className).getDeclaredConstructor().newInstance();
		} catch (ClassNotFoundException |
				 InvocationTargetException |
				 InstantiationException |
				 IllegalAccessException |
				 NoSuchMethodException e) {
			logger.error("Could not instantiate optimization factory.", e);
			throw e;
		}
		return factory;
	}
	private void setTwoTheoreticalFitness(FlightMO[] flights, Slot[] slots, MOJeneticsOptimization newOptimization) {
		logger.info("Get theoretical maximum fitness for the two optimization targets by running the Hungarian algorithm before the actual optimization.");

		double theoreticalMaximumFitness = applyHungarian(new HungarianOptimization(flights, slots));

		newOptimization.getStatistics().setTheoreticalMaxFitness((int) theoreticalMaximumFitness);
		newOptimization.getConfiguration().setParameter("theoreticalMaximumFitnessFirst", theoreticalMaximumFitness);

		double secondTheoreticalMaximumFitness = applyHungarian(new MOHungarianOptimization(flights, slots));

		newOptimization.getStatistics().setTheoreticalMaxFitnessTwo((int)secondTheoreticalMaximumFitness);
		newOptimization.getConfiguration().setParameter("theoreticalMaximumFitnessSecond", secondTheoreticalMaximumFitness);
	}

	private void instantiateNewOptimization(OptimizationDTO optimizationDto, JeneticsOptimization newOptimization, UUID optId) {
		newOptimization.setOptId(optId);
		// set initial flight sequence
		newOptimization.setInitialFlightSequence(optimizationDto.getInitialFlightSequence());

		// set the benchmarking mode (whether evolution of fitness is tracked)
		newOptimization.setTraceFitnessEvolution(optimizationDto.isTraceFitnessEvolution());

		if(optimizationDto.isTraceFitnessEvolution()) {
			newOptimization.getStatistics().setFitnessEvolution(new LinkedList<>());
		}

		// set the creation time in the optimization's statistics
		newOptimization.getStatistics().setTimeCreated(LocalDateTime.now());

		logger.info("Store optimization " + optId + " for later invocation");
		optimizations.put(optId, newOptimization);
	}

	private void setFitnessEstimator(OptimizationDTO optimizationDto, JeneticsOptimization newOptimization) {
		String estimatorName = optimizationDto.getFitnessEstimator();
		String estimatorClassName;

		logger.info("Read the fitness estimator class from the JSON properties file.");
		String estimatorClasses = System.getProperty(OptimizerApplication.FITNESS_ESTIMATOR);

		if(estimatorName != null) {
			estimatorClassName =
					Utils.getMapFromJson(estimatorClasses).get(optimizationDto.getFitnessEstimator());

			try {
				logger.info("Setting fitness estimator to " + estimatorClassName);
				FitnessEstimator estimator =
						(FitnessEstimator) Class.forName(estimatorClassName).getDeclaredConstructor().newInstance();

				newOptimization.setFitnessEstimator(estimator);
			} catch (ClassNotFoundException |
					 InvocationTargetException |
					 InstantiationException |
					 IllegalAccessException |
					 NoSuchMethodException e) {
				logger.error("Could not instantiate fitness estimator.", e);
			}
		} else {
			logger.info("No fitness estimator specified. Trying to use absolute fitness.");
		}
	}

	private void setFitnessMethod(OptimizationDTO optimizationDto, JeneticsOptimization newOptimization) {
		if(optimizationDto.getFitnessMethod() != null) {
			logger.info("Set fitness method: " + optimizationDto.getFitnessMethod());
			switch(optimizationDto.getFitnessMethod()) {
				case FITNESS_RANGE_QUANTILES -> {
					newOptimization.setFitnessMethod(FitnessMethod.FITNESS_RANGE_QUANTILES);
				}
				case ABOVE_RELATIVE_THRESHOLD -> {
					newOptimization.setFitnessMethod(FitnessMethod.ABOVE_RELATIVE_THRESHOLD);
				}
				case ABOVE_ABSOLUTE_THRESHOLD -> {
					newOptimization.setFitnessMethod(FitnessMethod.ABOVE_ABSOLUTE_THRESHOLD);
				}
				case ORDER -> {
					newOptimization.setFitnessMethod(FitnessMethod.ORDER);
				}
				case ORDER_QUANTILES -> {
					newOptimization.setFitnessMethod(FitnessMethod.ORDER_QUANTILES);
				}
				case ACTUAL_VALUES -> {
					newOptimization.setFitnessMethod(FitnessMethod.ACTUAL_VALUES);
				}
			}
		}
	}

	private void setPrivacyEngineEndpoint(OptimizationDTO optimizationDto, JeneticsOptimization newOptimization) {
		if (optimizationDto.getOptimizationMode() == OptimizationModeEnum.PRIVACY_PRESERVING) {
			String privacyEngineEndpoint = optimizationDto.getPrivacyEngineEndpoint();
			logger.info("Set the endpoint URI for connection with the PrivacyEngine: " + privacyEngineEndpoint);
			newOptimization.setPrivacyEngineEndpoint(privacyEngineEndpoint);

			logger.debug("Setting the Privacy Engine service using the class " + this.privacyEngineService.getClass());
			newOptimization.setPrivacyEngineService(this.privacyEngineService);

			logger.info("Setting optimization mode to PRIVACY_PRESERVING.");
			newOptimization.setMode(OptimizationMode.PRIVACY_PRESERVING);
		} else if (optimizationDto.getOptimizationMode() == OptimizationModeEnum.NON_PRIVACY_PRESERVING) {
			logger.info("Setting optimization mode to NON_PRIVACY_PRESERVING.");
			newOptimization.setMode(OptimizationMode.NON_PRIVACY_PRESERVING);
		} else if (optimizationDto.getOptimizationMode() == OptimizationModeEnum.DEMONSTRATION) {
			logger.info("Setting optimization mode to DEMONSTRATION.");
			newOptimization.setMode(OptimizationMode.DEMONSTRATION);
		} else if (optimizationDto.getOptimizationMode() == OptimizationModeEnum.BENCHMARKING) {
			logger.info("Setting optimization mode to BENCHMARKING.");
			newOptimization.setMode(OptimizationMode.BENCHMARKING);
		}
	}

	private double applyHungarian(HungarianOptimization optimization) {
		var optimalSolution = optimization.run();
		logger.info("Checking if optimal solution produced by Hungarian is valid.");
		var invalidMappings = optimalSolution.entrySet().stream().filter(e -> e.getKey().getScheduledTime() != null && e.getValue().getTime().isBefore(e.getKey().getScheduledTime())).count();
		logger.info("Solution contains {} assignments where the scheduled time of the flight is available and after the assigned slots' time.", invalidMappings);

		logger.info("Optimal flight sequence according to Hungarian: {}", Arrays.toString(optimalSolution.keySet()
				.stream()
				.sorted(Comparator.comparing(flight -> optimalSolution.get(flight).getTime()))
				.map(Flight::getFlightId)
				.toArray()));

		return optimization.getOptimalFitness();
	}

	private void setOptimizationToInitialized(JeneticsOptimization newOptimization, OptimizationDTO optimizationDto) {
		logger.info("Set optimization status to INITIALIZED");
		newOptimization.setStatus(OptimizationStatus.INITIALIZED);
		optimizationDto.setOptimizationStatus(OptimizationStatusEnum.INITIALIZED);

		// set the timestamp to indicate to the caller when the information was created
		optimizationDto.setTimestamp(LocalDateTime.now());
	}
}
