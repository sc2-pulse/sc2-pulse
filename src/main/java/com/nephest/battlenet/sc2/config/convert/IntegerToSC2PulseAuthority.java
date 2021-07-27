// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import com.nephest.battlenet.sc2.config.security.SC2PulseAuthority;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class IntegerToSC2PulseAuthority
implements Converter<Integer, SC2PulseAuthority>
{

    @Override
    public SC2PulseAuthority convert(@NonNull Integer id)
    {
        return SC2PulseAuthority.from(id);
    }

}
