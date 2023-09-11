// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.security.WithBlizzardMockUser;
import com.nephest.battlenet.sc2.model.Partition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Flux;

@SpringBootTest(classes = {AllTestConfig.class})
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class PersonalServiceIT
{

    @MockBean
    private BlizzardSC2API api;

    @Autowired
    private PersonalService personalService;

    @Test
    @WithBlizzardMockUser(partition = Partition.GLOBAL, username = "btag")
    public void whenOidcUserIsPresentButThereIsCharacterError_thenReturnEmptyList()
    {
        assertTrue(personalService.getOidcUser().isPresent());
        when(api.getPlayerCharacters(any(), eq(1L)))
            .thenReturn(Flux.error(new RuntimeException("test")));
        assertTrue(personalService.getCharacters().isEmpty());
    }

}
