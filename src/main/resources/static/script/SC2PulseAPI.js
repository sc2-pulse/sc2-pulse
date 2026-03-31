class SC2PulseAPI {

    static #TEAM_LEGACY_UID_COUNT_MAX = 10;
    static #TEAM_ID_COUNT_MAX = 250;
    static #LAST_TEAM_LEGACY_UID_COUNT_MAX = 100;

    #baseUrl;
    #requestRateLimiter;
    #beforeRequest;
    #apiVersion;
    #errorCodeOnInvalidVersion

    constructor({
        baseUrl = "https://sc2pulse.nephest.com/sc2/api",
        requestRateLimiter = new RequestRateLimiter(),
        beforeRequest = ()=>Promise.resolve(),
        apiVersion,
        errorCodeOnInvalidVersion
    }={}) {
        this.#baseUrl = baseUrl;
        this.#requestRateLimiter = requestRateLimiter;
        this.#beforeRequest = beforeRequest;
        this.#apiVersion = apiVersion,
        this.#errorCodeOnInvalidVersion = errorCodeOnInvalidVersion;
    }

    async verifyResponse(resp, {validStatuses = [200]} = {}) {
        if (!validStatuses.includes(resp.status)) throw new Error(resp.status + " " + resp.statusText);

        if(this.#errorCodeOnInvalidVersion != null) this.verifyResponseVersion(resp);
        return resp;
    }

    verifyResponseVersion(resp) {
        const versionHeader = resp.headers.get("X-Application-Version");
        const cacheHeader = resp.headers.get("Cache-Control");
        if((!cacheHeader || cacheHeader.toLowerCase().includes("max-age=0")) && versionHeader && versionHeader != this.#apiVersion)
            throw new Error(this.#errorCodeOnInvalidVersion + " API version has changed");
    }

    verifyJsonResponse(resp, {validStatuses = [200]} = {}) {
        return this.verifyResponse(resp, {validStatuses})
            .then(resp=>resp.text())
            .then(body=>body && (body.startsWith("{") || body.startsWith("[")) ? JSON.parse(body) : null)
            .then(json=>SC2PulseAPI.isErrorDetails(json) ? null : json);
    }
    
    #fetch(url, {validStatuses = [200]} = {}) {
        return this.#beforeRequest()
            .then(n=>this.#requestRateLimiter.fetch(url))
            .then(resp=>this.verifyJsonResponse(resp, {validStatuses}));
    }

    static isErrorDetails(json) {
        return json != null && json.status != null && json.type != null;
    }

    static async batchExecute(params, batchSize, consumer) {
        if(params == null || params.length == 0) return [];
        if(params.length <= batchSize) return consumer(params);

        const promises = [];
        for (let i = 0; i < params.length; i += batchSize)
            promises.push(consumer(params.slice(i, i + batchSize)));
        const allBatchResults = await Promise.all(promises);

        return allBatchResults.flat();
    }

    static #createParameterString(parameters) {
        return parameters.size > 0 ? ("?" + parameters.toString()) : "";
    }

    getSeasons({season}={}) {
        const params = new URLSearchParams();
        if(season != null) params.append("season", season);
        const request = this.#baseUrl + "/seasons" + SC2PulseAPI.#createParameterString(params);

        return this.#fetch(request);
    }

    getPatches({buildMin} = {}) {
        const params = new URLSearchParams();
        if(buildMin != null) params.append("buildMin", buildMin);
        const request = this.#baseUrl + "/patches" + SC2PulseAPI.#createParameterString(params);

        return this.#fetch(request);
    }

    async getTeamLadder({
        queue,
        teamType,
        season,
        regions,
        leagues,
        sort = new SortParameter("rating", SORTING_ORDER.DESC),
        cursor
    } = {}) {
        const params = new URLSearchParams();
        if(queue != null) params.append("queue", queue.fullName);
        if(teamType != null) params.append("teamType", teamType.fullName);
        if(season != null) params.append("season", season);
        if(regions?.length > 0) regions.forEach((region)=>params.append("region", region.fullName));
        if(leagues?.length > 0) leagues.forEach((league)=>params.append("league", league.fullName));
        if(cursor != null) params.append(cursor.direction.relativePosition, cursor.token);
        if(sort != null) params.append("sort", sort.toPrefixedString());
        const request = this.#baseUrl + "/teams" + SC2PulseAPI.#createParameterString(params);

        const responseJson = await this.#fetch(request);
        responseJson.navigation.cursors = Cursor.parseNavigation(responseJson.navigation);
        return responseJson;
    }

    static createTeamGroupParameters({ids, legacyUids, fromSeason, toSeason}={})
    {
        const params = new URLSearchParams();
        if(ids) ids.forEach(id=>params.append("id", id));
        if(legacyUids) legacyUids.forEach(l=>params.append("teamLegacyUid", l));
        if(fromSeason != null) params.append("seasonMin", fromSeason);
        if(toSeason != null) params.append("seasonMax", toSeason);
        return params;
    }

    static createTeamHistoryParameters({legacyUids, from, to}={})
    {
        const params = SC2PulseAPI.createTeamGroupParameters({legacyUids});
        if(from) params.append("from", from.toISOString());
        if(to) params.append("to", to.toISOString());

        return params;
    }

    getTeamHistories({legacyUids, from, to, historyColumns, batchSize = SC2PulseAPI.#TEAM_LEGACY_UID_COUNT_MAX}={})
    {
        return SC2PulseAPI.batchExecute(legacyUids, batchSize, batch=>this.#getTeamHistoriesBatch({legacyUids: batch, from, to, historyColumns}));
    }

    #getTeamHistoriesBatch({legacyUids, from, to, historyColumns}={})
    {
        const params = SC2PulseAPI.createTeamHistoryParameters({legacyUids, from, to});
        if(historyColumns) historyColumns.forEach(h=>params.append("history", h.fullName));
        const request = this.#baseUrl + "/team-histories" + SC2PulseAPI.#createParameterString(params);

        return this.#fetch(request);
    }

    getTeamHistorySummaries({legacyUids, from, to, summaryColumns, batchSize = SC2PulseAPI.#TEAM_LEGACY_UID_COUNT_MAX}={})
    {
        return SC2PulseAPI.batchExecute(legacyUids, batchSize, batch=>this.#getHistorySummariesBatch({legacyUids: batch, from, to, summaryColumns}));
    }

    #getHistorySummariesBatch({legacyUids, from, to, summaryColumns}={})
    {
        const params = SC2PulseAPI.createTeamHistoryParameters({legacyUids, from, to});
        if(summaryColumns) summaryColumns.forEach(s=>params.append("summary", s.fullName));
        const request = this.#baseUrl + "/team-history-summaries" + SC2PulseAPI.#createParameterString(params);

        return this.#fetch(request);
    }

    async getTeams({
        ids,
        legacyUids,
        fromSeason,
        toSeason,
        last,
        idBatchSize = SC2PulseAPI.#TEAM_ID_COUNT_MAX,
        legacyUidBatchSize = last === true
            ? SC2PulseAPI.#LAST_TEAM_LEGACY_UID_COUNT_MAX
            : SC2PulseAPI.#TEAM_LEGACY_UID_COUNT_MAX
    }={}) {
        return (ids != null
            ? await SC2PulseAPI.batchExecute(ids, idBatchSize,
                batch=>this.#getTeamsBatch({ids: batch, fromSeason, toSeason, last}))
            : []
        ).concat(legacyUids != null
            ? await SC2PulseAPI.batchExecute(legacyUids, legacyUidBatchSize,
                batch=>this.#getTeamsBatch({legacyUids: batch, fromSeason, toSeason, last}))
            : []
        )
    }

    #getTeamsBatch({ids, legacyUids, fromSeason, toSeason, last}={}) {
        const params = SC2PulseAPI.createTeamGroupParameters({ids, legacyUids, fromSeason, toSeason});
        if(last != null) params.append("last", last);
        const request = this.#baseUrl + "/teams" + SC2PulseAPI.#createParameterString(params);

        return this.#fetch(request);
    }

    static createCharacterGroupParameters({characterIds, clanIds, proPlayerIds, accountIds, toonHandles}={}) {
        const params = new URLSearchParams();
        if(characterIds?.length > 0) characterIds.forEach(id=>params.append("characterId", id));
        if(clanIds?.length > 0) clanIds.forEach(id=>params.append("clanId", id));
        if(proPlayerIds?.length > 0) proPlayerIds.forEach(id=>params.append("proPlayerId", id));
        if(accountIds?.length > 0) accountIds.forEach(id=>params.append("accountId", id));
        if(toonHandles?.length > 0) toonHandles.forEach(id=>params.append("toonHandle", id));
        return params;
    }

    getCharacterTeams({characterIds, clanIds, proPlayerIds, accountIds, toonHandles, queue, season, races, limit} = {}) {
        const params = SC2PulseAPI.createCharacterGroupParameters({characterIds, clanIds, proPlayerIds, accountIds, toonHandles});
        if(queue != null) params.append("queue", queue.fullName);
        if(season != null) params.append("season", season);
        if(races?.length > 0) races.forEach(race=>params.append("race", race.fullName));
        if(limit != null) params.append("limit", limit);
        const request = this.#baseUrl + "/character-teams" + SC2PulseAPI.#createParameterString(params);

        return this.#fetch(request);
    }

}