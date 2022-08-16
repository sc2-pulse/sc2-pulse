// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.PopulationState;
import com.nephest.battlenet.sc2.model.local.TeamState;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamState;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = {AllTestConfig.class})
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class JacksonSerializationIT
{

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testLadderTeamStateArraySerialization()
    throws JsonProcessingException
    {
        LadderTeamStateContainer src = new LadderTeamStateContainer();
        OffsetDateTime odtBase = OffsetDateTime.now();
        List<LadderTeamState> srcList = List.of
        (
            create(1, odtBase),
            create(100, odtBase)
        );
        src.setHistory(srcList);

        String str = objectMapper.writeValueAsString(src);
        LadderTeamStateContainer dest = objectMapper.readValue(str, LadderTeamStateContainer.class);
        verifyBase(dest.getHistory().get(0), 1, odtBase);
        verifyBase(dest.getHistory().get(1), 100, odtBase);
    }

    public static LadderTeamState create(int base, OffsetDateTime odtBase)
    {
        return new LadderTeamState
        (
            new TeamState
            (
                base + 1L,
                odtBase.plusSeconds(base + 2),
                null,
                base + 4,
                base + 5,
                base + 6,
                base + 7,
                base + 8,
                base + 9,
                null,
                null
            ),
            Race.values()[Math.min(Race.values().length % base, Race.values().length - 1)],
            BaseLeagueTier.LeagueTierType.values()[Math.min(
                BaseLeagueTier.LeagueTierType.values().length % base,
                BaseLeagueTier.LeagueTierType.values().length - 1
            )],
            new League
            (
                null,
                null,
                BaseLeague.LeagueType.values()[Math.min(
                    BaseLeague.LeagueType.values().length % base,
                    BaseLeague.LeagueType.values().length - 1
                )],
                QueueType.values()[Math.min(
                    QueueType.values().length % base,
                    (QueueType.values().length - 1)
                )],
                TeamType.values()[Math.min(
                    TeamType.values().length % base,
                    TeamType.values().length - 1
                )]
            ),
            base + 10,
            new PopulationState
            (
                null,
                null,
                base + 11,
                base + 12,
                base + 13
            )
        );
    }

    public static void verifyBase(LadderTeamState state, int base, OffsetDateTime odtBase)
    {
        assertEquals(base + 1, state.getTeamState().getTeamId());
        assertEquals(odtBase.plusSeconds(base + 2), state.getTeamState().getDateTime());
        assertEquals(base + 4, state.getTeamState().getWins());
        assertEquals(base + 5, state.getTeamState().getGames());
        assertEquals(base + 6, state.getTeamState().getRating());
        assertEquals(base + 7, state.getTeamState().getGlobalRank());
        assertEquals(base + 8, state.getTeamState().getRegionRank());
        assertEquals(base + 9, state.getTeamState().getLeagueRank());
        assertEquals
        (
            Race.values()[Math.min(Race.values().length % base, Race.values().length - 1)],
            state.getRace()
        );
        assertEquals
        (
            BaseLeagueTier.LeagueTierType.values()[Math.min(
                BaseLeagueTier.LeagueTierType.values().length % base,
                BaseLeagueTier.LeagueTierType.values().length - 1
            )],
            state.getTier()
        );
        assertEquals
        (
            BaseLeague.LeagueType.values()[Math.min(
                BaseLeague.LeagueType.values().length % base,
                BaseLeague.LeagueType.values().length - 1
            )],
            state.getLeague().getType()
        );
        assertEquals
        (
            QueueType.values()[Math.min(
                QueueType.values().length % base,
                (QueueType.values().length - 1)
            )],
            state.getLeague().getQueueType()
        );
        assertEquals
        (
            TeamType.values()[Math.min(
                TeamType.values().length % base,
                TeamType.values().length - 1
            )],
            state.getLeague().getTeamType()
        );
        assertEquals(base + 10, state.getSeason());
        assertEquals(base + 11, state.getPopulationState().getGlobalTeamCount());
        assertEquals(base + 12, state.getPopulationState().getRegionTeamCount());
        assertEquals(base + 13, state.getPopulationState().getLeagueTeamCount());
    }

    private static class LadderTeamStateContainer
    {

        @JsonSerialize(using = LadderTeamStateCollectionToArraySerializer.class)
        @JsonDeserialize(using = ArrayToLadderTeamStateArrayListDeserializer.class)
        private List<LadderTeamState> history;

        public LadderTeamStateContainer(){}

        public List<LadderTeamState> getHistory()
        {
            return history;
        }

        public void setHistory(List<LadderTeamState> history)
        {
            this.history = history;
        }

    }

}
