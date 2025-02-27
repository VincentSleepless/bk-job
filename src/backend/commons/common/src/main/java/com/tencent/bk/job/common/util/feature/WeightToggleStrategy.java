package com.tencent.bk.job.common.util.feature;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Random;

/**
 * 根据权重灰度策略
 */
@Slf4j
public class WeightToggleStrategy extends AbstractToggleStrategy {
    /**
     * 策略参数-权重
     */
    public static final String INIT_PARAM_WEIGHT = "weight";
    /**
     * 特性开关开启策略ID
     */
    public static final String STRATEGY_ID = "WeightToggleStrategy";

    private final int weight;

    private static final Random RANDOM = new SecureRandom();

    public WeightToggleStrategy(String featureId, Map<String, String> initParams) {
        super(featureId, initParams);
        assertRequiredParameter(INIT_PARAM_WEIGHT);
        String weightStrValue = initParams.get(INIT_PARAM_WEIGHT);
        this.weight = computeWeight(weightStrValue);
    }

    private int computeWeight(String weightStrValue) {
        String weightValue = weightStrValue.trim();
        if (StringUtils.isBlank(weightStrValue)) {
            log.error("Weight is empty!");
            throw new IllegalArgumentException("Weight is empty!");
        }
        try {
            int weight = Integer.parseInt(weightValue);
            if (weight < 0 || weight > 100) {
                log.error("Weight should be set between 0 and 100, value: {}", weight);
                throw new IllegalArgumentException("Weight should be set between 0 and 100");
            }
            return weight;
        } catch (NumberFormatException e) {
            log.error("Invalid weight value: {}, not a valid number", weightValue);
            throw new IllegalArgumentException("Weight should be a number");
        }
    }

    @Override
    public boolean evaluate(String featureId, FeatureExecutionContext ctx) {
        if (weight == 0) {
            return false;
        } else if (weight == 100) {
            return true;
        } else {
            int random = RANDOM.nextInt(100) + 1;
            return random <= weight;
        }
    }
}
