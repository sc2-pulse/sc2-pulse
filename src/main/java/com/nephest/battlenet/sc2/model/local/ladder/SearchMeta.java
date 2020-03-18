/*-
 * =========================LICENSE_START=========================
 * SC2 Ladder Generator
 * %%
 * Copyright (C) 2020 Oleksandr Masniuk
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END=========================
 */
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
