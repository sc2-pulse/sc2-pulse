// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration @Order(100)
@EnableWebSecurity
public class SecurityConfig
{

    public static final Duration REMEMBER_ME_DURATION = Duration.ofDays(365);
    public static final String REMEMBER_ME_COOKIE_NAME = "remember-me";

    @Autowired
    private RegistrationDelegatingOauth2UserService registrationDelegatingOauth2UserService;

    @Autowired @Qualifier("concurrentPersistentTokenBasedRememberMeService")
    private RememberMeServices rememberMeServices;

    @Autowired @Qualifier("updateDataAuthenticationSuccessHandler")
    private AuthenticationSuccessHandler authenticationSuccessHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http)
    throws Exception
    {
        return http
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
                .mvcMatchers("/api/reveal/**").hasRole(SC2PulseAuthority.REVEALER.getName())
                .mvcMatchers
                (
                    "/api/my/**",
                    "/verify/*"
                ).authenticated()
                .mvcMatchers("/data/battle-net").fullyAuthenticated()
            .and().logout()
                .logoutSuccessUrl("/?#stats")
            .and().oauth2Login()
                .loginPage("/login")
                .successHandler(authenticationSuccessHandler)
                .failureUrl("/login?oauthError=1")
                .userInfoEndpoint().userService(registrationDelegatingOauth2UserService)
            .and().and().rememberMe()
                .rememberMeServices(rememberMeServices)
            .and().build();
    }

}
