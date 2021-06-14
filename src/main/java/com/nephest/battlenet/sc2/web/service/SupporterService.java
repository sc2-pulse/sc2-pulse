// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
public class SupporterService
{

    @Autowired
    private final Random rng;

    @Value("${com.nephest.battlenet.sc2.supporters:#{''}}")
    private final List<String> supporters;

    @Autowired
    private SupporterService
    (
        @Qualifier("simpleRng") Random rng,
        @Value("${com.nephest.battlenet.sc2.supporters:#{''}}") List<String> supporters
    )
    {
        this.rng = rng;
        this.supporters = supporters;
    }

    public String getRandomSupporter()
    {
        return supporters.get(rng.nextInt(supporters.size()));
    }

    public List<String> getSupporters()
    {
        return supporters;
    }

}
