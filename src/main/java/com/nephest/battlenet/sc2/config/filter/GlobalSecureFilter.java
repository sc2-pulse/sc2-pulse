// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Removes cache from protected responses in the global scope.
 */
@Component
@WebFilter({"/**"}) @Order()
public class GlobalSecureFilter
extends AbstractSecureCacheFilter
{

    @Override
    public void setCache(HttpServletResponse resp){}

}
