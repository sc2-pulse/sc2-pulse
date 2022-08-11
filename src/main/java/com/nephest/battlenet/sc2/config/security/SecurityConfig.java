// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.AccountRoleDAO;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration @Order(100)
@EnableWebSecurity
public class SecurityConfig
extends WebSecurityConfigurerAdapter
{

    public static final Duration REMEMBER_ME_DURATION = Duration.ofDays(365);
    public static final String REMEMBER_ME_COOKIE_NAME = "remember-me";

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private AccountRoleDAO accountRoleDAO;

    @Autowired
    private PersistentTokenRepository persistentTokenRepository;

    @Autowired
    private ConcurrentPersistentTokenBasedRememberMeService concurrentPersistentTokenBasedRememberMeService;

    @Override
    public void configure(HttpSecurity http)
    throws Exception
    {
        http
            .mvcMatcher("/**")
            .csrf()
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            .and().exceptionHandling()
                .defaultAuthenticationEntryPointFor
                (
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    new AntPathRequestMatcher("/api/my/**")
                )
            .and().authorizeRequests()
                .mvcMatchers("/admin/**").hasRole(SC2PulseAuthority.ADMIN.getName())
                .mvcMatchers("/api/character/report/vote/**").hasRole(SC2PulseAuthority.MODERATOR.getName())
                .mvcMatchers("/api/my/**").authenticated()
            .and().logout()
                .logoutSuccessUrl("/?#stats")
            .and().oauth2Login()
                .loginPage("/?#personal")
                .defaultSuccessUrl("/?#personal-characters")
                .failureUrl("/?oauthError=1#personal")
                .userInfoEndpoint().oidcUserService(new BlizzardOidcUserService(accountDAO, accountRoleDAO))
            .and().and().rememberMe()
                .rememberMeServices(concurrentPersistentTokenBasedRememberMeService)
                .key(concurrentPersistentTokenBasedRememberMeService.getKey());
    }

}
