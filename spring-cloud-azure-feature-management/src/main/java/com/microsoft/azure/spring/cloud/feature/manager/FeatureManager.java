/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */
package com.microsoft.azure.spring.cloud.feature.manager;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.spring.cloud.feature.manager.entities.Feature;
import com.microsoft.azure.spring.cloud.feature.manager.entities.FeatureFilterEvaluationContext;

import reactor.core.publisher.Mono;

/**
 * Holds information on Feature Management properties and can check if a given feature is
 * enabled.
 */
@SuppressWarnings("serial")
@Component("FeatureManagement")
@ConfigurationProperties(prefix = "feature-management")
public class FeatureManager extends HashMap<String, Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureManager.class);

    @Autowired
    private ApplicationContext context;

    private FeatureManagementConfigProperties properties;

    private HashMap<String, Feature> featureManagement;

    private HashMap<String, Boolean> onOff;

    private ObjectMapper mapper = new ObjectMapper();

    public FeatureManager(FeatureManagementConfigProperties properties) {
        this.properties = properties;
        featureManagement = new HashMap<String, Feature>();
        onOff = new HashMap<String, Boolean>();
    }

    /**
     * Checks to see if the feature is enabled. If enabled it check each filter, once a
     * single filter returns true it returns true. If no filter returns true, it returns
     * false. If there are no filters, it returns true. If feature isn't found it returns
     * false.
     * 
     * @param feature Feature being checked.
     * @return state of the feature
     */
    public Mono<Boolean> isEnabledAsync(String feature) {
        return Mono.just(checkFeatures(feature));
    }

    private boolean checkFeatures(String feature) {
        boolean enabled = false;
        if (featureManagement == null || onOff == null) {
            return false;
        }

        Feature featureItem = featureManagement.get(feature);
        Boolean boolFeature = onOff.get(feature);

        if (boolFeature != null) {
            return boolFeature;
        } else if (featureItem == null) {
            return false;
        }

        for (FeatureFilterEvaluationContext filter : featureItem.getEnabledFor()) {
            if (filter != null && filter.getName() != null) {
                try {
                    FeatureFilter featureFilter = (FeatureFilter) context.getBean(filter.getName());
                    enabled = Mono.just(featureFilter.evaluate(filter)).block();
                } catch (NoSuchBeanDefinitionException e) {
                    LOGGER.error("Was unable to find Filter " + filter.getName()
                            + ". Does the class exist and set as an @Component?");
                    if (properties.isFailFast()) {
                        String message = "Fail fast is set and a Filter was unable to be found.";
                        ReflectionUtils.rethrowRuntimeException(new FilterNotFoundException(message, e, filter));
                    }
                }
            }
            if (enabled) {
                return enabled;
            }
        }
        return enabled;
    }

    @SuppressWarnings("unchecked")
    private void addToFeatures(Map<? extends String, ? extends Object> features, String key, String combined) {
        Object featureKey = features.get(key);
        if (!combined.isEmpty() && !combined.endsWith(".")) {
            combined += ".";
        }
        if (featureKey instanceof Boolean) {
            onOff.put(combined + key, (Boolean) featureKey);
        } else {
            Feature feature = null;
            try {
                feature = mapper.convertValue(featureKey, Feature.class);
            } catch (IllegalArgumentException e) {
                LOGGER.error("Found invalid feature {} with value {}.", combined + key, featureKey.toString());
            }

            // When coming from a file "feature.flag" is not a possible flag name
            if (feature != null && feature.getEnabledFor() == null && feature.getKey() == null) {
                if (LinkedHashMap.class.isAssignableFrom(featureKey.getClass())) {
                    features = (LinkedHashMap<String, Object>) featureKey;
                    for (String fKey : features.keySet()) {
                        addToFeatures(features, fKey, combined + key);
                    }
                }
            } else {
                if (feature != null) {
                    feature.setKey(key);
                    featureManagement.put(key, feature);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void putAll(Map<? extends String, ? extends Object> m) {
        if (m == null) {
            return;
        }
        
        if (m.size() == 1 && m.get("featureManagement") != null) {
            m = (Map<? extends String, ? extends Object>) m.get("featureManagement");
        }
        
        for (String key : m.keySet()) {
            addToFeatures(m, key, "");
        }
    }

    /**
     * @return the featureManagement
     */
    HashMap<String, Feature> getFeatureManagement() {
        return featureManagement;
    }

    /**
     * @return the onOff
     */
    HashMap<String, Boolean> getOnOff() {
        return onOff;
    }
    
    
}
