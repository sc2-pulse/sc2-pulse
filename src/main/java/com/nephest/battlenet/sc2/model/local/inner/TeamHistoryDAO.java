// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.local.Division;
import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.LeagueTier;
import com.nephest.battlenet.sc2.model.local.dao.DivisionDAO;
import com.nephest.battlenet.sc2.model.local.dao.LeagueDAO;
import com.nephest.battlenet.sc2.model.local.dao.LeagueTierDAO;
import jakarta.validation.constraints.NotNull;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

@Repository
public class TeamHistoryDAO
{

    private static final Set<HistoryColumn> FIND_COLUMN_HARDCODED_COLUMNS
        = EnumSet.of(HistoryColumn.TIMESTAMP);
    private static final Set<String> FIND_COLUMN_STATIC_COLUMN_NAMES =
        Set.of("team_id");

    public enum HistoryColumn
    {
        TIMESTAMP
        (
            "timestamp",
            "season.end",
            "team_state.timestamp"
        ),
        RATING("rating"),
        GAMES
        (
            "games",
            "wins + losses",
            "games"
        ),
        WINS("wins"),
        LEAGUE("league", true),
        TIER("tier", true),
        DIVISION_ID("division_id"),
        RANK_GLOBAL("global_rank"),
        RANK_REGION("region_rank"),
        RANK_LEAGUE("league_rank"),
        COUNT_GLOBAL
        (
            "global_team_count", "global_team_count", "global_team_count",
            List.of
            (
                """
                LEFT JOIN population_state
                    ON team.population_state_id = population_state.id
                """
            ),
            List.of
            (
                """
                LEFT JOIN population_state
                    ON team_state.population_state_id = population_state.id
                """
            )
        ),
        COUNT_REGION
        (
            "region_team_count", "region_team_count", "region_team_count",
            COUNT_GLOBAL.teamJoins, COUNT_GLOBAL.stateJoins
        ),
        COUNT_LEAGUE
        (
            "league_team_count", "league_team_count", "league_team_count",
            COUNT_GLOBAL.teamJoins, COUNT_GLOBAL.stateJoins
        );

        private final String name,
            teamColumnName, stateColumName,
            teamColumnAliasedName, stateColumnAliasedName,
            aggregationFunction;
        private final List<String> teamJoins, stateJoins;
        private final Map<String, Class<?>> typeMapping;
        private final Class<?> valueConversionClass;
        private final boolean expanded;

        HistoryColumn
        (
            String name,
            String teamColumnName,
            String stateColumName,
            List<String> teamJoins,
            List<String> stateJoins,
            Map<String, Class<?>> typeMapping,
            Class<?> valueConversionClass,
            boolean expanded
        )
        {
            this.name = name;
            this.teamColumnName = teamColumnName;
            this.teamColumnAliasedName = teamColumnName + " AS " + name;
            this.stateColumName = stateColumName;
            this.stateColumnAliasedName = stateColumName + " AS " + name;
            this.teamJoins = teamJoins;
            this.stateJoins = stateJoins;
            this.aggregationFunction = "array_agg(" + name + ") AS " + name;
            this.typeMapping = typeMapping;
            this.valueConversionClass = valueConversionClass;
            this.expanded = expanded;
        }

        HistoryColumn
        (
            String name,
            String teamColumnName,
            String stateColumName,
            List<String> teamJoins,
            List<String> stateJoins
        )
        {
            this
            (
                name,
                teamColumnName, stateColumName,
                teamJoins, stateJoins,
                Map.of(),
                null,
                false
            );
        }

        HistoryColumn
        (
            String name,
            String teamColumnName,
            String stateColumName
        )
        {
            this
            (
                name,
                teamColumnName, stateColumName,
                List.of(), List.of(),
                Map.of(),
                null,
                false
            );
        }

        HistoryColumn(String name)
        {
            this(name, name, name);
        }

        HistoryColumn(String name, boolean expanded)
        {
            this(name, name, name, List.of(), List.of(), Map.of(), null, expanded);
        }

        public static HistoryColumn fromName(String name)
        {
            return Arrays.stream(HistoryColumn.values())
                .filter(c->c.getName().equals(name))
                .findFirst()
                .orElseThrow();
        }

        public String getName()
        {
            return name;
        }

        public String getTeamColumnName()
        {
            return teamColumnName;
        }

        public String getTeamColumnAliasedName()
        {
            return teamColumnAliasedName;
        }

        public String getStateColumName()
        {
            return stateColumName;
        }

        public String getStateColumnAliasedName()
        {
            return stateColumnAliasedName;
        }

        public String getAggregationFunction()
        {
            return aggregationFunction;
        }

        public List<String> getTeamJoins()
        {
            return teamJoins;
        }

        public List<String> getStateJoins()
        {
            return stateJoins;
        }

        public Map<String, Class<?>> getTypeMapping()
        {
            return typeMapping;
        }

        public Class<?> getValueConversionClass()
        {
            return valueConversionClass;
        }

        public boolean isExpanded()
        {
            return expanded;
        }

    }

    public enum StaticColumn
    {

        REGION("region"),
        QUEUE("queue_type"),
        LEGACY_ID("legacy_id"),
        SEASON("season");

        public static final String COLUMN_NAME_PREFIX = "team.";
        public static final String JOIN = "INNER JOIN team_filter team USING(team_id)";

        private final String name;
        private final String aliasedName;

        StaticColumn(String name)
        {
            this.name = COLUMN_NAME_PREFIX + name;
            this.aliasedName = this.name + " AS \"" + this.name + "\"";
        }

        public static StaticColumn fromName(String name)
        {
            return Arrays.stream(StaticColumn.values())
                .filter(c->c.getName().equals(name))
                .findFirst()
                .orElseThrow();
        }

        public String getName()
        {
            return name;
        }

        public String getAliasedName()
        {
            return aliasedName;
        }

    }

    private static final String FIND_COLUMNS_TEMPLATE =
        """
        WITH
        current_season AS
        (
            SELECT region, MAX(battlenet_id) as battlenet_id
            FROM season
            GROUP by region
        ),
        team_filter AS
        (
            SELECT team.id AS team_id
            %6$s
            FROM team
            INNER JOIN season ON team.region = season.region
                AND team.season = season.battlenet_id
            INNER JOIN current_season ON season.region = current_season.region
            WHERE team.id IN(:teamIds)
            AND
            (
                :from::timestamp with time zone IS NULL
                OR season.battlenet_id = current_season.battlenet_id
                OR season.start >= :from::timestamp with time zone
                OR season.end >= :from::timestamp with time zone
            )
            AND
            (
                :to::timestamp with time zone IS NULL
                OR season.battlenet_id = current_season.battlenet_id
                OR season.start < :to::timestamp with time zone
                OR season.end < :to::timestamp with time zone
            )
        ),
        data_group AS
        (
            SELECT
            team_id,
            %1$s
            FROM
            (
                SELECT * FROM
                (
                    SELECT team_id, timestamp
                    %2$s
                    FROM team_filter
                    INNER JOIN team_state USING(team_id)
                    %3$s
                    WHERE
                    (
                        :from::timestamp with time zone IS NULL
                        OR timestamp >= :from::timestamp with time zone
                    )
                    AND
                    (
                        :to::timestamp with time zone IS NULL
                        OR timestamp < :to::timestamp with time zone
                    )
        
                    UNION ALL
        
                    SELECT team_id,
                    GREATEST(season.end, prev_timestamp.timestamp + INTERVAL '1 second') AS timestamp
                    %4$s
                    FROM team_filter
                    INNER JOIN team ON team.id = team_filter.team_id
                    INNER JOIN season ON team.region = season.region
                        AND team.season = season.battlenet_id
                    INNER JOIN current_season ON season.region = current_season.region
                    LEFT JOIN LATERAL
                    (
                        SELECT timestamp
                        FROM team_state
                        WHERE team_state.team_id = team_filter.team_id
                        ORDER BY team_state.timestamp DESC
                        LIMIT 1
                    ) prev_timestamp ON true
                    %5$s
                    WHERE season.battlenet_id != current_season.battlenet_id
                    AND
                    (
                        :to::timestamp with time zone IS NULL
                        OR GREATEST(season.end, prev_timestamp.timestamp + INTERVAL '1 second')
                            < :to::timestamp with time zone
                    )
                ) data ORDER BY team_id, timestamp
            ) team_state_ordered
            GROUP BY team_state_ordered.team_id
        )
            SELECT *
            FROM data_group
            %7$s
        """;

    private static ResultSetExtractor<List<TeamHistory>> COLUMN_TEAM_HISTORY_EXTRACTOR;

    private final DivisionDAO divisionDAO;
    private final LeagueTierDAO leagueTierDAO;
    private final LeagueDAO leagueDAO;
    private final NamedParameterJdbcTemplate template;

    @Autowired
    public TeamHistoryDAO
    (
        DivisionDAO divisionDAO,
        LeagueTierDAO leagueTierDAO,
        LeagueDAO leagueDAO,
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService sc2StatsConversionService,
        @Qualifier("minimalConversionService") ConversionService minConversionService
    )
    {
        this.divisionDAO = divisionDAO;
        this.leagueTierDAO = leagueTierDAO;
        this.leagueDAO = leagueDAO;
        this.template = template;
        initMappers(sc2StatsConversionService, minConversionService);
    }

    private static Map<StaticColumn, ?> mapTeamColumns
    (
        ResultSet rs,
        List<StaticColumn> staticColumns,
        ConversionService minConversionService
    )
    {
        return staticColumns.stream()
            .collect(Collectors.toMap(
                Function.identity(),
                column->
                {
                    try
                    {
                        return minConversionService.convert(rs.getObject(column.name), Object.class);
                    }
                    catch (SQLException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            ));
    }

    private static Map<HistoryColumn, List<?>> mapColumns
    (
        ResultSet rs,
        List<HistoryColumn> historyColumns,
        ConversionService sc2StatsConversionService,
        ConversionService minConversionService
    )
    {
        return historyColumns.stream()
            .collect(Collectors.toMap(
                Function.identity(),
                historyColumn ->
                {
                    try
                    {
                        Array dbArray = rs.getArray(historyColumn.getName());
                        Object array = !historyColumn.getTypeMapping().isEmpty()
                            ? dbArray.getArray(historyColumn.getTypeMapping())
                            : dbArray.getArray();
                        return historyColumn.getValueConversionClass() != null
                            ? Arrays.stream((Number[]) array)
                                .map(number -> number != null ? number.intValue() : null)
                                .map(id->sc2StatsConversionService.convert(id, historyColumn.getValueConversionClass()))
                                .toList()
                            : Arrays.asList(minConversionService.convert(array, Object[].class));

                    }
                    catch (SQLException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            ));
    }


    private static TeamHistory map
    (
        ResultSet rs,
        List<StaticColumn> staticColumns,
        List<HistoryColumn> historyColumns,
        ConversionService sc2StatsConversionService,
        ConversionService minConversionService
    )
    throws SQLException
    {
        return new TeamHistory
        (
            rs.getLong("team_id"),
            mapTeamColumns(rs, staticColumns, minConversionService),
            mapColumns(rs, historyColumns, sc2StatsConversionService, minConversionService)
        );
    }

    private static void initMappers
    (
        ConversionService sc2StatsConversionService,
        ConversionService minConversionService
    )
    {
        if(COLUMN_TEAM_HISTORY_EXTRACTOR == null) COLUMN_TEAM_HISTORY_EXTRACTOR = (rs)->
        {
            if(!rs.isBeforeFirst()) return List.of();

            ResultSetMetaData meta  = rs.getMetaData();
            List<StaticColumn> staticColumns = new ArrayList<>();
            List<HistoryColumn> historyColumns = new ArrayList<>();
            IntStream.rangeClosed(1, meta.getColumnCount())
                .boxed()
                .map(i->
                {
                    try
                    {
                        return meta.getColumnLabel(i);
                    }
                    catch (SQLException e)
                    {
                        throw new RuntimeException(e);
                    }
                })
                .filter(columnName -> !FIND_COLUMN_STATIC_COLUMN_NAMES.contains(columnName))
                .forEach(columnName->{
                    if(columnName.startsWith(StaticColumn.COLUMN_NAME_PREFIX))
                    {
                        staticColumns.add(StaticColumn.fromName(columnName));
                    }
                    else
                    {
                        historyColumns.add(HistoryColumn.fromName(columnName));
                    }
                });
            List<TeamHistory> result = new ArrayList<>();
            while(rs.next())
                result.add(map(rs, staticColumns, historyColumns,
                    sc2StatsConversionService, minConversionService));

            return result;
        };
    }

    private static String generateFindColumnsQuery
    (
        Set<StaticColumn> staticColumns,
        Set<HistoryColumn> historyColumns
    )
    {
        List<HistoryColumn> directHistoryColumns = historyColumns.stream()
            .filter(historyColumn ->!historyColumn.isExpanded())
            .toList();
        List<HistoryColumn> dynamicHistoryColumns = directHistoryColumns.stream()
            .filter(historyColumn ->!FIND_COLUMN_HARDCODED_COLUMNS.contains(historyColumn))
            .toList();
        String dynamicPrefix = dynamicHistoryColumns.isEmpty() ? "" : ",";
        return FIND_COLUMNS_TEMPLATE.formatted
        (
            directHistoryColumns.stream()
                .map(HistoryColumn::getAggregationFunction)
                .collect(Collectors.joining(",\n")),

            dynamicPrefix + dynamicHistoryColumns.stream()
                    .map(HistoryColumn::getStateColumnAliasedName)
                    .collect(Collectors.joining(",\n")),
            dynamicHistoryColumns.stream()
                .flatMap(historyColumn -> historyColumn.getStateJoins().stream())
                .distinct()
                .collect(Collectors.joining("\n")),

            dynamicPrefix + dynamicHistoryColumns.stream()
                .map(HistoryColumn::getTeamColumnAliasedName)
                .collect(Collectors.joining(",\n")),
            dynamicHistoryColumns.stream()
                .flatMap(historyColumn -> historyColumn.getTeamJoins().stream())
                .distinct()
                .collect(Collectors.joining("\n")),

            staticColumns.isEmpty() ? "" : "," + staticColumns.stream()
                .map(StaticColumn::getAliasedName)
                .collect(Collectors.joining(",\n")),
            staticColumns.isEmpty() ? "" : StaticColumn.JOIN
        );
    }

    public List<TeamHistory> find
    (
        @NotNull Set<Long> teamIds,
        @Nullable OffsetDateTime from,
        @Nullable OffsetDateTime to,
        @NotNull Set<StaticColumn> staticColumns,
        @NotNull Set<HistoryColumn> historyColumns
    )
    {
        if(teamIds.isEmpty() || (historyColumns.isEmpty() && staticColumns.isEmpty())) return List.of();
        if(from != null && to != null && !from.isBefore(to))
            throw new IllegalArgumentException("'from' parameter must be before 'to' parameter");

        String query = generateFindColumnsQuery(staticColumns, expand(historyColumns));
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("teamIds", teamIds)
            .addValue("from", from, Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("to", to, Types.TIMESTAMP_WITH_TIMEZONE);
        List<TeamHistory> history = template.query(query, params, COLUMN_TEAM_HISTORY_EXTRACTOR);
        expand(history, historyColumns);
        return history;
    }

    private static Set<HistoryColumn> expand(Set<HistoryColumn> historyColumns)
    {
        if(!shouldExpand(historyColumns)) return historyColumns;

        Set<HistoryColumn> expandedHistoryColumns = EnumSet.copyOf(historyColumns);
        expandedHistoryColumns.add(HistoryColumn.DIVISION_ID);
        return expandedHistoryColumns;
    }

    private static boolean shouldExpand(Set<HistoryColumn> historyColumns)
    {
        return historyColumns.contains(HistoryColumn.TIER)
            || historyColumns.contains(HistoryColumn.LEAGUE);
    }

    @SuppressWarnings("unchecked")
    private void expand(List<TeamHistory> history, Set<HistoryColumn> historyColumns)
    {
        if(!shouldExpand(historyColumns)) return;

        Map<Integer, Division> divisions = divisionDAO.findByIds
        (
            history.stream()
                .map(TeamHistory::history)
                .map(data->(List<Integer>) data.get(HistoryColumn.DIVISION_ID))
                .flatMap(Collection::stream)
                .collect(Collectors.toSet())
        )
            .stream()
            .collect(Collectors.toMap(Division::getId, Function.identity()));

        Map<Integer, LeagueTier> tiers = leagueTierDAO.findByIds
        (
            divisions.values().stream()
                .map(Division::getTierId)
                .collect(Collectors.toSet())
        )
            .stream()
            .collect(Collectors.toMap(LeagueTier::getId, Function.identity()));
        boolean expandTiers = historyColumns.contains(HistoryColumn.TIER);
        boolean expandLeagues = historyColumns.contains(HistoryColumn.LEAGUE);
        boolean wantedDivisionIds = historyColumns.contains(HistoryColumn.DIVISION_ID);

        Map<Integer, League> leagues = expandLeagues
            ? leagueDAO.find
                (
                    tiers.values().stream()
                        .map(LeagueTier::getLeagueId)
                        .distinct()
                        .toList()
                )
                .stream()
                .collect(Collectors.toMap(League::getId, Function.identity()))
            : Map.of();

        history.forEach(h->{
            List<Integer> divisionIds = (List<Integer>) h.history().get(HistoryColumn.DIVISION_ID);
            List<BaseLeagueTier.LeagueTierType> historyTiers = expandTiers
                ? new ArrayList<>(divisionIds.size())
                : List.of();
            List<BaseLeague.LeagueType> historyLeagues = expandLeagues
                ? new ArrayList<>(divisionIds.size())
                : List.of();
            for(Integer id : divisionIds)
            {
                Division currentDivision = divisions.get(id);
                LeagueTier currentTier = tiers.get(currentDivision.getTierId());

                if(expandTiers) historyTiers.add(currentTier.getType());
                if(expandLeagues)
                {
                    League currentLeague = leagues.get(currentTier.getLeagueId());
                    historyLeagues.add(currentLeague.getType());
                }

            }
            if(expandTiers) h.history().put(HistoryColumn.TIER, historyTiers);
            if(expandLeagues) h.history().put(HistoryColumn.LEAGUE, historyLeagues);
            if(!wantedDivisionIds) h.history().remove(HistoryColumn.DIVISION_ID);
        });
    }


}
