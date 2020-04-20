package com.nephest.battlenet.sc2.model.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PostgreSQLUtilsTest
{

    @Test
    public void testEscapeLike()
    {
        assertEquals("\\\\asd\\%\\\\\\%\\_", PostgreSQLUtils.escapeLikePattern("\\asd%\\%_"));
    }

}
