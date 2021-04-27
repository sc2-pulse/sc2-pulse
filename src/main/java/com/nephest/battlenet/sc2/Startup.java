// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

@Profile({"!maintenance & !dev"})
@Component
public class Startup
implements ApplicationRunner
{

    @Autowired @Qualifier("taskScheduler")
    private Executor executor;

    @Override
    public void run(ApplicationArguments args)
    {
    }
}
