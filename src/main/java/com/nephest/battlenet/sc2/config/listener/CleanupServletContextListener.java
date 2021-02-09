// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

@Component
public class CleanupServletContextListener
implements ServletContextListener
{

    private final Logger LOG = LoggerFactory.getLogger(CleanupServletContextListener.class);

    @Override
    public void contextDestroyed(ServletContextEvent ctx)
    {
        LOG.info("Clearing context");
        SecurityContextHolder.clearContext();
    }

}
