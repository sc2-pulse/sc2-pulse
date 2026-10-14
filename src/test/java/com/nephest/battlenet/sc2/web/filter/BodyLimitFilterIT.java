// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.filter;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.security.SC2PulseAuthority;
import com.nephest.battlenet.sc2.config.security.WithBlizzardMockUser;
import com.nephest.battlenet.sc2.model.Partition;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(classes = AllTestConfig.class)
@AutoConfigureMockMvc
@TestPropertySource("classpath:application.properties")
public class BodyLimitFilterIT
{

    public static final String PATH = "/api/reveal/player/edit";
    public static final int LIMIT = 1024 * 1024;

    @Autowired
    private MockMvc mvc;

    private static byte[] body(int size)
    {
        String prefix = "{\"name\":\"";
        String suffix = "\"}";
        int padding = size - prefix.length() - suffix.length();
        return (prefix + "a".repeat(padding) + suffix)
            .getBytes(StandardCharsets.UTF_8);
    }

    private static RequestPostProcessor unknownLength()
    {
        return request ->
        {
            MockHttpServletRequest spy = spy(request);
            doReturn(-1L).when(spy).getContentLengthLong();
            doReturn(-1).when(spy).getContentLength();
            return spy;
        };
    }

    @Test
    public void whenContentLengthExceedsLimit_then413()
    throws Exception
    {
        mvc.perform
        (
            post(PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(LIMIT + 1))
        )
            .andExpect(status().isPayloadTooLarge());
    }

    @Test
    @WithBlizzardMockUser
    (
        partition = Partition.GLOBAL,
        username = "revealer",
        roles = {SC2PulseAuthority.USER, SC2PulseAuthority.REVEALER}
    )
    public void whenUnknownLengthExceedsLimit_then413()
    throws Exception
    {
        mvc.perform
        (
            post(PATH)
                .with(csrf())
                .with(unknownLength())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(LIMIT + 1))
        )
            .andExpect(status().isPayloadTooLarge());
    }

    @Test
    public void whenBodyWithinLimit_thenNot413()
    throws Exception
    {
        int status = mvc.perform
        (
            post(PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(LIMIT))
        )
            .andReturn().getResponse().getStatus();
        assertNotEquals(413, status);
    }

}
