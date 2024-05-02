// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.Race;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class MatchUp
{

    @NotNull
    private final List<Race> races, versusRaces;


    public MatchUp(List<Race> races, List<Race> versusRaces)
    {
        Objects.requireNonNull(races);
        Objects.requireNonNull(versusRaces);
        if(races.size() != versusRaces.size())
            throw new IllegalArgumentException("Match-up sizes are not equal");
        if(races.isEmpty())
            throw new IllegalArgumentException("Empty match-up");

        races = new ArrayList<>(races);
        versusRaces = new ArrayList<>(versusRaces);
        this.races = Collections.unmodifiableList(races);
        this.versusRaces = Collections.unmodifiableList(versusRaces);
    }

    public MatchUp(Race race, Race versusRace)
    {
        this(List.of(race), List.of(versusRace));
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof MatchUp matchUp)) {return false;}
        return Objects.equals(getRaces(), matchUp.getRaces())
            && Objects.equals(getVersusRaces(), matchUp.getVersusRaces());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getRaces(), getVersusRaces());
    }

    @Override
    public String toString()
    {
        return "MatchUp{"
            + "races=" + races.stream().map(Race::getName).collect(Collectors.joining())
            + ", versusRaces=" + versusRaces.stream().map(Race::getName)
                .collect(Collectors.joining())
            + '}';
    }

    public List<Race> getRaces()
    {
        return races;
    }

    public List<Race> getVersusRaces()
    {
        return versusRaces;
    }

    public int getTeamSize()
    {
        return getRaces().size();
    }

    public int getSize()
    {
        return getRaces().size() * 2;
    }

}
