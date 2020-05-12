// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

public enum Race
implements Identifiable
{

    TERRAN(1, "Terran"), PROTOSS(2, "Protoss"), ZERG(3, "Zerg"), RANDOM(4, "Random");

    private final int id;
    private final String name;

    private Race(int id, String name)
    {
        this.id = id;
        this.name = name;
    }

    public static final Race from(String name)
    {
        for(Race race : Race.values())
        {
            if(race.getName().equals(name)) return race;
        }
        throw new IllegalArgumentException("Invalid name");
    }

    public static final Race from(int id)
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
