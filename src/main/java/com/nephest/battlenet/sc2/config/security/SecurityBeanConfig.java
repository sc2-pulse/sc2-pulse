// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import com.nephest.battlenet.sc2.config.filter.RobotsDenyFilter;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.AuthorizationCodeOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ClientCredentialsOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.DelegatingOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.JdbcOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.RefreshTokenOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.RemoveAuthorizedClientOAuth2AuthorizationFailureHandler;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.DefaultClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.DefaultRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
public class SecurityBeanConfig
{

    private static final Logger LOG = LoggerFactory.getLogger(SecurityBeanConfig.class);

    @Bean
    public PersistentTokenRepository persistentTokenRepository(@Autowired DataSource dataSource)
    {
        JdbcTokenRepositoryImpl repository = new JdbcTokenRepositoryImpl();
        repository.setDataSource(dataSource);
        return repository;
    }

    @Bean
    public Map<String, OAuth2RateLimiter> oAuth2RateLimiters(List<OAuth2RateLimiter> rateLimiters)
    {
        for(OAuth2RateLimiter l : rateLimiters)
            LOG.debug("OAuth2 token client rate limiter found: {}", l.getClientRegistrationId());
        return RateLimitedTokenResponseClient.mapRateLimiters(rateLimiters);
    }

    @Bean
    public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> rateLimitedOAuth2AuthorizationCodeClient
    (
        @Qualifier("oAuth2RateLimiters") Map<String, OAuth2RateLimiter> rateLimiters
    )
    {
        return new RateLimitedTokenResponseClient<>
        (
            new DefaultAuthorizationCodeTokenResponseClient(),
            rateLimiters
        );
    }

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager
    (
        ClientRegistrationRepository clientRegistrationRepository,
        OAuth2AuthorizedClientService authorizedClientService,
        @Qualifier("oAuth2RateLimiters") Map<String, OAuth2RateLimiter> rateLimiters
    )
    {
        OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> clientCredentialsClient
            = new RateLimitedTokenResponseClient<>
            (
                new DefaultClientCredentialsTokenResponseClient(),
                rateLimiters
            );
        ClientCredentialsOAuth2AuthorizedClientProvider clientCredentialsProvider =
            new ClientCredentialsOAuth2AuthorizedClientProvider();
        clientCredentialsProvider.setAccessTokenResponseClient(clientCredentialsClient);

        OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> refreshTokenClient
            = new RateLimitedTokenResponseClient<>
            (
                new DefaultRefreshTokenTokenResponseClient(),
                rateLimiters
            );
        RefreshTokenOAuth2AuthorizedClientProvider refreshTokenProvider =
            new RefreshTokenOAuth2AuthorizedClientProvider();
        refreshTokenProvider.setAccessTokenResponseClient(refreshTokenClient);

        OAuth2AuthorizedClientProvider authorizedClientProvider =
            new DelegatingOAuth2AuthorizedClientProvider
            (
                clientCredentialsProvider,
                new AuthorizationCodeOAuth2AuthorizedClientProvider(),
                new CachedRefreshTokenOAuth2AuthorizedClientProvider(refreshTokenProvider)
            );

        AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientManager =
            new AuthorizedClientServiceOAuth2AuthorizedClientManager
            (
                clientRegistrationRepository,
                authorizedClientService
            );
        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

        return authorizedClientManager;
    }

    @Bean
    public FilterRegistrationBean<RobotsDenyFilter> robotsDenyFilterFilterRegistrationBean()
    {
        FilterRegistrationBean<RobotsDenyFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new RobotsDenyFilter());
        bean.addUrlPatterns("/oauth2/*", "/logout");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Bean
    public OAuth2AuthorizedClientService oAuth2AuthorizedClientService
    (
        JdbcOperations jdbcOperations,
        ClientRegistrationRepository clientRegistrationRepository
    )
    {
        return new JdbcOAuth2AuthorizedClientService(jdbcOperations, clientRegistrationRepository);
    }

    @Bean
    public RemoveAuthorizedClientOAuth2AuthorizationFailureHandler oAuth2FailureHandler
    (
        OAuth2AuthorizedClientService oAuth2AuthorizedClientService
    )
    {
        RemoveAuthorizedClientOAuth2AuthorizationFailureHandler.OAuth2AuthorizedClientRemover
            remover = (clientRegistrationId, principal, attributes)->oAuth2AuthorizedClientService
                .removeAuthorizedClient(clientRegistrationId, principal.getName());
        return new RemoveAuthorizedClientOAuth2AuthorizationFailureHandler(remover);
    }

    @Bean
    public PasswordEncoder passwordEncoder()
    {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public StringKeyGenerator secureStringGenerator()
    {
        return KeyGenerators.string();
    }

    @Bean
    public SessionRegistry sessionRegistry()
    {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher()
    {
        return new HttpSessionEventPublisher();
    }

}
