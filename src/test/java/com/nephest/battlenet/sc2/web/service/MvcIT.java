// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static com.nephest.battlenet.sc2.web.util.MvcTestUtil.flattened;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.filter.NoCacheFilter;
import com.nephest.battlenet.sc2.extension.AutoConfigureDatabase;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.DigestUtils;

@SpringBootTest(classes = AllTestConfig.class)
@AutoConfigureMockMvc
@TestPropertySource("classpath:application.properties")
@AutoConfigureDatabase(AutoConfigureDatabase.ExecutionPhase.CLASS)
public class MvcIT
{

    @Autowired
    private MockMvc mvc;

    @Value("classpath:/static/script/shared/sc2pulse-util.min.js")
    private Resource sc2PulseUtilResource;

    @Value("${com.nephest.battlenet.sc2.cors.allowed-origin-patterns:#{''}}")
    private List<String> corsAllowedOriginPatterns;

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
    public void whenSupportedApiOrigin_then200AndCors()
    throws Exception
    {
        String origin = "https://sub.cors.test.localhost";
        mvc.perform
        (
            get("/api/seasons")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ORIGIN, origin)
                .with(csrf())
        )
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
            .andExpect(header().string(HttpHeaders.VARY, containsString(HttpHeaders.ORIGIN)));
    }

    @Test
    public void whenSupportedApiOriginPreflight_then200AndCors()
    throws Exception
    {
        String origin = "https://sub.cors.test.localhost";
        mvc.perform
        (
            options("/api/seasons")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header
                (
                    HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,

                    HttpHeaders.CONTENT_TYPE,
                    "Custom-Header"
                )
                .with(csrf())
        )
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET"))
            .andExpect(header().stringValues(
                HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,

                flattened(containsInAnyOrder(
                    HttpHeaders.CONTENT_TYPE,
                    "Custom-Header"
                ))
            ))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"))
            .andExpect(header().stringValues(
                HttpHeaders.VARY,

                flattened(hasItems(
                    HttpHeaders.ORIGIN,
                    HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                    HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS
                ))
            ))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    public void whenUnsupportedApiOrigin_then403AndNoCors()
    throws Exception
    {
        mvc.perform
        (
            get("/api/seasons")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ORIGIN, "https://sub.cors1.test.localhost")
                .with(csrf())
        )
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
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
