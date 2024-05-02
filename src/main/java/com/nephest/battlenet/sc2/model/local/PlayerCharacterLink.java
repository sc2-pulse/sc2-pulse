// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nephest.battlenet.sc2.model.SocialMedia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

public class PlayerCharacterLink
{

    public static final int URL_MAX_SIZE = 150;

    public static final Comparator<PlayerCharacterLink> NATURAL_ID_COMPARATOR = Comparator
        .comparing(PlayerCharacterLink::getPlayerCharacterId)
        .thenComparing(PlayerCharacterLink::getType);

    @NotNull
    private Long playerCharacterId;

    @NotNull
    private SocialMedia type;

    @NotBlank @Size(max = URL_MAX_SIZE)
    private String relativeUrl;

    public PlayerCharacterLink()
    {
    }

    public PlayerCharacterLink(Long playerCharacterId, SocialMedia type, String relativeUrl)
    {
        this.playerCharacterId = playerCharacterId;
        this.type = type;
        this.relativeUrl = relativeUrl;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof PlayerCharacterLink that)) {return false;}
        return playerCharacterId.equals(that.playerCharacterId) && type == that.type;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(playerCharacterId, type);
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s]",
            PlayerCharacterLink.class.getSimpleName(),
            getPlayerCharacterId(), getType()
        );
    }

    public Long getPlayerCharacterId()
    {
        return playerCharacterId;
    }

    public void setPlayerCharacterId(Long playerCharacterId)
    {
        this.playerCharacterId = playerCharacterId;
    }

    public static Optional<String> getRelativeUrl(String absoluteUrl)
    {
        if(absoluteUrl == null) return Optional.empty();

        SocialMedia type = SocialMedia.fromBaseUserUrlPrefix(absoluteUrl);
        if(type == SocialMedia.UNKNOWN) return Optional.of(absoluteUrl);
        if(absoluteUrl.length() < type.getBaseUserUrl().length() + 2) //slash + at least 1 char
            return Optional.empty();

        return Optional.of(absoluteUrl.substring(type.getBaseUserUrl().length() + 1));
    }

    public SocialMedia getType()
    {
        return type;
    }

    public void setType(SocialMedia type)
    {
        this.type = type;
    }

    @JsonIgnore
    public String getRelativeUrl()
    {
        return relativeUrl;
    }

    public void setRelativeUrl(String relativeUrl)
    {
        this.relativeUrl = relativeUrl;
    }

    public String getAbsoluteUrl()
    {
        return getType().getBaseUserUrl() != null
            ? getType().getBaseUserUrl() + "/" + getRelativeUrl()
            : getRelativeUrl();
    }

    public void setAbsoluteUrl(String url)
    {
        this.type = SocialMedia.fromBaseUserUrlPrefix(url);
        relativeUrl = getRelativeUrl(url).orElseThrow();
    }

}
