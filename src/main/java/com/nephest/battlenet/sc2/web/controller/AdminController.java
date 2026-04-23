// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.web.service.SupporterService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@Hidden
public class AdminController
{

    @Autowired
    private SupporterService supporterService;

    @PostMapping("/supporters/supporter/{name}")
    public void addSupporter(@PathVariable String name)
    {
        supporterService.addSupporter(name);
    }

    @DeleteMapping("/supporters/supporter/{name}")
    public void removeSupporter(@PathVariable String name)
    {
        supporterService.removeSupporter(name);
    }

    @PostMapping("/supporters/donor/{name}")
    public void addDonor(@PathVariable String name)
    {
        supporterService.getDonorsVar().getValue().add(name);
        supporterService.getDonorsVar().save();
    }

    @DeleteMapping("/supporters/donor/{name}")
    public void removeDonor(@PathVariable String name)
    {
        supporterService.getDonorsVar().getValue().remove(name);
        supporterService.getDonorsVar().save();
    }

}
