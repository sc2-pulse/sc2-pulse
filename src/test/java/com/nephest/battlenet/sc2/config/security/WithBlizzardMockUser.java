package com.nephest.battlenet.sc2.config.security;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = AccountSecurityContextFactory.class)
public @interface WithBlizzardMockUser
{
    String username();
    String[] roles() default {"USER"};
}
