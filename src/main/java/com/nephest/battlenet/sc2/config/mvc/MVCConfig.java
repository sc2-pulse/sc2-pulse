// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.mvc;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MVCConfig
implements WebMvcConfigurer
{

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("home");
        registry.addViewController("/login").setViewName("plogin");
        registry.addViewController("/terms-of-service").setViewName("terms-of-service");
        registry.addViewController("/privacy-policy").setViewName("privacy-policy");
        registry.addViewController("/about").setViewName("about");
        registry.addViewController("/status").setViewName("status");
        registry.addViewController("/donate").setViewName("donate");
        registry.addViewController("/team/history").setViewName("team-history");
    }

}
