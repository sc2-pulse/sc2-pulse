// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.openapi;

import com.nephest.battlenet.sc2.Application;
import com.nephest.battlenet.sc2.model.PlayerCharacterNaturalId;
import com.nephest.battlenet.sc2.model.Region;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import jakarta.servlet.ServletContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringDocConfig
{

    @Bean
    public OpenAPI customOpenAPI(@Autowired ServletContext servletContext) {
        return new OpenAPI()
            .components(new Components()
                .addSchemas
                (
                    "ToonHandle",
                    ModelConverters.getInstance().resolveAsResolvedSchema(
                            new AnnotatedType(PlayerCharacterNaturalId.class).resolveAsRef(false))
                        .schema
                        .type("string")
                        .pattern(PlayerCharacterNaturalId.TOON_HANDLE_REGEXP)
                        .description(PlayerCharacterNaturalId.TOON_HANDLE_DESCRIPTION)
                        .example(
                            PlayerCharacterNaturalId.of(Region.EU, 1, 3141896L).toToonHandle()
                        )
                )
            )
            .addServersItem(new Server().url(servletContext.getContextPath()).description("local server"))
            .info(new Info()
                .title("SC2 Pulse API")
                .version(Application.VERSION)
                .license(new License()
                    .name("Blizzard Developer API Terms Of Use")
                    .url("https://www.blizzard.com/en-us/legal/a2989b50-5f16-43b1-abec-2ae17cc09dd6/blizzard-developer-api-terms-of-use"))
                .description("""
                    You are free to use this web API for non-commercial purposes if you
                    credit the original website(https://sc2pulse.nephest.com/sc2). Please note that
                    we provide different types of data from upstream services for convenience and
                    improved player experience. You can only access the data if: it's public such as
                    MMR history or clan history; it's created by us such as anonymous aggregated
                    stats; you direct a corresponding user to give us explicit permission to share
                    their original upstream data with you.
                    """));
    }

}
