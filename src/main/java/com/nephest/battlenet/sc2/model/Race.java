// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Optional;

public enum Race
implements Identifiable
{

    TERRAN(1, "Terran"), PROTOSS(2, "Protoss"), ZERG(3, "Zerg"), RANDOM(4, "Random");

    public static final Race[] EMPTY_RACE_ARRAY = new Race[0];

    private final int id;
    private final String name;

    Race(int id, String name)
    {
        this.id = id;
        this.name = name;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Race from(String name)
    {
        return optionalFrom(name).orElseThrow();
    }

    public static Optional<Race> optionalFrom(String name)
    {
        String lowerName = name.toLowerCase();
        for(Race race : Race.values())
            if(race.getName().equalsIgnoreCase(lowerName)) return Optional.of(race);
        return Optional.empty();
    }

    public static Race from(int id)
    {
        for(Race race : Race.values())
        {
            if(race.getId() == id) return race;
        }
        throw new IllegalArgumentException("Invalid id");
    }

    @Override
    public int getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }

}
