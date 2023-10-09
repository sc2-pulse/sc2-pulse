// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.util.wrapper;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
public class DefaultThreadLocalRandomSupplier
implements ThreadLocalRandomSupplier
{

    @Override
    public Random get()
    {
        return ThreadLocalRandom.current();
    }

}
