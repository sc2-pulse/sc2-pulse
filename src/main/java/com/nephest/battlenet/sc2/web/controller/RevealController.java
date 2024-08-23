// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.config.security.AccountUser;
import com.nephest.battlenet.sc2.model.local.AuditLogEntry;
import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.dao.AuditLogEntryDAO;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerAccountDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderProPlayer;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.web.service.ProPlayerForm;
import com.nephest.battlenet.sc2.web.service.ProPlayerService;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

    public static final int LOG_MAX_LIMIT = 100;

    @Autowired
    private ProPlayerAccountDAO proPlayerAccountDAO;

    @Autowired
    private AuditLogEntryDAO auditLogEntryDAO;

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
        form.getProPlayer().setUpdated(SC2Pulse.offsetDateTime());
        return ResponseEntity.ok(WebServiceUtil.wrapSecurity(proPlayerService.edit(form)).block());
    }

    @GetMapping("/log")
    public ResponseEntity<?> getLogEntries
    (
        @Min(1L) @Max(LOG_MAX_LIMIT)
        @RequestParam(name = "limit", defaultValue = LOG_MAX_LIMIT + "") int limit,
        @RequestParam(name = "excludeSystemAuthor", defaultValue = "false") boolean excludeSystemAuthor,
        @RequestParam(name = "authorAccountId", required = false) Long authorAccountId,
        @RequestParam(name = "action", required = false) AuditLogEntry.Action action,
        @RequestParam(name = "createdCursor", required = false) OffsetDateTime createdCursor,
        @RequestParam(name = "idCursor", required = false) Long idCursor,
        @RequestParam(name = "accountId", required = false) Long accountId
    )
    {
        if(createdCursor == null) createdCursor = SC2Pulse.offsetDateTime();
        return WebServiceUtil.notFoundIfEmpty(auditLogEntryDAO.findRevealerLog
        (
            limit,
            excludeSystemAuthor,
            authorAccountId,
            action,
            createdCursor,
            idCursor,
            accountId
        ));
    }

}
