// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.SpyBeanConfig;
import com.nephest.battlenet.sc2.config.security.WithBlizzardMockUser;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.util.PostgreSQLUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

@SpringBootTest(classes = {AllTestConfig.class, SpyBeanConfig.class})
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class PersonalServiceIT
{

    @Autowired
    private BlizzardSC2API api;

    @Autowired
    private PersonalService personalService;

    @Autowired
    private PostgreSQLUtils postgreSQLUtils;

    @Test
    @WithBlizzardMockUser(partition = Partition.GLOBAL, username = "btag")
    public void whenOidcUserIsPresentButThereIsCharacterError_thenReturnEmptyList()
    {
        assertTrue(personalService.getOidcUser().isPresent());
        doReturn(Flux.error(new RuntimeException("test")))
            .when(api).getPlayerCharacters(any(), eq(1L));
        assertTrue(personalService.getCharacters().isEmpty());
    }

    @Test
    @Transactional
    @WithBlizzardMockUser(partition = Partition.GLOBAL, username = "btag")
    public void testSetDbTransactionUserId()
    {
        assertNull(postgreSQLUtils.getTransactionUserId());
        personalService.setDbTransactionUserId();
        assertEquals("1", postgreSQLUtils.getTransactionUserId());
    }

    @Test
    @Transactional
    public void testSetDbTransactionUserId_noUser()
    {
        assertNull(postgreSQLUtils.getTransactionUserId());
        personalService.setDbTransactionUserId();
        assertNull(postgreSQLUtils.getTransactionUserId());
    }

    @Test
    @WithBlizzardMockUser(partition = Partition.GLOBAL, username = "btag")
    public void testConvenienceAccountIdCasts()
    {
        assertEquals
        (
            1L,
            PersonalService.getAccountId(PersonalService.getAuthentication().orElseThrow())
                .orElseThrow()
        );
        assertEquals(1L, PersonalService.getAccountId().orElseThrow());
    }

}
