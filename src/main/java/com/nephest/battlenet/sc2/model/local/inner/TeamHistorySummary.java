// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import jakarta.validation.constraints.NotNull;
import org.springframework.core.convert.ConversionService;

public record TeamHistorySummary<S extends TeamHistoryStaticData, U extends TeamHistorySummaryData>
(
    @NotNull S staticData,
    @NotNull U summary
)
{

    public static TeamHistorySummary<TypedTeamHistoryStaticData, TypedTeamHistorySummaryData> cast
    (
        TeamHistorySummary<RawTeamHistoryStaticData, RawTeamHistorySummaryData> raw
    )
    {
        return new TeamHistorySummary<>
        (
            TypedTeamHistoryStaticData.from(raw.staticData()),
            TypedTeamHistorySummaryData.from(raw.summary())
        );
    }

    public static TeamHistorySummary<ConvertedTeamHistoryStaticData, ConvertedTeamHistorySummaryData> convert
    (
        TeamHistorySummary<TypedTeamHistoryStaticData, TypedTeamHistorySummaryData> typed,
        ConversionService conversionService
    )
    {
        return new TeamHistorySummary<>
        (
            ConvertedTeamHistoryStaticData.from(typed.staticData(), conversionService),
            ConvertedTeamHistorySummaryData.from(typed.summary(), conversionService)
        );
    }

}
