// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class DAOUtils
{

    private static final Logger LOG = LoggerFactory.getLogger(DAOUtils.class);

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

    public static final ResultSetExtractor<OffsetDateTime> OFFSET_DATE_TIME_RESULT_SET_EXTRACTOR =
    (rs)->
    {
        if(!rs.next()) return null;
        return rs.getObject(1, OffsetDateTime.class);
    };

    public static final RowMapper<Long> LONG_MAPPER =
    (rs, ix)->
    {
        Long val = rs.getLong(1);
        return rs.wasNull() ? null : val;
    };

    public static <T>  T[] updateOriginals
    (
        T[] originalArray,
        List<T> mergedList,
        Comparator<T> comparator, BiConsumer<T, T> originalUpdater,
        Consumer<T> originalNullifier
    )
    {
        if(originalNullifier != null) for(T t : originalArray) originalNullifier.accept(t);
        if(mergedList.isEmpty()) return originalArray;

        Arrays.sort(originalArray, comparator);
        mergedList.sort(comparator);
        for(int originalIx = 0, mergedIx = 0; originalIx < originalArray.length; originalIx++)
        {
            //merged list can be smaller than original arrays due to original clones, reuse the max ix
            if(mergedIx >= mergedList.size()) mergedIx = mergedList.size() - 1;
            T original = originalArray[originalIx];
            T merged = mergedList.get(mergedIx);

            //if there are multiple clones in the original array
            if(!merged.equals(original))
            {
                //for nullables, if it's not a clone, than it's a null entity, skip it
                if(originalNullifier != null && mergedIx == 0) continue;
                //the merged list contains one entity for multiple original clones, use it repeatedly
                mergedIx--;
                merged = mergedList.get(mergedIx);
                if(!merged.equals(original))
                {
                    //for nullables, if it's not a clone, than it's a null entity, skip it, ++ to offset the --
                    if(originalNullifier != null)
                    {
                        mergedIx++;
                        continue;
                    }
                    //this can happen only if db query is invalid
                    throw new IllegalStateException("Pair didn't match");
                }
            }
            originalUpdater.accept(original, merged);
            mergedIx++;
        }

        return originalArray;
    }

    public static <T>  T[] updateOriginals
    (T[] originalArray, List<T> mergedList, Comparator<T> comparator, BiConsumer<T, T> originalUpdater)
    {
        return updateOriginals(originalArray, mergedList, comparator, originalUpdater, null);
    }

    public static <T> Predicate<T> beanValidationPredicate(Validator validator){
        return o->
        {
            Errors errors = new BeanPropertyBindingResult(o, o.toString());
            validator.validate(o, errors);
            boolean result = !errors.hasErrors();
            if(!result) LOG.debug("{}", errors);
            return result;
        };
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

    public static Double getDouble(ResultSet rs, String param)
    throws SQLException
    {
        double i = rs.getDouble(param);
        return rs.wasNull() ? null : i;
    }

    public static Float getFloat(ResultSet rs, String param)
    throws SQLException
    {
        float i = rs.getFloat(param);
        return rs.wasNull() ? null : i;
    }

    public static Boolean getBoolean(ResultSet rs, String param)
    throws SQLException
    {
        boolean i = rs.getBoolean(param);
        return rs.wasNull() ? null : i;
    }

    public static Byte getByte(ResultSet rs, String param)
    throws SQLException
    {
        byte i = rs.getByte(param);
        return rs.wasNull() ? null : i;
    }

    public static <T> T getConvertedObjectFromInteger
    (ResultSet rs, String param, ConversionService conversionService, Class<T> clazz)
    throws SQLException
    {
        int i = rs.getInt(param);
        return rs.wasNull() ? null : conversionService.convert(i, clazz);
    }

}
