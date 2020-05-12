// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import javax.validation.constraints.NotNull;

public class SearchMeta
{

    @NotNull
    private final Long totalCount;

    @NotNull
    private final Long perPage;

    @NotNull
    private final Long pageCount;

    @NotNull
    private Long page;

    public SearchMeta(Long totalCount, Long perPage, Long page)
    {
        this.totalCount = totalCount;
        this.perPage = perPage;
        this.page = page;
        this.pageCount = (long) Math.ceil(totalCount / (double) perPage);
    }

    public Long getTotalCount()
    {
        return totalCount;
    }

    public Long getPerPage()
    {
        return perPage;
    }

    public Long getPageCount()
    {
        return pageCount;
    }

    protected void setPage(Long page)
    {
        this.page = page;
    }

    public Long getPage()
    {
        return page;
    }

}
