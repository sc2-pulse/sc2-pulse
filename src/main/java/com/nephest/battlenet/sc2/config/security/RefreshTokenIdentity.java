// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import java.util.Objects;
import org.springframework.security.oauth2.client.OAuth2AuthorizationContext;

public class RefreshTokenIdentity
{

    private final String registrationId, principalName, refreshToken;

    public RefreshTokenIdentity(String registrationId, String principalName, String refreshToken)
    {
        this.registrationId = registrationId;
        this.principalName = principalName;
        this.refreshToken = refreshToken;
    }

    public static RefreshTokenIdentity from(OAuth2AuthorizationContext context)
    {
        if(context.getAuthorizedClient() == null
            || context.getAuthorizedClient().getRefreshToken() == null) return null;

        return new RefreshTokenIdentity
        (
            context.getClientRegistration().getRegistrationId(),
            context.getPrincipal().getName(),
            context.getAuthorizedClient().getRefreshToken().getTokenValue()
        );
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof RefreshTokenIdentity)) {return false;}
        RefreshTokenIdentity that = (RefreshTokenIdentity) o;
        return getRegistrationId().equals(that.getRegistrationId())
            && getPrincipalName().equals(that.getPrincipalName())
            && getRefreshToken().equals(that.getRefreshToken());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getRegistrationId(), getPrincipalName(), getRefreshToken());
    }

    public String getRegistrationId()
    {
        return registrationId;
    }

    public String getPrincipalName()
    {
        return principalName;
    }

    public String getRefreshToken()
    {
        return refreshToken;
    }

}
