// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TeamStateArchiveDAO
{

    private static final String ARCHIVE =
        """
        WITH archive_group AS
        (
            SELECT DISTINCT ON(team_id)
            team_id,
            first_value(timestamp) OVER team_rating_window as rating_min_ts,
            last_value(timestamp) OVER team_rating_window as rating_max_ts,
            last_value(timestamp) OVER team_timestamp_window as timestamp_last_ts
            FROM team_state
            WHERE team_id IN(:teamIds)
            WINDOW team_rating_window AS
            (
                PARTITION BY team_id
                ORDER BY rating ASC
                ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
            ),
            team_timestamp_window AS
            (
                PARTITION BY team_id
                ORDER BY timestamp ASC
                ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
            )
            ORDER BY team_id
        ),
        archive AS
        (
            SELECT DISTINCT ON(archive.team_id, archive.timestamp)
            archive.team_id, archive.timestamp
            FROM archive_group
            CROSS JOIN LATERAL
            (
                VALUES(archive_group.team_id, archive_group.rating_min_ts),
                (archive_group.team_id, archive_group.rating_max_ts),
                (archive_group.team_id, archive_group.timestamp_last_ts)
            )
            AS archive(team_id, timestamp)
            ORDER BY archive.team_id, archive.timestamp
        )
        INSERT INTO team_state_archive(team_id, timestamp)
        SELECT team_id, timestamp FROM archive
        """;

    private static final String DELETE_ARCHIVE =
        "DELETE FROM team_state_archive WHERE team_id IN(:teamIds)";

    private final NamedParameterJdbcTemplate template;

    @Autowired
    public TeamStateArchiveDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template
    )
    {
        this.template = template;
    }

    public int archive(Set<Long> teamIds)
    {
        if(teamIds.isEmpty()) return 0;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("teamIds", teamIds);
        return template.update(ARCHIVE, params);
    }

    public int delete(Set<Long> teamIds)
    {
        if(teamIds.isEmpty()) return 0;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("teamIds", teamIds);
        return template.update(DELETE_ARCHIVE, params);
    }

}
