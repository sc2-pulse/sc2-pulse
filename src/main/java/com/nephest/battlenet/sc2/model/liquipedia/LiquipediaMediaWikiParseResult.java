// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.liquipedia;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy.class)
public class LiquipediaMediaWikiParseResult
{

    private LiquipediaMediaWikiParse parse;

    public LiquipediaMediaWikiParse getParse()
    {
        return parse;
    }

    public void setParse(LiquipediaMediaWikiParse parse)
    {
        this.parse = parse;
    }

}
