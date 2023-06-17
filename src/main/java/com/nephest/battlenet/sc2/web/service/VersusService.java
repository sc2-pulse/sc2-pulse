// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMatch;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMatchParticipant;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.model.local.ladder.PagedSearchResult;
import com.nephest.battlenet.sc2.model.local.ladder.Versus;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderMatchDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VersusService
{

    private final LadderMatchDAO ladderMatchDAO;
    private final LadderSearchDAO ladderSearchDAO;
    private final ClanDAO clanDAO;

    @Autowired
    public VersusService(LadderMatchDAO ladderMatchDAO, LadderSearchDAO ladderSearchDAO, ClanDAO clanDAO)
    {
        this.ladderMatchDAO = ladderMatchDAO;
        this.ladderSearchDAO = ladderSearchDAO;
        this.clanDAO = clanDAO;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
    public Versus getVersus
    (
        Integer[] clans1,
        Set<TeamLegacyUid> teams1,
        Integer[] clans2,
        Set<TeamLegacyUid> teams2,
        OffsetDateTime dateAnchor,
        BaseMatch.MatchType typeAnchor,
        int mapAnchor,
        Region regionAnchor,
        int page,
        int pageDiff,
        BaseMatch.MatchType... types
    )
    {
        PagedSearchResult<List<LadderMatch>> matches = ladderMatchDAO.findVersusMatches
        (
            clans1, teams1,
            clans2, teams2,
            dateAnchor, typeAnchor, mapAnchor, regionAnchor,
            page, pageDiff,
            types
        );
        return new Versus
        (
            findTeams(teams1, matches.getResult()),
            findClans(clans1, matches.getResult()),
            findTeams(teams2, matches.getResult()),
            findClans(clans2, matches.getResult()),
            ladderMatchDAO.getVersusSummary(clans1, teams1, clans2, teams2, types),
            matches
        );
    }

    /*
        There is a high probability that the target teams and clans will be included in the first batch of matches.
        Try to avoid DB usage if possible, extract target entities from matches, fallback to DB search otherwise.
     */

    private List<LadderTeam> findTeams(Set<TeamLegacyUid> teamIds, List<LadderMatch> matches)
    {
        if(teamIds.isEmpty()) return List.of();

        List<LadderTeam> teams = matches.stream()
            .flatMap(m->m.getParticipants().stream())
            .map(LadderMatchParticipant::getTeam)
            .distinct()
            .filter(t->t != null && teamIds.contains(TeamLegacyUid.of(t)))
            .collect(Collectors.toList());
        return teams.size() == teamIds.size() ? teams : ladderSearchDAO.findLegacyTeams(teamIds, false);
    }

    private List<Clan> findClans(Integer[] clanIds, List<LadderMatch> matches)
    {
        if(clanIds.length == 0) return List.of();

        Set<Integer> idSet = Set.of(clanIds);
        List<Clan> clans = matches.stream()
            .flatMap(m->m.getParticipants().stream())
            .flatMap(p->p.getTeam() != null ? p.getTeam().getMembers().stream() : Stream.empty())
            .map(LadderTeamMember::getClan)
            .distinct()
            .filter(c->c != null && idSet.contains(c.getId()))
            .collect(Collectors.toList());
        return clans.size() == clanIds.length ? clans : clanDAO.findByIds(clanIds);
    }

}
