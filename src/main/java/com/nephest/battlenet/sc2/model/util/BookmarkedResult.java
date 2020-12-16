// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.util;

public class BookmarkedResult<T>
{

    private final T result;
    private final Long[] bookmark;

    public BookmarkedResult(T result, Long[] bookmark)
    {
        this.result = result;
        this.bookmark = bookmark;
    }

    public T getResult()
    {
        return result;
    }

    public Long[] getBookmark()
    {
        return bookmark;
    }

}
