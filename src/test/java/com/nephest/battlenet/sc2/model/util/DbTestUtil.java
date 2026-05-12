// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Statement;
import java.util.Arrays;

public class DbTestUtil
{

    public static boolean isBatchUpdateValid(int rowsChanged, int expected)
    {
        return rowsChanged == expected || rowsChanged == Statement.SUCCESS_NO_INFO;
    }

    public static boolean isBatchUpdateValid(int rowsChanged)
    {
        return isBatchUpdateValid(rowsChanged, 1);
    }

    public static void assertBatchUpdate(int[] rowsChanged, int[] expected)
    {
        if(rowsChanged.length != expected.length)
            throw new IllegalArgumentException("Arrays must be of equal length");

        for(int i = 0; i < rowsChanged.length; i++)
        {
            int finalI = i;
            assertTrue
            (
                isBatchUpdateValid(rowsChanged[i], expected[i]),
                ()->"Invalid batch update: "
                    + "changed=" + Arrays.toString(rowsChanged) + ", "
                    + "expected=" + Arrays.toString(expected) + ", "
                    + "i=" + finalI
            );
        }
    }

    public static void assertBatchUpdate(int[] rowsChanged)
    {
        int[] expected = new int[rowsChanged.length];
        Arrays.fill(expected, 1);
        assertBatchUpdate(rowsChanged, expected);
    }

}
