// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2;

import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class Startup
implements ApplicationRunner
{

    private static final Logger LOG = LoggerFactory.getLogger(Startup.class);

    @Autowired @Qualifier("taskScheduler")
    private Executor executor;

    @Override
    public void run(ApplicationArguments args)
    {
        //Spring uses internal RestTemplates in oauth client. These templates can't be configured via external config.
        if
        (
            System.getProperty("sun.net.client.defaultConnectTimeout") == null
            || Integer.parseInt(System.getProperty("sun.net.client.defaultConnectTimeout")) < 1
            || System.getProperty("sun.net.client.defaultReadTimeout") == null
            || Integer.parseInt(System.getProperty("sun.net.client.defaultReadTimeout")) < 1
        )
        LOG.warn("sun.net.client IO timeouts are not set. The application can block indefinitely without the timeouts");
    }
}
