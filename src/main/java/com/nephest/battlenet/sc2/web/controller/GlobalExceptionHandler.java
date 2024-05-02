// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
@Order(-1)
public class GlobalExceptionHandler
{

    /*TODO
        https://github.com/spring-projects/spring-framework/issues/31569
        Spring and Boot error handling is not is sync atm. Boot doesn't support Spring errors,
        and Spring doesn't support HTML responses.
        Throw spring exceptions to propagate them to Boot for now. This workaround should be
        removed when Boot supports it.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public final ResponseEntity<Object> handleResourceNotFound(Exception ex)
    throws Exception
    {
        throw ex;
    }

}
