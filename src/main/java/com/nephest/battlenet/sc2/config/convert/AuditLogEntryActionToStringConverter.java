// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import com.nephest.battlenet.sc2.model.local.AuditLogEntry;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;

public class AuditLogEntryActionToStringConverter
implements Converter<AuditLogEntry.Action, String>
{

    @Override
    public String convert(@NonNull AuditLogEntry.Action action)
    {
        return action.getShortName();
    }

}
