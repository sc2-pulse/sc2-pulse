// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.net;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class StdControllerAdvice
extends ResponseEntityExceptionHandler
{

    @ExceptionHandler({IllegalArgumentException.class})
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

}
