// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.util;

import java.text.DecimalFormat;
import java.time.Duration;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.springframework.stereotype.Service;

@Service
public class FormatUtil
{

    public static DecimalFormat DEFAULT_DECIMAL_FORMAT = new DecimalFormat("#.##");

    public Collector<CharSequence, ?, String> joining(CharSequence delimiter)
    {
        return Collectors.joining(delimiter);
    }

    public String formatWords(Duration duration)
    {
        return DurationFormatUtils.formatDurationWords(duration.toMillis(), true, true);
    }

    public String formatFirstWord(Duration duration)
    {
        String words = formatWords(duration);
        int ix1 = words.indexOf(" ");
        int ix2 = words.indexOf(" ", ix1 + 1);
        if(ix2 != -1) words = words.substring(0, ix2);
        return words;
    }

}
