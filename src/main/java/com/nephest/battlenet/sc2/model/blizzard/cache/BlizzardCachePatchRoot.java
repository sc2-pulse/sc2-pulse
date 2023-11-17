// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard.cache;

public class BlizzardCachePatchRoot
{

    private BlizzardCachePatch[] patchNotes;

    public BlizzardCachePatchRoot()
    {
    }

    public BlizzardCachePatch[] getPatchNotes()
    {
        return patchNotes;
    }

    public void setPatchNotes(BlizzardCachePatch[] patchNotes)
    {
        this.patchNotes = patchNotes;
    }

}
