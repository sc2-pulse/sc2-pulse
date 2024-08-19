// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.twitch4j.TwitchClient;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TwitchAPITest
{

    @Mock
    private TwitchClient twitchClient;

    private TwitchAPI api;

    @BeforeEach
    public void beforeEach()
    {
        api = new TwitchAPI(twitchClient);
    }

    @CsvSource
    ({
        "-1, false",
        "0, false",
        "1, true",
        "2, true"
    })
    @ParameterizedTest
    public void testAllowedBatchConcurrency(int concurrency, boolean allowed)
    {
        Set<String> tokens = Set.of("1", "2", "123", "12345");
        List<Executable> execs = List.of
        (
            ()->api.getUsersByIds(tokens, concurrency),
            ()->api.getUsersByLogins(tokens, concurrency)
        );
        if(allowed)
        {
            execs.forEach(e->assertDoesNotThrow(e, "Concurrency >= 1 required"));
        }
        else
        {
            execs.forEach(e->assertThrows(
                IllegalArgumentException.class, e, "Concurrency >= 1 required"));
        }
    }

}
