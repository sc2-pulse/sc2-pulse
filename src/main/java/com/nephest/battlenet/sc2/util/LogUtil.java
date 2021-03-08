// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.util;

import org.slf4j.Logger;

public final class LogUtil
{

    public enum LogLevel
    {
        TRACE, DEBUG, INFO, WARNING, ERROR
    }

    private LogUtil(){}

    public static void log(Logger log, LogLevel level, String template, Object... params)
    {
        switch (level)
        {
            case TRACE: log.trace(template, params); break;
            case DEBUG: log.debug(template, params); break;
            case INFO: log.info(template, params); break;
            case WARNING: log.warn(template, params); break;
            case ERROR: log.error(template, params); break;
        }
    }

}
