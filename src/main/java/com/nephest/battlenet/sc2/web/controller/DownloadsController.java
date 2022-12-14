// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.config.filter.NoCacheFilter;
import io.swagger.v3.oas.annotations.Hidden;
import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController @Hidden
@RequestMapping("/dl")
public class DownloadsController
{

    @Value("${com.nephest.battlenet.sc2.db-dump-file:#{''}}")
    private String dbDumpPath;

    @GetMapping("/db-dump")
    public ResponseEntity<Resource> downloadDatabaseDump()
    {
        if(dbDumpPath.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
        File file = new File(dbDumpPath);
        if(!file.exists()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename="
            + URLEncoder.encode(file.getName(), StandardCharsets.UTF_8));
        NoCacheFilter.NO_CACHE_HEADERS.forEach(headers::add);

        return ResponseEntity.ok()
            .headers(headers)
            .contentLength(file.length())
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(new FileSystemResource(file));
    }

}
