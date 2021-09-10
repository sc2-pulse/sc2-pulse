// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import com.nephest.battlenet.sc2.config.GlobalRestTemplateCustomizer;
import com.nephest.battlenet.sc2.config.filter.RobotsDenyFilter;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.AccountRoleDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.endpoint.DefaultClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig
extends WebSecurityConfigurerAdapter
{

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private AccountRoleDAO accountRoleDAO;

    @Autowired
    private SameSiteRememberMeAuthenticationSuccessfulHandler sameSiteRememberMeAuthenticationSuccessfulHandler;

    @Override
    public void configure(HttpSecurity http)
    throws Exception
    {
        http
            .csrf().disable() //handled by sameSite
            .exceptionHandling()
                .defaultAuthenticationEntryPointFor
                (
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    new AntPathRequestMatcher("/api/my/**")
                )
            .and().authorizeRequests()
                 .antMatchers("/actuator/**").hasRole(SC2PulseAuthority.ADMIN.getName())
            .and().authorizeRequests()
                .antMatchers("/api/character/report/vote/**").hasRole(SC2PulseAuthority.MODERATOR.getName())
            .and().authorizeRequests()
                .antMatchers("/api/my/**").authenticated()
            .and().logout()
                .logoutSuccessUrl("/?#stats")
                .deleteCookies(SameSiteRememberMeAuthenticationSuccessfulHandler.COOKIE_NAME)
            .and().oauth2Login()
                .loginPage("/?#personal")
                .successHandler(sameSiteRememberMeAuthenticationSuccessfulHandler)
                .userInfoEndpoint().oidcUserService(new BlizzardOidcUserService(accountDAO, accountRoleDAO));
    }

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager
    (
        ClientRegistrationRepository clientRegistrationRepository,
        OAuth2AuthorizedClientService authorizedClientService
    )
    {
        OAuth2AuthorizedClientProvider authorizedClientProvider = getAuthorizedClientProvider();

        AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientManager =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager
        (
            clientRegistrationRepository,
            authorizedClientService
        );
        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

        return authorizedClientManager;
    }

    //this properly sets the IO timeouts for internal RestTemplate
    private OAuth2AuthorizedClientProvider getAuthorizedClientProvider()
    {
        DefaultClientCredentialsTokenResponseClient accessTokenResponseClient
            = new DefaultClientCredentialsTokenResponseClient();
        RestTemplate restTemplate = new RestTemplate(Arrays.asList
        (
            new FormHttpMessageConverter(),
            new OAuth2AccessTokenResponseHttpMessageConverter()
        ));
        GlobalRestTemplateCustomizer.setTimeouts(restTemplate);
        accessTokenResponseClient.setRestOperations(restTemplate);

        ClientCredentialsOAuth2AuthorizedClientProvider cp = new ClientCredentialsOAuth2AuthorizedClientProvider();
        cp.setAccessTokenResponseClient(accessTokenResponseClient);

        DelegatingOAuth2AuthorizedClientProvider authorizedClientProvider = new DelegatingOAuth2AuthorizedClientProvider(cp);
        return authorizedClientProvider;
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

}
