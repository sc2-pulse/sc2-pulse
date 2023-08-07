// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.openapi;

import com.nephest.battlenet.sc2.Application;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import javax.servlet.ServletContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
                .license(new License()
                    .name("Blizzard Developer API Terms Of Use")
                    .url("https://www.blizzard.com/en-us/legal/a2989b50-5f16-43b1-abec-2ae17cc09dd6/blizzard-developer-api-terms-of-use"))
                .description("You are free to use this web API for non-commercial purposes if you "
                    + "credit the original website(https://sc2pulse.nephest.com/sc2)"));
    }

}
