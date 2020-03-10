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
package com.nephest.battlenet.sc2.model;

public enum Region
implements Identifiable
{

    US(1, "https://us.api.blizzard.com/"),
    EU(2, "https://eu.api.blizzard.com/"),
    KR(3, "https://kr.api.blizzard.com/"),
    CN(5, "https://gateway.battlenet.com.cn/");

    private final int id;
    private final String baseUrl;

    private Region(int id, String baseUrl)
    {
        this.id = id;
        this.baseUrl = baseUrl;
    }

    public static Region from(int id)
    {
        for (Region region : Region.values())
        {
            if (region.getId() == id) return region;
        }

        throw new IllegalArgumentException("Invalid id");
    }

    @Override
    public int getId()
    {
        return id;
    }

    public String getBaseUrl()
    {
        return baseUrl;
    }

}
