// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.model.local.ClanMemberEvent;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanMemberEventDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderClanMemberEvents;
import com.nephest.battlenet.sc2.model.navigation.Cursor;
import com.nephest.battlenet.sc2.model.validation.CursorNavigableResult;
import com.nephest.battlenet.sc2.model.validation.Version;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

@Repository
@Validated
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
        Set<Integer> clanIds,
        OffsetDateTime createdCursor,
        Long characterIdCursor,
        Integer limit
    )
    {
        List<ClanMemberEvent> events = clanMemberEventDAO.find
        (
            characterIds,
            clanIds,
            createdCursor,
            characterIdCursor,
            limit
        );
        if(events.isEmpty()) return Optional.empty();

        Set<Long> eventChars = events.stream()
            .map(ClanMemberEvent::getPlayerCharacterId)
            .collect(Collectors.toSet());
        Set<Integer> eventClans = events.stream()
            .map(ClanMemberEvent::getClanId)
            .collect(Collectors.toSet());
        return Optional.of(new LadderClanMemberEvents
        (
            ladderCharacterDAO.findDistinctCharactersByCharacterIds(eventChars),
            clanDAO.findByIds(eventClans),
            events
        ));
    }

    public CursorNavigableResult<LadderClanMemberEvents> find
    (
        Set<Long> characterIds,
        Set<Integer> clanIds,
        @Valid @Version(ClanMemberEventDAO.CURSOR_POSITION_VERSION) Cursor cursor,
        Integer limit
    )
    {
        CursorNavigableResult<List<ClanMemberEvent>> events = clanMemberEventDAO.find
        (
            characterIds,
            clanIds,
            cursor,
            limit
        );
        if(events.result().isEmpty()) return CursorNavigableResult.emptyValue();

        Set<Long> eventChars = events.result().stream()
            .map(ClanMemberEvent::getPlayerCharacterId)
            .collect(Collectors.toSet());
        Set<Integer> eventClans = events.result().stream()
            .map(ClanMemberEvent::getClanId)
            .collect(Collectors.toSet());
        return new CursorNavigableResult<>
        (
            new LadderClanMemberEvents
            (
                ladderCharacterDAO.findDistinctCharactersByCharacterIds(eventChars),
                clanDAO.findByIds(eventClans),
                events.result()
            ),
            events.navigation()
        );
    }

}
