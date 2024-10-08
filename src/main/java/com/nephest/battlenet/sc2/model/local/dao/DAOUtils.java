// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public final class DAOUtils
{

    private static final Logger LOG = LoggerFactory.getLogger(DAOUtils.class);

    public static final String REMOVE_OUTDATED_TEMPLATE = "DELETE FROM %1$s WHERE %2$s < NOW() - INTERVAL '%3$s'";
    public static final int[] EMPTY_INT_ARRAY = new int[0];

    private DAOUtils(){}

    public static <T> ResultSetExtractor<T> getResultSetExtractor(RowMapper<T> rowMapper)
    {
        return (rs)->
        {
            if(!rs.next()) return null;
            return rowMapper.mapRow(rs, 1);
        };
    }

    public static final ResultSetExtractor<Long> LONG_EXTRACTOR =
    (rs)->
    {
        if(!rs.next()) return null;
        long val = rs.getLong(1);
        return rs.wasNull() ? null : val;
    };

    public static ResultSetExtractor<Long[]> LONG_PAIR_EXTRACTOR =
        arrayExtractor
        (
            (rs, ix)->{long l = rs.getLong(ix); return rs.wasNull() ? null : l;},
            Long[]::new,
            2
        );

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

    public static final RowMapper<Integer> INT_MAPPER =
    (rs, ix)->
    {
        Integer val = rs.getInt(1);
        return rs.wasNull() ? null : val;
    };

    public static <T> ResultSetExtractor<T[]> arrayExtractor
    (
        ColumnMapper<T> mapper,
        IntFunction<T[]> arrayFunction,
        int length
    )
    {
        return (rs)->
        {
            T[] result = arrayFunction.apply(length);
            if(!rs.next()) return result;

            for(int i = 0; i < result.length; i++) result[i] = mapper.mapColumn(rs, i + 1);
            return result;
        };
    }

    public static <T> Set<T> updateOriginals
    (
        Set<T> originalSet,
        List<T> mergedList,
        BiConsumer<T, T> originalUpdater,
        Consumer<T> originalNullifier
    )
    {
        if(originalNullifier != null) for(T t : originalSet) originalNullifier.accept(t);
        if(mergedList.isEmpty()) return originalSet;

        Map<T, T> mergedMap = mergedList.stream()
            .collect(Collectors.toMap(Function.identity(), Function.identity()));
        for(T original : originalSet)
        {
            T merged = mergedMap.get(original);
            if(merged == null)
            {
                if(originalNullifier == null) throw new IllegalStateException("Pair didn't match");
            }
            else
            {
                originalUpdater.accept(original, merged);
            }
        }
        return originalSet;
    }

    public static <T> Set<T> updateOriginals
    (Set<T> originalSet, List<T> mergedList, BiConsumer<T, T> originalUpdater)
    {
        return updateOriginals(originalSet, mergedList, originalUpdater, null);
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

    /**
     * This method is useful when converting arbitrary collections to {@link java.util.Set}
     * before passing it to DAO layer. It covers a typical scenario where collisions are
     * considered error.
     *
     * @param list Source list that should not contain any duplicates
     * @return {@link java.util.Set} that contains all items from the supplied {@code list}
     * @throws IllegalArgumentException if supplied {@code list} contains duplicates
     */
    public static <T> Set<T> toCollisionFreeSet(Collection<T> list)
    {
        Set<T> set = new HashSet<>(list);
        if(list.size() != set.size())
            throw new IllegalArgumentException("Collision detected");
        return set;
    }

}
