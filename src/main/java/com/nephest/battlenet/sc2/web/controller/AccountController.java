// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import com.nephest.battlenet.sc2.web.service.linked.LinkedAccountService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*TODO
    This should be public once the underlying syntax is improved. The endpoint itself is not
    deprecated.
 */
@Hidden
@RestController
@RequestMapping("/api/account")
public class AccountController
{

    @Autowired
    private LinkedAccountService linkedAccountService;

    @GetMapping("/{id}/linked/external/account")
    public ResponseEntity<?> getLinkedExternalAccounts(@PathVariable("id") long accountId)
    {
        return WebServiceUtil.notFoundIfEmpty(linkedAccountService.getAccounts(accountId));
    }

}
