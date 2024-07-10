// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import java.util.Arrays;
import java.util.Comparator;
import org.springframework.stereotype.Service;

@Service
public class ServerSideRenderService
{

    @SuppressWarnings("unchecked")
    public <T> T[] reverseOrder(T[] array)
    {
        Arrays.sort(array, (Comparator<T>) Comparator.nullsLast(Comparator.reverseOrder()));
        return array;
    }

}
