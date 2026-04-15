// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clickhouse.client.api.Client;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.filter.NoCacheFilter;
import com.nephest.battlenet.sc2.model.util.DbTestUtil;
import java.io.InputStream;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.DigestUtils;

@SpringBootTest(classes = AllTestConfig.class)
@AutoConfigureMockMvc
@TestPropertySource("classpath:application.properties")
public class MvcIT
{

    @Autowired
    private MockMvc mvc;

    @Value("classpath:/static/script/shared/sc2pulse-util.min.js")
    private Resource sc2PulseUtilResource;

    @BeforeAll
    public static void beforeAll(@Autowired DataSource dataSource, @Autowired Client clickHouseClient)
    throws Exception
    {
        DbTestUtil.initDb(dataSource, clickHouseClient);
    }

    @AfterAll
    public static void afterAll(@Autowired DataSource dataSource, @Autowired Client clickHouseClient)
    throws Exception
    {
        DbTestUtil.clearDb(dataSource, clickHouseClient);
    }

    @CsvSource
    ({
        "/static/sc2.css, text/css",
        "/webjars/bootstrap/css/bootstrap.min.css, text/css",
        "/static/script/shared/sc2pulse-util.min.js, application/javascript"
    })
    @ParameterizedTest
    public void staticResourcesMustHaveCacheControl(String url, String contentType)
    throws Exception
    {
        mvc.perform(get(url).contentType(contentType))
            .andExpect(status().isOk())
            // 1 year cache
            .andExpect(header().string("Cache-Control", "max-age=31536000, must-revalidate"));
    }

    @Test
    public void testSc2PulseUtilLoader()
    throws Exception
    {
        String md5Hash;
        try (InputStream is = sc2PulseUtilResource.getInputStream()) {
            md5Hash = DigestUtils.md5DigestAsHex(is);
        }
        ResultActions actions = mvc.perform
        (
            get("/static/script/shared/sc2pulse-util-loader.js")
                .contentType("application/javascript")
        )
            .andExpect(status().isOk());
        for(Map.Entry<String, String> entry : NoCacheFilter.NO_CACHE_HEADERS.entrySet())
            actions = actions.andExpect(header().string(entry.getKey(), entry.getValue()));
        actions.andExpect(content().string(
            "export * from './sc2pulse-util.min-" + md5Hash + ".js';"))
            .andReturn();
    }

}
