// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import java.util.List;

public class StringAllowedFieldValidator
extends AbstractAllowedFieldValidator<String>
{

    @Override
    public boolean isTraversable(String value)
    {
        return false;
    }

    @Override
    public List<String> getFields(String value)
    {
        return value == null ? null : List.of(value);
    }

}
