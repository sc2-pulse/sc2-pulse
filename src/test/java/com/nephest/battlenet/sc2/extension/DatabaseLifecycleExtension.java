// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.extension;

import com.nephest.battlenet.sc2.model.util.TestDatabaseLifecycleService;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

public class DatabaseLifecycleExtension
implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback
{

    @Override
    public void beforeAll(ExtensionContext context)
    {
        if(getPhase(context) == AutoConfigureDatabase.ExecutionPhase.CLASS)
            getService(context).initDb();
    }

    @Override
    public void afterAll(ExtensionContext context)
    {
        if(getPhase(context) == AutoConfigureDatabase.ExecutionPhase.CLASS)
            getService(context).clearDb();
    }

    @Override
    public void beforeEach(ExtensionContext context)
    {
        if(getPhase(context) == AutoConfigureDatabase.ExecutionPhase.METHOD)
            getService(context).initDb();
    }

    @Override
    public void afterEach(ExtensionContext context)
    {
        if(getPhase(context) == AutoConfigureDatabase.ExecutionPhase.METHOD)
            getService(context).clearDb();
    }

    private AutoConfigureDatabase.ExecutionPhase getPhase(ExtensionContext context)
    {
        return context.getTestClass()
            .map(c -> c.getAnnotation(AutoConfigureDatabase.class))
            .map(AutoConfigureDatabase::value)
            .orElse(null);
    }

    private TestDatabaseLifecycleService getService(ExtensionContext context)
    {
        return SpringExtension.getApplicationContext(context)
            .getBean(TestDatabaseLifecycleService.class);
    }

}
