// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.web.controller.group.TeamGroup;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/team/group")
public class TeamGroupController
{

    @GetMapping("/flat") @TeamGroup
    public ResponseEntity<Set<Long>> getCharacterIds(@TeamGroup Set<Long> teamIds)
    {
        return WebServiceUtil.notFoundIfEmpty(teamIds);
    }

}
