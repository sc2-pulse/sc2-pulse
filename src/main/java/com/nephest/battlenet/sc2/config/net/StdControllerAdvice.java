// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.net;

import com.nephest.battlenet.sc2.web.servlet.BodyLimitExceededException;
import jakarta.validation.ValidationException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class StdControllerAdvice
extends ResponseEntityExceptionHandler
{

    @ExceptionHandler
    ({
        IllegalArgumentException.class,
        InvalidDataAccessApiUsageException.class
    })
    protected ResponseEntity<Object> handleBadArgument(RuntimeException ex, WebRequest request)
    {
        String bodyOfResponse = "Invalid argument";
        return handleExceptionInternal(ex, bodyOfResponse, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler({ArithmeticException.class })
    protected ResponseEntity<Object> handleBadNumber(RuntimeException ex, WebRequest request)
    {
        String bodyOfResponse = "Invalid number";
        return handleExceptionInternal(ex, bodyOfResponse, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler({ValidationException.class})
    protected ResponseEntity<Object> handleValidationError(RuntimeException ex, WebRequest request)
    {
        return handleExceptionInternal(ex, ex.getMessage(), new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class })
    protected ResponseEntity<Object> handleOptimisticLock(RuntimeException ex, WebRequest request)
    {
        String bodyOfResponse = "Invalid entity version. "
            + "Someone has already modified the entity while you were editing it.";
        return handleExceptionInternal
        (
            ex,
            bodyOfResponse,
            new HttpHeaders(),
            HttpStatus.CONFLICT,
            request
        );
    }

    @ExceptionHandler(BodyLimitExceededException.class)
    protected ResponseEntity<Object> handleBodyLimit
    (
        BodyLimitExceededException ex,
        WebRequest request
    )
    {
        return handleExceptionInternal
        (
            ex,
            "Request body is too large",
            new HttpHeaders(),
            HttpStatus.PAYLOAD_TOO_LARGE,
            request
        );
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable
    (
        HttpMessageNotReadableException ex,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
    )
    {
        BodyLimitExceededException limitEx = ExceptionUtils
            .throwableOfType(ex, BodyLimitExceededException.class);
        if(limitEx != null) return handleBodyLimit(limitEx, request);

        return super.handleHttpMessageNotReadable(ex, headers, status, request);
    }

}
