// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.mvc;

import com.nephest.battlenet.sc2.web.controller.group.CharacterGroupArgumentResolver;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
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

}
