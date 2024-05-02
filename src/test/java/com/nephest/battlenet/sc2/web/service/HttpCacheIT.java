// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.filter.NoCacheFilter;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.web.util.StatefulRestTemplateInterceptor;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
(
    classes = AllTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT
)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class HttpCacheIT
{

    private static final Pattern NON_ZERO_CACHE_PATTERN = Pattern.compile(".*max-age=[^0].*");

    @LocalServerPort
    private int port;

    @Autowired
    private SeasonDAO seasonDAO;

    private TestRestTemplate restTemplate;

    @BeforeEach
    public void beforeEach()
    {
        restTemplate = new TestRestTemplate();
        restTemplate.getRestTemplate().setInterceptors(List.of(new StatefulRestTemplateInterceptor()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testCacheSecurity(boolean cookie)
    {
        String url = "http://localhost:" + port + "/api/test/cache" + (cookie ? "/cookie" : "");
        //get cookie csrf token in case cookie csrf storage is enabled
        restTemplate.exchange
        (
            url,
            HttpMethod.GET,
            new HttpEntity<>(null, null),
            String.class
        );
        //the qctual request without the csrf cookie
        ResponseEntity<String> response = restTemplate.exchange
        (
            url,
            HttpMethod.GET,
            new HttpEntity<>(null, null),
            String.class
        );
        if(cookie)
        {
            verifyNoCacheHeaders(response.getHeaders());
        }
        else
        {
            assertTrue(NON_ZERO_CACHE_PATTERN
                .matcher(response.getHeaders().get(HttpHeaders.CACHE_CONTROL).get(0))
                .matches()
            );
        }
    }

    public static void verifyNoCacheHeaders(HttpHeaders headers)
    {
        NoCacheFilter.NO_CACHE_HEADERS.forEach((key, value)->
        {
            List<String> vals = headers.get(key);
            assertEquals(1, vals.size());
            assertEquals(value, vals.get(0));
        });
    }

}
