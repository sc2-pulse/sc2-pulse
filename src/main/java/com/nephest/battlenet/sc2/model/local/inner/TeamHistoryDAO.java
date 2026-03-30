// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.command.CommandResponse;
import com.clickhouse.client.api.data_formats.ClickHouseBinaryFormatReader;
import com.clickhouse.client.api.query.QueryResponse;
import com.clickhouse.client.api.query.QuerySettings;
import com.clickhouse.data.ClickHouseFormat;
import com.nephest.battlenet.sc2.model.Identifiable;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.model.util.ClickHouseUtil;
import com.nephest.battlenet.sc2.model.util.PostgreSQLUtils;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.model.validation.UInt32EpochSeconds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

@Repository
@Validated
public class TeamHistoryDAO
{

    private static final Logger LOG = LoggerFactory.getLogger(TeamHistoryDAO.class);

    public static final String SYNC_FROM_VAR_NAME = "team_state.clickhouse.from";
    public static final String TABLE_NAME = "team_state";
    public static final int DEFAULT_SYNC_BATCH_SIZE = 10000;

    public enum Source
    implements Identifiable
    {

        SYSTEM(0),
        USER(1);

        private final int id;

        Source(int id)
        {
            this.id = id;
        }

        public static Source from(int id)
        {
            for (Source source : Source.values())
                if (source.getId() == id) return source;

            throw new IllegalArgumentException("Invalid id");
        }

        @Override
        public int getId()
        {
            return id;
        }

    }

    public enum HistoryColumn
    {
        TIMESTAMP("timestamp", "UInt32"),
        RATING("rating", "Int32"),
        GAMES("games", "Int32"),
        WINS("wins", "Int32", "-1"),
        LEAGUE_TYPE("league_type", "Int8"),
        TIER_TYPE("tier_type", "Int8"),
        DIVISION_BATTLENET_ID("division_battlenet_id", "Int64"),
        GLOBAL_RANK("global_rank", "Int32", "-1"),
        REGION_RANK("region_rank", "Int32", "-1"),
        LEAGUE_RANK("league_rank", "Int32", "-1"),
        GLOBAL_TEAM_COUNT("global_team_count", "Int32", "-1"),
        REGION_TEAM_COUNT("region_team_count", "Int32", "-1"),
        LEAGUE_TEAM_COUNT("league_team_count", "Int32", "-1"),
        SEASON("season", "Int16");

        private final String columName, sentinelValue, tupleNameDefinition;

        HistoryColumn
        (
            String columName,
            String sqlType,
            String sentinelValue
        )
        {
            this.columName = columName;
            this.sentinelValue = sentinelValue;
            this.tupleNameDefinition = this.name()
                + " Array("
                + (sentinelValue != null ? "Nullable(" + sqlType + ")" : sqlType)
                + ")";
        }

        HistoryColumn
        (
            String columName,
            String sqlType
        )
        {
            this(columName, sqlType, null);
        }

        public String getColumName()
        {
            return columName;
        }

        public String getTupleNameDefinition()
        {
            return tupleNameDefinition;
        }

        public String getTupleDataDefinition(int ix)
        {
            String tupleEntry = "t." + ix;
            return "arrayMap(t -> "
                + (
                    sentinelValue != null
                        ? "if(" + tupleEntry + " = " + sentinelValue + ", NULL, " + tupleEntry + ")"
                        : tupleEntry
                )
                + ", sorted)";
        }

    }

    public enum StaticColumn
    {

        LEGACY_UID("team_legacy_uid");

        private final String name;

        StaticColumn(String name)
        {
            this.name = name;
        }

        public String getName()
        {
            return name;
        }

    }

    public enum SummaryColumn
    {
        GAMES
        (
            "games",
            "toInt32OrDefault(SUM(games_delta) - argMin(games_delta, timestamp) + 1, "
                + Integer.MAX_VALUE + "::Int32)"
        ),

        RATING_MIN("rating_min",  "MIN(rating)"),
        RATING_AVG("rating_avg", "AVG(rating)"),
        RATING_MAX("rating_max", "MAX(rating)"),
        RATING_LAST("rating_last", "argMax(rating, timestamp)"),
        REGION_RANK_LAST
        (
            "region_rank_last",
            "nullIf(argMax(region_rank, timestamp), -1)"
        ),
        REGION_TEAM_COUNT_LAST
        (
            "region_team_count_last",
            "nullIf(argMax(region_team_count, timestamp), -1)"
        );

        private final String name, function, aliasedFunction;

        SummaryColumn
        (
            String name,
            String function
        )
        {
            this.name = name;
            this.function = function;
            this.aliasedFunction = function + " AS " + name;
        }

        public String getName()
        {
            return name;
        }

        public String getFunction()
        {
            return function;
        }

        public String getAliasedFunction()
        {
            return aliasedFunction;
        }

    }

    private static final String FIND_COLUMNS_TEMPLATE =
        """
        SELECT
        tuple(team_legacy_uid)::Tuple(LEGACY_UID String) AS staticData,
        tuple(%3$s)::Tuple(%4$s) AS history
        FROM
        (
            SELECT team_legacy_uid,
            arraySort(t -> t.%2$s, groupArray(tuple(%1$s))) AS sorted
            FROM team_state
            WHERE team_legacy_uid IN({teamLegacyUids:Array(String)})
            %5$s
            %6$s
            GROUP BY team_legacy_uid
        )
        """;

    private static final String FIND_SUMMARY_TEMPLATE =
        """
            SELECT team_legacy_uid,
            %1$s
            FROM team_state
            WHERE team_legacy_uid IN({teamLegacyUids:Array(String)})
            AND source = 'USER'
            %2$s
            %3$s
            GROUP BY team_legacy_uid
        """;

    private static final String FIND_MAX_TEAM_STATE_TIMESTAMP_PG =
        """
            WITH timestamp_frame AS
            (
                SELECT timestamp
                FROM team_state
                WHERE timestamp > :from
                ORDER BY timestamp
                LIMIT :limit
            )
            SELECT MAX(timestamp)
            FROM timestamp_frame
        """;

    private static final String FIND_MAX_TEAM_STATE_TIMESTAMP =
        "SELECT MAX(timestamp) AS max_timestamp FROM team_state";

    private static final String SYNC_TEMPLATE =
        """
        INSERT INTO team_state
        (
            team_legacy_uid,
            timestamp,
            division_battlenet_id,
            league_type,
            tier_type,
            season,
            wins,
            games,
            games_delta,
            rating,
            global_rank,
            region_rank,
            league_rank,
            global_team_count,
            region_team_count,
            league_team_count,
            source
        )
        SELECT
        team_legacy_uid,
        timestamp,
        division_battlenet_id,
        league_type,
        tier_type,
        season,
        wins,
        games,
        games_delta,
        rating,
        global_rank,
        region_rank,
        league_rank,
        global_team_count,
        region_team_count,
        league_team_count,
        source
        FROM postgresql('%1$s', '%2$s', 'team_state_denormalized', '%3$s', {password:String}) AS team_state_pg
        WHERE team_state_pg.timestamp > parseDateTime64BestEffort({from:String}, 6)
        AND team_state_pg.timestamp <= parseDateTime64BestEffort({to:String}, 6)
        """;
    private final String SYNC;
    private final String postgresPassword;

    private final ConversionService sc2StatsConversionService;

    private final NamedParameterJdbcTemplate template;
    private final Client clickHouseClient;
    private final VarDAO varDAO;
    private final AtomicBoolean syncLock = new AtomicBoolean(false);

    private int syncBatchSize = DEFAULT_SYNC_BATCH_SIZE;

    @Autowired
    public TeamHistoryDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        Client clickHouseClient,
        VarDAO varDAO,
        @Qualifier("sc2StatsConversionService") ConversionService sc2StatsConversionService,
        JdbcConnectionDetails jdbcConnectionDetails,
        @Qualifier("postgresUrlProperties") Properties postgresUrlProperties
    )
    {
        this.template = template;
        this.clickHouseClient = clickHouseClient;
        this.varDAO = varDAO;
        this.sc2StatsConversionService = sc2StatsConversionService;
        SYNC = SYNC_TEMPLATE.formatted
        (
            PostgreSQLUtils.getContainerHostAndPort(postgresUrlProperties),
            postgresUrlProperties.get(PostgreSQLUtils.DRIVER_DB_NAME),
            jdbcConnectionDetails.getUsername()
        );
        postgresPassword = jdbcConnectionDetails.getPassword();
    }

    private static String generateFindColumnsQuery
    (
        Set<HistoryColumn> historyColumns,
        @Nullable OffsetDateTime from,
        @Nullable OffsetDateTime to
    )
    {
        List<HistoryColumn> historyColumnList = new ArrayList<>(historyColumns);
        int timestampIx = historyColumnList.indexOf(HistoryColumn.TIMESTAMP);
        return FIND_COLUMNS_TEMPLATE.formatted
        (
            (
                timestampIx != -1
                    ? historyColumnList.stream()
                    : Stream.concat(historyColumnList.stream(), Stream.of(HistoryColumn.TIMESTAMP))
            )
                .map(HistoryColumn::getColumName)
                .collect(Collectors.joining(", ")),
            //clickhouse index is 1 based
            timestampIx != -1 ? timestampIx + 1 : historyColumns.size() + 1,
            IntStream.range(0, historyColumnList.size())
                .mapToObj(ix->historyColumnList.get(ix).getTupleDataDefinition(ix + 1))
                .collect(Collectors.joining(", ")),
            historyColumnList.stream()
                .map(HistoryColumn::getTupleNameDefinition)
                .collect(Collectors.joining(", ")),
            from == null ? "" : "AND timestamp >= {from:DateTime}",
            to == null ? "" : "AND timestamp < {to:DateTime}"
        );
    }

    private static void checkParameters
    (
        @Nullable OffsetDateTime from,
        @Nullable OffsetDateTime to
    )
    {
        if(from != null && to != null && !from.isBefore(to))
            throw new IllegalArgumentException("'from' parameter must be before 'to' parameter");
    }

    public void findHistoryJson
    (
        @NotNull @Valid Set<TeamLegacyUid> teamLegacyUids,
        @Nullable @Valid @UInt32EpochSeconds OffsetDateTime from,
        @Nullable @Valid @UInt32EpochSeconds OffsetDateTime to,
        @NotNull Set<HistoryColumn> historyColumns,
        @NotNull Consumer<InputStream> isConsumer
    )
    {
        if(teamLegacyUids.isEmpty() || historyColumns.isEmpty())
        {
            try(InputStream is = ClickHouseUtil.createEmptyJsonArrayInputStream())
            {
                isConsumer.accept(is);
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
            return;
        }

        checkParameters(from, to);

        List<TeamLegacyUid> expandedTeamLegacyUids = teamLegacyUids.stream()
            .flatMap(TeamLegacyUid::expandWildcards)
            .toList();
        Map<String, Object> params = new HashMap<>(3);
        params.put
        (
            "teamLegacyUids",
            expandedTeamLegacyUids.stream()
                .map(uid->sc2StatsConversionService.convert(uid, String.class))
                .map(ClickHouseUtil::quote)
                .toList()
        );
        if(from != null) params.put("from", from.toEpochSecond());
        if(to != null) params.put("to", to.toEpochSecond());
        try
        (
            QueryResponse response = clickHouseClient.query
            (
                generateFindColumnsQuery(historyColumns, from, to),
                params,
                new QuerySettings().setFormat(ClickHouseFormat.JSONEachRow)
                    .serverSetting("output_format_json_array_of_rows", "1")
            ).get()
        )
        {
            try(InputStream is = response.getInputStream())
            {
                isConsumer.accept(is);
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private static String generateFindSummaryQuery
    (
        Set<SummaryColumn> summaryColumns,
        @Nullable OffsetDateTime from,
        @Nullable OffsetDateTime to
    )
    {

        return FIND_SUMMARY_TEMPLATE.formatted
        (
            summaryColumns.stream()
                .map(SummaryColumn::getAliasedFunction)
                .collect(Collectors.joining(", ")),
            from == null ? "" : "AND timestamp >= {from:DateTime}",
            to == null ? "" : "AND timestamp < {to:DateTime}"
        );
    }

    public List<TeamHistorySummary<RawTeamHistoryStaticData, RawTeamHistorySummaryData>> findSummary
    (
        @NotNull @Valid Set<TeamLegacyUid> teamLegacyUids,
        @Nullable @Valid @UInt32EpochSeconds OffsetDateTime from,
        @Nullable @Valid @UInt32EpochSeconds OffsetDateTime to,
        @NotNull Set<SummaryColumn> summaryColumns
    )
    {
        if(teamLegacyUids.isEmpty() || summaryColumns.isEmpty()) return List.of();
        checkParameters(from, to);

        List<TeamLegacyUid> expandedTeamLegacyUids = teamLegacyUids.stream()
            .flatMap(TeamLegacyUid::expandWildcards)
            .toList();
        Map<String, Object> queryParams = new HashMap<>(3);
        queryParams.put
        (
            "teamLegacyUids",
            expandedTeamLegacyUids.stream()
                .map(uid->sc2StatsConversionService.convert(uid, String.class))
                .map(ClickHouseUtil::quote)
                .toList()
        );
        if(from != null) queryParams.put("from", from.toEpochSecond());
        if(to != null) queryParams.put("to", to.toEpochSecond());

        List<TeamHistorySummary<RawTeamHistoryStaticData, RawTeamHistorySummaryData>> result
            = new ArrayList<>(expandedTeamLegacyUids.size());
        try
        (
            QueryResponse response = clickHouseClient
                .query(generateFindSummaryQuery(summaryColumns, from, to), queryParams)
                .get();
            ClickHouseBinaryFormatReader reader = clickHouseClient.newBinaryFormatReader(response)
        )
        {
            Map<String, Object> row;
            while ((row = reader.next()) != null)
            {
                Map<SummaryColumn, Object> summaryData = new EnumMap<>(SummaryColumn.class);
                for(SummaryColumn summaryColumn : summaryColumns)
                    summaryData.put(summaryColumn, row.get(summaryColumn.getName()));
                result.add(new TeamHistorySummary<>(
                    new RawTeamHistoryStaticData(Map.of(
                        StaticColumn.LEGACY_UID,
                        row.get(StaticColumn.LEGACY_UID.getName())
                    )),
                    new RawTeamHistorySummaryData(summaryData)
                ));
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        return result;
    }

    private Optional<OffsetDateTime> getMaxTimestamp()
    {
        try
        (
            QueryResponse response = clickHouseClient
                .query(FIND_MAX_TEAM_STATE_TIMESTAMP)
                .get();
            ClickHouseBinaryFormatReader reader = clickHouseClient.newBinaryFormatReader(response)
        )
        {
            return reader.next() == null
                ? Optional.empty()
                : Optional.ofNullable(reader.getOffsetDateTime("max_timestamp"));
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private OffsetDateTime getSyncFrom()
    {
        OffsetDateTime fromVar = varDAO.find(SYNC_FROM_VAR_NAME)
            .map(OffsetDateTime::parse)
            .orElse(SC2Pulse.EPOCH_ODT);
        OffsetDateTime maxTimestamp = getMaxTimestamp().orElse(SC2Pulse.EPOCH_ODT);
        return maxTimestamp.isAfter(fromVar)
            ? maxTimestamp.plus
                (
                    SC2Pulse.CHRONO_UNIT_MIN.between(maxTimestamp, maxTimestamp.plusSeconds(1)) - 1,
                    SC2Pulse.CHRONO_UNIT_MIN
                )
            : fromVar;
    }

    private OffsetDateTime getSyncTo(OffsetDateTime from, int limit)
    {
        return template.queryForObject
        (
            FIND_MAX_TEAM_STATE_TIMESTAMP_PG,
            Map.of
            (
                "from", from,
                "limit", limit
            ),
            OffsetDateTime.class
        );
    }

    private long syncBatch(int batchSize)
    {
        OffsetDateTime from = getSyncFrom();
        OffsetDateTime to = getSyncTo(from, batchSize);
        if(to == null) return 0;

        LOG.trace("Syncing team history {}-{}", from, to);
        long rowsSynced = 0;
        Map<String, Object> queryParams = Map.of
        (
            "password", postgresPassword,
            "from", from,
            "to", to
        );
        try(CommandResponse response = clickHouseClient.execute(SYNC, queryParams).get())
        {
            rowsSynced = response.getWrittenRows();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        varDAO.merge(SYNC_FROM_VAR_NAME, to.toString());
        if(rowsSynced > 0) LOG.trace("Synced {} team history rows batch", rowsSynced);
        return rowsSynced;
    }

    public int getSyncBatchSize()
    {
        return syncBatchSize;
    }

    public void setSyncBatchSize(int syncBatchSize)
    {
        if(syncBatchSize < 1) throw new IllegalArgumentException("Positive batch size expected");

        this.syncBatchSize = syncBatchSize;
    }

    public long trySync()
    {
        if(!syncLock.compareAndSet(false, true))
        {
            LOG.trace("Tried to sync team history but the task was already active");
            return -1;
        }
        try
        {
            long syncedBatch = -1;
            long syncedTotal = 0;
            while (syncedBatch != 0)
            {
                syncedBatch = syncBatch(getSyncBatchSize());
                syncedTotal += syncedBatch;
            }
            if (syncedTotal > 0) LOG.info("Synced {} team history rows", syncedTotal);
            return syncedTotal;
        }
        finally
        {
            syncLock.compareAndSet(true, false);
        }
    }


}
