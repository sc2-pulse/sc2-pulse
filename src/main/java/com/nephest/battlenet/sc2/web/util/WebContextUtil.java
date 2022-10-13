// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.servlet.ServletContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class WebContextUtil
{

    private final String publicUrl;

    @Autowired
    public WebContextUtil(Environment env, ServletContext ctx)
    throws UnknownHostException
    {
        String publicUrl = env.getProperty("com.nephest.battlenet.sc2.url.public");
        if(publicUrl != null)
        {
            this.publicUrl = publicUrl;
        }
        else
        {
            String port = env.getProperty("local.server.port");
            if(port == null) port = env.getProperty("server.port");
            this.publicUrl = "http://"
                + InetAddress.getLocalHost().getHostAddress()
                + ":" + port
                + (ctx.getContextPath().isEmpty() ? "/" : ctx.getContextPath());
        }
    }

    /**
     * Returns {@code com.nephest.battlenet.sc2.url.public} application property.
     * If the property is missing, then it constructs default URL using:
     * <ul>
     *     <li>HTTP protocol</li>
     *     <li>{@link InetAddress#getLocalHost()}.{@link InetAddress#getHostAddress()
     *     getHostAddress()} as host</li>
     *     <li>{@code local.server.port} or {@code server.port} application property as port</li>
     *     <li>{@link ServletContext#getContextPath()} as context path</li>
     * </ul>
     * <p>
     *     This is useful when you are behind a proxy and don't have access to web or servlet
     *     context(i.e. Discord)
     * </p>
     * @return public url
     */
    public String getPublicUrl()
    {
        return publicUrl;
    }

}
