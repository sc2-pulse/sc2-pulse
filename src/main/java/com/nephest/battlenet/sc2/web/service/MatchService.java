// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.blizzard.BlizzardMatch;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardMatches;
import com.nephest.battlenet.sc2.model.local.Match;
import com.nephest.battlenet.sc2.model.local.MatchParticipant;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.dao.MatchDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchParticipantDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.web.service.blizzard.BlizzardSC2API;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.logging.Logger;

@Service
public class MatchService
{

    private static final Logger LOG = Logger.getLogger(MatchService.class.getName());

    private final BlizzardSC2API api;
    private final MatchDAO matchDAO;
    private final MatchParticipantDAO matchParticipantDAO;
    private final PlayerCharacterDAO playerCharacterDAO;

    @Autowired
    private MatchService matchService;

    @Autowired
    public MatchService
    (
        BlizzardSC2API api,
        PlayerCharacterDAO playerCharacterDAO,
        MatchDAO matchDAO,
        MatchParticipantDAO matchParticipantDAO
    )
    {
        this.api = api;
        this.playerCharacterDAO = playerCharacterDAO;
        this.matchDAO = matchDAO;
        this.matchParticipantDAO = matchParticipantDAO;
    }

    protected void setNestedService(MatchService matchService)
    {
        this.matchService = matchService;
    }

    public void update()
    {
        api.getMatches(playerCharacterDAO.findProPlayerCharacters())
            .doOnNext((m)->matchService.saveMatches(m.getT1(), m.getT2()))
            .sequential().blockLast();
        matchDAO.removeExpired();
        LOG.info("Updated matches");
    }

    @Transactional
    public void saveMatches(BlizzardMatches matches, PlayerCharacter playerCharacter)
    {
        for(BlizzardMatch bMatch : matches.getMatches())
        {
            Match match = matchDAO.merge(Match.of(bMatch));
            matchParticipantDAO.merge(MatchParticipant.of(match, playerCharacter, bMatch));
        }
    }

}
