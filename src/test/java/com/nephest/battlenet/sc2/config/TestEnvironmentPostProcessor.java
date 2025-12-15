// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import com.nephest.battlenet.sc2.TestApplication;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/*TODO This should be replaced with DynamicPropertyRegistrar available in Spring Boot 3.4+. The
 * META-INFO/spring.factories file should also be removed then. Although this is also a valid way
 * to do this, just slightly less elegant, so it's not that important.
 */
/**
 * Using {@link System#out} for logging because proper logging is not initialized when
 * {@link EnvironmentPostProcessor} is executed.
 */
public class TestEnvironmentPostProcessor
implements EnvironmentPostProcessor
{

    private static final Map<String, String> OVERRIDES = Map.of
    (
        "server.port", "org.testcontainers.dev.http.server.port"
    );

    @Override
    public void postProcessEnvironment
    (
        ConfigurableEnvironment environment,
        SpringApplication application
    )
    {
        overrideApplicationProperties(environment);
    }

    private void overrideApplicationProperties
    (
        ConfigurableEnvironment environment
    )
    {
        if (!Boolean.getBoolean(TestApplication.SYSTEM_PROPERTY_NAME)) return;

        System.out.println("Overriding spring application properties");
        Map<String, Object> properties = new HashMap<>(OVERRIDES.size());
        for(Map.Entry<String, String> entry : OVERRIDES.entrySet())
        {
            String originalName = entry.getKey();
            String overrideName = entry.getValue();
            String overrideVal = environment.getProperty(overrideName);
            if(overrideVal == null) continue;

            properties.put(originalName, overrideVal);
            System.out.println
            (
                "Overridden application property value: "
                + originalName + "->" + overrideName
            );
        }
        if(properties.isEmpty()) return;

        MapPropertySource overriddenPropertySource =
            new MapPropertySource("devRunOverride", properties);
        environment.getPropertySources().addFirst(overriddenPropertySource);
    }

}
