// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.aligulac.AligulacProPlayer;
import com.nephest.battlenet.sc2.model.revealed.RevealedProPlayer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import javax.validation.constraints.NotNull;

public class ProPlayer
implements java.io.Serializable
{

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long aligulacId;

    @NotNull
    private String nickname;

    private String name;

    private String country;

    private LocalDate birthday;

    private Integer earnings;

    @NotNull
    private OffsetDateTime updated = OffsetDateTime.now();

    public ProPlayer()
    {
    }

    public ProPlayer(Long id, Long aligulacId, String nickname, String name)
    {
        this.id = id;
        this.aligulacId = aligulacId;
        this.nickname = nickname;
        this.name = name;
    }

    public ProPlayer
    (
        Long id,
        Long aligulacId,
        String nickname,
        String name,
        String country,
        LocalDate birthday,
        Integer earnings,
        OffsetDateTime updated
    )
    {
        this.id = id;
        this.aligulacId = aligulacId;
        this.nickname = nickname;
        this.name = name;
        this.country = country;
        this.birthday = birthday;
        this.earnings = earnings;
        this.updated = updated;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProPlayer proPlayer = (ProPlayer) o;
        return Objects.equals(aligulacId, proPlayer.aligulacId);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(aligulacId);
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s]",
            ProPlayer.class.getSimpleName(),
            aligulacId
        );
    }

    public static ProPlayer of(RevealedProPlayer revealedProPlayer)
    {
        if(revealedProPlayer.getFirstName() == null || revealedProPlayer.getFirstName().isEmpty())
            throw new IllegalArgumentException("Only identified pro players are allowed");

        Long aligulacId = revealedProPlayer.getSocialMedia().entrySet().stream()
            .filter(e->e.getKey() == SocialMedia.ALIGULAC)
            .map(e->SocialMedia.getAligulacIdFromUrl(e.getValue()))
            .findFirst()
            .orElseThrow();
        ProPlayer proPlayer = new ProPlayer
        (
            null,
            aligulacId,
            revealedProPlayer.getPlayer(),
            (revealedProPlayer.getFirstName()
                + (revealedProPlayer.getLastName() != null ? " " + revealedProPlayer.getLastName() : ""))
        );
        proPlayer.setCountry(revealedProPlayer.getNationality().get("iso2"));
        return proPlayer;
    }

    public static ProPlayer update(ProPlayer proPlayer, AligulacProPlayer aligulacProPlayer)
    {
        proPlayer.setEarnings(aligulacProPlayer.getTotalEarnings());
        proPlayer.setNickname(aligulacProPlayer.getTag());
        proPlayer.setName(aligulacProPlayer.getCombinedName());
        proPlayer.setBirthday(aligulacProPlayer.getBirthday());
        proPlayer.setCountry(aligulacProPlayer.getCountry());
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
