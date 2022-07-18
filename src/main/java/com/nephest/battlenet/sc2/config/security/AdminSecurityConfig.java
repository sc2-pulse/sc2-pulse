// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import static org.springframework.security.config.Customizer.withDefaults;

import de.codecentric.boot.admin.server.config.EnableAdminServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * This configuration sets up credentials for basic authentication for built-in SBA server
 */
@Configuration @Profile("prod")
@EnableAdminServer
@Order(99)
public class AdminSecurityConfig
extends WebSecurityConfigurerAdapter
{

    //sba user security
    @Override
    public void configure(HttpSecurity http)
    throws Exception
    {
        http
            .antMatcher("/sba/**")
            .csrf()
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers
                (
                    new AntPathRequestMatcher("/sba/instances", HttpMethod.POST.toString()),
                    new AntPathRequestMatcher("/sba/instances/*", HttpMethod.DELETE.toString()),
                    new AntPathRequestMatcher("/sba/actuator/**")
                )
            .and().authorizeRequests()
                .antMatchers("/sba/instances/**/loggers").hasRole(SC2PulseAuthority.ADMIN.getName())
                .antMatchers("/sba/actuator/**").hasRole(SC2PulseAuthority.ACTUATOR.getName())
                .antMatchers("/sba/**").hasRole(SC2PulseAuthority.SERVER_WATCHER.getName())
                .anyRequest().denyAll()
            .and().httpBasic(withDefaults());
    }

    @Autowired
    public void configureGlobal
    (
        AuthenticationManagerBuilder auth,
        @Value("${spring.boot.admin.client.username:}") String name,
        @Value("${spring.boot.admin.client.password:}") String password
    ) throws Exception
    {
        auth.inMemoryAuthentication()
            .withUser(name).password(passwordEncoder().encode(password))
            .authorities
            (
                SC2PulseAuthority.SERVER_WATCHER.getRoleName(),
                SC2PulseAuthority.ACTUATOR.getRoleName()
            );
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
