// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.revealed.RevealedProPlayer;

import javax.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;

public class SocialMediaLink
implements java.io.Serializable
{

    private static final long serialVersionUID = 1L;

    @NotNull
    private Long proPlayerId;

    @NotNull
    private SocialMedia type;

    @NotNull
    private String url;

    @NotNull
    private OffsetDateTime updated = OffsetDateTime.now();

    public SocialMediaLink
    (
        @NotNull Long proPlayerId, @NotNull SocialMedia type, @NotNull String url
    )
    {
        this.proPlayerId = proPlayerId;
        this.type = type;
        this.url = url;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SocialMediaLink that = (SocialMediaLink) o;
        return proPlayerId.equals(that.proPlayerId) && type == that.type;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(proPlayerId, type);
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s]",
            SocialMediaLink.class.getSimpleName(),
            getProPlayerId(), getType()
        );
    }

    public static SocialMediaLink[] of(ProPlayer proPlayer, RevealedProPlayer revealedProPlayer)
    {
        SocialMediaLink[] links = new SocialMediaLink[revealedProPlayer.getSocialMedia().size()];
        int ix = 0;
        for(Map.Entry<SocialMedia, String> entry : revealedProPlayer.getSocialMedia().entrySet())
        {
            links[ix] = new SocialMediaLink(proPlayer.getId(), entry.getKey(), entry.getValue());
            ix++;
        }
        return links;
    }

    public Long getProPlayerId()
    {
        return proPlayerId;
    }

    public void setProPlayerId(Long proPlayerId)
    {
        this.proPlayerId = proPlayerId;
    }

    public SocialMedia getType()
    {
        return type;
    }

    public void setType(SocialMedia type)
    {
        this.type = type;
    }

    public String getUrl()
    {
        return url;
    }

    public void setUrl(String url)
    {
        this.url = url;
    }

    public OffsetDateTime getUpdated()
    {
        return updated;
    }

    public void setUpdated(OffsetDateTime updated)
    {
        this.updated = updated;
    }

}
