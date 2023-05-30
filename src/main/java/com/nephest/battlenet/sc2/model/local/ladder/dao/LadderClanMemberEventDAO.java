// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.model.local.ClanMemberEvent;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanMemberEventDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderClanMemberEvents;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class LadderClanMemberEventDAO
{

    private final LadderCharacterDAO ladderCharacterDAO;
    private final ClanDAO clanDAO;
    private final ClanMemberEventDAO clanMemberEventDAO;

    @Autowired
    public LadderClanMemberEventDAO
    (
        LadderCharacterDAO ladderCharacterDAO,
        ClanDAO clanDAO,
        ClanMemberEventDAO clanMemberEventDAO
    )
    {
        this.ladderCharacterDAO = ladderCharacterDAO;
        this.clanDAO = clanDAO;
        this.clanMemberEventDAO = clanMemberEventDAO;
    }

    public Optional<LadderClanMemberEvents> find
    (
        Set<Long> characterIds,
        OffsetDateTime createdCursor,
        Long characterIdCursor,
        Integer limit
    )
    {
        List<ClanMemberEvent> events = clanMemberEventDAO.find
        (
            characterIds,
            createdCursor,
            characterIdCursor,
            limit
        );
        if(events.isEmpty()) return Optional.empty();

        Long[] eventChars = events.stream()
            .map(ClanMemberEvent::getPlayerCharacterId)
            .distinct()
            .toArray(Long[]::new);
        Integer[] eventClans = events.stream()
            .map(ClanMemberEvent::getClanId)
            .distinct()
            .toArray(Integer[]::new);
        return Optional.of(new LadderClanMemberEvents
        (
            ladderCharacterDAO.findDistinctCharactersByCharacterIds(eventChars),
            clanDAO.findByIds(eventClans),
            events
        ));
    }

}
