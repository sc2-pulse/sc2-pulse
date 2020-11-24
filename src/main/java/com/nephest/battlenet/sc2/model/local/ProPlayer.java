// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.aligulac.AligulacProPlayer;
import com.nephest.battlenet.sc2.model.revealed.RevealedProPlayer;
import org.springframework.security.crypto.codec.Hex;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;

public class ProPlayer
implements java.io.Serializable
{

    private static final long serialVersionUID = 1L;

    private Long id;

    /*
        sc2revealed data treats multi-region players as distinct entities. Using aligulac id as revealed id
        to merge multi-region players in single entity. This still allows multi-region players to exist if
        they do not have an aligulac link.
     */
    @NotNull
    private byte[] revealedId;

    private Long aligulacId;

    @NotNull
    private String nickname;

    @NotNull
    private String name;

    private String country;

    private LocalDate birthday;

    private Integer earnings;

    @NotNull
    private OffsetDateTime updated = OffsetDateTime.now();

    public ProPlayer
    (
        Long id, @NotNull byte[] revealedId, @NotNull String nickname, @NotNull String name
    )
    {
        this.id = id;
        this.revealedId = revealedId;
        this.nickname = nickname;
        this.name = name;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProPlayer proPlayer = (ProPlayer) o;
        return Arrays.equals(revealedId, proPlayer.revealedId);
    }

    @Override
    public int hashCode()
    {
        return Arrays.hashCode(revealedId);
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s]",
            getClass().getSimpleName(),
            Arrays.toString(revealedId)
        );
    }

    public static ProPlayer of(RevealedProPlayer revealedProPlayer)
    {
        if(revealedProPlayer.getFirstName() == null || revealedProPlayer.getFirstName().isEmpty())
            throw new IllegalArgumentException("Only identified pro players are allowed");

        ProPlayer proPlayer = new ProPlayer
        (
            null, Hex.decode(revealedProPlayer.get_id()), revealedProPlayer.getPlayer(),
            (revealedProPlayer.getFirstName()
                + (revealedProPlayer.getLastName() != null ? " " + revealedProPlayer.getLastName() : ""))
        );
        Long aligulacId = revealedProPlayer.getSocialMedia().entrySet().stream()
            .filter(e->e.getKey() == SocialMedia.ALIGULAC)
            .map(e->SocialMedia.getAligulacIdFromUrl(e.getValue()))
            .findFirst().orElse(null);
        proPlayer.setAligulacId(aligulacId);
        proPlayer.setCountry(revealedProPlayer.getNationality().get("iso2"));
        return proPlayer;
    }

    public static ProPlayer update(ProPlayer proPlayer, AligulacProPlayer aligulacProPlayer)
    {
        proPlayer.setEarnings(aligulacProPlayer.getTotalEarnings());
        if(aligulacProPlayer.getTag() != null) proPlayer.setNickname(aligulacProPlayer.getTag());
        if(aligulacProPlayer.getCombinedName() != null) proPlayer.setName(aligulacProPlayer.getCombinedName());
        proPlayer.setBirthday(aligulacProPlayer.getBirthday());
        if(aligulacProPlayer.getCountry() != null) proPlayer.setCountry(aligulacProPlayer.getCountry());
        return proPlayer;
    }

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public byte[] getRevealedId()
    {
        return revealedId;
    }

    public void setRevealedId(byte[] revealedId)
    {
        this.revealedId = revealedId;
    }

    public Long getAligulacId()
    {
        return aligulacId;
    }

    public void setAligulacId(Long aligulacId)
    {
        this.aligulacId = aligulacId;
    }

    public String getNickname()
    {
        return nickname;
    }

    public void setNickname(String nickname)
    {
        this.nickname = nickname;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getCountry()
    {
        return country;
    }

    public void setCountry(String country)
    {
        this.country = country;
    }

    public LocalDate getBirthday()
    {
        return birthday;
    }

    public void setBirthday(LocalDate birthday)
    {
        this.birthday = birthday;
    }

    public Integer getEarnings()
    {
        return earnings;
    }

    public void setEarnings(Integer earnings)
    {
        this.earnings = earnings;
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
