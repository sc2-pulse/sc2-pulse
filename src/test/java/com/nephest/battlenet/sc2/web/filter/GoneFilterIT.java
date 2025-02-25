// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.filter;

import static com.nephest.battlenet.sc2.config.filter.ParameterBasedGoneFilter.LEGACY_UID_PARAMETER_NAME;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = AllTestConfig.class)
@AutoConfigureMockMvc
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class GoneFilterIT
{

    public static final String OLD_LEGACY_UID = "201-1-12345";

    @Autowired
    private MockMvc mvc;

    @ValueSource(strings = {
        "/team/history",
        "/",
        ""
    })
    @ParameterizedTest
    public void whenOldLegacyUidParameter_thenGone(String path)
    throws Exception
    {
        mvc.perform
        (
            get(path)
                .queryParam(LEGACY_UID_PARAMETER_NAME, OLD_LEGACY_UID)
                .contentType(MediaType.TEXT_HTML)
        )
            .andExpect(status().isGone())
            .andReturn();
    }

    @Test
    public void whenOldLegacyUidParameterButApiPath_thenBadRequest()
    throws Exception
    {
        mvc.perform
        (
            get("/api/team/group/flat")
                .queryParam(LEGACY_UID_PARAMETER_NAME, OLD_LEGACY_UID)
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isBadRequest())
            .andReturn();
    }

}
