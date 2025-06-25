// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import jakarta.validation.constraints.NotNull;
import org.springframework.core.convert.ConversionService;

public record TeamHistory<S extends TeamHistoryStaticData, H extends TeamHistoryHistoryData>
(
    @NotNull S staticData,
    @NotNull H history
)
{

    public static TeamHistory<TypedTeamHistoryStaticData, TypedTeamHistoryHistoryData> cast
    (
        TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData> raw
    )
    {
        return new TeamHistory<>
        (
            TypedTeamHistoryStaticData.from(raw.staticData()),
            TypedTeamHistoryHistoryData.from(raw.history())
        );
    }

    public static TeamHistory<ConvertedTeamHistoryStaticData, ConvertedTeamHistoryHistoryData> convert
    (
        TeamHistory<TypedTeamHistoryStaticData, TypedTeamHistoryHistoryData> typed,
        ConversionService conversionService
    )
    {
        return new TeamHistory<>
        (
            ConvertedTeamHistoryStaticData.from(typed.staticData(), conversionService),
            ConvertedTeamHistoryHistoryData.from(typed.history(), conversionService)
        );
    }

}
