package optimizer.optimization.jenetics.jeneticsMLA;

import optimizer.optimization.jenetics.jeneticsMO.MOJeneticsOptimizationConfiguration;
import io.jenetics.*;
import io.jenetics.ext.moea.Vec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MLAJeneticsOptimizationConfiguration extends MOJeneticsOptimizationConfiguration {

    private static final Logger logger = LogManager.getLogger();

    @Override
    public Selector<EnumGene<Integer>, Vec<int[]>> getSelector(String selectorType, Number selectorParameter) {
        Selector<EnumGene<Integer>, Vec<int[]>> selector = null;

        if(selectorType != null) {
            switch(selectorType) {
                case "EXPONENTIAL_RANK_SELECTOR":
                    if(selectorParameter != null) {
                        selector = new ExponentialRankSelector<>(selectorParameter.doubleValue());
                    } else {
                        selector = new ExponentialRankSelector<>();
                    }
                    break;
                case "LINEAR_RANK_SELECTOR":
                    if(selectorParameter != null) {
                        selector = new LinearRankSelector<>(selectorParameter.doubleValue());
                    } else {
                        selector = new LinearRankSelector<>();
                    }
                    break;
                case "TOURNAMENT_SELECTOR":
                    if(selectorParameter != null) {
                        logger.info("Using tournament selector with parameter " + selectorParameter.intValue() + ".");
                        selector = new TournamentSelector<>(selectorParameter.intValue());
                    } else {
                        logger.info("Using tournament selector with default parameter.");
                        selector = new TournamentSelector<>();
                    }
                    break;
                case "TRUNCATION_SELECTOR":
                    if(selectorParameter != null) {
                        selector = new TruncationSelector<>(selectorParameter.intValue());
                    } else {
                        selector = new TruncationSelector<>();
                    }
                    break;
            }
        }

        return selector;
    }
}

