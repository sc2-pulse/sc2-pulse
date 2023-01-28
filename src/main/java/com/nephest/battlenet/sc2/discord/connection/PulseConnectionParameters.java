// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord.connection;

import com.nephest.battlenet.sc2.discord.Discord;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Component;

@Discord
@Component
public final class PulseConnectionParameters
{

    public static final String DELIMITER = ", ";

    private final Map<PulseConnectionParameter, List<ConnectionMetaData>> parameters;

    @Autowired
    public PulseConnectionParameters
    (
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        Map<PulseConnectionParameter, List<ConnectionMetaData>> parameters =
            new EnumMap<>(PulseConnectionParameter.class);
        parameters.put
        (
            PulseConnectionParameter.REGION,
            List.of
            (
                new ConnectionMetaData
                (
                    ConnectionMetaData.Type.INTEGER_EQUAL,
                    PulseConnectionParameter.REGION.getName().toLowerCase(),
                    "Region",
                    Arrays.stream(Region.values())
                        .map(r->r.getName() + "(" + conversionService.convert(r, Integer.class) + ")")
                        .collect(Collectors.joining(DELIMITER))
                )
            )
        );
        parameters.put
        (
            PulseConnectionParameter.RACE,
            List.of
            (
                new ConnectionMetaData
                (
                    ConnectionMetaData.Type.INTEGER_EQUAL,
                    PulseConnectionParameter.RACE.getName().toLowerCase(),
                    "Race",
                    Arrays.stream(Race.values())
                        .map(r->r.getName() + "(" + conversionService.convert(r, Integer.class) + ")")
                        .collect(Collectors.joining(DELIMITER))
                )
            )
        );
        parameters.put
        (
            PulseConnectionParameter.LEAGUE,
            List.of
            (
                new ConnectionMetaData
                (
                    ConnectionMetaData.Type.INTEGER_EQUAL,
                    PulseConnectionParameter.LEAGUE.getName().toLowerCase(),
                    "League",
                    Arrays.stream(BaseLeague.LeagueType.values())
                        .map(r->r.getName() + "(" + conversionService.convert(r, Integer.class) + ")")
                        .collect(Collectors.joining(DELIMITER))
                )
            )
        );
        parameters.put
        (
            PulseConnectionParameter.RATING,
            List.of
            (
                new ConnectionMetaData
                (
                    ConnectionMetaData.Type.INTEGER_GREATER_THAN_OR_EQUAL,
                    PulseConnectionParameter.RATING.getName().toLowerCase() + "_from",
                    "MMR from",
                    "MMR starting from(including)"
                ),
                new ConnectionMetaData
                (
                    ConnectionMetaData.Type.INTEGER_LESS_THAN_OR_EQUAL,
                    PulseConnectionParameter.RATING.getName().toLowerCase() + "_to",
                    "MMR to",
                    "MMR to(including)"
                )
            )
        );
        this.parameters = Collections.unmodifiableMap(parameters);
    }

    public Map<PulseConnectionParameter, List<ConnectionMetaData>> getParameters()
    {
        return parameters;
    }

}
