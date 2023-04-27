// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.liquipedia.query.revision;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy.class)
public class Revision
{

    private Map<String, RevisionSlot> slots;

    public Revision()
    {
    }

    public Revision(Map<String, RevisionSlot> slots)
    {
        this.slots = slots;
    }

    public Map<String, RevisionSlot> getSlots()
    {
        return slots;
    }

    public void setSlots(Map<String, RevisionSlot> slots)
    {
        this.slots = slots;
    }

}
