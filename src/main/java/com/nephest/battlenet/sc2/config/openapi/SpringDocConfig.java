// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.openapi;

import com.nephest.battlenet.sc2.Application;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.ServletContext;

@Configuration
public class SpringDocConfig
{

    @Bean
    public OpenAPI customOpenAPI(@Autowired ServletContext servletContext) {
        return new OpenAPI()
            .addServersItem(new Server().url(servletContext.getContextPath()).description("local server"))
            .info(new Info()
                .title("SC2 Pulse API")
                .version(Application.VERSION)
                .description("You are free to use the API for non-commercial purposes if you credit the original "
                    + "website(nephest.com/sc2). API endpoints are pretty self explanatory, there are no special params or "
                    + "hidden points, anything you can use is already used by the website, so grab whatever data you like. "
                    + "Just be reasonable with your request rate."));
    }

}
