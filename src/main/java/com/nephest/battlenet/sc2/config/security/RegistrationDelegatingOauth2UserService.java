// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * The service delegates requests to {@link Oauth2UserServiceRegistration} services based on id of
 * the request.
 * Unlike Spring's {@link org.springframework.security.oauth2.client.userinfo.DelegatingOAuth2UserService}
 * it can house both oidc and oauth2 services in a single oauth2 entry point, and calls specific
 * service for the request which allows service chain to throw exceptions without breaking the
 * logic.
 */
@Service
public class RegistrationDelegatingOauth2UserService
implements OAuth2UserService<OAuth2UserRequest, OAuth2User>
{

    private final Map<String, OAuth2UserService<OAuth2UserRequest, OAuth2User>> services
        = new HashMap<>();

    @Autowired
    public RegistrationDelegatingOauth2UserService
    (
        List<Oauth2UserServiceRegistration<OAuth2UserRequest, OAuth2User>> registrations
    )
    {
        for(Oauth2UserServiceRegistration<OAuth2UserRequest, OAuth2User> registration : registrations)
            for(String id : registration.getRegistrationIds())
                services.put(id, registration);
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest)
    throws OAuth2AuthenticationException
    {
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuth2UserService<OAuth2UserRequest, OAuth2User> service = services.get(registrationId);
        if(service == null) throw new IllegalStateException(
            "Unsupported registration id for oauth2 user request: " + registrationId);

        return service.loadUser(userRequest);
    }

}
