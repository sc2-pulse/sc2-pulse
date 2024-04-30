// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.PopulationState;
import com.nephest.battlenet.sc2.model.local.TeamState;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamState;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;

public class ArrayToLadderTeamStateArrayListDeserializer
extends SimpleArrayFieldToArrayListDeserializer<List<LadderTeamState>, LadderTeamState>
{

    public ArrayToLadderTeamStateArrayListDeserializer
    (
        @Qualifier("mvcConversionService") ConversionService conversionService
    )
    {
        super
        (
            LadderTeamState.class,
            List::of,
            ()->
            {
                LadderTeamState ladderTeamState = new LadderTeamState();
                ladderTeamState.setTeamState(new TeamState());
                ladderTeamState.setPopulationState(new PopulationState());
                ladderTeamState.setLeague(new League());
                return ladderTeamState;
            },
            Map.ofEntries
            (
                Map.entry("race", (node, state)->state
                    .setRace(conversionService.convert(node.asText(), Race.class))),
                Map.entry("season", (node, state)->state.setSeason(node.asInt())),
                Map.entry("tier", (node, state)->state.setTier(
                    conversionService.convert(node.asInt(), BaseLeagueTier.LeagueTierType.class))),
                Map.entry("teamId", (node, state)->state.getTeamState()
                    .setTeamId(node.asLong())),
                Map.entry("dateTime", (node, state)->state.getTeamState()
                    .setDateTime(SC2Pulse.offsetDateTime(OffsetDateTime.parse(node.asText())))),
                Map.entry("wins", (node, state)->state.getTeamState()
                    .setWins(node.isNull() ? null : node.asInt())),
                Map.entry("games", (node, state)->state.getTeamState()
                    .setGames(node.asInt())),
                Map.entry("rating", (node, state)->state.getTeamState()
                    .setRating(node.asInt())),
                Map.entry("globalRank", (node, state)->state.getTeamState()
                    .setGlobalRank(node.isNull() ? null : node.asInt())),
                Map.entry("regionRank", (node, state)->state.getTeamState()
                    .setRegionRank(node.isNull() ? null : node.asInt())),
                Map.entry("leagueRank", (node, state)->state.getTeamState()
                    .setLeagueRank(node.isNull() ? null : node.asInt())),
                Map.entry("teamType", (node, state)->state.getLeague()
                    .setTeamType(conversionService.convert(node.asInt(), TeamType.class))),
                Map.entry("leagueType", (node, state)->state.getLeague()
                    .setType(conversionService.convert(node.asInt(), BaseLeague.LeagueType.class))),
                Map.entry("queueType", (node, state)->state.getLeague()
                    .setQueueType(conversionService.convert(node.asInt(), QueueType.class))),
                Map.entry("globalTeamCount", (node, state)->state.getPopulationState()
                    .setGlobalTeamCount(node.isNull() ? null : node.asInt())),
                Map.entry("regionTeamCount", (node, state)->state.getPopulationState()
                    .setRegionTeamCount(node.isNull() ? null : node.asInt())),
                Map.entry("leagueTeamCount", (node, state)->state.getPopulationState()
                    .setLeagueTeamCount(node.isNull() ? null : node.asInt()))
            )
        );
    }

}
