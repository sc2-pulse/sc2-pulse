// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import javax.validation.constraints.NotNull;

public class PagedSearchResult<T>
{

    @NotNull
    private final SearchMeta meta;

    @NotNull
    private final T result;

    public PagedSearchResult
    (
        Long totalCount, Long perPage, Long page,
        T result
    )
    {
        this.meta = new SearchMeta(totalCount, perPage, page);
        this.result = result;
    }

    public SearchMeta getMeta()
    {
        return meta;
    }

    public T getResult()
    {
        return result;
    }

    public PagedSearchResult<T> withPage(Long page)
    {
        getMeta().setPage(page);
        return this;
    }

}
