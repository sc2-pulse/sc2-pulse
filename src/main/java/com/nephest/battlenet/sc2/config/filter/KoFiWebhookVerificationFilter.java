// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

public class KoFiWebhookVerificationFilter
extends OncePerRequestFilter
{

    private final ObjectMapper objectMapper;
    private final byte[] verificationToken;

    public KoFiWebhookVerificationFilter
    (
        ObjectMapper objectMapper,
        String verificationToken
    )
    {
        if(verificationToken == null || verificationToken.isBlank())
            throw new IllegalArgumentException
            (
                "Ko-fi verification token must not be blank"
            );

        this.objectMapper = objectMapper;
        this.verificationToken = verificationToken
            .getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal
    (
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    )
    throws ServletException, IOException
    {
        if(!HttpMethod.POST.matches(request.getMethod()))
        {
            filterChain.doFilter(request, response);
            return;
        }

        String suppliedToken = null;
        try
        {
            JsonNode payload = objectMapper
                .readTree(request.getParameter("data"));
            if(payload.hasNonNull("verification_token"))
                suppliedToken = payload.get("verification_token").asText();
        }
        catch (IOException | RuntimeException ex)
        {
            // Invalid payloads cannot provide a valid verification token.
        }
        if
        (
            suppliedToken == null
                || !MessageDigest.isEqual
                (
                    suppliedToken.getBytes(StandardCharsets.UTF_8),
                    verificationToken
                )
        )
        {
            response.sendError(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        filterChain.doFilter(request, response);
    }

}
