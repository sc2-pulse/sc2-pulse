// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.PulseSeason;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Pulse seasons are canonical season keys for (year, number) pairs. Region
 * seasons reference them, and Pulse-internal season ids, e.g. team.season,
 * are pulse season ids.
 */
@Repository
public class PulseSeasonDAO
{

    private static final String FIND_ID_BY_YEAR_AND_NUMBER =
        """
            SELECT id
            FROM pulse_season
            WHERE year = :year
            AND number = :number
        """;

    /*
        Ids are ordered by (year, number). The newest season id is max + 1,
        but never lower than the hint. This keeps pulse season ids equal to
        the global Blizzard season ids. Older seasons use the hint if it fits
        between neighbor ids. The first season uses the hint as is.
        GREATEST ignores NULL prev_id.
     */
    private static final String MERGE =
        """
            WITH
            existing AS
            (
                %1$s
            ),
            neighbor AS
            (
                SELECT
                (
                    SELECT MAX(id)
                    FROM pulse_season
                    WHERE (year, number) < (:year, :number)
                ) AS prev_id,
                (
                    SELECT MIN(id)
                    FROM pulse_season
                    WHERE (year, number) > (:year, :number)
                ) AS next_id
            ),
            candidate AS
            (
                SELECT
                    CASE
                        WHEN next_id IS NULL
                            THEN GREATEST(prev_id + 1, :idHint)
                        ELSE :idHint
                    END AS id,
                    prev_id,
                    next_id
                FROM neighbor
            ),
            inserted AS
            (
                INSERT INTO pulse_season(id, year, number)
                SELECT id, :year, :number
                FROM candidate
                WHERE NOT EXISTS(SELECT 1 FROM existing)
                AND (prev_id IS NULL OR id > prev_id)
                AND (next_id IS NULL OR id < next_id)
                ON CONFLICT DO NOTHING
                RETURNING id
            )
            SELECT id FROM existing
            UNION ALL
            SELECT id FROM inserted
        """.formatted(FIND_ID_BY_YEAR_AND_NUMBER);

    private final NamedParameterJdbcTemplate template;

    @Autowired
    public PulseSeasonDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template
    )
    {
        this.template = template;
    }

    public Optional<Integer> findId(int year, int number)
    {
        return Optional.ofNullable(template.query(
            FIND_ID_BY_YEAR_AND_NUMBER,
            createParameterSource(year, number),
            DAOUtils.INT_EXTRACTOR
        ));
    }

    /**
     * Finds or creates a pulse season.
     *
     * @param year season year
     * @param number season number
     * @param idHint preferred id of a new pulse season, typically a Blizzard
     * season id. It's the minimum id of the newest season.
     * @return merged pulse season
     * @throws IllegalStateException if a new season can't be created without
     * breaking the (year, number) order of ids, or if its id was taken by a
     * concurrent transaction
     */
    public PulseSeason merge(int year, int number, int idHint)
    {
        MapSqlParameterSource params = createParameterSource(year, number)
            .addValue("idHint", idHint);
        Integer id = template.query(MERGE, params, DAOUtils.INT_EXTRACTOR);
        /*
            Concurrent inserts. The statement snapshot doesn't see pulse
            seasons that are inserted by concurrent transactions.
            * Same (year, number). The insert waits for the concurrent
            transaction. If it commits, ON CONFLICT DO NOTHING skips the
            insert and the statement returns nothing. The next statement has
            a new snapshot(READ COMMITTED), so the committed id is found here.
            If it rolls back, the insert proceeds as usual.
            * Different (year, number), same candidate id, e.g. two new newest
            seasons. The second insert is skipped and there is no id, so it
            fails with an exception below. The caller's transaction is rolled
            back and can be retried, the retry gets the next id.
         */
        if(id == null) id = findId(year, number).orElse(null);
        if(id == null) throw new IllegalStateException
        (
            "Can't create pulse season, it's out of order or its id was taken"
                + " by a concurrent transaction: "
                + year + " " + number + " " + idHint
        );
        return new PulseSeason(id, year, number);
    }

    private static MapSqlParameterSource createParameterSource
    (
        int year,
        int number
    )
    {
        return new MapSqlParameterSource()
            .addValue("year", year)
            .addValue("number", number);
    }

}
