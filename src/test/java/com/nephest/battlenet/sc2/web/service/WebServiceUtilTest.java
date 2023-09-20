// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class WebServiceUtilTest
{

    @Test
    public void verifyRunnableElasticThread()
    {
        String[] threadName = new String[1];
        WebServiceUtil
            .blockingRunnable(()->threadName[0] = Thread.currentThread().getName())
            .block();
        assertTrue(threadName[0].toLowerCase().contains("elastic"));
    }

    @Test
    public void verifyCallableElasticThread()
    {
        assertTrue
        (
            WebServiceUtil.blockingCallable(()->Thread.currentThread().getName())
                .block()
                .toLowerCase()
                .contains("elastic")
        );
    }

}
