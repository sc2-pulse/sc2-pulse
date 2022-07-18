// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import com.nephest.battlenet.sc2.config.filter.RobotsDenyFilter;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.AccountRoleDAO;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
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
    private AccountUserDetailsService accountUserDetailsService;

    @Autowired
    private PersistentTokenRepository persistentTokenRepository;

    @Autowired
    private ConcurrentPersistentTokenBasedRememberMeService concurrentPersistentTokenBasedRememberMeService;

    @Override
    public void configure(HttpSecurity http)
    throws Exception
    {
        http
            .antMatcher("/**")
            .csrf()
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            .and().exceptionHandling()
                .defaultAuthenticationEntryPointFor
                (
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    new AntPathRequestMatcher("/api/my/**")
                )
            .and().authorizeRequests()
                .antMatchers("/admin/**").hasRole(SC2PulseAuthority.ADMIN.getName())
                .antMatchers("/api/character/report/vote/**").hasRole(SC2PulseAuthority.MODERATOR.getName())
                .antMatchers("/api/my/**").authenticated()
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

    //the been definition is needed by remember me
    @Bean
    public UserDetailsService userDetailsService()
    {
        return accountUserDetailsService;
    }

    @Bean
    public PersistentTokenRepository persistentTokenRepository(@Autowired DataSource dataSource)
    {
        JdbcTokenRepositoryImpl repository = new JdbcTokenRepositoryImpl();
        repository.setDataSource(dataSource);
        return repository;
    }

    @Bean
    public ConcurrentPersistentTokenBasedRememberMeService concurrentPersistentTokenBasedRememberMeService
    (
        @Autowired UserDetailsService userDetailsService,
        @Autowired PersistentTokenRepository persistentTokenRepository
    )
    {
        PersistentTokenBasedRememberMeServices services = new PersistentTokenBasedRememberMeServices
        (
            new Base64StringKeyGenerator().generateKey(),
            userDetailsService,
            persistentTokenRepository
        );
        services.setAlwaysRemember(true);
        services.setTokenValiditySeconds((int) REMEMBER_ME_DURATION.toSeconds());
        services.setCookieName(REMEMBER_ME_COOKIE_NAME);
        return new ConcurrentPersistentTokenBasedRememberMeService(services);
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
