// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.mvc;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MVCConfig
implements WebMvcConfigurer
{

    private final WebProperties webProperties;

    public MVCConfig(WebProperties webProperties)
    {
        this.webProperties = webProperties;
    }

    @Autowired
    private List<HandlerMethodArgumentResolver> customArgumentResolvers;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry)
    {
        //add /static prefix to static resource URLs
        registry.addResourceHandler("/static/**")
            .addResourceLocations(webProperties.getResources().getStaticLocations());
    }

    @Override
    public void addArgumentResolvers(@NotNull List<HandlerMethodArgumentResolver> argumentResolvers)
    {
        argumentResolvers.addAll(customArgumentResolvers);
    }

}
