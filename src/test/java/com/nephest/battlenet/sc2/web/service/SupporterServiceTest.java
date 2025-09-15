// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SupporterServiceTest
{

    @Mock
    private VarDAO varDAO;

    @Mock
    private Random rng;

    @CsvSource
    ({
        "1, 2, name3",
        "2, 0, 'name1, name2'",
        "2, 1, 'name3, name4'",
        "3, 1, name4",
        "5, 0, 'name1, name2, name3, name4'"
    })
    @ParameterizedTest
    public void testGetRandomSupporters(int count, int subListIx, String expectedResult)
    {
        when(varDAO.find(argThat(s->s.equals("supporters") || s.equals("donors"))))
            .thenReturn(Optional.of("name1,name2,name3,name4"));
        when(rng.nextInt(anyInt())).thenReturn(subListIx);

        SupporterService supporterService = new SupporterService
        (
            varDAO, ()->rng,
            "", "", List.of(), List.of(), Map.of(), Map.of()
        );
        supporterService.setSupporterService(supporterService);

        assertEquals(expectedResult, supporterService.getRandomSupporters(count));
    }

}
