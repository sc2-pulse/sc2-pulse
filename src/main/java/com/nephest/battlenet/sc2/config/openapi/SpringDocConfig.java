// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.openapi;

import com.nephest.battlenet.sc2.Application;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.PlayerCharacterNaturalId;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.web.service.StatsService;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import jakarta.servlet.ServletContext;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.RouterOperationCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringDocConfig
{


    @Bean
    public OpenAPI customOpenAPI
    (
        @Autowired ServletContext servletContext,
        @Value("${com.nephest.battlenet.sc2.cors.allowed-origin-patterns:#{''}}")
        List<String> corsAllowedOriginPatterns
    )
    {
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
                    ## License
                    You are free to use this web API for non-commercial purposes if you
                    credit the original website(https://sc2pulse.nephest.com/sc2). Please note that
                    we provide different types of data from upstream services for convenience and
                    improved player experience. You can only access the data if: it's public such as
                    MMR history or clan history; it's created by us such as anonymous aggregated
                    stats; you direct a corresponding user to give us explicit permission to share
                    their original upstream data with you.
                    ## Parameter concatenation
                    Many endpoints accept parameter arrays.
                    Supported formats. Note that you can't mix these formats in the same request.
                    * `/api/endpoint?param=1&param=2`
                    * `/api/endpoint?param=1,2`
                    ## Cursor navigation
                    Some endpoints don't return the entire result set at once. Instead, you can
                    navigate it via opaque tokens. Each response contains a `navigation` object with
                    `before` and `after` text fields. Add a parameter to your next
                    request to navigate the result set.
                    
                    For example, to navigate forward, you should add an `after` query parameter
                    and set its value to the text token of the corresponding field of the navigation
                    object from the previous response.
                    ## Sorting
                    Supported formats(asc/desc):
                    * `+name`/`-name`. The plus char is optional.
                    * `name:asc`/`name:desc`
                    ## DateTime
                    * ISO-8601 text
                    * Epoch milliseconds
                    ## teamLegacyUid
                    `queueId-teamTypeId-regionId-legacyId`
                    ### teamLegacyId
                    `member1Realm.member1BnetId.member1Race~memberNRealm.memberNBnetId.memberNRace`
                    * Team members must be sorted by realm and bnet id in ascending order.
                    * Only 1v1 teams have races, the race is null in other team formats,
                    i.e. `member1Realm.member1BnetId.`
                    * The wildcard race `*` can be used for solo teams.
                    ## Request rate limit
                    The request rate limiter uses a token bucket algorithm. Any API request costs
                    1 token(this may be changed in the future). You start with a full bucket.
                    ### HTTP headers
                    * `RateLimit-Burst`: bucket capacity(max tokens)
                    * `RateLimit-Reset`: interval in seconds at which new tokens are added
                    * `RateLimit-Limit`: amount of tokens added in each interval/update
                    %1$s
                    ## Common ids
                    ### Regions
                    %2$s
                    ### Queues
                    %3$s
                    ### Team types
                    %4$s
                    ### Leagues
                    %5$s
                    ### Tiers
                    %6$s
                    ### Races
                    %7$s
                    """.formatted(
                        corsAllowedOriginPatterns.isEmpty()
                            ? ""
                            : """
                                ## CORS
                                CORS is supported for the following origins:
                                %1$s
                                """.formatted
                                (
                                    corsAllowedOriginPatterns.stream()
                                        .map(origin->"* " + origin)
                                        .collect(Collectors.joining("\n"))
                                ),
                        Arrays.stream(Region.values())
                            .map(r->"* " + r.name() + ": " + r.getId())
                            .collect(Collectors.joining("\n")),
                        QueueType.getTypes(StatsService.VERSION).stream()
                            .map(q->"* " + q.name() + ": " + q.getId())
                            .collect(Collectors.joining("\n")),
                        Arrays.stream(TeamType.values())
                            .map(tt->"* " + tt.name() + ": " + tt.getId())
                            .collect(Collectors.joining("\n")),
                        Arrays.stream(BaseLeague.LeagueType.values())
                            .map(l->"* " + l.name() + ": " + l.getId())
                            .collect(Collectors.joining("\n")),
                        Arrays.stream(BaseLeagueTier.LeagueTierType.values())
                            .map(t->"* " + t.name() + ": " + t.getId())
                            .collect(Collectors.joining("\n")),
                        Arrays.stream(Race.values())
                            .map(r->"* " + r.name() + ": " + r.getId())
                            .collect(Collectors.joining("\n"))
                )));
    }

    @Bean
    public RouterOperationCustomizer parameterPathRouterOperationCustomizer()
    {
        return (routerOperation, handlerMethod) ->
        {
            if(routerOperation.getParams().length > 0)
                routerOperation.setPath
                (
                    routerOperation.getPath()
                        + "?" + String.join("&", routerOperation.getParams())
                );
            return routerOperation;
        };
    }

}
