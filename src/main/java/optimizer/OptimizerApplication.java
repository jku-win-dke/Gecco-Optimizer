package optimizer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.io.InputStream;

@SpringBootApplication
public class OptimizerApplication {
    private static final Logger logger = LogManager.getLogger();

    public static final String FACTORY_PROPERTY = "optimization.factory";
    public static final String FITNESS_ESTIMATOR = "optimization.estimator";

    public static void main(String[] args) {
        final String resourceName = "optimizationFactorySettings.json";

        ClassLoader loader = Thread.currentThread().getContextClassLoader();

        try(InputStream resourceStream = loader.getResourceAsStream(resourceName)) {
            String optimizationFactorySettings = new String(resourceStream.readAllBytes());
            System.setProperty(FACTORY_PROPERTY, optimizationFactorySettings);
        } catch (IOException e) {
            logger.error("Could not read optimization factory settings.", e);
        }


        final String estimatorResourceName = "fitnessEstimatorSettings.json";

        try(InputStream resourceStream = loader.getResourceAsStream(estimatorResourceName)) {
            String estimatorSettings = new String(resourceStream.readAllBytes());
            System.setProperty(FITNESS_ESTIMATOR, estimatorSettings);
        } catch (IOException e) {
            logger.error("Could not read fitness estimator settings.", e);
        }

        SpringApplication.run(OptimizerApplication.class, args);
    }
}
