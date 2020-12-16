// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.util;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SimpleBookmarkedResultSetExtractor<T>
implements ResultSetExtractor<BookmarkedResult<List<T>>>
{

    private final RowMapper<T> rowMapper;
    private final String[] bookmarkParams;

    public SimpleBookmarkedResultSetExtractor(RowMapper<T> rowMapper, String... bookmarkParams)
    {
        this.rowMapper = rowMapper;
        this.bookmarkParams = bookmarkParams;
    }

    @Override
    public BookmarkedResult<List<T>> extractData(ResultSet rs)
    throws SQLException, DataAccessException
    {
        List<T> list = new ArrayList<>();
        Long[] bookmark = new Long[bookmarkParams.length];
        while(rs.next())
        {
            list.add(rowMapper.mapRow(rs, 0));
            for(int i = 0; i < bookmarkParams.length; i++) bookmark[i] = rs.getLong(bookmarkParams[i]);
        }
        return new BookmarkedResult<>(list, bookmark);
    }

}
