/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */
package com.microsoft.azure.spring.cloud.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.web.context.support.ServletRequestHandledEvent;

@Component
public class ConfigListener implements ApplicationListener<ServletRequestHandledEvent> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigListener.class);

    private AzureCloudConfigRefresh azureCloudConfigRefresh;

    public ConfigListener(AzureCloudConfigRefresh azureCloudConfigRefresh) {
        this.azureCloudConfigRefresh = azureCloudConfigRefresh;
    }

    @Override
    public void onApplicationEvent(ServletRequestHandledEvent event) {
        try {
            azureCloudConfigRefresh.refreshConfigurations();
        } catch (Exception e) {
            LOGGER.error("Refresh failed with unexpected exception.", e);
        }
    }

}
