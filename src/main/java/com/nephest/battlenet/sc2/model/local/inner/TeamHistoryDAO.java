// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
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
import java.util.Comparator;
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

    public static final String PARAMETER_JOIN_DELIMITER = ",\n";
    public static final String JOIN_JOIN_DELIMITER = "\n";

    public enum HistoryColumn
    {
        TIMESTAMP("timestamp", "team_state.timestamp", "BIGINT"),
        RATING("rating", "INTEGER"),
        GAMES("games", "INTEGER"),
        WINS("wins", "INTEGER"),
        LEAGUE_TYPE("league", "SMALLINT", true),
        TIER_TYPE("tier", "SMALLINT", true),
        DIVISION_ID("division_id", "INTEGER"),
        GLOBAL_RANK("global_rank", "INTEGER"),
        REGION_RANK("region_rank", "INTEGER"),
        LEAGUE_RANK("league_rank", "INTEGER"),
        GLOBAL_TEAM_COUNT
        (
            "global_team_count", "global_team_count", "INTEGER",
            List.of
            (
                """
                LEFT JOIN population_state
                    ON team_state.population_state_id = population_state.id
                """
            )
        ),
        REGION_TEAM_COUNT("region_team_count", "team_state.region_team_count", "INTEGER"),
        LEAGUE_TEAM_COUNT("league_team_count", "league_team_count", "INTEGER", GLOBAL_TEAM_COUNT.joins),

        ID("id", "BIGINT", true),
        SEASON("season", "INTEGER", true);

        private final String name, columName, columnAliasedName, aggregationFunction;
        private final List<String> joins;
        private final Map<String, Class<?>> typeMapping;
        private final Class<?> valueConversionClass;
        private final boolean expanded;

        HistoryColumn
        (
            String name,
            String columName,
            String sqlType,
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
            this.aggregationFunction = "array_agg(" + name + ")::" + sqlType + "[] AS " + name;
            this.typeMapping = typeMapping;
            this.valueConversionClass = valueConversionClass;
            this.expanded = expanded;
        }

        HistoryColumn
        (
            String name,
            String columName,
            String sqlType,
            List<String> joins
        )
        {
            this
            (
                name,
                columName,
                sqlType,
                joins,
                Map.of(),
                null,
                false
            );
        }

        HistoryColumn(String name, String columName, String sqlType)
        {
            this
            (
                name,
                columName,
                sqlType,
                List.of(),
                Map.of(),
                null,
                false
            );
        }

        HistoryColumn(String name, String sqlType)
        {
            this(name, name, sqlType);
        }

        HistoryColumn(String name, String sqlType, boolean expanded)
        {
            this(name, name, sqlType, List.of(), Map.of(), null, expanded);
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
        QUEUE_TYPE("queue_type", REGION.joins),
        TEAM_TYPE("team_type", REGION.joins),
        LEGACY_ID("legacy_id", REGION.joins),
        SEASON("season", REGION.joins);

        public static final String COLUMN_NAME_PREFIX = "team.";
        public static final String TEAM_JOIN = "INNER JOIN team ON team_id = team.id";

        private final String name;
        private final String alias;
        private final String aliasedName;
        private final String aggregationAliasedName;
        private final List<String> joins;

        StaticColumn(String name, List<String> joins)
        {
            this.name = name;
            this.alias = COLUMN_NAME_PREFIX + name;
            this.aliasedName = name + " AS \"" + this.alias + "\"";
            this.aggregationAliasedName = "MAX(" + name + ") AS \"" + this.alias + "\"";
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

        public String getAggregationAliasedName()
        {
            return aggregationAliasedName;
        }

        public List<String> getJoins()
        {
            return joins;
        }

    }

    public enum GroupMode
    {

        TEAM
        (
            EnumSet.of(StaticColumn.ID),
            EnumSet.allOf(StaticColumn.class),
            EnumSet.noneOf(StaticColumn.class)
        ),
        LEGACY_UID
        (
            EnumSet.of
            (
                StaticColumn.REGION,
                StaticColumn.QUEUE_TYPE,
                StaticColumn.TEAM_TYPE,
                StaticColumn.LEGACY_ID
            ),
            EnumSet.of
            (
                StaticColumn.REGION,
                StaticColumn.QUEUE_TYPE,
                StaticColumn.TEAM_TYPE,
                StaticColumn.LEGACY_ID,
                StaticColumn.SEASON
            )
        );

        private final Set<StaticColumn> groupStaticColumns;
        private final Set<StaticColumn> supportedStaticColumns;
        private final Set<StaticColumn> requiredStaticParameters;

        GroupMode
        (
            EnumSet<StaticColumn> groupStaticColumns,
            EnumSet<StaticColumn> supportedStaticColumns,
            EnumSet<StaticColumn> requiredStaticParameters
        )
        {
            this.groupStaticColumns = Collections.unmodifiableSet(groupStaticColumns);
            this.supportedStaticColumns = Collections.unmodifiableSet(supportedStaticColumns);
            this.requiredStaticParameters = Collections.unmodifiableSet(requiredStaticParameters);
        }

        GroupMode
        (
            EnumSet<StaticColumn> supportedStaticColumns,
            EnumSet<StaticColumn> requiredStaticParameters
        )
        {
            this.supportedStaticColumns = Collections.unmodifiableSet(supportedStaticColumns);
            this.requiredStaticParameters = Collections.unmodifiableSet(requiredStaticParameters);
            this.groupStaticColumns = supportedStaticColumns;
        }

        public Set<StaticColumn> getGroupStaticColumns()
        {
            return groupStaticColumns;
        }

        public Set<StaticColumn> getSupportedStaticColumns()
        {
            return supportedStaticColumns;
        }

        public boolean isSupported(StaticColumn staticColumn)
        {
            return supportedStaticColumns.contains(staticColumn);
        }

        public Set<StaticColumn> getRequiredStaticParameters()
        {
            return requiredStaticParameters;
        }

        public boolean injectsParameters()
        {
            return !requiredStaticParameters.isEmpty();
        }

        public static final class NAMES
        {
            public static final String TEAM = "TEAM";
            public static final String LEGACY_UID = "LEGACY_UID";
        }

    }

    public enum SummaryColumn
    {
        GAMES
        (
            "games_diff",
            """
                CASE
                    WHEN LAG(games) OVER w IS NULL THEN 1
                    WHEN games - LAG(games) OVER w < 0
                        OR team_id != LAG(team_id) OVER w
                        OR
                        (
                            games = LAG(games) OVER w
                            AND team_state.rating != LAG(team_state.rating) OVER w
                        )
                    THEN games
                    ELSE games - LAG(games) OVER w
                END
            """,
            "INTEGER",
            "games",
            "SUM"
        ),

        RATING_MIN
        (
            "rating", "team_state.rating", "INTEGER",
            "rating_min", "MIN"
        ),
        RATING_AVG
        (
            "rating", "team_state.rating", "DOUBLE PRECISION",
            "rating_avg", "AVG"
        ),
        RATING_MAX
        (
            "rating", "team_state.rating", "INTEGER",
            "rating_max", "MAX"
        ),
        RATING_LAST
        (
            "rating_last", "LAST_VALUE(team_state.rating) OVER w", "INTEGER",
            "rating_last", "MAX"
        ),

        REGION_RANK_LAST
        (
            "region_rank_last", "LAST_VALUE(team_state.region_rank) OVER w", "INTEGER",
            "region_rank_last", "MAX"
        ),
        REGION_TEAM_COUNT_LAST
        (
            "region_team_count_last", "LAST_VALUE(team_state.region_team_count) OVER w", "INTEGER",
            "region_team_count_last", "MAX"
        );


        private final String dataName, dataColumn, dataAliasedColumn,
            aggregationName,  aggregationFunction, aggregationAliasedFunction;

        SummaryColumn
        (
            String dataName,
            String dataColumn,
            String sqlType,
            String aggregationName,
            String aggregationFunctionName
        )
        {
            this.dataName = dataName;
            this.dataColumn = dataColumn;
            this.dataAliasedColumn = dataColumn + " AS " + dataName;
            this.aggregationName = aggregationName;
            this.aggregationFunction = aggregationFunctionName + "(" + dataName + ")::" + sqlType;
            this.aggregationAliasedFunction = aggregationFunction + " AS " + aggregationName;
        }

        public static SummaryColumn fromAggregationName(String name)
        {
            return Arrays.stream(SummaryColumn.values())
                .filter(c->c.getAggregationName().equals(name))
                .findFirst()
                .orElseThrow();
        }

        public String getDataName()
        {
            return dataName;
        }

        public String getDataColumn()
        {
            return dataColumn;
        }

        public String getDataAliasedColumn()
        {
            return dataAliasedColumn;
        }

        public String getAggregationName()
        {
            return aggregationName;
        }

        public String getAggregationFunction()
        {
            return aggregationFunction;
        }

        public String getAggregationAliasedFunction()
        {
            return aggregationAliasedFunction;
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
                    SELECT team_id, FLOOR(EXTRACT(epoch FROM team_state.timestamp)) AS timestamp
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

    private static final Map<GroupMode, String> FIND_SUMMARY_ID_COLUMN = Map.of
    (
        GroupMode.TEAM, "team_id",
        GroupMode.LEGACY_UID, "team.id"
    );

    private static final String FIND_SUMMARY_TEMPLATE =
        """
        WITH
        data_group AS
        (
            SELECT
            CASE
                WHEN games IS DISTINCT FROM LAG(games) OVER w
                    OR team_state.rating IS DISTINCT FROM LAG(team_state.rating) OVER w
                    OR team_id IS DISTINCT FROM LAG(team_id) OVER w
                THEN true
                ELSE false
            END AS is_player_action,
            %1$s
            FROM team_state
            %2$s
            WHERE %5$s IN(:teamIds)
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
            WINDOW w AS (PARTITION BY %4$s ORDER BY timestamp ASC ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)
        )
            SELECT
            %3$s
            FROM data_group
            WHERE is_player_action
            GROUP BY %4$s
        """;

    private static ResultSetExtractor<List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>>> COLUMN_TEAM_HISTORY_EXTRACTOR;
    private static ResultSetExtractor<List<TeamHistorySummary<RawTeamHistoryStaticData, RawTeamHistorySummaryData>>> TEAM_HISTORY_SUMMARY_EXTRACTOR;

    private final DivisionDAO divisionDAO;
    private final LeagueTierDAO leagueTierDAO;
    private final LeagueDAO leagueDAO;
    private final NamedParameterJdbcTemplate template;
    private final ConversionService sc2StatsConversionService;
    private final ConversionService minConversionService;

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
        this.sc2StatsConversionService = sc2StatsConversionService;
        this.minConversionService = minConversionService;
        initMappers(sc2StatsConversionService, minConversionService);
    }

    private static <T extends Enum<T>> Map<T, ?>  mapGenericColumns
    (
        ResultSet rs,
        List<T> columns,
        ConversionService minConversionService,
        Function<T, String> columnNameMapper,
        Class<T> clazz
    )
    {
        try
        {
            Map<T, Object> result = new EnumMap<>(clazz);
            for(T column : columns) result.put(column, minConversionService
                .convert(rs.getObject(columnNameMapper.apply(column)), Object.class));
            return result;
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static RawTeamHistoryStaticData mapTeamColumns
    (
        ResultSet rs,
        List<StaticColumn> staticColumns,
        ConversionService minConversionService
    )
    {
        return new RawTeamHistoryStaticData(mapGenericColumns(
            rs,
            staticColumns,
            minConversionService,
            StaticColumn::getAlias,
            StaticColumn.class
        ));
    }

    private static RawTeamHistoryHistoryData mapColumns
    (
        ResultSet rs,
        List<HistoryColumn> historyColumns,
        ConversionService sc2StatsConversionService,
        ConversionService minConversionService
    )
    {
        return new RawTeamHistoryHistoryData(historyColumns.stream()
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
            )));
    }


    private static TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData> map
    (
        ResultSet rs,
        List<StaticColumn> staticColumns,
        List<HistoryColumn> historyColumns,
        ConversionService sc2StatsConversionService,
        ConversionService minConversionService
    )
    {
        return new TeamHistory<>
        (
            mapTeamColumns(rs, staticColumns, minConversionService),
            mapColumns(rs, historyColumns, sc2StatsConversionService, minConversionService)
        );
    }

    private static TeamHistorySummary<RawTeamHistoryStaticData, RawTeamHistorySummaryData> mapSummary
    (
        ResultSet rs,
        List<StaticColumn> staticColumns,
        List<SummaryColumn> summaryColumns,
        ConversionService minConversionService
    )
    {
        return new TeamHistorySummary<>
        (
            mapTeamColumns(rs, staticColumns, minConversionService),
            new RawTeamHistorySummaryData(mapGenericColumns(
                rs,
                summaryColumns,
                minConversionService,
                SummaryColumn::getAggregationName,
                SummaryColumn.class
            ))
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
            List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> result
                = new ArrayList<>();
            while(rs.next())
                result.add(map(rs, staticColumns, historyColumns,
                    sc2StatsConversionService, minConversionService));

            return result;
        };

        if(TEAM_HISTORY_SUMMARY_EXTRACTOR == null) TEAM_HISTORY_SUMMARY_EXTRACTOR
            = createTeamHistorySummaryExtractor(minConversionService);
    }

    private static ResultSetExtractor<List<TeamHistorySummary<RawTeamHistoryStaticData, RawTeamHistorySummaryData>>> createTeamHistorySummaryExtractor
    (
        ConversionService minConversionService
    )
    {
        return rs->{
            if(!rs.isBeforeFirst()) return List.of();

            ResultSetMetaData meta  = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            List<StaticColumn> staticColumns = new ArrayList<>(columnCount);
            List<SummaryColumn> summaryColumns = new ArrayList<>(columnCount);
            IntStream.rangeClosed(1, columnCount)
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
                        summaryColumns.add(SummaryColumn.fromAggregationName(columnName));
                    }
                });

            List<TeamHistorySummary<RawTeamHistoryStaticData, RawTeamHistorySummaryData>> result
                = new ArrayList<>();
            while(rs.next())
                result.add(mapSummary(rs, staticColumns, summaryColumns, minConversionService));
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
                .collect(Collectors.joining(PARAMETER_JOIN_DELIMITER)),

            dynamicPrefix + dynamicHistoryColumns.stream()
                    .map(HistoryColumn::getColumnAliasedName)
                    .collect(Collectors.joining(PARAMETER_JOIN_DELIMITER)),
            dynamicHistoryColumns.stream()
                .flatMap(historyColumn -> historyColumn.getJoins().stream())
                .distinct()
                .collect(Collectors.joining(JOIN_JOIN_DELIMITER)),

            Stream.concat
            (
                directHistoryColumns.stream().map(FINAL_HISTORY_FQDN_NAMES::get),
                parameters.staticColumns().stream().map(StaticColumn::getAliasedName)
            )
                .collect(Collectors.joining(PARAMETER_JOIN_DELIMITER)),
            parameters.staticColumns().stream()
                .map(StaticColumn::getJoins)
                .flatMap(Collection::stream)
                .distinct()
                .collect(Collectors.joining(PARAMETER_JOIN_DELIMITER))
        );
    }

    private static void checkParameters
    (
        @Nullable OffsetDateTime from,
        @Nullable OffsetDateTime to,
        @NotNull Set<StaticColumn> staticColumns,
        @NotNull GroupMode groupMode
    )
    {
        if(from != null && to != null && !from.isBefore(to))
            throw new IllegalArgumentException("'from' parameter must be before 'to' parameter");
        if(staticColumns.stream().anyMatch(c->!groupMode.isSupported(c)))
            throw new IllegalArgumentException
            (
                "Some static columns in " + Arrays.toString(staticColumns.toArray())
                    + " are not supported by the group mode " + groupMode
            );
    }

    public List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> find
    (
        @NotNull Set<Long> teamIds,
        @Nullable OffsetDateTime from,
        @Nullable OffsetDateTime to,
        @NotNull Set<StaticColumn> staticColumns,
        @NotNull Set<HistoryColumn> historyColumns,
        @NotNull GroupMode groupMode
    )
    {
        if(teamIds.isEmpty() || (historyColumns.isEmpty() && staticColumns.isEmpty())) return List.of();
        checkParameters(from, to, staticColumns, groupMode);

        HistoryParameters parameters = new HistoryParameters(staticColumns, historyColumns);
        String query = generateFindColumnsQuery(createExpandedParameters(parameters, groupMode));
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("teamIds", teamIds)
            .addValue("from", from, Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("to", to, Types.TIMESTAMP_WITH_TIMEZONE);
        List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> history
            = template.query(query, params, COLUMN_TEAM_HISTORY_EXTRACTOR);
        expandAll(history, parameters);
        history = group(history, parameters, groupMode);
        prune(history, parameters);
        return history;
    }

    private HistoryParameters createExpandedParameters
    (
        HistoryParameters parameters, GroupMode groupMode
    )
    {
        boolean shouldExpand = parameters.historyColumns().stream()
            .anyMatch(HistoryColumn::isExpanded);
        if(!shouldExpand && !groupMode.injectsParameters()) return parameters;

        HistoryParameters expanded = HistoryParameters.copyOf(parameters);
        if(shouldExpand) expandParameters(expanded);
        if(groupMode.injectsParameters()) injectGroupModeParameters(expanded, groupMode);
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

    private static void injectGroupModeParameters(HistoryParameters parameters, GroupMode groupMode)
    {
        parameters.staticColumns().addAll(groupMode.getRequiredStaticParameters());
    }

    private static void prune
    (
        List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> history,
        HistoryParameters parameters
    )
    {
        for(TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData> curHistory : history)
        {

            curHistory.staticData().data().keySet().retainAll(parameters.staticColumns());
            curHistory.history().data().keySet().retainAll(parameters.historyColumns());
        }
    }

    private List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> group
    (
        List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> history,
        HistoryParameters parameters,
        GroupMode groupMode
    )
    {
        return switch (groupMode)
        {
            case TEAM->history;
            case LEGACY_UID->groupByLegacyUid(history, parameters);
            default->throw new IllegalArgumentException("Unsupported group mode: " + groupMode);
        };
    }

    private List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> groupByLegacyUid
    (
        List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> history,
        HistoryParameters parameters
    )
    {
        return history.stream()
            .map(this::toLegacyUidGroup)
            .collect(Collectors.groupingBy(HistoryLegacyUidGroup::legacyUid))
                .values().stream()
            .map(group->group.stream().map(HistoryLegacyUidGroup::history).collect(Collectors.toList()))
            .map(historyGroup->concatGroup(historyGroup, parameters))
            .toList();
    }

    private TeamLegacyUid toLegacyUid
    (
        TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData> teamHistory
    )
    {
        return new TeamLegacyUid
        (
            sc2StatsConversionService.convert
            (
                teamHistory.staticData().data().get(StaticColumn.QUEUE_TYPE),
                QueueType.class
            ),
            sc2StatsConversionService.convert
            (
                teamHistory.staticData().data().get(StaticColumn.TEAM_TYPE),
                TeamType.class
            ),
            sc2StatsConversionService.convert
            (
                teamHistory.staticData().data().get(StaticColumn.REGION),
                Region.class
            ),
            (String) teamHistory.staticData().data().get(StaticColumn.LEGACY_ID)
        );
    }

    private HistoryLegacyUidGroup toLegacyUidGroup
    (
        TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData> teamHistory
    )
    {
        return new HistoryLegacyUidGroup(teamHistory, toLegacyUid(teamHistory));
    }

    private static TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData> concatGroup
    (
        List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> history,
        HistoryParameters parameters
    )
    {
        if(history.isEmpty()) return new TeamHistory<>
        (
            RawTeamHistoryStaticData.EMPTY,
            RawTeamHistoryHistoryData.EMPTY
        );

        history.sort(Comparator.comparing(h->(int) h.staticData().data().get(StaticColumn.SEASON)));
        return new TeamHistory<>
        (
            new RawTeamHistoryStaticData(parameters.staticColumns.stream()
                .collect(Collectors.toMap(
                    Function.identity(),
                    col->history.get(0).staticData().data().get(col),
                    (l, r)->{throw new IllegalStateException("Unexpected merge");},
                    ()->new EnumMap<StaticColumn, Object>(StaticColumn.class)
                ))),
            new RawTeamHistoryHistoryData(parameters.historyColumns.stream()
                .collect(Collectors.toMap(
                    Function.identity(),
                    col->history.stream()
                        .flatMap(h->h.history().data().get(col).stream())
                        .toList(),
                    (l, r)->{throw new IllegalStateException("Unexpected merge");},
                    ()->new EnumMap<>(HistoryColumn.class)
                )))
        );
    }

    private void expandAll
    (
        List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> history,
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
        List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> history,
        HistoryParameters parameters
    )
    {
        if(!shouldExpandStaticHistory(parameters)) return;

        List<BiConsumer<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>, Integer>> expanders
            = createStaticHistoryExpanders(parameters);
        history.forEach(h->{
            int size = h.history().data().values().iterator().next().size();
            expanders.forEach(expander->expander.accept(h, size));
        });
    }

    private static List<BiConsumer<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>, Integer>> createStaticHistoryExpanders
    (
        HistoryParameters parameters
    )
    {
        List<BiConsumer<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>, Integer>> expanders = new ArrayList<>(2);
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
        TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData> history,
        StaticColumn staticColumn,
        HistoryColumn historyColumn,
        int size
    )
    {
        history.history().data().put
        (
            historyColumn,
            Collections.nCopies(size, history.staticData().data().get(staticColumn))
        );
    }

    private static void expandDivisionParameters(HistoryParameters parameters)
    {
        if(shouldExpandDivisions(parameters))
            parameters.historyColumns().add(HistoryColumn.DIVISION_ID);
    }

    private static boolean shouldExpandDivisions(HistoryParameters parameters)
    {
        return parameters.historyColumns().contains(HistoryColumn.TIER_TYPE)
            || parameters.historyColumns().contains(HistoryColumn.LEAGUE_TYPE);
    }

    @SuppressWarnings("unchecked")
    private void expandDivisions
    (
        List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> history,
        HistoryParameters parameters
    )
    {
        if(!shouldExpandDivisions(parameters)) return;

        Map<Integer, Division> divisions = divisionDAO.findByIds
        (
            history.stream()
                .map(TeamHistory::history)
                .map(data->(List<Integer>) data.data().get(HistoryColumn.DIVISION_ID))
                .flatMap(Collection::stream)
                .collect(Collectors.toSet())
        )
            .stream()
            .collect(Collectors.toMap(Division::getId, Function.identity()));
        if(divisions.isEmpty()) return;

        Map<Integer, LeagueTier> tiers = leagueTierDAO.findByIds
        (
            divisions.values().stream()
                .map(Division::getTierId)
                .collect(Collectors.toSet())
        )
            .stream()
            .collect(Collectors.toMap(LeagueTier::getId, Function.identity()));
        boolean expandTiers = parameters.historyColumns().contains(HistoryColumn.TIER_TYPE);
        boolean expandLeagues = parameters.historyColumns().contains(HistoryColumn.LEAGUE_TYPE);

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
            List<Integer> divisionIds = (List<Integer>) h.history().data().get(HistoryColumn.DIVISION_ID);
            List<Object> historyTiers = expandTiers
                ? new ArrayList<>(divisionIds.size())
                : List.of();
            List<Object> historyLeagues = expandLeagues
                ? new ArrayList<>(divisionIds.size())
                : List.of();
            for(Integer id : divisionIds)
            {
                Division currentDivision = divisions.get(id);
                LeagueTier currentTier = tiers.get(currentDivision.getTierId());

                if(expandTiers) historyTiers
                    .add(minConversionService.convert(currentTier.getType(), Object.class));
                if(expandLeagues)
                {
                    League currentLeague = leagues.get(currentTier.getLeagueId());
                    historyLeagues
                        .add(minConversionService.convert(currentLeague.getType(), Object.class));
                }

            }
            if(expandTiers) h.history().data().put(HistoryColumn.TIER_TYPE, historyTiers);
            if(expandLeagues) h.history().data().put(HistoryColumn.LEAGUE_TYPE, historyLeagues);
        });
    }

    private static String generateFindSummaryQuery
    (
        HistorySummaryParameters parameters,
        GroupMode groupMode
    )
    {
        return FIND_SUMMARY_TEMPLATE.formatted
        (
            Stream.concat
            (
                Stream.of(groupMode.getGroupStaticColumns(), parameters.staticColumns())
                    .flatMap(Collection::stream)
                    .distinct()
                    .map(StaticColumn::getName),
                parameters.summaryColumns().stream()
                    .map(SummaryColumn::getDataAliasedColumn)
                    .distinct()
            )
                .collect(Collectors.joining(PARAMETER_JOIN_DELIMITER)),
            Stream.of(groupMode.getGroupStaticColumns(), parameters.staticColumns())
                .flatMap(Collection::stream)
                .distinct()
                .map(StaticColumn::getJoins)
                .flatMap(Collection::stream)
                .distinct()
                .collect(Collectors.joining(JOIN_JOIN_DELIMITER)),

            Stream.concat
            (
                parameters.staticColumns().stream()
                    .map(StaticColumn::getAggregationAliasedName),
                parameters.summaryColumns().stream()
                    .map(SummaryColumn::getAggregationAliasedFunction)
            )
                .collect(Collectors.joining(PARAMETER_JOIN_DELIMITER)),

            groupMode.getGroupStaticColumns().stream()
                .map(StaticColumn::getName)
                .collect(Collectors.joining(PARAMETER_JOIN_DELIMITER)),
            FIND_SUMMARY_ID_COLUMN.get(groupMode)
        );
    }

    public List<TeamHistorySummary<RawTeamHistoryStaticData, RawTeamHistorySummaryData>> findSummary
    (
        @NotNull Set<Long> teamIds,
        @Nullable OffsetDateTime from,
        @Nullable OffsetDateTime to,
        @NotNull Set<StaticColumn> staticColumns,
        @NotNull Set<SummaryColumn> summaryColumns,
        @NotNull GroupMode groupMode
    )
    {
        if(teamIds.isEmpty() || (summaryColumns.isEmpty() && staticColumns.isEmpty())) return List.of();
        checkParameters(from, to, staticColumns, groupMode);

        String query = generateFindSummaryQuery
        (
            new HistorySummaryParameters(staticColumns, summaryColumns),
            groupMode
        );
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("teamIds", teamIds)
            .addValue("from", from)
            .addValue("to", to);
        return template.query(query, params, TEAM_HISTORY_SUMMARY_EXTRACTOR);
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

    private record HistorySummaryParameters
    (
        @NotNull Set<StaticColumn> staticColumns,
        @NotNull Set<SummaryColumn> summaryColumns
    )
    {}

    private record HistoryLegacyUidGroup
    (
        @NotNull TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData> history,
        @NotNull TeamLegacyUid legacyUid
    )
    {}


}
