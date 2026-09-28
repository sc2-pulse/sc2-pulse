// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import java.util.List;
import org.springframework.data.domain.Sort;

public class SortAllowedFieldValidator
extends AbstractAllowedFieldValidator<Sort>
{

    @Override
    public boolean isTraversable(Sort value)
    {
        return value.stream().count() > 1;
    }

    @Override
    public List<String> getFields(Sort value)
    {
        return value == null || value.isUnsorted()
            ? null
            : value.stream().map(Sort.Order::getProperty).toList();
    }


}
