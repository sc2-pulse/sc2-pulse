// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.LeagueTier;
import com.nephest.battlenet.sc2.model.local.PopulationState;
import com.nephest.battlenet.sc2.model.local.Team;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyId;
import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LadderTeam
extends Team
implements java.io.Serializable
{

    private static final long serialVersionUID = 5L;

    private List<LadderTeamMember> members;

    @JsonUnwrapped
    private PopulationState populationState;

    public LadderTeam(){}

    public LadderTeam
    (
        Long id,
        Integer season,
        Region region,
        BaseLeague league,
        LeagueTier.LeagueTierType leagueTierType,
        TeamLegacyId legacyId,
        Integer divisionId,
        Long rating, Integer wins, Integer losses, Integer ties, Integer points,
        OffsetDateTime lastPlayed, OffsetDateTime joined, OffsetDateTime primaryDataUpdated,
        List<LadderTeamMember> members,
        PopulationState populationState
    )
    {
        super
        (
            id,
            season, region, league, leagueTierType,
            legacyId, divisionId,
            rating, wins, losses, ties, points,
            lastPlayed, joined, primaryDataUpdated
        );
        this.members = members;
        this.populationState = populationState;
    }

    public static LadderTeam joined
    (
        Long id,
        Integer season,
        Region region,
        BaseLeague league,
        LeagueTier.LeagueTierType leagueTierType,
        TeamLegacyId legacyId,
        Integer divisionId,
        Long rating, Integer wins, Integer losses, Integer ties, Integer points,
        OffsetDateTime lastPlayed,
        List<LadderTeamMember> members,
        PopulationState populationState
    )
    {
        return new LadderTeam
        (
            id,
            season,
            region,
            league,
            leagueTierType,
            legacyId,
            divisionId,
            rating, wins, losses, ties, points,
            lastPlayed, lastPlayed, lastPlayed,
            members,
            populationState
        );
    }

    public PopulationState getPopulationState()
    {
        return populationState;
    }

    public void setPopulationState(PopulationState populationState)
    {
        this.populationState = populationState;
    }

    public List<LadderTeamMember> getMembers()
    {
        return members;
    }

    public void setMembers(List<LadderTeamMember> members)
    {
        this.members = members;
    }

}

