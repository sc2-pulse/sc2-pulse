// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.util;

import org.springframework.stereotype.Service;

import java.util.stream.Collector;
import java.util.stream.Collectors;

@Service
public class FormatUtil
{

    public Collector<CharSequence, ?, String> joining(CharSequence delimiter)
    {
        return Collectors.joining(delimiter);
    }

}
