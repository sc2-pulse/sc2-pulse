// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class StringToTeamLegacyUidConverter
implements Converter<String, TeamLegacyUid>
{

    @Autowired @Qualifier("sc2StatsConversionService")
    private ConversionService conversionService;

    @Override
    public TeamLegacyUid convert(String source)
    {
        String[] split = source.split("-");
        if(split.length != 4) throw new IllegalArgumentException("legacyUid must have 4 components");

        return new TeamLegacyUid
        (
            conversionService.convert(Integer.parseInt(split[0]), QueueType.class),
            conversionService.convert(Integer.parseInt(split[1]), TeamType.class),
            conversionService.convert(Integer.parseInt(split[2]), Region.class),
            split[3]
        );
    }

}
