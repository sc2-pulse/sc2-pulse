// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

/**
 * This is a single field enum for manual API projections. It should not be modified.
 * It is used for convenience since enum provides a good UI/UX in open API out of the box, and
 * enums will be used if we need more fields for some specific entities, so it makes sense to use
 * enum even for a single field right from the start.
 */
public enum IdField
{

    ID;

    public static final String NAME = "ID";

}
