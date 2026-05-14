// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Hidden @Controller @HtmlController
public class HtmlRedirectController
{

    private final PlayerCharacterDAO playerCharacterDAO;
    private final ConversionService conversionService;

    @Autowired
    public HtmlRedirectController
    (
        PlayerCharacterDAO playerCharacterDAO,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.playerCharacterDAO = playerCharacterDAO;
        this.conversionService = conversionService;
    }

    @GetMapping("/profile/{region}/{realm}/{battlenetId}")
    public String profileRedirect
    (
        @PathVariable int region,
        @PathVariable int realm,
        @PathVariable long battlenetId
    )
    throws NoResourceFoundException
    {
        Region regionEnum = conversionService.convert(region, Region.class);
        return playerCharacterDAO.find(regionEnum, realm, battlenetId)
            .map(c->"redirect:/?type=character&id=" + c.getId() + "&m=1")
            .orElseThrow(()->new NoResourceFoundException(
                HttpMethod.GET,
                "/profile/" + region + "/" + realm + "/" + battlenetId
            ));
    }

}
