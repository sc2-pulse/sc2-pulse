// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import javax.validation.constraints.Future;
import javax.validation.constraints.NotNull;
import java.time.Instant;

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
