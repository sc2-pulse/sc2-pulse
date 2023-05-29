// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.mvc;

import com.nephest.battlenet.sc2.web.controller.group.CharacterGroupArgumentResolver;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MVCConfig
implements WebMvcConfigurer
{

    @Autowired
    private CharacterGroupArgumentResolver characterGroupArgumentResolver;

    @Override
    public void addArgumentResolvers(@NotNull List<HandlerMethodArgumentResolver> argumentResolvers)
    {
        argumentResolvers.add(characterGroupArgumentResolver);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("home");
        registry.addViewController("/login").setViewName("plogin");
        registry.addViewController("/terms-of-service").setViewName("terms-of-service");
        registry.addViewController("/privacy-policy").setViewName("privacy-policy");
        registry.addViewController("/about").setViewName("about");
        registry.addViewController("/status").setViewName("status");
        registry.addViewController("/contacts").setViewName("contacts");
        registry.addViewController("/donate").setViewName("donate");
        registry.addViewController("/team/history").setViewName("team-history");
        registry.addViewController("/discord/bot").setViewName("discord-bot");
    }

}
