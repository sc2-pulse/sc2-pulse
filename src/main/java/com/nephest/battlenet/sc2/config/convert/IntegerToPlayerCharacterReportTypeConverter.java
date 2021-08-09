// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import com.nephest.battlenet.sc2.model.local.PlayerCharacterReport;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class IntegerToPlayerCharacterReportTypeConverter
implements Converter<Integer, PlayerCharacterReport.PlayerCharacterReportType>
{

    @Override
    public PlayerCharacterReport.PlayerCharacterReportType convert(@NonNull Integer id)
    {
        return PlayerCharacterReport.PlayerCharacterReportType.from(id);
    }

}
