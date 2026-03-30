// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import com.nephest.battlenet.sc2.model.local.inner.TeamHistoryDAO;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class IntegerToSourceConverter
implements Converter<Integer, TeamHistoryDAO.Source>
{

    @Override
    public TeamHistoryDAO.Source convert(@NonNull Integer id)
    {
        return TeamHistoryDAO.Source.from(id);
    }

}
