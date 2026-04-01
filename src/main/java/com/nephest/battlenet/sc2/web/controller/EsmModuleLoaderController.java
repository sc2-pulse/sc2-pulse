// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.config.filter.NoCacheFilter;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.ResourceUrlProvider;

@RestController
@Hidden
@RequestMapping("/static/script/shared")
public class EsmModuleLoaderController
{

    @Autowired
    private ResourceUrlProvider urlProvider;

    private String sc2pulseUtilLoader;

    @EventListener(ApplicationReadyEvent.class)
    public void init()
    {
        sc2pulseUtilLoader = getLoader(urlProvider, "sc2pulse-util.min.js");
    }

    private static String getLoader(ResourceUrlProvider urlProvider, String fileName)
    {
        String path = "/static/script/shared/" + fileName;
        String hashedPath = urlProvider.getForLookupPath(path);
        if(hashedPath == null) throw new IllegalStateException("Resource URL not found: " + path);

        String versionedFileName = hashedPath.substring(hashedPath.lastIndexOf('/') + 1);
        if(versionedFileName.equals(fileName))
            throw new IllegalArgumentException("Resource chain versioning is disabled");

        return "export * from './" + versionedFileName + "';";
    }

    @GetMapping(value = "sc2pulse-util-loader.js", produces = "application/javascript")
    public ResponseEntity<String> getSc2PulseUtilLoader()
    {
        ResponseEntity.BodyBuilder bb = ResponseEntity.ok();
        for(Map.Entry<String, String> entry : NoCacheFilter.NO_CACHE_HEADERS.entrySet())
            bb = bb.header(entry.getKey(), entry.getValue());
        return bb.body(sc2pulseUtilLoader);
    }

}
