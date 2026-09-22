// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import com.nephest.battlenet.sc2.web.servlet.BufferedHttpServletRequest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

public class PatreonWebhookVerificationFilter
extends OncePerRequestFilter
{

    public static final String HMAC_ALGORITHM = "HmacMD5";
    public static final HexFormat HEX_FORMAT = HexFormat.of();
    public static final String SIGNATURE_HEADER_NAME = "X-Patreon-Signature";

    private final SecretKeySpec secretKeySpec;

    public PatreonWebhookVerificationFilter(byte[] secret)
    {
        this.secretKeySpec = new SecretKeySpec(secret, HMAC_ALGORITHM);
        getMac(); //test Mac initialization eagerly
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

        BufferedHttpServletRequest bufferedRequest
            = BufferedHttpServletRequest.from(request);
        if
        (
            !isSignatureValid
            (
                bufferedRequest.getInputStream().readAllBytes(),
                bufferedRequest.getHeader(SIGNATURE_HEADER_NAME)
            )
        )
        {
            response.sendError(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        filterChain.doFilter(bufferedRequest, response);
    }

    private Mac getMac()
    {
        try
        {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            return mac;
        }
        catch (NoSuchAlgorithmException | InvalidKeyException e)
        {
            throw new IllegalStateException(e);
        }
    }

    private boolean isSignatureValid(byte[] body, String signature)
    {
        if(signature == null) return false;

        byte[] signatureHex;
        try
        {
            signatureHex = HEX_FORMAT.parseHex(signature);
        }
        catch (IllegalArgumentException ex)
        {
            return false;
        }

        return MessageDigest.isEqual(getMac().doFinal(body), signatureHex);
    }

}
