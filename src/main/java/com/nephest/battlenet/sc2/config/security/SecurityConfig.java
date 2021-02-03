// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import com.nephest.battlenet.sc2.config.filter.RobotsDenyFilter;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig
extends WebSecurityConfigurerAdapter
{

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private SameSiteRememberMeAuthenticationSuccessfulHandler sameSiteRememberMeAuthenticationSuccessfulHandler;

    @Value("${com.nephest.battlenet.sc2.admin.btag:#{''}}")
    private String adminBattletag;

    @Value("${com.nephest.battlenet.sc2.admin.partition:#{'-1'}}")
    private int adminPartition;

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
                .antMatchers("/api/my/**").authenticated()
            .and().logout()
                .logoutSuccessUrl("/?t=stats")
                .deleteCookies(SameSiteRememberMeAuthenticationSuccessfulHandler.COOKIE_NAME)
            .and().oauth2Login()
                .loginPage("/#personal")
                .successHandler(sameSiteRememberMeAuthenticationSuccessfulHandler)
                .userInfoEndpoint().oidcUserService(new BlizzardOidcUserService(accountDAO, adminBattletag, adminPartition));
    }

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager
    (
        ClientRegistrationRepository clientRegistrationRepository,
        OAuth2AuthorizedClientService authorizedClientService
    )
    {
        OAuth2AuthorizedClientProvider authorizedClientProvider =
            OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .authorizationCode()
                .build();

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

}
