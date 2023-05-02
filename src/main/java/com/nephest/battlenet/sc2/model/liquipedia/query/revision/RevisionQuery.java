// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.liquipedia.query.revision;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy.class)
public class RevisionQuery
{

    @JsonAlias("normalized")
    private List<Normalization> normalizations;
    private List<RevisionPage> pages;

    public RevisionQuery()
    {
    }

    public RevisionQuery(List<Normalization> normalizations, List<RevisionPage> pages)
    {
        this.normalizations = normalizations;
        this.pages = pages;
    }

    public List<Normalization> getNormalizations()
    {
        return normalizations;
    }

    public void setNormalizations(List<Normalization> normalizations)
    {
        this.normalizations = normalizations;
    }

    public List<RevisionPage> getPages()
    {
        return pages;
    }

    public void setPages(List<RevisionPage> pages)
    {
        this.pages = pages;
    }

}
