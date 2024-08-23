// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;

public class CoreTestConfig
{

    public static final Duration IO_TIMEOUT = Duration.ofMillis(500);

    @Bean
    public RestTemplateCustomizer restTemplateCustomizer() {
        return new GlobalRestTemplateCustomizer((int) IO_TIMEOUT.toMillis());
    }

}
