// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import com.nephest.battlenet.sc2.config.filter.CsrfCookieFilter;
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
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration @Order(100)
@EnableWebSecurity
public class SecurityConfig
{

    public static final String REMEMBER_ME_COOKIE_NAME = "remember-me";
    public static final String REMEMBER_ME_KEY_PROPERTY_NAME = "security.remember-me.token.key";

    public static final Set<String> BLIZZARD_OAUTH_REGISTRATION_IDS = Set.of
    (
        "sc2-lg-us",
        "sc2-lg-eu",
        "sc2-lg-kr",
        "sc2-lg-cn"
    );

    @Autowired
    private RegistrationDelegatingOauth2UserService registrationDelegatingOauth2UserService;

    @Autowired @Qualifier("updateDataAuthenticationSuccessHandler")
    private AuthenticationSuccessHandler authenticationSuccessHandler;

    @Autowired @Qualifier("rateLimitedOAuth2AuthorizationCodeClient")
    OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> oAuth2AuthorizationCodeClient;

    @Autowired
    private Environment environment;

    @Autowired
    private SessionRegistry sessionRegistry;

    @Value("${" + REMEMBER_ME_KEY_PROPERTY_NAME + ":dev}")
    private String rememberMeKey;

    @Value("${server.servlet.session.cookie.name:JSESSIONID}")
    private String sessionCookieName;

    @Value("${security.remember-me.token.max-age:P3650D}")
    private Duration rememberMeDuration;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http)
    throws Exception
    {
        ResetSessionStrategy resetSessionStrategy
            = new ResetSessionStrategy(sessionCookieName, "/", "/api/");
        checkConfig();
        SecurityFilterChain chain = http
            .securityMatcher("/**")
            .sessionManagement(sessionManagement->sessionManagement
                .sessionConcurrency
                (
                    c->c.maximumSessions(-1)
                        .sessionRegistry(sessionRegistry)
                        .expiredSessionStrategy(resetSessionStrategy)
                )
                .invalidSessionStrategy(resetSessionStrategy))
            .csrf(csrf->csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            .exceptionHandling(exceptionHandling->exceptionHandling
                .defaultAuthenticationEntryPointFor
                (
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    new AntPathRequestMatcher("/api/my/**")
                ))
            .authorizeHttpRequests(authorizeHttpRequests->authorizeHttpRequests
                .requestMatchers("/admin/**").hasRole(SC2PulseAuthority.ADMIN.getName())
                .requestMatchers("/api/character/report/vote/**").hasRole(SC2PulseAuthority.MODERATOR.getName())
                .requestMatchers("/api/reveal/**").hasRole(SC2PulseAuthority.REVEALER.getName())
                .requestMatchers
                (
                    "/api/my/**",
                    "/verify/*"
                ).authenticated()
                .requestMatchers
                (
                    "/data/battle-net",
                    "/settings/**"
                ).access(new OAuthRegistrationAuthorizationManager<>(
                    AuthenticatedAuthorizationManager.fullyAuthenticated(),
                    BLIZZARD_OAUTH_REGISTRATION_IDS
                ))
                .anyRequest().permitAll())
            .logout(logout->logout.logoutSuccessUrl("/?#stats"))
            .oauth2Login(oauth2Login->oauth2Login
                .loginPage("/login")
                .successHandler(authenticationSuccessHandler)
                .failureUrl("/login?oauthError=1")
                .userInfoEndpoint(userInfoEndpoint->
                    userInfoEndpoint.userService(registrationDelegatingOauth2UserService))
                .tokenEndpoint(tokenEndpoint->
                    tokenEndpoint.accessTokenResponseClient(oAuth2AuthorizationCodeClient)))
            .rememberMe(rememberMe->rememberMe
                .key(rememberMeKey)
                .alwaysRemember(true)
                .rememberMeCookieName(REMEMBER_ME_COOKIE_NAME)
                .tokenValiditySeconds((int) rememberMeDuration.toSeconds())
                .useSecureCookie(true))
            .build();
        init(chain);
        return chain;
    }

    private void init(SecurityFilterChain chain)
    {
        replaceAuthenticationTrustResolver(chain);
    }

    private void replaceAuthenticationTrustResolver(SecurityFilterChain chain)
    {
        ExceptionTranslationFilter etf = chain.getFilters().stream()
            .filter(f->f instanceof ExceptionTranslationFilter)
            .map(f->(ExceptionTranslationFilter) f)
            .findAny()
            .orElseThrow();
        AuthenticationTrustResolver resolver =
            new OAuthRegistrationAuthenticationTrustResolver
            (
                new AuthenticationTrustResolverImpl(),
                BLIZZARD_OAUTH_REGISTRATION_IDS
            );
        etf.setAuthenticationTrustResolver(resolver);
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
