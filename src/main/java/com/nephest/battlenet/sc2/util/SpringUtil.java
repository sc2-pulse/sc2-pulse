// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.util;

import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import org.springframework.core.MethodParameter;

public final class SpringUtil
{

    private SpringUtil(){}

    public static Class<?> getClass(MethodParameter methodParameter)
    {
        Class<?> type = methodParameter.getParameter().getType();
        return Collection.class.isAssignableFrom(type)
            ? (Class<?>)((ParameterizedType) methodParameter.getNestedGenericParameterType())
            .getActualTypeArguments()[0]
            : type.isArray()
                ? type.getComponentType()
                : type;
    }

}
