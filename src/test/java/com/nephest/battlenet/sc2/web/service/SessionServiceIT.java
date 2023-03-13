// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.security.ConcurrentPersistentTokenBasedRememberMeService;
import com.nephest.battlenet.sc2.config.security.SecurityConfig;
import com.nephest.battlenet.sc2.config.security.WithBlizzardMockUser;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.local.AuthenticationRequest;
import com.nephest.battlenet.sc2.model.local.dao.AuthenticationRequestDAO;
import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import javax.servlet.http.Cookie;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = {AllTestConfig.class})
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class SessionServiceIT
{

    private static MockMvc mvc;

    @Autowired
    private AuthenticationRequestDAO authenticationRequestDAO;

    @Autowired
    private PersistentTokenRepository persistentTokenRepository;

    @Autowired
    private ConcurrentPersistentTokenBasedRememberMeService rememberMeService;

    @BeforeAll
    public static void init
    (
        @Autowired DataSource dataSource,
        @Autowired WebApplicationContext webApplicationContext,
        @Autowired CacheManager cacheManager
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
            cacheManager.getCacheNames()
                .forEach(cacheName->cacheManager.getCache(cacheName).clear());
        }
        mvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .alwaysDo(print())
            .build();
    }

    @AfterEach
    public void afterEach
    (
        @Autowired DataSource dataSource,
        @Autowired WebApplicationContext webApplicationContext,
        @Autowired CacheManager cacheManager
    ) throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
            cacheManager.getCacheNames()
                .forEach(cacheName->cacheManager.getCache(cacheName).clear());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @WithBlizzardMockUser(partition =  Partition.GLOBAL, username = "user")
    public void testSessionSynchronization(boolean isOldCookieCached)
    throws Exception
    {
        String cachedCookie = "cachedCookie123";
        String[] cachedToken = rememberMeService.decodeCookie(cachedCookie);
        String activeTokenValue = "token";
        String activeCookie = rememberMeService
            .encodeCookie(new String[]{cachedToken[0], activeTokenValue});

        if(isOldCookieCached) authenticationRequestDAO.merge(new AuthenticationRequest(
            String.join("", cachedToken), OffsetDateTime.now()));
        PersistentRememberMeToken activeToken = new PersistentRememberMeToken
        (
            "1",
            cachedToken[0],
            activeTokenValue,
            Date.from(Instant.now())
        );
        persistentTokenRepository.createNewToken(activeToken);

        mvc.perform
        (
            get("/api/my/session/synchronize")
                .cookie(new Cookie(SecurityConfig.REMEMBER_ME_COOKIE_NAME, cachedCookie))
        )
            .andExpect(status().isOk())
            .andExpect
            (
                isOldCookieCached
                    ? cookie().value(SecurityConfig.REMEMBER_ME_COOKIE_NAME, activeCookie)
                    : cookie().doesNotExist(SecurityConfig.REMEMBER_ME_COOKIE_NAME)
            );
    }

}