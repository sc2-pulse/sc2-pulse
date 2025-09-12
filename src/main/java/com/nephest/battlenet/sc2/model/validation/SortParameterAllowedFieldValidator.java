// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import com.nephest.battlenet.sc2.model.web.SortParameter;

public class SortParameterAllowedFieldValidator
extends AbstractAllowedFieldValidator<SortParameter>
{

    @Override
    public String getField(SortParameter value)
    {
        return value == null ? null : value.field();
    }

}

