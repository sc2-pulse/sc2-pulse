// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.*;
import com.nephest.battlenet.sc2.model.local.dao.*;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMatch;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMatchParticipant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

import static com.nephest.battlenet.sc2.model.BaseMatch.Decision.WIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class LadderMatchDAOIT
{

    public static final QueueType QUEUE_TYPE = QueueType.LOTV_4V4;
    public static final TeamType TEAM_TYPE = TeamType.ARRANGED;
    public static final BaseLeagueTier.LeagueTierType TIER_TYPE = BaseLeagueTier.LeagueTierType.FIRST;
    public static final Match MATCH1 = new Match(null, OffsetDateTime.now(), BaseMatch.MatchType._1V1, "map1");
    public static final Match MATCH2 = new Match(null, OffsetDateTime.now().minusDays(1), BaseMatch.MatchType._1V1,
        "map1");

    @Autowired
    private LadderMatchDAO ladderMatchDAO;

    @BeforeEach
    public void beforeAll
    (
        @Autowired DataSource dataSource,
        @Autowired MatchDAO matchDAO,
        @Autowired MatchParticipantDAO matchParticipantDAO,
        @Autowired SeasonGenerator seasonGenerator,
        @Autowired ProPlayerDAO proPlayerDAO,
        @Autowired ProTeamDAO proTeamDAO,
        @Autowired ProTeamMemberDAO proTeamMemberDAO,
        @Autowired ProPlayerAccountDAO proPlayerAccountDAO
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
            seasonGenerator.generateDefaultSeason
            (
                List.of(Region.values()),
                List.of(BaseLeague.LeagueType.values()),
                List.of(QUEUE_TYPE), TEAM_TYPE, TIER_TYPE, 3
            );
            matchDAO.merge(MATCH1);
            matchDAO.merge(MATCH2);
            matchParticipantDAO.merge(new MatchParticipant(MATCH1.getId(), 2L, WIN));
            matchParticipantDAO.merge(new MatchParticipant(MATCH1.getId(), 3L, BaseMatch.Decision.LOSS));
            matchParticipantDAO.merge(new MatchParticipant(MATCH2.getId(), 2L, BaseMatch.Decision.LOSS));
            matchParticipantDAO.merge(new MatchParticipant(MATCH2.getId(), 3L, WIN));

            ProPlayer proPlayer = new ProPlayer(null, new byte[]{0x1, 0x1}, "proNickname", "proName");
            proPlayerDAO.merge(proPlayer);
            ProTeam proTeam = proTeamDAO.merge(new ProTeam(null, 1L, "proTeamName", "proTeamShortName"));
            proTeamMemberDAO.merge(new ProTeamMember(proTeam.getId(), proPlayer.getId()));
            proPlayerAccountDAO.link(proPlayer.getId(), "battletag#1");
        }
    }

    @AfterEach
    public void afterAll
    (
        @Autowired DataSource dataSource
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
    }

    @Test
    public void testFindMatches()
    {
        //search for character#1, naming is zero based, id is 1 based
        List<LadderMatch> matches = ladderMatchDAO.findMatchesByCharacterId(2L);
        assertEquals(2, matches.size());

        //ORDER BY match.date DESC, match.type DESC, match.map DESC, player_character.id DESC
        //match 1
        LadderMatch match1 = matches.get(0);
        assertEquals(MATCH1, match1.getMatch());
        LadderMatchParticipant participant11 = match1.getParticipants().get(0);
        assertEquals(BaseMatch.Decision.LOSS, participant11.getParticipant().getDecision());
        assertEquals("character#2", participant11.getTeamMember().getCharacter().getName());
        assertEquals("battletag#2", participant11.getTeamMember().getAccount().getBattleTag());
        assertNull(participant11.getTeamMember().getProTeam());
        assertNull(participant11.getTeamMember().getProNickname());
        LadderMatchParticipant participant12 = match1.getParticipants().get(1);
        assertEquals(BaseMatch.Decision.WIN, participant12.getParticipant().getDecision());
        assertEquals("character#1", participant12.getTeamMember().getCharacter().getName());
        assertEquals("battletag#1", participant12.getTeamMember().getAccount().getBattleTag());
        assertEquals("proTeamShortName", participant12.getTeamMember().getProTeam());
        assertEquals("proNickname", participant12.getTeamMember().getProNickname());
        //match 2
        LadderMatch match2 = matches.get(1);
        assertEquals(MATCH2, match2.getMatch());
        LadderMatchParticipant participant21 = match2.getParticipants().get(0);
        assertEquals(BaseMatch.Decision.WIN, participant21.getParticipant().getDecision());
        assertEquals("character#2", participant21.getTeamMember().getCharacter().getName());
        LadderMatchParticipant participant22 = match2.getParticipants().get(1);
        assertEquals(BaseMatch.Decision.LOSS, participant22.getParticipant().getDecision());
        assertEquals("character#1", participant22.getTeamMember().getCharacter().getName());
    }

}
