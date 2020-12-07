// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.blizzard.*;
import com.nephest.battlenet.sc2.model.local.Division;
import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.LeagueTier;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.validation.Validator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {AllTestConfig.class})
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class StatsServiceIT
{

    private StatsService statsService;
    private TeamDAO teamDAO;

    @BeforeEach
    public void beforeEach(@Autowired Validator validator)
    {
        teamDAO = mock(TeamDAO.class);
        statsService = new StatsService(null, null, null, null, null, teamDAO, null, null, null, null, null, null, null,
            validator);
        StatsService nss = mock(StatsService.class);
        statsService.setNestedService(nss);
    }

    @Test
    public void testInvalidTeam()
    {
        BlizzardTeam noBattletagTeam = new BlizzardTeam();
        BlizzardAccount account = new BlizzardAccount(1L, null); //null battletag
        BlizzardPlayerCharacter character = new BlizzardPlayerCharacter(1L, 1, "name#123");
        noBattletagTeam.setWins(1);
        noBattletagTeam.setMembers(new BlizzardTeamMember[]{new BlizzardTeamMember(
            character,
            new BlizzardTeamMemberRace[]{new BlizzardTeamMemberRace(Race.PROTOSS, 1)},
            account)
        });

        statsService.updateTeams(new BlizzardTeam[]{noBattletagTeam}, mock(Season.class),
            new League(1L, 1L, BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
            mock(LeagueTier.class), mock(Division.class));

        verify(teamDAO, never()).merge(any());
    }

}
