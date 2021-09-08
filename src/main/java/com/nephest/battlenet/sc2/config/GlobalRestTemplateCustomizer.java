// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

public class GlobalRestTemplateCustomizer
implements RestTemplateCustomizer
{

    private int timeout = -1;

    public GlobalRestTemplateCustomizer(){}

    public GlobalRestTemplateCustomizer(int timeout)
    {
        this.timeout = timeout;
    }

    @Override
    public void customize(RestTemplate restTemplate)
    {
        SimpleClientHttpRequestFactory factory = restTemplate.getRequestFactory() instanceof SimpleClientHttpRequestFactory
            ? (SimpleClientHttpRequestFactory) restTemplate.getRequestFactory()
            : new SimpleClientHttpRequestFactory();
        if(timeout == -1)
        {
            factory.setConnectTimeout((int) WebServiceUtil.CONNECT_TIMEOUT.toMillis());
            factory.setReadTimeout((int) WebServiceUtil.IO_TIMEOUT.toMillis());
        }
        else
        {
            factory.setConnectTimeout(timeout);
            factory.setReadTimeout(timeout);
        }
        restTemplate.setRequestFactory(factory);
    }

}
