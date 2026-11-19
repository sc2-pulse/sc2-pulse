// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import jakarta.validation.constraints.NotNull;
import java.util.Objects;

/**
 * Canonical season, identified by (year, number). Region seasons reference
 * it, and Pulse-internal season ids, e.g. team.season, are pulse season ids.
 */
public class PulseSeason
implements java.io.Serializable
{

    private static final long serialVersionUID = 1L;

    @NotNull
    private Integer id;

    @NotNull
    private Integer year;

    @NotNull
    private Integer number;

    public PulseSeason(){}

    public PulseSeason(Integer id, Integer year, Integer number)
    {
        this.id = id;
        this.year = year;
        this.number = number;
    }

    @Override
    public boolean equals(Object o)
    {
        if(this == o) return true;
        if(!(o instanceof PulseSeason that)) return false;
        return Objects.equals(getYear(), that.getYear())
            && Objects.equals(getNumber(), that.getNumber());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getYear(), getNumber());
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s]",
            PulseSeason.class.getSimpleName(),
            getYear(), getNumber()
        );
    }

    public Integer getId()
    {
        return id;
    }

    public void setId(Integer id)
    {
        this.id = id;
    }

    public Integer getYear()
    {
        return year;
    }

    public void setYear(Integer year)
    {
        this.year = year;
    }

    public Integer getNumber()
    {
        return number;
    }

    public void setNumber(Integer number)
    {
        this.number = number;
    }

}
