// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import com.nephest.battlenet.sc2.web.controller.group.TeamGroup;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/team/group")
public class TeamGroupController
{

    @Autowired
    private LadderSearchDAO ladderSearchDAO;

    @GetMapping("/flat") @TeamGroup
    public ResponseEntity<Set<Long>> getCharacterIds(@TeamGroup Set<Long> teamIds)
    {
        return WebServiceUtil.notFoundIfEmpty(teamIds);
    }

    @GetMapping("/teams/full") @TeamGroup
    public ResponseEntity<?> getLadderTeams(@TeamGroup Set<Long> teamIds)
    {
        return WebServiceUtil.notFoundIfEmpty(ladderSearchDAO.findTeamsByIds(teamIds));
    }

}
