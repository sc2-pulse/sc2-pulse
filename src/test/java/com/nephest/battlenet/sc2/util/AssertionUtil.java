// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.util;

import java.util.List;
import java.util.function.BiPredicate;

/**
 * This class is intended to be used with {@link org.assertj.core.api.Assertions} or similar libs,
 * specifically in custom comparisons such as
 * {@link org.assertj.core.api.RecursiveComparisonAssert#withEqualsForFields(BiPredicate, String...)}
 *
 */
public final class AssertionUtil
{

    private AssertionUtil(){}

    @SuppressWarnings("unchecked")
    public static boolean numberListEquals(Object l, Object r)
    {
        List<Long> lLong = ((List<Number>) l).stream()
            .map(Number::longValue)
            .toList();
        List<Long> rLong = ((List<Number>) r).stream()
            .map(Number::longValue)
            .toList();
        return lLong.equals(rLong);
    }

    public static boolean numberEquals(Object l, Object r)
    {
        if(l == null && r != null) return false;
        if(l != null && r == null) return false;
        if(l == null) return true;
        return ((Number) l).longValue() == ((Number) r).longValue();
    }


}
