// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Service;

@Service
public class ConversionUtil
{

    private final ConversionService mvcConversionService;

    @Autowired
    public ConversionUtil
    (
        @Qualifier("mvcConversionService") ConversionService mvcConversionService
    )
    {
        this.mvcConversionService = mvcConversionService;
    }

    public String convertToString(Object obj)
    {
        return mvcConversionService.convert(obj, String.class);
    }

}
