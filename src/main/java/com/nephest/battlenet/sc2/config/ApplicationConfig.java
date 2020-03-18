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

import javax.annotation.Resource;
import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.nephest.battlenet.sc2.config.convert.IdentifiableToIntegerConverter;
import com.nephest.battlenet.sc2.config.convert.IntegerToLeagueTierTypeConverter;
import com.nephest.battlenet.sc2.config.convert.IntegerToLeagueTypeConverter;
import com.nephest.battlenet.sc2.config.convert.IntegerToQueueTypeConverter;
import com.nephest.battlenet.sc2.config.convert.IntegerToRegionConverter;
import com.nephest.battlenet.sc2.config.convert.IntegerToTeamTypeConverter;

@Configuration
@ComponentScan("com.nephest.battlenet.sc2")
@EnableTransactionManagement
@EnableScheduling
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
