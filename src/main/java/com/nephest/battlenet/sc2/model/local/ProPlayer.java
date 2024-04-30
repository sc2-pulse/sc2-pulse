// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.aligulac.AligulacProPlayer;
import com.nephest.battlenet.sc2.model.revealed.RevealedProPlayer;
import com.nephest.battlenet.sc2.model.util.ModelUtil;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.model.validation.CountryAlpha2;
import com.nephest.battlenet.sc2.util.MiscUtil;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Past;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class ProPlayer
implements java.io.Serializable
{

    private static final long serialVersionUID = 1L;

    public static final int NAME_LENGTH_MAX = 100;
    public static final long EARNINGS_MIN = 1;
    public static final long EARNINGS_MAX = 1_000_000_000;

    private Long id;

    private Long aligulacId;

    @NotNull @Pattern(regexp = ModelUtil.VALIDATION_REGEXP_TRIMMED_NOT_BLANK_SINGLE_SPACE)
    @Size(max = NAME_LENGTH_MAX)
    private String nickname;

    @Pattern(regexp = ModelUtil.VALIDATION_REGEXP_TRIMMED_NOT_BLANK_SINGLE_SPACE)
    @Size(max = NAME_LENGTH_MAX)
    private String name;

    @CountryAlpha2
    private String country;

    @Past
    private LocalDate birthday;

    @Min(EARNINGS_MIN) @Max(EARNINGS_MAX)
    private Integer earnings;

    @NotNull
    private OffsetDateTime updated = SC2Pulse.offsetDateTime();

    private Integer version;

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
        OffsetDateTime updated,
        Integer version
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
        this.version = version;
    }

    public static LocalDate minBirthday()
    {
        return LocalDate.now().minusYears(110);
    }

    public static LocalDate maxBirthday()
    {
        return LocalDate.now().minusYears(6);
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
        proPlayer.setNickname(ModelUtil.trimSingleSpaceNotBlank(aligulacProPlayer.getTag()));
        proPlayer.setName(ModelUtil.trimSingleSpaceNotBlank(aligulacProPlayer.getCombinedName()));
        proPlayer.setBirthday(aligulacProPlayer.getBirthday());
        proPlayer.setCountry
        (
            aligulacProPlayer.getCountry() != null
                ? MiscUtil.convertReservedISO3166Alpha2Code(aligulacProPlayer.getCountry())
                : null
        );
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

    public Integer getVersion()
    {
        return version;
    }

    public void setVersion(Integer version)
    {
        this.version = version;
    }

}
