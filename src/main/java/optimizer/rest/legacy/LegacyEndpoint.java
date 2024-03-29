package optimizer.rest.legacy;

import com.optimization.data.optimizer.service.dto.FitnessMethodEnum;
import com.optimization.data.optimizer.service.dto.OptimizationModeEnum;
import com.optimization.data.optimizer.service.dto.FlightDTO;
import com.optimization.data.optimizer.service.dto.MarginsDTO;
import optimizer.service.OptimizationService;
import io.swagger.annotations.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

@Api(value = "SlotMachine Optimization Legacy")
@RestController
public class LegacyEndpoint {
    private static final Logger logger = LogManager.getLogger();
    
    @Autowired
    OptimizationService optimizationService;

    @ApiOperation(
            value = "Convert an optimization in the legacy representation to the new format.",
            response = com.optimization.data.optimizer.service.dto.OptimizationDTO.class,
            produces = "application/json",
            consumes = "application/json"
    )
    @PostMapping(path = "/conversion/optimizations", produces = "application/json", consumes = "application/json")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "OK"),
                    @ApiResponse(code = 400, message = "Bad Request")
            }
    )
    public ResponseEntity<com.optimization.data.optimizer.service.dto.OptimizationDTO> convertOptimization(@RequestBody OptimizationDTO input) {
        ResponseEntity<com.optimization.data.optimizer.service.dto.OptimizationDTO> optimizationResponse;

        try {
            com.optimization.data.optimizer.service.dto.OptimizationDTO optimizationDto =
                    new com.optimization.data.optimizer.service.dto.OptimizationDTO();

            logger.info("Converting base data ...");
            optimizationDto.setOptId(input.getOptId());

            optimizationDto.setInitialFlightSequence(input.getInitialFlightSequence());
            optimizationDto.setFitnessEstimator(input.getFitnessEstimator());
            optimizationDto.setParameters(input.getParameters());
            optimizationDto.setOptimizationFramework(input.getOptimizationFramework());
            optimizationDto.setOptimizationType(input.getOptimizationType());
            optimizationDto.setOptimizationMode(input.getOptimizationMode());
            optimizationDto.setPrivacyEngineEndpoint(input.getPrivacyEngineEndpoint());

            logger.info("Done. Converting flights ...");

            com.optimization.data.optimizer.service.dto.FlightDTO[] flights = Arrays.stream(input.getFlights())
                    .map(flight -> {
                        FlightDTO newFlight = new FlightDTO();

                        newFlight.setFlightId(flight.getFlightId());
                        newFlight.setScheduledTime(LocalDateTime.ofInstant(flight.getScheduledTime(), ZoneOffset.UTC));
                        newFlight.setWeightMap(flight.getWeightMap());
                        newFlight.setTimeNotAfter(LocalDateTime.ofInstant(flight.getTimeNotAfter(), ZoneOffset.UTC));
                        if(flight.getSecondWeightMap() != null) {
                            newFlight.setSecondWeightMap(flight.getSecondWeightMap());
                        }

                        Optional<optimizer.rest.legacy.MarginsDTO> oldMargins = null;

                        if(input.getMargins() != null) {
                            oldMargins =
                                    Arrays.stream(input.getMargins()).filter(marginsOpt -> marginsOpt.getFlightId().equals(flight.getFlightId())).findFirst();
                        }

                        if(oldMargins != null && oldMargins.isPresent()) {
                            MarginsDTO newMargins =
                                    new MarginsDTO();

                            newMargins.setScheduledTime(LocalDateTime.ofInstant(oldMargins.get().getScheduledTime(), ZoneOffset.UTC));
                            newMargins.setTimeWished(LocalDateTime.ofInstant(oldMargins.get().getTimeWished(), ZoneOffset.UTC));
                            newMargins.setTimeNotBefore(LocalDateTime.ofInstant(oldMargins.get().getTimeNotBefore(), ZoneOffset.UTC));
                            newMargins.setTimeNotAfter(LocalDateTime.ofInstant(oldMargins.get().getTimeNotAfter(), ZoneOffset.UTC));

                            newFlight.setMargins(newMargins);
                        }

                        return newFlight;
                    })
                    .toArray(com.optimization.data.optimizer.service.dto.FlightDTO[]::new);

            optimizationDto.setFlights(flights);

            logger.info("Done. Converting slots ...");

            com.optimization.data.optimizer.service.dto.SlotDTO[] slots = Arrays.stream(input.getSlots())
                    .map(slot -> {
                        com.optimization.data.optimizer.service.dto.SlotDTO newSlot =
                                new com.optimization.data.optimizer.service.dto.SlotDTO();

                        newSlot.setTime(LocalDateTime.ofInstant(slot.getTime(), ZoneOffset.UTC));

                        return newSlot;
                    })
                    .toArray(com.optimization.data.optimizer.service.dto.SlotDTO[]::new);

            optimizationDto.setSlots(slots);

            logger.info("Done.");

            optimizationResponse = new ResponseEntity<>(optimizationDto, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Couldn't finish conversion.", e);
            optimizationResponse = new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        return optimizationResponse;
    }

    @ApiOperation(
            value = "Create an optimization session with an optimization in the legacy representation as input.",
            response = com.optimization.data.optimizer.service.dto.OptimizationDTO.class,
            produces = "application/json",
            consumes = "application/json"
    )
    @PostMapping(path = "/legacy/optimizations", produces = "application/json", consumes = "application/json")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "OK"),
                    @ApiResponse(code = 400, message = "Bad Request")
            }
    )
    public ResponseEntity<com.optimization.data.optimizer.service.dto.OptimizationDTO> createAndInitializeOptimizationLegacy(
            @RequestBody OptimizationDTO input,
            @RequestParam(name = "optimizationMode", required = false)
            @ApiParam(value = "the optimization mode")
            OptimizationModeEnum optimizationMode,
            @RequestParam(name = "fitnessEstimator", required = false)
            @ApiParam(value = "the fitness estimator used to determine the fitness (logarithmic, sigmoid, linear, etc.)")
            String fitnessEstimator,
            @RequestParam(name = "traceFitnessEvolution", defaultValue = "true")
            @ApiParam(value = "true if evolution of fitness should be included in stats; used for evaluation.")
            boolean traceFitnessEvolution,
            @RequestParam(name = "fitnessMethod", required = false)
            @ApiParam(value = "the fitness method used by the Privacy Engine or Optimizer")
            FitnessMethodEnum fitnessMethod,
            @RequestParam(name = "fitnessPrecision", required = false)
            @ApiParam(value = "the precision of the fitness computation")
            Integer fitnessPrecision,
            @RequestParam(name = "additionalParameters", required = false)
            @ApiParam(value = "Additional parameters for the optimization")
            Map<String, Object> additionalParameters,
            @RequestParam(name = "deduplicate", required = false)
            @ApiParam(value = "the deduplication flag for Jenetics")
                    Boolean deduplicate,
            @RequestParam(name = "deduplicateMaxRetries", required = false)
            @ApiParam(value = "the max retries for the deduplication")
                    Integer deduplicateMaxRetries,
            @RequestParam(name = "secondObfuscated", required = false)
            @ApiParam(value = "true if both objectives should be obfuscated")
                    Boolean secondObfuscated
    ) {
        ResponseEntity<com.optimization.data.optimizer.service.dto.OptimizationDTO> optimizationResponse = null;

        com.optimization.data.optimizer.service.dto.OptimizationDTO optDto = null;
    	
    	ResponseEntity<com.optimization.data.optimizer.service.dto.OptimizationDTO> optimizationConvertResponse =
                this.convertOptimization(input);

    	if (optimizationConvertResponse.getStatusCode().equals(HttpStatus.OK)) {
    		optDto = optimizationConvertResponse.getBody();
    	} else {
            optimizationResponse = new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    	}

        if(optimizationResponse == null) {
            if (optimizationMode != null) {
                optDto.setOptimizationMode(optimizationMode);
            }

            if (fitnessEstimator != null) {
                optDto.setFitnessEstimator(fitnessEstimator);
            }

            if(fitnessMethod != null) {
                optDto.setFitnessMethod(fitnessMethod);
            }

            if(fitnessPrecision != null) {
                optDto.setFitnessPrecision(fitnessPrecision);
            }

            if(additionalParameters != null) {
                optDto.getParameters().putAll(additionalParameters);
            }

            if(deduplicate != null && optDto.getParameters() != null){
                optDto.getParameters().put("deduplicate", deduplicate);
            }
            if(deduplicateMaxRetries != null && optDto.getParameters() != null){
                optDto.getParameters().put("deduplicateMaxRetries", deduplicateMaxRetries);
            }

            if(secondObfuscated != null && optDto.getParameters() != null){
                optDto.getParameters().put("secondObfuscated", secondObfuscated);
            }

            optDto.setTraceFitnessEvolution(traceFitnessEvolution);

            try {
                com.optimization.data.optimizer.service.dto.OptimizationDTO optimizationDto =
                        optimizationService.createAndInitializeOptimization(optDto);

                optimizationResponse = new ResponseEntity<>(optimizationDto, HttpStatus.OK);

            } catch (Exception e) {
                optimizationResponse = new ResponseEntity<>(optDto, HttpStatus.BAD_REQUEST);
            }
        }

        return optimizationResponse;
    }
}
