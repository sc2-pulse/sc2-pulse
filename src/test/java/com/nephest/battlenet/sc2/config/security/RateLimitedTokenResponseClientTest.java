// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.nephest.battlenet.sc2.web.service.WebServiceTestUtil;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Publisher;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
public class RateLimitedTokenResponseClientTest
{

    @Mock
    private OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> clientMock;

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    public void testRateLimiter(boolean limiterExists)
    {
        AtomicBoolean limiterUsed = new AtomicBoolean(false);
        OAuth2RateLimiter limiter = new OAuth2RateLimiter()
        {
            @Override
            public String getClientRegistrationId()
            {
                return "test";
            }

            @Override
            public <T> Flux<T> withLimiter(Publisher<T> publisher, boolean localLimiter)
            {
                limiterUsed.set(true);
                return Flux.empty();
            }
        };
        OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> client
            = new RateLimitedTokenResponseClient<>(clientMock, List.of(limiter));

        ClientRegistration clientRegistration = WebServiceTestUtil
            .createClientRegistration(limiterExists ? "test" : "notExistingId");
        OAuth2ClientCredentialsGrantRequest request
            = new OAuth2ClientCredentialsGrantRequest(clientRegistration);

        client.getTokenResponse(request);
        assertEquals(limiterExists, limiterUsed.get());
        verify(clientMock, times(limiterExists ? 0 : 1)).getTokenResponse(request);
    }

}
