/*-
 * =========================LICENSE_START=========================
 * SC2 Ladder Generator
 * %%
 * Copyright (C) 2020 Oleksandr Masniuk
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END=========================
 */
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
