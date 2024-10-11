// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import com.nephest.battlenet.sc2.model.Partition;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.security.test.context.support.WithSecurityContext;

@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = AccountSecurityContextFactory.class)
public @interface WithBlizzardMockUser
{

    String BLIZZARD_OAUTH_REGISTRATION_ID = "sc2-lg-us";
    String NON_BLIZZARD_OAUTH_REGISTRATION_ID = "secondary-oauth-id";

    long id() default 1L;
    Partition partition();
    String username();
    SC2PulseAuthority[] roles() default {SC2PulseAuthority.USER};
    long subject() default 1L;
    String authorizedClientRegistrationId() default BLIZZARD_OAUTH_REGISTRATION_ID;

}
