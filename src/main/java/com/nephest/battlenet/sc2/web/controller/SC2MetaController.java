// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.web.service.SC2MetaService;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meta")
public class SC2MetaController
{

    @Autowired
    private SC2MetaService sc2MetaService;

    @GetMapping("/patch")
    public ResponseEntity<?> getPatches
    (
        @RequestParam(value = "publishedMin", required = false) OffsetDateTime publishedMin
    )
    {
        return WebServiceUtil.notFoundIfEmpty(sc2MetaService
            .getPatches(publishedMin != null ? publishedMin : SC2MetaService.PATCH_START));
    }

}
