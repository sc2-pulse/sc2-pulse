// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.util;

public final class HTMLTemplateUtil
{

    public static final String SECURE_URI_REGEX = "^(?!.*[%;/\\\\])(?!^(\\.)\\1*$).*$";
    public static final String SECURE_URI_REGEX_DESCRIPTION =
        "'%/\\;' characters and dot sequences are forbidden";

}
