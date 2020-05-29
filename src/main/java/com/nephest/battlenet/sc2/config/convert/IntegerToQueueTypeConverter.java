// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import com.nephest.battlenet.sc2.model.QueueType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class IntegerToQueueTypeConverter
implements Converter<Integer, QueueType>
{

    @Override
    public QueueType convert(@NonNull Integer id)
    {
        return QueueType.from(id);
    }

}
