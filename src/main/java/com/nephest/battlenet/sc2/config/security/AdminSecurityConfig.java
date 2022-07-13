// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import de.codecentric.boot.admin.server.config.EnableAdminServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * This configuration sets up credentials for basic authentication for built-in SBA server
 */
@Configuration @Profile("prod")
@EnableAdminServer
public class AdminSecurityConfig
{

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
            .authorities(SC2PulseAuthority.SERVER_WATCHER.getRoleName());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
