// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.liquipedia.query.revision;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy.class)
public class LiquipediaMediaWikiRevisionQueryResult
{

    private boolean batchComplete;
    private RevisionQuery query;

    public LiquipediaMediaWikiRevisionQueryResult()
    {
    }

    public LiquipediaMediaWikiRevisionQueryResult
    (
        boolean batchComplete,
        RevisionQuery query
    )
    {
        this.batchComplete = batchComplete;
        this.query = query;
    }

    public boolean isBatchComplete()
    {
        return batchComplete;
    }

    public void setBatchComplete(boolean batchComplete)
    {
        this.batchComplete = batchComplete;
    }

    public RevisionQuery getQuery()
    {
        return query;
    }

    public void setQuery(RevisionQuery query)
    {
        this.query = query;
    }

}
