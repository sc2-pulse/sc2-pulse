// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.extension;

import com.nephest.battlenet.sc2.model.util.TestDatabaseLifecycleService;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * <p>
 * Resets the DB after a test class or method, depending on the
 * {@link AutoConfigureDatabase.ExecutionPhase}. The DB is initialized by
 * containers on launch, so there is no need to initialize it before tests.
 * This guarantees that each test has a properly initialized empty DB even
 * before the Spring context is loaded.
 * </p>
 */
public class DatabaseLifecycleExtension
implements AfterAllCallback, AfterEachCallback
{

    @Override
    public void afterAll(ExtensionContext context)
    {
        if(getPhase(context) == AutoConfigureDatabase.ExecutionPhase.CLASS)
            getService(context).resetDb();
    }

    @Override
    public void afterEach(ExtensionContext context)
    {
        if(getPhase(context) == AutoConfigureDatabase.ExecutionPhase.METHOD)
            getService(context).resetDb();
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
