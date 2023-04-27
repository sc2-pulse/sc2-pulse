// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.liquipedia.query.revision;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy.class)
public class RevisionPage
{

    private Long pageId, ns;
    private String title;
    private List<Revision> revisions;

    public RevisionPage()
    {
    }

    public RevisionPage(Long pageId, Long ns, String title, List<Revision> revisions)
    {
        this.pageId = pageId;
        this.ns = ns;
        this.title = title;
        this.revisions = revisions;
    }

    public Long getPageId()
    {
        return pageId;
    }

    public void setPageId(Long pageId)
    {
        this.pageId = pageId;
    }

    public Long getNs()
    {
        return ns;
    }

    public void setNs(Long ns)
    {
        this.ns = ns;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public List<Revision> getRevisions()
    {
        return revisions;
    }

    public void setRevisions(List<Revision> revisions)
    {
        this.revisions = revisions;
    }

}
