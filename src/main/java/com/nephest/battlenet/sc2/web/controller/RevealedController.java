// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderProPlayer;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderProPlayerDAO;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/revealed")
public class RevealedController
{

    @Autowired
    private ProPlayerDAO proPlayerDAO;

    @Autowired
    private LadderProPlayerDAO ladderProPlayerDAO;

    @GetMapping("/players")
    public List<ProPlayer> getPlayers()
    {
        return proPlayerDAO.findAll();
    }

    @GetMapping("/player/{ids}/full")
    public ResponseEntity<List<LadderProPlayer>> getLadderProPlayers(@PathVariable("ids") Set<Long> ids)
    {
        return WebServiceUtil.notFoundIfEmpty(ladderProPlayerDAO.findByIds(ids));
    }

}
