// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UnsignedLongSerializerTest
{

    private UnsignedLongSerializer serializer;

    @BeforeEach
    public void beforeEach()
    {
        serializer = new UnsignedLongSerializer(Long.class);
    }

    @Test
    public void testSerialize()
    throws IOException
    {
        JsonGenerator generator = mock(JsonGenerator.class);
        String longStr = "9223372036854775808";
        serializer.serialize(Long.parseUnsignedLong(longStr), generator, null);
        verify(generator).writeString(longStr);
    }

}
