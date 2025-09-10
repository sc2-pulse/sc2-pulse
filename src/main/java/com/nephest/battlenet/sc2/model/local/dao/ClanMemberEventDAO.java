// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;


import com.nephest.battlenet.sc2.model.SortingOrder;
import com.nephest.battlenet.sc2.model.local.ClanMemberEvent;
import com.nephest.battlenet.sc2.model.navigation.Cursor;
import com.nephest.battlenet.sc2.model.navigation.CursorUtil;
import com.nephest.battlenet.sc2.model.navigation.NavigationDirection;
import com.nephest.battlenet.sc2.model.navigation.Position;
import com.nephest.battlenet.sc2.model.validation.CursorNavigableResult;
import com.nephest.battlenet.sc2.model.validation.Version;
import jakarta.validation.Valid;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

@Repository
@Validated
public class ClanMemberEventDAO
{

    public static final String STD_SELECT =
        "clan_member_event.player_character_id AS \"clan_member_event.player_character_id\", "
        + "clan_member_event.clan_id AS \"clan_member_event.clan_id\", "
        + "clan_member_event.type AS \"clan_member_event.type\", "
        + "clan_member_event.created AS \"clan_member_event.created\", "
        + "clan_member_event.seconds_since_previous AS \"clan_member_event.seconds_since_previous\" ";

    private static final String MERGE =
        "WITH vals AS (VALUES :data), "
        + "previous AS "
        + "("
            + "SELECT v.*, "
            + "prev.clan_id AS \"previous_clan_id\", "
            + "prev.type AS \"previous_type\", "
            + "prev.created AS \"previous_created\" "
            + "FROM vals v(player_character_id, type, created, clan_id) "
            + "LEFT JOIN LATERAL "
            + "("
                + "SELECT clan_member_event.clan_id, "
                + "clan_member_event.type, "
                + "clan_member_event.created "
                + "FROM clan_member_event "
                + "WHERE clan_member_event.player_character_id = v.player_character_id "
                + "ORDER BY clan_member_event.player_character_id DESC, clan_member_event.created DESC "
                + "LIMIT 1"
            + ") prev ON true "
        + "), "
        + "inject_leave AS "
        + "("
            + "INSERT INTO clan_member_event(player_character_id, clan_id, type, created, seconds_since_previous) "
            + "SELECT player_character_id, previous_clan_id, 0, created - INTERVAL '1 millisecond', "
            + "EXTRACT(EPOCH FROM (created - INTERVAL '1 millisecond') - previous_created) "
            + "FROM previous "
            + "WHERE previous_type = 1 "
            + "AND type = 1 "
            + "AND clan_id::integer != previous_clan_id "
            + "RETURNING player_character_id "
        + ") "
        + "INSERT INTO clan_member_event(player_character_id, clan_id, type, created, seconds_since_previous) "
        + "SELECT player_character_id, "
        + "CASE WHEN clan_id::integer IS NOT NULL THEN clan_id::integer ELSE previous_clan_id END, "
        + "type, "
        + "created, "
        + "CASE "
            + "WHEN "
                + "previous_type = 1 "
                + "AND type = 1 "
                + "AND clan_id::integer != previous_clan_id "
            + "THEN 0 "
            + "ELSE EXTRACT(EPOCH FROM created - previous_created) "
        + "END "
        + "FROM previous "
        + "LEFT JOIN inject_leave USING(player_character_id) "
        + "WHERE "
        + "CASE "
            + "WHEN inject_leave.player_character_id IS NULL "
            + "THEN previous_type "
            + "ELSE 0 "
        + "END != type "
        + "OR (previous_clan_id IS NULL AND type = 1)";

    private static final String CURSOR_TEMPLATE =
        """
        (
            (
                :createdCursor::timestamp with time zone IS NULL
                AND :playerCharacterIdCursor::bigint IS NULL
            )
            OR (created, player_character_id) %1$s (:createdCursor, :playerCharacterIdCursor)
        )
       """;
    /*
        It seems that an additional filter can be applied here: select UID instead of STD_SELECT
        and then do limited STD_SELECT from the joined table.
        In practice, the queries that will be executed will rarely benefit from this optimization,
        so it only leads to redundant index scan in most cases.
     */
    private static final String FIND_TEMPLATE =
        "("
            + "SELECT " + STD_SELECT
            + "FROM clan_member_event "
            + "WHERE "
            + CURSOR_TEMPLATE
            + "AND (array_length(:playerCharacterIds::integer[], 1) IS NOT NULL "
            + "AND player_character_id = ANY(:playerCharacterIds)) "
            + "ORDER BY created %2$s, player_character_id %2$s "
            + "LIMIT :limit "
        + ") "

        + "UNION "

        + "("
            + "SELECT " + STD_SELECT
            + "FROM clan_member_event "
            + "WHERE "
            + CURSOR_TEMPLATE
            + "AND (array_length(:clanIds::integer[], 1) IS NOT NULL "
            + "AND clan_id = ANY(:clanIds)) "
            + "ORDER BY created %2$s, player_character_id %2$s "
            + "LIMIT :limit "
        + ") "

        + "ORDER BY \"clan_member_event.created\" %2$s, "
        + "\"clan_member_event.player_character_id\" %2$s "
        + "LIMIT :limit";

    private static RowMapper<ClanMemberEvent> STD_ROW_MAPPER;

    public static final long CURSOR_POSITION_VERSION = 1L;

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    @Autowired
    public ClanMemberEventDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        initMappers(conversionService);
    }

    private void initMappers(ConversionService conversionService)
    {
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, i)->new ClanMemberEvent
        (
            rs.getLong("clan_member_event.player_character_id"),
            rs.getInt("clan_member_event.clan_id"),
            conversionService.convert(rs.getInt("clan_member_event.type"), ClanMemberEvent.EventType.class),
            rs.getObject("clan_member_event.created", OffsetDateTime.class),
            DAOUtils.getInteger(rs, "clan_member_event.seconds_since_previous")
        );
    }

    public static RowMapper<ClanMemberEvent> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    public int merge(Set<ClanMemberEvent> events)
    {
        if(events.isEmpty()) return 0;

        Set<Long> characters = new HashSet<>(events.size());
        List<Object[]> data = events.stream()
            .filter(e->characters.add(e.getPlayerCharacterId()))
            .map(evt->new Object[]{
                evt.getPlayerCharacterId(),
                conversionService.convert(evt.getType(), Integer.class),
                evt.getCreated(),
                evt.getClanId() == null ? null : String.valueOf(evt.getClanId()),
            })
            .collect(Collectors.toList());
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("data", data);
        return template.update(MERGE, params);
    }

    public List<ClanMemberEvent> find
    (
        Set<Long> playerCharacterIds,
        Set<Integer> clanIds,
        OffsetDateTime createdCursor,
        Long playerCharacterIdCursor,
        int limit
    )
    {
        if((playerCharacterIds.isEmpty() && clanIds.isEmpty()) || limit < 1) return List.of();

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("playerCharacterIds", playerCharacterIds.toArray(Long[]::new))
            .addValue("clanIds", clanIds.toArray(Integer[]::new))
            .addValue("createdCursor", createdCursor)
            .addValue("playerCharacterIdCursor", playerCharacterIdCursor)
            .addValue("limit", limit);
        return template.query(FIND_TEMPLATE.formatted("<", "DESC"), params, STD_ROW_MAPPER);
    }

    public CursorNavigableResult<List<ClanMemberEvent>> find
    (
        Set<Long> playerCharacterIds,
        Set<Integer> clanIds,
        @Valid @Version(CURSOR_POSITION_VERSION) Cursor cursor,
        int limit
    )
    {
        if((playerCharacterIds.isEmpty() && clanIds.isEmpty()) || limit < 1)
            return CursorNavigableResult.emptyList();

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("playerCharacterIds", playerCharacterIds.toArray(Long[]::new))
            .addValue("clanIds", clanIds.toArray(Integer[]::new))
            .addValue
            (
                "createdCursor",
                cursor != null
                    ? OffsetDateTime.parse((String) cursor.position().anchor().get(0))
                    : null,
                Types.TIMESTAMP_WITH_TIMEZONE
            )
            .addValue
            (
                "playerCharacterIdCursor",
                cursor != null ? cursor.position().anchor().get(1) : null,
                Types.BIGINT
            )
            .addValue("limit", limit);

        NavigationDirection direction = cursor != null
            ? cursor.direction()
            : NavigationDirection.FORWARD;
        String q = CursorUtil.formatCursorNavigableQuery
        (
            FIND_TEMPLATE,
            SortingOrder.DESC,
            direction,
            false
        );
        List<ClanMemberEvent> events = template.query(q, params, STD_ROW_MAPPER);
        if(direction == NavigationDirection.BACKWARD) Collections.reverse(events);
        return CursorNavigableResult.wrap
        (
            events,
            limit,
            cursor == null,
            ClanMemberEventDAO::createCursorPosition
        );
    }

    public static Position createCursorPosition(OffsetDateTime createdCursor, long characterId)
    {
        return new Position
        (
            CURSOR_POSITION_VERSION,
            List.of(createdCursor.toString(), characterId)
        );
    }

    public static Position createCursorPosition(ClanMemberEvent event)
    {
        if(event == null) return null;

        return createCursorPosition(event.getCreated(), event.getPlayerCharacterId());
    }

}
