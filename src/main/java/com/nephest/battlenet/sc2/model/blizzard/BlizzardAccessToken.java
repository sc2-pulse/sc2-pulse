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
package com.nephest.battlenet.sc2.model.blizzard;

import java.time.Instant;

import javax.validation.constraints.Future;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(SnakeCaseStrategy.class)
public class BlizzardAccessToken
{

    @NotNull
    private String accessToken;

    @NotNull
    private String tokenType;

    @NotNull
    private Integer expiresIn;

    @Future
    private Instant expires;

    public BlizzardAccessToken(){}

    public BlizzardAccessToken(String accessToken, String tokenType, Integer expiresIn)
    {
        if (expiresIn < 0) throw new IllegalArgumentException("Negative values are not allowed");
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        setExpiresIn(expiresIn);
    }

    public void setAccessToken(String accessToken)
    {
        this.accessToken = accessToken;
    }

    public String getAccessToken()
    {
        return accessToken;
    }

    public void setTokenType(String tokenType)
    {
        this.tokenType = tokenType;
    }

    public String getTokenTYpe()
    {
        return tokenType;
    }

    public void setExpiresIn(Integer expiresIn)
    {
        this.expiresIn = expiresIn;
        expires = Instant.now().plusSeconds(expiresIn);
    }

    public Integer getExpiresIn()
    {
        return expiresIn;
    }

    public boolean isExpired()
    {
        return !isValid();
    }

    public boolean isValid()
    {
        return expires.isAfter(Instant.now());
    }

}
