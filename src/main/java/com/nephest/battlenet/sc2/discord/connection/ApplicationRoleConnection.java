// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord.connection;

import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.local.Team;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.ConversionService;

public class ApplicationRoleConnection
{

    public static final String DEFAULT_PLATFORM_NAME = "SC2 Pulse";

    private final String platformName, platformUsername;

    @NotNull
    private final Map<String, String> metadata;

    public ApplicationRoleConnection
    (
        String platformName,
        String platformUsername,
        Map<String, String> metadata
    )
    {
        this.platformName = platformName;
        this.platformUsername = platformUsername;
        this.metadata = metadata;
    }

    public static ApplicationRoleConnection from
    (
        Team team,
        String name,
        Race race,
        Map<PulseConnectionParameter, List<ConnectionMetaData>> parameters,
        ConversionService conversionService
    )
    {
        Map<String, String> extractedParams = new LinkedHashMap<>();
        for(Map.Entry<PulseConnectionParameter, List<ConnectionMetaData>> param : parameters.entrySet())
        {
            for(ConnectionMetaData metaData : param.getValue())
            {
                String paramName = metaData.getKey();
                switch(param.getKey())
                {
                    case REGION:
                        extractedParams.put
                        (
                            paramName,
                            String.valueOf(conversionService.convert(team.getRegion(), Integer.class))
                        );
                        break;
                    case RACE:
                        extractedParams.put
                        (
                            paramName,
                            String.valueOf(conversionService.convert(race, Integer.class))
                        );
                        break;
                    case LEAGUE:
                        extractedParams.put
                        (
                            paramName,
                            String.valueOf(conversionService.convert(team.getLeagueType(), Integer.class))
                        );
                        break;
                    case RATING:
                        extractedParams.put
                        (
                            paramName,
                            String.valueOf(team.getRating())
                        );
                        break;
                }
            }
        }

        return new ApplicationRoleConnection
        (
            DEFAULT_PLATFORM_NAME,
            name,
            extractedParams
        );
    }

    private static ConnectionMetaData getLastRatingMetaData
    (
        Map<PulseConnectionParameter, List<ConnectionMetaData>> parameters
    )
    {
        if(parameters.isEmpty()) throw new IllegalArgumentException("Parameters expected");

        List<ConnectionMetaData> ratingMetaData = parameters.get(PulseConnectionParameter.RATING);
        if (ratingMetaData == null || ratingMetaData.isEmpty())
            throw new IllegalArgumentException("Rating parameters expected");

        return ratingMetaData.get(ratingMetaData.size() - 1);
    }


    public static ApplicationRoleConnection empty
    (
        String name,
        Map<PulseConnectionParameter, List<ConnectionMetaData>> parameters
    )
    {
        ConnectionMetaData ratingMetaData = getLastRatingMetaData(parameters);
        return new ApplicationRoleConnection
        (
            DEFAULT_PLATFORM_NAME,
            name,
            Map.of(ratingMetaData.getKey(), "0")
        );
    }

    public String getPlatformName()
    {
        return platformName;
    }

    public String getPlatformUsername()
    {
        return platformUsername;
    }

    public Map<String, String> getMetadata()
    {
        return metadata;
    }

}
