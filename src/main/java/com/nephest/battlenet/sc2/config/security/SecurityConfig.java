// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration @Order(100)
@EnableWebSecurity
public class SecurityConfig
{

    public static final String REMEMBER_ME_COOKIE_NAME = "remember-me-v2";
    public static final String REMEMBER_ME_KEY_PROPERTY_NAME = "security.remember-me.token.key";

    @Autowired
    private RegistrationDelegatingOauth2UserService registrationDelegatingOauth2UserService;

    @Autowired @Qualifier("updateDataAuthenticationSuccessHandler")
    private AuthenticationSuccessHandler authenticationSuccessHandler;

    @Autowired @Qualifier("rateLimitedOAuth2AuthorizationCodeClient")
    OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> oAuth2AuthorizationCodeClient;

    @Autowired
    private Environment environment;

    @Value("${" + REMEMBER_ME_KEY_PROPERTY_NAME + ":'dev'}")
    private String rememberMeKey;

    @Value("${security.remember-me.token.max-age:P3650D}")
    private Duration rememberMeDuration;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http)
    throws Exception
    {
        checkConfig();
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
                .and().tokenEndpoint().accessTokenResponseClient(oAuth2AuthorizationCodeClient)
            .and().and().rememberMe()
                .key(rememberMeKey)
                .alwaysRemember(true)
                .rememberMeCookieName(REMEMBER_ME_COOKIE_NAME)
                .tokenValiditySeconds((int) rememberMeDuration.toSeconds())
                .useSecureCookie(true)
            .and().build();
    }

    private void checkConfig()
    {
        Set<String> activeProfiles = Set.of(environment.getActiveProfiles());
        if
        (
            !activeProfiles.contains("test")
            && activeProfiles.contains("prod")
            && rememberMeKey.equals("dev")
        )
            throw new IllegalStateException("Static key(" + REMEMBER_ME_KEY_PROPERTY_NAME + ") "
                + "must be provided when prod profile is active");
    }

}
