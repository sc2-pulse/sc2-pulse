// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.aligulac;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDate;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AligulacProPlayer
{

    private static final AligulacProTeamRoot[] EMPTY_PRO_TEAM_ROOT_ARRAY = new AligulacProTeamRoot[0];

    private String name;

    private String romanizedName;

    private String tag;

    @JsonAlias("lp_name")
    private String liquipediaName;

    private LocalDate birthday;

    private String country;

    private Integer totalEarnings;

    private AligulacProTeamRoot[] currentTeams = EMPTY_PRO_TEAM_ROOT_ARRAY;

    public AligulacProPlayer(){}

    public AligulacProPlayer
    (
        String name,
        String romanizedName,
        String tag,
        String liquipediaName,
        LocalDate birthday,
        String country,
        Integer totalEarnings,
        AligulacProTeamRoot[] currentTeams
    )
    {
        this.name = name;
        this.romanizedName = romanizedName;
        this.tag = tag;
        this.liquipediaName = liquipediaName;
        this.birthday = birthday;
        this.country = country;
        this.totalEarnings = totalEarnings;
        this.currentTeams = currentTeams;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getRomanizedName()
    {
        return romanizedName;
    }

    public void setRomanizedName(String romanizedName)
    {
        this.romanizedName = romanizedName;
    }

    public String getCombinedName()
    {
        return romanizedName != null ? romanizedName : name;
    }

    public String getTag()
    {
        return tag;
    }

    public void setTag(String tag)
    {
        this.tag = tag;
    }

    public String getLiquipediaName()
    {
        return liquipediaName;
    }

    public void setLiquipediaName(String liquipediaName)
    {
        this.liquipediaName = liquipediaName;
    }

    public LocalDate getBirthday()
    {
        return birthday;
    }

    public void setBirthday(LocalDate birthday)
    {
        this.birthday = birthday;
    }

    public String getCountry()
    {
        return country;
    }

    public void setCountry(String country)
    {
        this.country = country;
    }

    public Integer getTotalEarnings()
    {
        return totalEarnings;
    }

    public void setTotalEarnings(Integer totalEarnings)
    {
        this.totalEarnings = totalEarnings;
    }

    public AligulacProTeamRoot[] getCurrentTeams()
    {
        return currentTeams;
    }

    public void setCurrentTeams(AligulacProTeamRoot[] currentTeams)
    {
        this.currentTeams = currentTeams;
    }

}
