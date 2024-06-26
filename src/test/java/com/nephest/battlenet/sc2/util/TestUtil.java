// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public final class TestUtil
{

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
    public static final ScheduledExecutorService EXECUTOR_SERVICE = Executors.newScheduledThreadPool(4);

    static
    {
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static Comparator<OffsetDateTime> UPDATED_IS_AFTER_ASSERTION_COMPARATOR =
        (l, r)->l.isAfter(r) ? 0 : 1;

    private TestUtil(){}

    public static void testUniqueness(Object object, Object equalObject, Object... notEqualObjects)
    {
        assertEquals(object, equalObject);
        assertEquals(object.hashCode(), equalObject.hashCode());
        assertEquals(object.toString(), equalObject.toString());

        for (Object notEqualObject : notEqualObjects)
        {
            assertNotEquals(object, notEqualObject);
            assertNotEquals(object.hashCode(), notEqualObject.hashCode());
            assertNotEquals(object.toString(), notEqualObject.toString());
        }
    }

    public static String readResource(Class<?> loader, String path, Charset charset)
    throws URISyntaxException, IOException
    {
        return Files.readString
        (
            Paths.get(loader.getClassLoader().getResource(path).toURI()),
            charset
        );
    }

    public static String readResource(Class<?> loader, String path)
    throws URISyntaxException, IOException
    {
        return readResource(loader, path, DEFAULT_CHARSET);
    }

    public static <T> T readResource
    (
        Class<?> loader,
        String path,
        Charset charset,
        Class<T> targetClass
    )
    throws URISyntaxException, IOException
    {
        return OBJECT_MAPPER.readValue(readResource(loader, path, charset), targetClass);
    }

    public static <T> T readResource(Class<?> loader, String path, Class<T> targetClass)
    throws URISyntaxException, IOException
    {
        return readResource(loader, path, DEFAULT_CHARSET, targetClass);
    }

}
