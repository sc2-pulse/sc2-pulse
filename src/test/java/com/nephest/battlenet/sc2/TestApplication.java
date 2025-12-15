// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2;

import com.nephest.battlenet.sc2.config.TestContainersDevRunConfig;
import org.springframework.boot.SpringApplication;

public class TestApplication
{

    public static final String SYSTEM_PROPERTY_NAME = "org.testcontainers.dev";

    public static void main(String[] args)
    {
        System.setProperty(SYSTEM_PROPERTY_NAME, Boolean.TRUE.toString());
        SpringApplication.from(Application::main)
            .with(TestContainersDevRunConfig.class)
            .run(args);
    }

}
