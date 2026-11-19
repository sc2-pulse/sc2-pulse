// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;

public class DbTestUtil
{

    public static final Duration LOCK_WAIT_TIMEOUT = Duration.ofSeconds(10);

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

    /**
     * Waits until any transaction waits for a lock. The provided connection
     * can be the one that holds the lock, inside its open transaction:
     * {@code pg_locks} reflects the current lock state on every query, unlike
     * {@code pg_stat_activity}, which is cached within a transaction.
     *
     * @param connection connection to query {@code pg_locks} with
     * @throws IllegalStateException if no transaction waits for a lock
     * within {@link #LOCK_WAIT_TIMEOUT}
     */
    public static void waitForLock(Connection connection)
    throws InterruptedException, SQLException
    {
        OffsetDateTime deadline = SC2Pulse.offsetDateTime()
            .plus(LOCK_WAIT_TIMEOUT);
        while(countWaitingLocks(connection) == 0)
        {
            if(SC2Pulse.offsetDateTime().isAfter(deadline))
                throw new IllegalStateException("Lock wait timeout");
            Thread.sleep(50);
        }
    }

    private static int countWaitingLocks(Connection connection)
    throws SQLException
    {
        try
        (
            PreparedStatement statement = connection.prepareStatement
            (
                """
                    SELECT COUNT(*)
                    FROM pg_locks
                    WHERE NOT granted
                """
            );
            ResultSet resultSet = statement.executeQuery()
        )
        {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

}
