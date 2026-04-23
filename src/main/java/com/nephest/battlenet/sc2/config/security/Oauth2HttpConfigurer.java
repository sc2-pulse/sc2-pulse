// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@OAuth2
public class Oauth2HttpConfigurer
implements Customizer<HttpSecurity>
{

    private final RegistrationDelegatingOauth2UserService registrationDelegatingOauth2UserService;
    private final OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest>
        oAuth2AuthorizationCodeClient;
    private final OAuth2AuthorizationRequestResolver delegatingAuthorizationRequestResolver;
    @Autowired(required = false) @Qualifier("updateDataAuthenticationSuccessHandler")
    private final AuthenticationSuccessHandler authenticationSuccessHandler;

    @Autowired
    public Oauth2HttpConfigurer
    (
        RegistrationDelegatingOauth2UserService registrationDelegatingOauth2UserService,
        @Qualifier("rateLimitedOAuth2AuthorizationCodeClient")
        OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> oAuth2AuthorizationCodeClient,
        @Qualifier("delegatingAuthorizationRequestResolver")
        OAuth2AuthorizationRequestResolver delegatingAuthorizationRequestResolver,
        @Autowired(required = false) @Qualifier("updateDataAuthenticationSuccessHandler")
        AuthenticationSuccessHandler authenticationSuccessHandler
    )
    {
        this.registrationDelegatingOauth2UserService = registrationDelegatingOauth2UserService;
        this.oAuth2AuthorizationCodeClient = oAuth2AuthorizationCodeClient;
        this.delegatingAuthorizationRequestResolver = delegatingAuthorizationRequestResolver;
        this.authenticationSuccessHandler = authenticationSuccessHandler;
    }

    @Override
    public void customize(HttpSecurity httpSecurity)
    {
        try
        {
            httpSecurity.oauth2Login(oauth2Login->{
                var configurer = oauth2Login.loginPage("/login");
                if(authenticationSuccessHandler != null) configurer = configurer
                    .successHandler(authenticationSuccessHandler);

                configurer
                    .failureUrl("/login?oauthError=1")
                    .userInfoEndpoint(userInfoEndpoint->
                        userInfoEndpoint.userService(registrationDelegatingOauth2UserService))
                    .tokenEndpoint(tokenEndpoint->
                        tokenEndpoint.accessTokenResponseClient(oAuth2AuthorizationCodeClient))
                    .authorizationEndpoint(c->
                        c.authorizationRequestResolver(delegatingAuthorizationRequestResolver));
            });
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

}
