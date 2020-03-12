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

import org.springframework.stereotype.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.scheduling.annotation.*;

import com.nephest.battlenet.sc2.web.service.*;
import com.nephest.battlenet.sc2.model.local.dao.*;

@Component
public class Cron
{

    @Autowired
    private StatsService statsService;

    @Autowired
    private AccountDAO accountDao;

    @Scheduled(cron="0 0 3 * * *")
    public void updateCurrentSeason()
    {
        statsService.updateCurrent();
    }

    @Scheduled(cron="0 0 5 * * SAT")
    public void updateAllSeasons()
    {
        statsService.updateAll();
    }

    @Scheduled(cron="45 6 0 * * *")
    public void removeExpiredPrivacy()
    {
        accountDao.removeExpiredByPrivacy();
    }

}
