// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.config.security.AccountUser;
import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerAccountDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderProPlayer;
import com.nephest.battlenet.sc2.web.service.ProPlayerForm;
import com.nephest.battlenet.sc2.web.service.ProPlayerService;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import io.swagger.v3.oas.annotations.Hidden;
import java.time.OffsetDateTime;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/api/reveal")
public class RevealController
{

    @Autowired
    private ProPlayerAccountDAO proPlayerAccountDAO;

    @Autowired
    private ProPlayerService proPlayerService;

    @PostMapping("/{accountId}/{proPlayerId}")
    public void reveal
    (
        @AuthenticationPrincipal AccountUser revealer,
        @PathVariable("accountId") long accountId,
        @PathVariable("proPlayerId") long proPlayerId
    )
    {
        proPlayerService.link(accountId, proPlayerId);
    }

    @DeleteMapping("/{accountId}/{proPlayerId}")
    public void clear
    (
        @PathVariable("accountId") long accountId,
        @PathVariable("proPlayerId") long proPlayerId
    )
    {
        proPlayerService.unlink(accountId, proPlayerId);
    }

    @PostMapping("/import")
    public ResponseEntity<ProPlayer> importProfile(@RequestParam("url") String url)
    {
        return proPlayerService.importProfile(url.trim())
            .map(ResponseEntity::ok)
            .orElseGet(()->ResponseEntity.notFound().build());
    }

    @PostMapping("/player/edit")
    public ResponseEntity<LadderProPlayer> edit(@Valid @RequestBody ProPlayerForm form)
    {
        form.getProPlayer().setUpdated(OffsetDateTime.now());
        return ResponseEntity.ok(WebServiceUtil.wrapSecurity(proPlayerService.edit(form)).block());
    }

}
