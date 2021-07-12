// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;

public final class DAOUtils
{

    public static final String REMOVE_OUTDATED_TEMPLATE = "DELETE FROM %1$s WHERE %2$s < NOW() - INTERVAL '%3$s'";
    public static final int[] EMPTY_INT_ARRAY = new int[0];

    private DAOUtils(){}

    public static final ResultSetExtractor<Long> LONG_EXTRACTOR =
    (rs)->
    {
        if(!rs.next()) return null;
        long val = rs.getLong(1);
        return rs.wasNull() ? null : val;
    };

    public static final ResultSetExtractor<Integer> INT_EXTRACTOR =
    (rs)->
    {
        if(!rs.next()) return null;
        int val = rs.getInt(1);
        return rs.wasNull() ? null : val;
    };

    public static final ResultSetExtractor<String> STRING_EXTRACTOR =
    (rs)->
    {
        if(!rs.next()) return null;
        return rs.getString(1);
    };

    public static final RowMapper<Long> LONG_MAPPER =
    (rs, ix)->
    {
        Long val = rs.getLong(1);
        return rs.wasNull() ? null : val;
    };

    public static <T>  T[] updateOriginals
    (T[] originalArray, List<T> mergedList, Comparator<T> comparator, BiConsumer<T, T> originalUpdater)
    {
        Arrays.sort(originalArray, comparator);
        mergedList.sort(comparator);
        for(int originalIx = 0, mergedIx = 0; originalIx < originalArray.length; originalIx++)
        {
            if(mergedIx >= mergedList.size()) mergedIx = mergedList.size() - 1; //should rarely happen
            T original = originalArray[originalIx];
            T merged = mergedList.get(mergedIx);

            if(!merged.equals(original)) //should rarely happen
            {
                mergedIx--;
                merged = mergedList.get(mergedIx);
                if(!merged.equals(original)) throw new IllegalStateException("Pair didn't match");
            }
            originalUpdater.accept(original, merged);
            mergedIx++;
        }

        return originalArray;
    }

    public static Integer getInteger(ResultSet rs, String param)
    throws SQLException
    {
        int i = rs.getInt(param);
        return rs.wasNull() ? null : i;
    }

    public static Long getLong(ResultSet rs, String param)
    throws SQLException
    {
        long i = rs.getLong(param);
        return rs.wasNull() ? null : i;
    }

}
