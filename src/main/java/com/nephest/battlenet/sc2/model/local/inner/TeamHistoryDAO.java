// Copyright (C) 2020-2025 Oleksandr Masniuk
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
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
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
    private static final String FINAL_HISTORY_TABLE = "data_group";
    private static final Map<HistoryColumn, String> FINAL_HISTORY_FQDN_NAMES =
        Arrays.stream(HistoryColumn.values())
            .filter(historyColumn ->!historyColumn.isExpanded())
            .collect(Collectors.toMap(
                Function.identity(),
                c->FINAL_HISTORY_TABLE + "." + c.getName(),
                (l,r)->{throw new IllegalArgumentException("Unexpected merge");},
                ()->new EnumMap<>(HistoryColumn.class)
            ));

    public enum HistoryColumn
    {
        TIMESTAMP("timestamp", "team_state.timestamp"),
        RATING("rating"),
        GAMES("games"),
        WINS("wins"),
        LEAGUE("league", true),
        TIER("tier", true),
        DIVISION_ID("division_id"),
        RANK_GLOBAL("global_rank"),
        RANK_REGION("region_rank"),
        RANK_LEAGUE("league_rank"),
        COUNT_GLOBAL
        (
            "global_team_count", "global_team_count",
            List.of
            (
                """
                LEFT JOIN population_state
                    ON team_state.population_state_id = population_state.id
                """
            )
        ),
        COUNT_REGION("region_team_count", "team_state.region_team_count"),
        COUNT_LEAGUE("league_team_count", "league_team_count", COUNT_GLOBAL.joins),

        ID("id", true),
        SEASON("season", true);

        private final String name, columName, columnAliasedName, aggregationFunction;
        private final List<String> joins;
        private final Map<String, Class<?>> typeMapping;
        private final Class<?> valueConversionClass;
        private final boolean expanded;

        HistoryColumn
        (
            String name,
            String columName,
            List<String> joins,
            Map<String, Class<?>> typeMapping,
            Class<?> valueConversionClass,
            boolean expanded
        )
        {
            this.name = name;
            this.columName = columName;
            this.columnAliasedName = columName + " AS " + name;
            this.joins = joins;
            this.aggregationFunction = "array_agg(" + name + ") AS " + name;
            this.typeMapping = typeMapping;
            this.valueConversionClass = valueConversionClass;
            this.expanded = expanded;
        }

        HistoryColumn
        (
            String name,
            String columName,
            List<String> joins
        )
        {
            this
            (
                name,
                columName,
                joins,
                Map.of(),
                null,
                false
            );
        }

        HistoryColumn(String name, String columName)
        {
            this
            (
                name,
                columName,
                List.of(),
                Map.of(),
                null,
                false
            );
        }

        HistoryColumn(String name)
        {
            this(name, name);
        }

        HistoryColumn(String name, boolean expanded)
        {
            this(name, name, List.of(), Map.of(), null, expanded);
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

        public String getColumName()
        {
            return columName;
        }

        public String getColumnAliasedName()
        {
            return columnAliasedName;
        }

        public String getAggregationFunction()
        {
            return aggregationFunction;
        }

        public List<String> getJoins()
        {
            return joins;
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

        ID("team_id", List.of()),
        REGION("region", List.of(StaticColumn.TEAM_JOIN)),
        QUEUE("queue_type", REGION.joins),
        LEGACY_ID("legacy_id", REGION.joins),
        SEASON("season", REGION.joins);

        public static final String COLUMN_NAME_PREFIX = "team.";
        public static final String TEAM_JOIN = "INNER JOIN team ON data_group.team_id = team.id";

        private final String name;
        private final String alias;
        private final String aliasedName;
        private final List<String> joins;

        StaticColumn(String name, List<String> joins)
        {
            this.name = name;
            this.alias = COLUMN_NAME_PREFIX + name;
            this.aliasedName = name + " AS \"" + this.alias + "\"";
            this.joins = joins;
        }

        public static StaticColumn fromAlias(String alias)
        {
            return Arrays.stream(StaticColumn.values())
                .filter(c->c.getAlias().equals(alias))
                .findFirst()
                .orElseThrow();
        }

        public String getName()
        {
            return name;
        }

        public String getAlias()
        {
            return alias;
        }

        public String getAliasedName()
        {
            return aliasedName;
        }

        public List<String> getJoins()
        {
            return joins;
        }

    }

    private static final String FIND_COLUMNS_TEMPLATE =
        """
        WITH
        data_group AS
        (
            SELECT
            team_id
            %1$s
            FROM
            (
                SELECT * FROM
                (
                    SELECT team_id, timestamp
                    %2$s
                    FROM team_state
                    %3$s
                    WHERE team_id IN(:teamIds)
                    AND
                    (
                        :from::timestamp with time zone IS NULL
                        OR timestamp >= :from::timestamp with time zone
                    )
                    AND
                    (
                        :to::timestamp with time zone IS NULL
                        OR timestamp < :to::timestamp with time zone
                    )
                ) data ORDER BY team_id, timestamp
            ) team_state_ordered
            GROUP BY team_state_ordered.team_id
        )
            SELECT
            %4$s
            FROM data_group
            %5$s
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
                        return minConversionService.convert(rs.getObject(column.alias), Object.class);
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
    {
        return new TeamHistory
        (
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
                .forEach(columnName->{
                    if(columnName.startsWith(StaticColumn.COLUMN_NAME_PREFIX))
                    {
                        staticColumns.add(StaticColumn.fromAlias(columnName));
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

    private static String generateFindColumnsQuery(HistoryParameters parameters)
    {
        List<HistoryColumn> directHistoryColumns = parameters.historyColumns().stream()
            .filter(historyColumn ->!historyColumn.isExpanded())
            .toList();
        List<HistoryColumn> dynamicHistoryColumns = directHistoryColumns.stream()
            .filter(historyColumn ->!FIND_COLUMN_HARDCODED_COLUMNS.contains(historyColumn))
            .toList();
        String dynamicPrefix = dynamicHistoryColumns.isEmpty() ? "" : ",";
        return FIND_COLUMNS_TEMPLATE.formatted
        (
            directHistoryColumns.isEmpty() ? "" : "," + directHistoryColumns.stream()
                .map(HistoryColumn::getAggregationFunction)
                .collect(Collectors.joining(",\n")),

            dynamicPrefix + dynamicHistoryColumns.stream()
                    .map(HistoryColumn::getColumnAliasedName)
                    .collect(Collectors.joining(",\n")),
            dynamicHistoryColumns.stream()
                .flatMap(historyColumn -> historyColumn.getJoins().stream())
                .distinct()
                .collect(Collectors.joining("\n")),

            Stream.concat
            (
                directHistoryColumns.stream().map(FINAL_HISTORY_FQDN_NAMES::get),
                parameters.staticColumns().stream().map(StaticColumn::getAliasedName)
            )
                .collect(Collectors.joining(",\n")),
            parameters.staticColumns().stream()
                .map(StaticColumn::getJoins)
                .flatMap(Collection::stream)
                .distinct()
                .collect(Collectors.joining(",\n"))
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

        HistoryParameters parameters = new HistoryParameters(staticColumns, historyColumns);
        String query = generateFindColumnsQuery(createExpandedParameters(parameters));
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("teamIds", teamIds)
            .addValue("from", from, Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("to", to, Types.TIMESTAMP_WITH_TIMEZONE);
        List<TeamHistory> history = template.query(query, params, COLUMN_TEAM_HISTORY_EXTRACTOR);
        expandAll(history, parameters);
        prune(history, parameters);
        return history;
    }

    private HistoryParameters createExpandedParameters(HistoryParameters parameters)
    {
        if(parameters.historyColumns().stream().noneMatch(HistoryColumn::isExpanded))
            return parameters;

        HistoryParameters expanded = HistoryParameters.copyOf(parameters);
        expandParameters(expanded);
        return expanded;
    }

    private void expandParameters(HistoryParameters parameters)
    {
        expandDivisionParameters(parameters);
        expandStaticHistoryParameters(parameters);
    }

    private static void expandStaticHistoryParameters(HistoryParameters parameters)
    {
        if(parameters.historyColumns().contains(HistoryColumn.ID))
            parameters.staticColumns().add(StaticColumn.ID);
        if(parameters.historyColumns().contains(HistoryColumn.SEASON))
            parameters.staticColumns().add(StaticColumn.SEASON);
        if(
            (
                parameters.historyColumns().contains(HistoryColumn.ID)
                    || parameters.historyColumns().contains(HistoryColumn.SEASON)
            )
                && parameters.historyColumns().stream().allMatch(HistoryColumn::isExpanded)
        )
            parameters.historyColumns().add(HistoryColumn.TIMESTAMP);
    }

    private static void prune(List<TeamHistory> history, HistoryParameters parameters)
    {
        for(TeamHistory curHistory : history)
        {
            curHistory.staticData().keySet().retainAll(parameters.staticColumns());
            curHistory.history().keySet().retainAll(parameters.historyColumns());
        }
    }

    private void expandAll
    (
        List<TeamHistory> history,
        HistoryParameters parameters
    )
    {
        expandDivisions(history, parameters);
        expandStaticHistory(history, parameters);
    }

    private static boolean shouldExpandStaticHistory(HistoryParameters parameters)
    {
        return parameters.historyColumns().contains(HistoryColumn.ID)
            || parameters.historyColumns().contains(HistoryColumn.SEASON);
    }

    private static void expandStaticHistory
    (
        List<TeamHistory> history,
        HistoryParameters parameters
    )
    {
        if(!shouldExpandStaticHistory(parameters)) return;

        List<BiConsumer<TeamHistory, Integer>> expanders
            = createStaticHistoryExpanders(parameters);
        history.forEach(h->{
            int size = h.history().values().iterator().next().size();
            expanders.forEach(expander->expander.accept(h, size));
        });
    }

    private static List<BiConsumer<TeamHistory, Integer>> createStaticHistoryExpanders
    (
        HistoryParameters parameters
    )
    {
        List<BiConsumer<TeamHistory, Integer>> expanders = new ArrayList<>(2);
        if(parameters.historyColumns().contains(HistoryColumn.ID))
            expanders.add((h, size)->expandStaticHistory(
                h, StaticColumn.ID, HistoryColumn.ID, size));
        if(parameters.historyColumns().contains(HistoryColumn.SEASON))
            expanders.add((h, size)->expandStaticHistory(
                h, StaticColumn.SEASON, HistoryColumn.SEASON, size));
        return expanders;
    }

    private static void expandStaticHistory
    (
        TeamHistory history,
        StaticColumn staticColumn,
        HistoryColumn historyColumn,
        int size
    )
    {
        history.history().put
        (
            historyColumn,
            Collections.nCopies(size, history.staticData().get(staticColumn))
        );
    }

    private static void expandDivisionParameters(HistoryParameters parameters)
    {
        if(shouldExpandDivisions(parameters))
            parameters.historyColumns().add(HistoryColumn.DIVISION_ID);
    }

    private static boolean shouldExpandDivisions(HistoryParameters parameters)
    {
        return parameters.historyColumns().contains(HistoryColumn.TIER)
            || parameters.historyColumns().contains(HistoryColumn.LEAGUE);
    }

    @SuppressWarnings("unchecked")
    private void expandDivisions(List<TeamHistory> history, HistoryParameters parameters)
    {
        if(!shouldExpandDivisions(parameters)) return;

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
        boolean expandTiers = parameters.historyColumns().contains(HistoryColumn.TIER);
        boolean expandLeagues = parameters.historyColumns().contains(HistoryColumn.LEAGUE);

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
        });
    }

    private record HistoryParameters
    (
        @NotNull Set<StaticColumn> staticColumns,
        @NotNull Set<HistoryColumn> historyColumns
    )
    {

        public static HistoryParameters copyOf(HistoryParameters parameters)
        {
            return new HistoryParameters
            (
                parameters.staticColumns.isEmpty()
                    ? EnumSet.noneOf(StaticColumn.class)
                    : EnumSet.copyOf(parameters.staticColumns()),
                parameters.historyColumns.isEmpty()
                    ? EnumSet.noneOf(HistoryColumn.class)
                    : EnumSet.copyOf(parameters.historyColumns())
            );
        }

    }


}
