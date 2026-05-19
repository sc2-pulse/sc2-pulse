// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.util;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.ClanMember;
import com.nephest.battlenet.sc2.model.local.ClanMemberEvent;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanMemberDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanMemberEventDAO;
import com.nephest.battlenet.sc2.model.local.dao.LeagueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchParticipantDAO;
import com.nephest.battlenet.sc2.model.local.dao.PopulationStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.QueueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistoryDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderMatchDAO;
import com.nephest.battlenet.sc2.web.service.StatsService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TestDbInitializer
{

    private final SeasonGenerator seasonGenerator;
    private final TeamDAO teamDAO;
    private final ClanDAO clanDAO;
    private final ClanMemberDAO clanMemberDAO;
    private final ClanMemberEventDAO clanMemberEventDAO;
    private final LeagueStatsDAO leagueStatsDAO;
    private final QueueStatsDAO queueStatsDAO;
    private final SeasonStateDAO seasonStateDAO;
    private final LadderMatchDAO ladderMatchDAO;
    private final MatchParticipantDAO matchParticipantDAO;
    private final PopulationStateDAO populationStateDAO;
    private final TeamHistoryDAO teamHistoryDAO;
    private final JdbcTemplate template;

    @Autowired
    public TestDbInitializer
    (
        SeasonGenerator seasonGenerator,
        TeamDAO teamDAO,
        ClanDAO clanDAO,
        ClanMemberDAO clanMemberDAO,
        ClanMemberEventDAO clanMemberEventDAO,
        LeagueStatsDAO leagueStatsDAO,
        QueueStatsDAO queueStatsDAO,
        SeasonStateDAO seasonStateDAO,
        LadderMatchDAO ladderMatchDAO,
        MatchParticipantDAO matchParticipantDAO,
        PopulationStateDAO populationStateDAO,
        TeamHistoryDAO teamHistoryDAO,
        JdbcTemplate template
    )
    {
        this.seasonGenerator = seasonGenerator;
        this.teamDAO = teamDAO;
        this.clanDAO = clanDAO;
        this.clanMemberDAO = clanMemberDAO;
        this.clanMemberEventDAO = clanMemberEventDAO;
        this.leagueStatsDAO = leagueStatsDAO;
        this.queueStatsDAO = queueStatsDAO;
        this.seasonStateDAO = seasonStateDAO;
        this.ladderMatchDAO = ladderMatchDAO;
        this.matchParticipantDAO = matchParticipantDAO;
        this.populationStateDAO = populationStateDAO;
        this.teamHistoryDAO = teamHistoryDAO;
        this.template = template;
    }

    public void setupData()
    {
        seasonGenerator.generateDefaultSeason
        (
            List.of(Region.values()),
            List.of(BaseLeague.LeagueType.values()),
            List.copyOf(QueueType.getTypes(StatsService.VERSION)),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            10
        );
        template.update("UPDATE team SET last_played = NOW()");
        Clan clan1 = clanDAO.merge(Set.of(new Clan(null, "clanTag1", Region.EU, "clanName1")))
            .iterator().next();
        setupClanData
        (
            template
                .queryForList("SELECT id FROM player_character WHERE id <= 140", Long.class),
            clan1
        );
        Clan clan2 = clanDAO.merge(Set.of(new Clan(null, "clanTag2", Region.EU, "clanName2")))
            .iterator().next();
        setupClanData
        (
            template.queryForList
            (
                "SELECT id FROM player_character WHERE id BETWEEN 141 AND 280",
                Long.class
            ),
            clan2
        );
        OffsetDateTime startDateTime = SC2Pulse.offsetDateTime();
        int matchCount = (int) Math.round(ladderMatchDAO.getResultsPerPage() * 2.5);
        seasonGenerator.createMatches
        (
            BaseMatch.MatchType._1V1,
            1, 280, new long[]{1}, new long[]{280},
            startDateTime, Region.EU, 1, 28,
            matchCount
        );
        matchParticipantDAO.identify(SeasonGenerator.DEFAULT_SEASON_ID, startDateTime.minusYears(1));
        matchParticipantDAO.calculateRatingDifference(startDateTime.minusYears(1));
        clanDAO.updateStats(List.of(clan1.getId(), clan2.getId()));
        leagueStatsDAO.calculateForSeason(SeasonGenerator.DEFAULT_SEASON_ID);
        populationStateDAO.takeSnapshot(List.of(SeasonGenerator.DEFAULT_SEASON_ID));
        teamDAO.updateRanks(SeasonGenerator.DEFAULT_SEASON_ID);
        queueStatsDAO.calculateForSeason(SeasonGenerator.DEFAULT_SEASON_ID);
        seasonStateDAO.merge(SeasonGenerator.DEFAULT_SEASON_START.plusMinutes(1),
            SeasonGenerator.DEFAULT_SEASON_ID);
        teamHistoryDAO.trySync();
    }

    private void setupClanData(List<Long> charIds, Clan clan)
    {
        Set<ClanMember> cm = charIds
            .stream()
            .map(id->new ClanMember(id, clan.getId()))
            .collect(Collectors.toSet());
        clanMemberDAO.merge(cm);
        Set<ClanMemberEvent> cme = charIds.stream()
            .map(id->new ClanMemberEvent(
                id, clan.getId(), ClanMemberEvent.EventType.JOIN, SC2Pulse.offsetDateTime()))
            .collect(Collectors.toSet());
        clanMemberEventDAO.merge(cme);
    }

}
