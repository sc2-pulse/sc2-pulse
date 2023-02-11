// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.config.security.AccountUser;
import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.ProPlayerAccount;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerAccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerDAO;
import com.nephest.battlenet.sc2.web.service.ProPlayerService;
import io.swagger.v3.oas.annotations.Hidden;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/api/reveal")
public class RevealController
{

    @Autowired
    private ProPlayerDAO proPlayerDAO;

    @Autowired
    private ProPlayerAccountDAO proPlayerAccountDAO;

    @Autowired
    private ProPlayerService proPlayerService;

    @GetMapping("/players")
    public List<ProPlayer> getPlayers()
    {
        return proPlayerDAO.findAll();
    }

    @PostMapping("/{accountId}/{proPlayerId}")
    public void reveal
    (
        @AuthenticationPrincipal AccountUser revealer,
        @PathVariable("accountId") long accountId,
        @PathVariable("proPlayerId") long proPlayerId
    )
    {
        ProPlayerAccount proPlayerAccount = new ProPlayerAccount
        (
            proPlayerId,
            accountId,
            revealer.getAccount().getId(),
            OffsetDateTime.now(),
            false
        );
        proPlayerAccountDAO.merge(proPlayerAccount);
    }

    @DeleteMapping("/{accountId}/{proPlayerId}")
    public void clear
    (
        @PathVariable("accountId") long accountId,
        @PathVariable("proPlayerId") long proPlayerId
    )
    {
        proPlayerAccountDAO.unlink(proPlayerId, accountId);
    }

    @PostMapping("/import")
    public ResponseEntity<ProPlayer> importProfile(@RequestParam("url") String url)
    {
        return proPlayerService.importProfile(url.trim())
            .map(ResponseEntity::ok)
            .orElseGet(()->ResponseEntity.notFound().build());
    }

}
