// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.aligulac;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AligulacProPlayerRoot
{

    private static final AligulacProPlayer[] EMPTY_ARRAY = new AligulacProPlayer[0];

    private AligulacProPlayer[] objects = EMPTY_ARRAY;

    public AligulacProPlayerRoot(){}

    public AligulacProPlayerRoot(AligulacProPlayer[] objects)
    {
        this.objects = objects;
    }

    public AligulacProPlayer[] getObjects()
    {
        return objects;
    }

    public void setObjects(AligulacProPlayer[] objects)
    {
        this.objects = objects;
    }

}
