/*-
 * =========================LICENSE_START=========================
 * SC2 Ladder Generator
 * %%
 * Copyright (C) 2020 Oleksandr Masniuk
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END=========================
 */
package com.nephest.battlenet.sc2.config;

import java.util.*;

import javax.sql.DataSource;
import javax.annotation.*;

import org.springframework.context.annotation.*;
import org.springframework.context.support.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.format.support.*;
import org.springframework.transaction.annotation.*;
import org.springframework.core.convert.*;
import org.springframework.core.convert.support.*;
import org.springframework.core.convert.converter.Converter;

import com.nephest.battlenet.sc2.config.convert.*;

@Configuration
@ComponentScan("com.nephest.battlenet.sc2")
@EnableTransactionManagement
public class ApplicationConfig
{

    @Resource(name="sc2StatsDataSource")
    private DataSource ds;

    @Bean
    public NamedParameterJdbcTemplate sc2StatsNamedTemplate()
    {
        return new NamedParameterJdbcTemplate(ds);
    }

    @Bean
    public ConversionService sc2StatsConversionService()
    {
        DefaultFormattingConversionService service = new DefaultFormattingConversionService();
        service.addConverter(new IdentifiableToIntegerConverter());
        service.addConverter(new IntegerToQueueTypeConverter());
        service.addConverter(new IntegerToLeagueTierTypeConverter());
        service.addConverter(new IntegerToLeagueTypeConverter());
        service.addConverter(new IntegerToRegionConverter());
        service.addConverter(new IntegerToTeamTypeConverter());
        return service;
    }

}
