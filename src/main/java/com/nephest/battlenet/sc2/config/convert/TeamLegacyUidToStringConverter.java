// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class TeamLegacyUidToStringConverter
implements Converter<TeamLegacyUid, String>
{

    @Autowired
    @Qualifier("sc2StatsConversionService")
    private ConversionService conversionService;

    public static final String SPLITTER = "-";

    @Override
    public String convert(TeamLegacyUid source)
    {
        return conversionService.convert(source.getQueueType(), Integer.class)
            + SPLITTER + conversionService.convert(source.getRegion(), Integer.class)
            + SPLITTER + source.getId();
    }
}
