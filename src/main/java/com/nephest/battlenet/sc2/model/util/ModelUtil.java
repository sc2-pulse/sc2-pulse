// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.util;

public final class ModelUtil
{

    public static final String VALIDATION_REGEXP_TRIMMED_NOT_BLANK = "^\\S+(?: +\\S+)*$";
    public static final String VALIDATION_REGEXP_TRIMMED_NOT_BLANK_SINGLE_SPACE = "^\\S+(?: \\S+)*$";
    public static final String VALIDATION_REGEXP_NOT_BLANK = "^(?!\\s*$).+";

    private ModelUtil(){}

    public static String trimSingleSpace(String input)
    {
        return input.trim().replaceAll("  +", " ");
    }

    public static String trimSingleSpaceNotBlank(String input)
    {
        if(input == null) return null;

        String result = trimSingleSpace(input);
        return result.isBlank() ? null : result;
    }

}
