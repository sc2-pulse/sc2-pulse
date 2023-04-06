// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import com.nephest.battlenet.sc2.model.local.ClanMemberEvent;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class IntegerToClanMemberEventTypeConverter
implements Converter<Integer, ClanMemberEvent.EventType>
{

    @Override
    public ClanMemberEvent.EventType convert(@NonNull Integer id)
    {
        return ClanMemberEvent.EventType.from(id);
    }

}
