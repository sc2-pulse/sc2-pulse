// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamState;

import java.util.Map;

public class LadderTeamStateCollectionToArraySerializer
extends SimpleCollectionToFieldArraySerializer<LadderTeamState>
{

    public LadderTeamStateCollectionToArraySerializer()
    {
        super(Map.ofEntries(
            Map.entry("teamId", (g, s)->g.writeNumber(s.getTeamState().getTeamId())),
            Map.entry("dateTime", (g, s)->g.writeString(s.getTeamState().getDateTime().toString())),
            Map.entry("games", (g, s)->g.writeNumber(s.getTeamState().getGames())),
            Map.entry("rating", (g, s)->g.writeNumber(s.getTeamState().getRating())),
            Map.entry("leagueType", (g, s)->g.writeNumber(s.getLeague().getType().getId())),
            Map.entry("queueType", (g, s)->g.writeNumber(s.getLeague().getQueueType().getId())),
            Map.entry("teamType", (g, s)->g.writeNumber(s.getLeague().getTeamType().getId())),
            Map.entry("tier", (g, s)->{
                if(s.getTier() == null) {g.writeNull();}
                else {g.writeNumber(s.getTier().getId());}
            }),
            Map.entry("globalRank", (g, s)->{
                if(s.getTeamState().getGlobalRank() == null) {g.writeNull();}
                else {g.writeNumber(s.getTeamState().getGlobalRank());}
            }),
            Map.entry("globalTeamCount", (g, s)->{
                if(s.getTeamState().getGlobalTeamCount() == null) {g.writeNull();}
                else {g.writeNumber(s.getTeamState().getGlobalTeamCount());}
            }),
            Map.entry("regionRank", (g, s)->{
                if(s.getTeamState().getRegionRank() == null) {g.writeNull();}
                else {g.writeNumber(s.getTeamState().getRegionRank());}
            }),
            Map.entry("regionTeamCount", (g, s)->{
                if(s.getTeamState().getRegionTeamCount() == null) {g.writeNull();}
                else {g.writeNumber(s.getTeamState().getRegionTeamCount());}
            }),
            Map.entry("race", (g, s)->{
                if(s.getRace() == null) {g.writeNull();}
                else {g.writeString(s.getRace().toString());}
            }),
            Map.entry("season", (g, s)->g.writeNumber(s.getSeason()))
        ));
    }

}
