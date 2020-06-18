// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MetaController
{

    @GetMapping(value = "/sitemap.xml", produces = {"application/xml"})
    public String sitemap()
    {
        return "sitemap.xml";
    }

}
