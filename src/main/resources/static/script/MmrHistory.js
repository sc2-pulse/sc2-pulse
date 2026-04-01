// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class MmrHistory
{
    constructor
    (
        domPrefix,
        getQueueDataFn,
        getChartRegionFn,
        getTeamNameFn,
        teamNameHeader = "Team",
        createTeamNameElementFn = legacyUidData=>document.createTextNode(getTeamNameFn(legacyUidData.toPulseString())),
        completePointTimeout = MmrHistory.MMR_HISTORY_COMPLETE_POINT_TIMEOUT,
    )
    {
        this.domPrefix = domPrefix;
        this.getQueueDataFn = getQueueDataFn;
        this.getChartRegionFn = getChartRegionFn;
        this.getTeamNameFn = getTeamNameFn;
        this.teamNameHeader = teamNameHeader;
        this.createTeamNameElementFn = createTeamNameElementFn;
        this.completePointTaskName = `${domPrefix}-${MmrHistory.MMR_HISTORY_COMPLETE_POINT_TASK_NAME}`;
        this.completePointTimeout = completePointTimeout;

        this.mmrHistory = {};
    }

    static createHistoryIndex(history, historyColumn)
    {
        const historyLength = Object.values(history)[0].length;
        const index = {};
        for(let i = 0; i < historyLength; i++)
            index[history[historyColumn.fullName][i]] = i;
        return index;
    }

    static calculateHistoryTimestampIndex(historyData)
    {
        historyData.forEach(d=>{
            d.timestampIndex = MmrHistory.createHistoryIndex(d.history, TEAM_HISTORY_HISTORY_COLUMN.TIMESTAMP);
        });
    }

    static calculateHistoryStats(history)
    {
        return {
            length: history.map(h=>Object.values(h.history)[0].length).reduce((a, b) => a + b, 0)
        };
    }

    recalculateHistoryStats()
    {
        if(!this.mmrHistory.history?.data) return;

        this.mmrHistory.history.stats = MmrHistory.calculateHistoryStats(this.mmrHistory.history.data);
    }

    resetHistoryFilteredData()
    {
        this.mmrHistory.history.data = structuredClone(this.mmrHistory.history.originalData);
        this.recalculateHistoryStats();
    }

    resetParameters()
    {
        delete this.mmrHistory.parameters;
    }

    resetHistoryModel()
    {
        delete this.mmrHistory.history;
    }

    resetHistoryView()
    {
        document.querySelector(`#${this.domPrefix}-chart-container`).classList.add("d-none");
    }

    resetHistory(resetLoading = false)
    {
        this.resetHistoryModel();
        this.resetHistoryView();
        if(resetLoading) Session.resetLoadingIndicator(document.querySelector(`#${this.domPrefix}-history-loading`));
    }

    resetHistoryAll(resetLoading = false)
    {
        this.resetHistory(resetLoading);
        this.resetSummary(resetLoading);
    }

    updateHistoryModel(legacyUids, from, to, historyColumns)
    {
        return Session.SC2_PULSE_API.getTeamHistories({legacyUids, from, to, historyColumns})
            .then(history=>{
                const dataHistory = {};
                this.mmrHistory.history = dataHistory;
                dataHistory.data = history;
                if(history.length > 0) {
                    MmrHistory.calculateHistoryTimestampIndex(history);
                    dataHistory.originalData = structuredClone(history);
                    this.recalculateHistoryStats();
                    this.filterHistory();
                }
                return dataHistory;
            });
    }

    createFilterText()
    {
        const parameters = this.mmrHistory.parameters;
        const lines = [];
        if(parameters.queueData.queue?.name != null) lines.push(parameters.queueData.queue.name);
        if(parameters.from != null) lines.push(
            Util.DATE_TIME_FORMAT.format(parameters.from)
            + " - " + Util.DATE_TIME_FORMAT.format(parameters.to)
        );
        lines.push((this.mmrHistory.history?.stats?.length || 0) + " entries");
        return "(" + lines.join(", ") + ")";
    }

    updateHistoryView()
    {
        if(!this.mmrHistory?.history?.data) return;

        const parameters = this.mmrHistory.parameters;
        const xAxisType = (localStorage.getItem(`${this.domPrefix}-x-type`) || "true") === "true" ? "time" : "category";

        const data = {};
        const rawData = {index: {}, history: {}};
        ChartUtil.CHART_RAW_DATA.set(`${this.domPrefix}-table`, {rawData: rawData, additionalDataGetter: this.getAdditionalHistoryData.bind(this)});
        ChartUtil.batchExecute(
            `${this.domPrefix}-table`,
            ()=>ChartUtil.setCustomConfigOption(`${this.domPrefix}-table`, "region", this.getChartRegionFn()),
            false
        );
        const mmrYValueGetter = MmrHistory.MMR_Y_VALUE_OPERATIONS.get(parameters.yAxis).get;
        for(const curHistory of this.mmrHistory.history.data) {
            const curHistoryLength = Object.values(curHistory.history)[0].length;
            const teamName = this.getTeamNameFn(curHistory.staticData[TEAM_HISTORY_STATIC_COLUMN.LEGACY_UID.fullName]);
            rawData.history[teamName] = curHistory;
            for(let i = 0; i < curHistoryLength; i++) {
                const curTimestamp = curHistory.history[TEAM_HISTORY_HISTORY_COLUMN.TIMESTAMP.fullName][i];
                let curRow = data[curTimestamp];
                if(!curRow) {
                    curRow = {};
                    data[curTimestamp] = curRow;
                    rawData.index[curTimestamp] = {};
                }
                curRow[teamName] = mmrYValueGetter(curHistory, i);
                rawData.index[curTimestamp][teamName] = i;
            }
        }
        TableUtil.updateVirtualColRowTable
        (
            document.getElementById(`${this.domPrefix}-table`),
            data,
            (tableData=>{
                if(parameters.showLeagues) MmrHistory.decorateMmrPointsIndex(tableData, rawData);
                ChartUtil.CHART_RAW_DATA.get(`${this.domPrefix}-table`).data = tableData;
            }),
            parameters.queueData.queue == TEAM_FORMAT._1V1
                ? (a, b)=>EnumUtil.enumOfName(a, RACE).order - EnumUtil.enumOfName(b, RACE).order
                : null,
            parameters.queueData.queue == TEAM_FORMAT._1V1 ? (name)=>EnumUtil.enumOfName(name, RACE).name : (name)=>name,
            xAxisType == "time" ? dt=>parseInt(dt) * 1000 : dt=>Util.DATE_TIME_FORMAT.format(new Date(parseInt(dt) * 1000))
        );
        document.getElementById(`${this.domPrefix}-history-filters`).textContent = this.createFilterText();
        document.querySelector(`#${this.domPrefix}-chart-container`).classList.remove("d-none");
    }

    static filterHistoryLastSeason(history)
    {
        if(!history[TEAM_HISTORY_HISTORY_COLUMN.SEASON.fullName]) return history;

        const names = Object.keys(history);
        const filtered = {};
        for(const name of names) filtered[name] = [];
        for(let i = 0; i < history[TEAM_HISTORY_HISTORY_COLUMN.SEASON.fullName].length; i++) {
            const nextSeason = i + 1 == history[TEAM_HISTORY_HISTORY_COLUMN.SEASON.fullName].length
                ? null
                : history[TEAM_HISTORY_HISTORY_COLUMN.SEASON.fullName][i + 1];
            const curSeason = history[TEAM_HISTORY_HISTORY_COLUMN.SEASON.fullName][i];
            if(curSeason != nextSeason) {
                for(const name of names) {
                    filtered[name].push(history[name][i]);
                }
            }
        }
        return filtered;
    }

    static calculateHistoryMax(history, valueGetter, maxGetter)
    {
        const length = Object.values(history.history)[0].length;
        return maxGetter(Array.from(Array(length).keys()).map(i=>valueGetter(history, i)));
    }

    static filterHistoryBestRace(histories, valueGetter, maxGetter, comparator)
    {
        if(histories.length == 1) return histories[0];

        return histories.map(h=>[h, MmrHistory.calculateHistoryMax(h, valueGetter, maxGetter)])
            .sort((a, b)=>comparator(a[1], b[1]))[0][0];
    }

    filterHistory()
    {
        if(this.mmrHistory.parameters.endOfSeason)
            for(let i = 0; i < this.mmrHistory.history.data.length; i++)
                this.mmrHistory.history.data[i].history
                    = MmrHistory.filterHistoryLastSeason(this.mmrHistory.history.data[i].history);
        if(this.mmrHistory.parameters.bestRaceOnly) {
            const operations = MmrHistory.MMR_Y_VALUE_OPERATIONS.get(this.mmrHistory.parameters.yAxis);
            this.mmrHistory.history.data = [MmrHistory.filterHistoryBestRace(
                this.mmrHistory.history.data,
                operations.get, operations.max, operations.compare)];
        }
        if(this.mmrHistory.parameters.endOfSeason || this.mmrHistory.parameters.bestRaceOnly)
            this.recalculateHistoryStats();
        if(this.mmrHistory.parameters.endOfSeason && this.mmrHistory.history.data != null)
            MmrHistory.calculateHistoryTimestampIndex(this.mmrHistory.history.data);
    }

    updateHistory()
    {
        this.resetHistory();
        this.setParameters();
        const params = this.mmrHistory.parameters;

        const historyColumns = new Set(MmrHistory.MMR_Y_REQUIRED_HISTORY_COLUMNS.get(params.yAxis));
        if(params.showLeagues) historyColumns.add(TEAM_HISTORY_HISTORY_COLUMN.LEAGUE_TYPE);
        if(params.endOfSeason) historyColumns.add(TEAM_HISTORY_HISTORY_COLUMN.SEASON);
        params.historyColumns = historyColumns;
        return this.updateHistoryModel
        (
            params.queueData.legacyUids,
            params.from,
            params.to,
            historyColumns
        )
            .then(history=>{
                this.updateHistoryView();
                return {data: history, status: LOADING_STATUS.COMPLETE};
            });
    }

    setParameters(rewrite = false)
    {
        if(!rewrite && this.mmrHistory.parameters != null) return false;

        this.mmrHistory.parameters = this.createParameters();
        return true;
    }

    getQueueData()
    {
        return this.getQueueDataFn();
    }

    createParameters()
    {
        const depth = document.getElementById(`${this.domPrefix}-depth`).value || null;
        const to = depth ? new Date() : null;
        const from = depth ? (new Date(to.valueOf() - (depth * 24 * 60 * 60 * 1000))) : null;
        const yAxis = localStorage.getItem(`${this.domPrefix}-y-axis`) || "mmr";
        return {
            from: from,
            to: to,
            queueData: this.getQueueData(),
            yAxis: yAxis,
            endOfSeason: (localStorage.getItem(`${this.domPrefix}-season-last`) || "false") === "true",
            showLeagues: (localStorage.getItem(`${this.domPrefix}-leagues`) || "false") === "true",
            bestRaceOnly: (localStorage.getItem(`${this.domPrefix}-best-race`) || "false") === "true"
        };
    }

    enqueueUpdateHistory()
    {
        return Session.load(document.querySelector(`#${this.domPrefix}-history-loading`), n=>this.updateHistory());
    }

    resetUpdateHistoryAllLoading()
    {
        Session.resetLoadingIndicator(document.querySelector(`#${this.domPrefix}-history-all-loading`));
    }

    updateHistoryAll(resetParameters = true)
    {
        const oldParameters = this.mmrHistory.parameters;
        this.mmrHistory = {};
        if(!resetParameters) this.mmrHistory.parameters = oldParameters;
        return Promise.allSettled([this.enqueueUpdateHistory(),
            this.enqueueUpdateSummary()
        ])
            .then(results=>{
                Util.throwFirstSettledError(results);
                return {data: results, status: LOADING_STATUS.COMPLETE};
            });
    }

    enqueueUpdateHistoryAll(resetParameters = true)
    {
        return Session.load(document.querySelector(`#${this.domPrefix}-history-all-loading`), n=>this.updateHistoryAll(resetParameters));
    }

    resetSummaryNumericView()
    {
        ElementUtil.removeChildren(document.querySelector(`#${this.domPrefix}-summary-table tbody`));
    }

    resetSummaryProgressView()
    {
        ElementUtil.removeChildren(document.querySelector(`#${this.domPrefix}-tier-progress-table tbody`));
    }

    resetSummaryView()
    {
        this.resetSummaryNumericView();
        this.resetSummaryProgressView();
    }

    resetSummaryModel()
    {
        delete this.mmrHistory.summary;
    }

    resetSummaryLoading()
    {
        Session.resetLoadingIndicator(document.querySelector(`#${this.domPrefix}-summary-table-container`));
    }

    resetSummary(resetLoading = false)
    {
        this.resetSummaryModel();
        this.resetSummaryView();
        if(resetLoading) this.resetSummaryLoading();
    }

    static updateSummaryWithTeams(summaries, teams)
    {
        const legacyIdSummaries = Util.toMap(summaries, s=>s.staticData[TEAM_HISTORY_STATIC_COLUMN.LEGACY_UID]);
        for(const team of teams) {
            const summary = legacyIdSummaries.get(team.legacyUid);
            if(summary == null) continue;

            summary.summary[TEAM_HISTORY_SUMMARY_COLUMN.REGION_RANK_LAST.fullName] = team.regionRank;
            summary.summary[TEAM_HISTORY_SUMMARY_COLUMN.REGION_TEAM_COUNT_LAST.fullName] = team.regionTeamCount;
        }
    }

    updateSummaryModel(legacyUids, from, to, summaryColumns)
    {
        return Promise.all([
            Session.SC2_PULSE_API.getTeamHistorySummaries({legacyUids, from, to, summaryColumns}),
            Session.SC2_PULSE_API.getTeams({legacyUids, fromSeason: Session.currentSeasons[0].battlenetId})
        ])
            .then(summaryBatch=>{
                const summary = summaryBatch[0];
                const currentTeams = summaryBatch[1];
                if(summary.length > 0) {
                    summary.forEach(MmrHistory.addLegacyUidData);
                    summary.sort((a, b)=>b.summary[TEAM_HISTORY_SUMMARY_COLUMN.RATING_MAX.fullName] -
                        a.summary[TEAM_HISTORY_SUMMARY_COLUMN.RATING_MAX.fullName]);
                    if(currentTeams.length > 0) MmrHistory.updateSummaryWithTeams(summary, currentTeams);
                }
                this.mmrHistory.summary = summary;
                return summary;
            });
    }

    updateSummaryNumericView()
    {
        if(!this.mmrHistory.summary) return;

        MmrHistory.updateGamesAndAverageMmrTable(
            document.querySelector(`#${this.domPrefix}-summary-table`),
            this.mmrHistory.summary,
            this.mmrHistory.parameters.numericSummaryColumns,
            this.teamNameHeader,
            this.createTeamNameElementFn
        );
    }

    updateSummaryProgressView()
    {
        if(!this.mmrHistory.summary) return;

        MmrHistory.updateTierProgressTable(
            document.querySelector(`#${this.domPrefix}-tier-progress-table`),
            this.mmrHistory.summary,
            this.teamNameHeader,
            this.createTeamNameElementFn
        );
    }

    updateSummaryView()
    {
        this.updateSummaryNumericView();
        this.updateSummaryProgressView();
    }

    updateSummary()
    {
        this.resetSummary();
        this.setParameters();
        const params = this.mmrHistory.parameters;

        params.numericSummaryColumns = new Set(MmrHistory.MMR_Y_REQUIRED_NUMERIC_SUMMARY_COLUMNS.get(params.yAxis));
        params.progressSummaryColumns = new Set(MmrHistory.MMR_REQUIRED_PROGRESS_SUMMARY_COLUMNS);
        params.summaryColumns = new Set([...params.numericSummaryColumns, ...params.progressSummaryColumns]);
        return this.updateSummaryModel(
            params.queueData.legacyUids,
            params.from,
            params.to,
            params.summaryColumns
        )
            .then(history=>{
                this.updateSummaryView();
                return {data: history, status: LOADING_STATUS.COMPLETE};
            });
    }

    enqueueUpdateSummary()
    {
        return Session.load(document.querySelector(`#${this.domPrefix}-summary-table-container`), n=>this.updateSummary());
    }

    static updateSummaryTableBody(tbody, summaries, summaryColumns, createTeamNameElementFn)
    {
        for(const summary of summaries) {
            const tr = tbody.insertRow();
            tr.insertCell().appendChild(createTeamNameElementFn(summary.legacyUidData));
            for(const column of summaryColumns) {
                const val = summary.summary[column.fullName];
                const td = tr.insertCell();
                if(val != null) td.textContent = MmrHistory.MMR_Y_SUMMARY_COLUMN_FORMATTERS.get(column)?.(val) || val;
            }
        }
    }

    static updateSummaryTableHeaders(thead, summaryColumns, teamNameHeader, removeChildren = true)
    {
        if(removeChildren) ElementUtil.removeChildren(thead);
        const thRow = thead.insertRow();
        TableUtil.createTh(thRow).textContent = teamNameHeader;
        for(const column of summaryColumns) TableUtil.createTh(thRow).textContent = column.textContent;
    }

    static updateGamesAndAverageMmrTable(table, summaries, columns, teamNameHeader, createTeamNameElementFn)
    {
        const thead = table.querySelector(":scope thead");
        MmrHistory.updateSummaryTableHeaders(thead, columns, teamNameHeader);
        if(!summaries) return;

        const tbody = table.querySelector(":scope tbody");
        MmrHistory.updateSummaryTableBody(tbody, summaries, columns, createTeamNameElementFn);
    }

    static updateTierProgressTable(table, summaries, teamNameHeader, createTeamNameElementFn)
    {
        table.querySelector(":scope thead tr th:first-child").textContent = teamNameHeader;
        const tbody = table.querySelector(":scope tbody");
        ElementUtil.removeChildren(tbody);
        for(const summary of summaries)
        {
            const progress = MmrHistory.createTierProgress(
                summary.summary[TEAM_HISTORY_SUMMARY_COLUMN.REGION_RANK_LAST.fullName],
                summary.summary[TEAM_HISTORY_SUMMARY_COLUMN.REGION_TEAM_COUNT_LAST.fullName],
            );
            if(!progress) continue;

            const tr = tbody.insertRow();
            tr.insertCell().appendChild(createTeamNameElementFn(summary.legacyUidData));
            TableUtil.insertCell(tr, "cell-main").appendChild(progress);
        }
    }

    static createTierProgress(rank, teamCount)
    {
        if(teamCount == null || rank == null) return null;
        const tierRange = Util.getLeagueRange(rank, teamCount);
        let min, max, cur, nextTierRange;
        if(tierRange.league == LEAGUE.GRANDMASTER) {
            nextTierRange = {league: LEAGUE.GRANDMASTER, tierType: 0};
            min = SC2Restful.GM_COUNT;
            max = 1;
            cur = rank;
        } else {
            nextTierRange = tierRange.league == LEAGUE.MASTER && tierRange.tierType == 0
                ? MmrHistory.getGrandmasterTierRange(teamCount)
                : TIER_RANGE[tierRange.order - 1];
            min = tierRange.bottomThreshold;
            max = nextTierRange.bottomThreshold;
            cur = (rank / teamCount) * 100;
        }
        const progressBar = ElementUtil.createProgressBar(cur, min, max);
        progressBar.classList.add("tier-progress", "flex-grow-1");
        progressBar.querySelector(":scope .progress-bar").classList.add("bg-" + tierRange.league.name.toLowerCase());
        const container = document.createElement("div");
        container.classList.add("text-nowrap", "d-flex", "gap-tiny");
        container.appendChild(SC2Restful.IMAGES.get(tierRange.league.name.toLowerCase()).cloneNode());
        container.appendChild(SC2Restful.IMAGES.get("tier-" + (tierRange.tierType + 1)).cloneNode());
        container.appendChild(progressBar);
        container.appendChild(SC2Restful.IMAGES.get(nextTierRange.league.name.toLowerCase()).cloneNode());
        container.appendChild(SC2Restful.IMAGES.get("tier-" + (nextTierRange.tierType + 1)).cloneNode());
        return container;
    }

    static getGrandmasterTierRange(regionTeamCount)
    {
        return {
            league: LEAGUE.GRANDMASTER,
            tierType: 0,
            bottomThreshold: (SC2Restful.GM_COUNT / regionTeamCount) * 100
        };
    }

    static decorateMmrPointsIndex(tableData, rawData)
    {
        const annotations = [];
        tableData.headers.forEach((header, datasetIx)=>{
            const rawHistory = rawData.history[header];
            const curAnnotations = [];
            annotations.push(curAnnotations);
            let prevLeague = null;
            Array.from(Object.values(rawData.index)).forEach((raw, valIx)=>{
                const rawIndex = raw[header];
                if(rawIndex == null) return;

                const leagueType = rawHistory.history[TEAM_HISTORY_HISTORY_COLUMN.LEAGUE_TYPE.fullName][rawIndex];
                if(leagueType != prevLeague && Number.isFinite(tableData.values[datasetIx][valIx])) {
                    const annotation = {
                        name: "league-" + header + "-" + rawIndex,
                        type: "point",
                        pointStyle: SC2Restful.IMAGES.get(EnumUtil.enumOfId(leagueType, LEAGUE).name.toLowerCase()),
                        xValue: tableData.rowHeaders[valIx],
                        yValue: tableData.values[datasetIx][valIx]
                    }
                    curAnnotations.push(annotation);
                    prevLeague = leagueType;
                }
            });
        });
        tableData.dataAnnotations = annotations;
    }

    static addLegacyUidData(history)
    {
        history.legacyUidData = TeamLegacyUid.fromPulseString(history.staticData[TEAM_HISTORY_STATIC_COLUMN.LEGACY_UID.fullName]);
    }

    static copyHistory(src, dest)
    {
        const srcKeys = Object.keys(src.history);
        const srcLength = Object.values(src.history)[0].length;
        const copiedTimestamps = [];
        for(let srcIx = 0; srcIx < srcLength; srcIx++) {
            const srcTimestamp = src.history[TEAM_HISTORY_HISTORY_COLUMN.TIMESTAMP.fullName][srcIx];
            const destIx = dest.timestampIndex[srcTimestamp];
            if(destIx == null) continue;

            for(const srcKey of srcKeys){
                if(!dest.history[srcKey]) dest.history[srcKey] = new Array(srcLength);
                dest.history[srcKey][destIx] = src.history[srcKey][srcIx];
            }
            copiedTimestamps.push(srcTimestamp);
        }
        return copiedTimestamps;
    }

    static isHistoryEntryComplete(historyData, index)
    {
        return historyData.history[TEAM_HISTORY_HISTORY_COLUMN.GLOBAL_TEAM_COUNT.fullName]?.[index] != null
            || historyData?.completeTimestamps?.has(historyData.history[TEAM_HISTORY_HISTORY_COLUMN.TIMESTAMP.fullName][index]);
    }

    updateHistoryWithCompleteData(date)
    {
        const from = new Date(date.getFullYear(), 0);
        const to = new Date(date.getFullYear() + 1, 0);

        return Session.SC2_PULSE_API.getTeamHistories({
            legacyUids: this.mmrHistory.parameters.queueData.legacyUids,
            from, to,
            historyColumns: MmrHistory.COMPLETE_HISTORY_DATA_COLUMNS
        })
            .then(historyArray=>{
                historyArray.forEach(history=>{
                    const existingHistory = this.mmrHistory.history.data.find(h=>h.staticData[TEAM_HISTORY_STATIC_COLUMN.LEGACY_UID.fullName]
                        === history.staticData[TEAM_HISTORY_STATIC_COLUMN.LEGACY_UID.fullName]);
                    if(existingHistory != null) {
                        if(existingHistory.completeTimestamps == null) existingHistory.completeTimestamps = new Set();
                        MmrHistory.copyHistory(history, existingHistory).forEach(ts=>existingHistory.completeTimestamps.add(ts));
                    }
                });
            });
    }

    requeueUpdateHistoryWithCompleteData(date, then)
    {
        return ElementUtil.clearAndSetInputTimeout(this.completePointTaskName,
            then != null
                ? ()=>this.updateHistoryWithCompleteData(date).then(then)
                : ()=>this.updateHistoryWithCompleteData(date),
            this.completePointTimeout);
    }

    getAdditionalHistoryData(data, dataset, ix1, ix2)
    {
        const header = dataset.datasets[ix2].label;
        const historyData = data.history[header];
        const history = historyData.history;
        const historyIx = Object.values(data.index)[ix1][header];
        const date = new Date(history[TEAM_HISTORY_HISTORY_COLUMN.TIMESTAMP.fullName][historyIx] * 1000);
        ElementUtil.clearInputTimeout(this.completePointTaskName);
        if(!MmrHistory.isHistoryEntryComplete(historyData, historyIx))
            this.requeueUpdateHistoryWithCompleteData(date, ()=>ChartUtil.CHARTS.get(`${this.domPrefix}-table`).tooltip.update(true, false));
        const lines = [];
        lines.push(history[TEAM_HISTORY_HISTORY_COLUMN.SEASON.fullName]?.[historyIx] || MmrHistory.MMR_HISTORY_PLACEHOLDER);
        lines.push(MmrHistory.createHistoryLeague(
            history[TEAM_HISTORY_HISTORY_COLUMN.LEAGUE_TYPE.fullName]?.[historyIx],
            history[TEAM_HISTORY_HISTORY_COLUMN.TIER_TYPE.fullName]?.[historyIx]
        ));
        lines.push(history[TEAM_HISTORY_HISTORY_COLUMN.RATING.fullName]?.[historyIx] || MmrHistory.MMR_HISTORY_PLACEHOLDER);
        lines.push(MmrHistory.createHistoryGames(
            history[TEAM_HISTORY_HISTORY_COLUMN.GAMES.fullName]?.[historyIx],
            history[TEAM_HISTORY_HISTORY_COLUMN.WINS.fullName]?.[historyIx]
        ));
        lines.push(MmrHistory.createHistoryRank(
            history[TEAM_HISTORY_HISTORY_COLUMN.GLOBAL_RANK.fullName]?.[historyIx],
            history[TEAM_HISTORY_HISTORY_COLUMN.GLOBAL_TEAM_COUNT.fullName]?.[historyIx]
        ));
        lines.push(MmrHistory.createHistoryRank(
            history[TEAM_HISTORY_HISTORY_COLUMN.REGION_RANK.fullName]?.[historyIx],
            history[TEAM_HISTORY_HISTORY_COLUMN.REGION_TEAM_COUNT.fullName]?.[historyIx]
        ));
        lines.push(MmrHistory.createHistoryRank(
            history[TEAM_HISTORY_HISTORY_COLUMN.LEAGUE_RANK.fullName]?.[historyIx],
            history[TEAM_HISTORY_HISTORY_COLUMN.LEAGUE_TEAM_COUNT.fullName]?.[historyIx]
        ));
        return lines;
    }

    static createHistoryLeague(leagueType, tierType)
    {
        if(leagueType == null) return MmrHistory.MMR_HISTORY_PLACEHOLDER;

        return TeamUtil.createLeagueDiv({league: {type: leagueType}, tierType: tierType});
    }

    static createTooltipPlaceholder(levels = 2, classes)
    {
        const container = document.createElement("span");
        container.appendChild(document.createTextNode(MmrHistory.MMR_HISTORY_PLACEHOLDER));
        for(let i = 0; i < levels - 1; i++) {
            container.appendChild(document.createElement("br"));
            container.appendChild(document.createTextNode(MmrHistory.MMR_HISTORY_PLACEHOLDER));
        }
        if(classes) classes.forEach(clazz=>container.classList.add(clazz));
        return container;
    }

    static createHistoryGames(games, wins)
    {
        if(games == null) return MmrHistory.createTooltipPlaceholder();

        const content = games + "</br>" + (wins != null ? `${Math.round((wins / games) * 100)}%` : MmrHistory.MMR_HISTORY_PLACEHOLDER);
        const container = document.createElement("span");
        container.innerHTML = content;
        return container;
    }

    static createHistoryRank(rank, teamCount)
    {
        if(rank == null || teamCount == null) return MmrHistory.createTooltipPlaceholder(2, ["tooltip-mmr-rank"]);

        const rankElem = document.createElement("span");
        rankElem.classList.add("tooltip-mmr-rank");
        rankElem.innerHTML = `${Util.NUMBER_FORMAT.format(rank)}/${Util.NUMBER_FORMAT.format(teamCount)}<br/>
            (${Util.DECIMAL_FORMAT.format((rank / teamCount) * 100)}%)`;
        return rankElem;
    }

    enhanceForm()
    {
        document.getElementById(`${this.domPrefix}-queue-filter`)?.addEventListener("change", e=>window.setTimeout(evt=>this.onHistoryQueueChange.call(this, e), 0));
        document.getElementById(`${this.domPrefix}-depth`).addEventListener("input",  e=>this.onHistoryDepthChange.call(this, e));
        document.getElementById(`${this.domPrefix}-best-race`)?.addEventListener("change", e=>window.setTimeout(evt=>this.onHistoryBestRaceOnlyChange.call(this, e), 0));
        document.getElementById(`${this.domPrefix}-season-last`).addEventListener("change", e=>window.setTimeout(evt=>this.onHistoryEndOfSeasonChange.call(this, e), 0));
        document.getElementById(`${this.domPrefix}-y-axis`).addEventListener("change", e=>window.setTimeout(evt=>{
            MmrHistory.setYAxis(e.target.value, e.target.getAttribute("data-chartable"));
            this.onHistoryYAxisChange(e);
        }, 0));
        document.getElementById(`${this.domPrefix}-x-type`).addEventListener("change", e=>window.setTimeout(evt=>this.updateHistoryView(), 0));
        document.getElementById(`${this.domPrefix}-leagues`).addEventListener("change", e=>window.setTimeout(evt=>this.onHistoryShowLeaguesChange.call(this, e), 0));
    }

    onHistoryShowLeaguesChange(evt)
    {
        this.mmrHistory.parameters.showLeagues = (localStorage.getItem(`${this.domPrefix}-leagues`) || "false") === "true";
        if(!this.mmrHistory.parameters.historyColumns.has(TEAM_HISTORY_HISTORY_COLUMN.LEAGUE_TYPE)) {
            this.reloadHistory();
        } else {
            this.updateHistoryView();
        }
    }

    onHistoryEndOfSeasonChange(evt)
    {
        this.mmrHistory.parameters.endOfSeason = (localStorage.getItem(`${this.domPrefix}-season-last`) || "false") === "true";
        if(!this.mmrHistory.parameters.historyColumns.has(TEAM_HISTORY_HISTORY_COLUMN.SEASON)) {
            this.reloadHistory();
        } else {
            this.refilterHistory();
        }
    }

    onHistoryBestRaceOnlyChange(evt)
    {
        this.mmrHistory.parameters.bestRaceOnly = (localStorage.getItem(`${this.domPrefix}-best-race`) || "false") === "true";
        this.refilterHistory();
    }

    refilterHistory()
    {
        this.resetHistoryFilteredData();
        this.filterHistory();
        this.updateHistoryView();
    }

    onHistoryYAxisChange(evt)
    {
        const yAxis = localStorage.getItem(`${this.domPrefix}-y-axis`) || "mmr";
        this.mmrHistory.parameters.yAxis = yAxis;
        const requiredColumns = new Set(MmrHistory.MMR_Y_REQUIRED_HISTORY_COLUMNS.get(yAxis));
        const loadedColumns = Object.keys(this.mmrHistory.history.data[0].history);
        if(Array.from(requiredColumns.values()).every(c=>loadedColumns.includes(c))) {
            this.refilterHistory();
        } else {
            this.reloadHistory();
        }
    }

    onHistoryQueueChange(evt)
    {
        this.mmrHistory.parameters.queueData = this.getQueueData();
        this.reloadHistoryAll();
    }

    onHistoryDepthChange(evt)
    {
        const prev = ElementUtil.INPUT_TIMEOUTS.get(evt.target.id);
        if(prev != null)  window.clearTimeout(prev);
        ElementUtil.INPUT_TIMEOUTS.set(evt.target.id, window.setTimeout(e=>this.reloadHistoryAll(true), ElementUtil.INPUT_TIMEOUT));
    }

    static setYAxis(mode, chartable)
    {
        if(mode == "mmr") {
            ChartUtil.setNormalYAxis(chartable);
        } else {
            ChartUtil.setTopPercentYAxis(chartable);
        }
    }

    reloadHistory(resetParameters = false)
    {
        this.resetHistory(true);
        this.setParameters(resetParameters);
        return this.enqueueUpdateHistory();
    }

    reloadHistoryAll(resetParameters = false)
    {
        this.resetHistoryAll(true)
        this.resetUpdateHistoryAllLoading();
        return this.enqueueUpdateHistoryAll(resetParameters);
    }

}

MmrHistory.MMR_Y_VALUE_OPERATIONS = new Map([
    ["mmr", {
        get: (history, ix)=>history.history[TEAM_HISTORY_HISTORY_COLUMN.RATING.fullName][ix],
        max: (values)=>Math.max(...values.filter(v=>v != null)),
        compare: (a, b)=>b - a
    }],
    ["percent-region", {
        get:  (history, ix)=>{
            const rank = history.history[TEAM_HISTORY_HISTORY_COLUMN.REGION_RANK.fullName][ix];
            return rank != null
                ? (rank / history.history[TEAM_HISTORY_HISTORY_COLUMN.REGION_TEAM_COUNT.fullName][ix]) * 100
                : null;
        },
        max: (values)=>Math.min(...values.filter(v=>v != null)),
        compare: (a, b)=>a - b
    }]
]);
MmrHistory.MMR_Y_REQUIRED_HISTORY_COLUMNS = new Map([
    ["mmr", new Set([TEAM_HISTORY_HISTORY_COLUMN.TIMESTAMP, TEAM_HISTORY_HISTORY_COLUMN.RATING])],
    ["percent-region", new Set([
        TEAM_HISTORY_HISTORY_COLUMN.TIMESTAMP,
        TEAM_HISTORY_HISTORY_COLUMN.REGION_RANK,
        TEAM_HISTORY_HISTORY_COLUMN.REGION_TEAM_COUNT
    ])]
]);
MmrHistory.MMR_Y_SUMMARY_COLUMN_FORMATTERS = new Map([
    [TEAM_HISTORY_SUMMARY_COLUMN.RATING_AVG, Math.floor]
]);
MmrHistory.MMR_Y_REQUIRED_NUMERIC_SUMMARY_COLUMNS = new Map([
    ["mmr", new Set([
        TEAM_HISTORY_SUMMARY_COLUMN.GAMES,
        TEAM_HISTORY_SUMMARY_COLUMN.RATING_LAST,
        TEAM_HISTORY_SUMMARY_COLUMN.RATING_AVG,
        TEAM_HISTORY_SUMMARY_COLUMN.RATING_MAX,
    ])],
    //the same util top% summary is done
    ["percent-region", new Set([
        TEAM_HISTORY_SUMMARY_COLUMN.GAMES,
        TEAM_HISTORY_SUMMARY_COLUMN.RATING_LAST,
        TEAM_HISTORY_SUMMARY_COLUMN.RATING_AVG,
        TEAM_HISTORY_SUMMARY_COLUMN.RATING_MAX,
    ])]
]);
MmrHistory.COMPLETE_HISTORY_DATA_COLUMNS = new Set([
    TEAM_HISTORY_HISTORY_COLUMN.TIMESTAMP,
    TEAM_HISTORY_HISTORY_COLUMN.RATING,
    TEAM_HISTORY_HISTORY_COLUMN.GAMES,
    TEAM_HISTORY_HISTORY_COLUMN.WINS,
    TEAM_HISTORY_HISTORY_COLUMN.LEAGUE_TYPE,
    TEAM_HISTORY_HISTORY_COLUMN.TIER_TYPE,
    TEAM_HISTORY_HISTORY_COLUMN.GLOBAL_RANK,
    TEAM_HISTORY_HISTORY_COLUMN.REGION_RANK,
    TEAM_HISTORY_HISTORY_COLUMN.LEAGUE_RANK,
    TEAM_HISTORY_HISTORY_COLUMN.GLOBAL_TEAM_COUNT,
    TEAM_HISTORY_HISTORY_COLUMN.REGION_TEAM_COUNT,
    TEAM_HISTORY_HISTORY_COLUMN.LEAGUE_TEAM_COUNT,
    TEAM_HISTORY_HISTORY_COLUMN.SEASON
]);
MmrHistory.MMR_REQUIRED_PROGRESS_SUMMARY_COLUMNS = new Set([
    TEAM_HISTORY_SUMMARY_COLUMN.REGION_RANK_LAST,
    TEAM_HISTORY_SUMMARY_COLUMN.REGION_TEAM_COUNT_LAST
]);
MmrHistory.MMR_HISTORY_PLACEHOLDER = '-';
MmrHistory.ALL_RACE = Object.freeze({name: "all", fullName: "ALL", order: 999});
MmrHistory.MMR_HISTORY_COMPLETE_POINT_TASK_NAME = "history-complete-point-task";
MmrHistory.MMR_HISTORY_COMPLETE_POINT_TIMEOUT = 100;