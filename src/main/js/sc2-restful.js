const PAGINATION_SIDE_BUTTON_COUNT = 4;
const RESOURCE_PATH = "static/";

const REGION = Object.freeze
({
    US: {code:1, name: "us"},
    EU: {code:2, name: "eu"},
    KR: {code:3, name: "kr"},
    CN: {code:5, name: "cn"}
});

const RACE = Object.freeze
({
    TERRAN: {name: "terran"},
    PROTOSS: {name: "protoss"},
    ZERG: {name: "zerg"},
    RANDOM: {name: "random"}
});

const LEAGUE = Object.freeze
({
    BRONZE: {code:0, name: "bronze"},
    SILVER: {code:1, name: "silver"},
    GOLD: {code:2, name: "gold"},
    PLATINUM: {code:3, name: "platinum"},
    DIAMOND: {code:4, name: "diamond"},
    MASTER: {code:5, name: "master"},
    GRANDMASTER: {code:6, name: "grandmaster"}
});

const TEAM_FORMAT = Object.freeze
({
    _1V1: {code:201, name: "1V1", fullName: "LOTV_1V1", memberCount: 1},
    _2V2: {code:202, name: "2V2", fullName: "LOTV_2V2", memberCount: 2},
    _3V3: {code:203, name: "3V3", fullName: "LOTV_3V3", memberCount: 3},
    _4V4: {code:204, name: "4V4", fullName: "LOTV_4V4", memberCount: 4},
    ARCHON: {code:206, name: "Archon", fullName: "LOTV_ARCHON", memberCount: 2}
});

const TEAM_TYPE = Object.freeze
({
    ARRANGED: {code:0, name: "Arranged"},
    RANDOM: {code:1, name: "Random"}
});

const PAGE_TYPE = Object.freeze
({
    FIRST: {}, LAST: {}, GENERAL: {}
});
function getMemberCount(teamFormat, teamType)
{
    if(teamType === TEAM_TYPE.RANDOM) return 1;
    return teamFormat.memberCount;
}
function enumOfId(id, enumObj)
{
    for(const curEnum of Object.values(enumObj)) if(curEnum.code == id) return curEnum;
    throw new Error("Invalid id");
}
function enumOfName(name, enumObj)
{
    name = name.toLowerCase();
    for(const curEnum of Object.values(enumObj)) if(curEnum.name.toLowerCase() == name) return curEnum;
    throw new Error("Invalid name");
}
function enumOfFullName(fullName, enumObj)
{
    fullName = fullName.toLowerCase();
    for(const curEnum of Object.values(enumObj)) if(curEnum.fullName.toLowerCase() == fullName) return curEnum;
    throw new Error("Invalid full name");
}

const CHARTABLE_OBSERVER_CONFIG =
{
    attributes: true,
    childList: false,
    subtree: false
}

const CHARTABLE_OBSERVER = new MutationObserver(onChartableMutation);

const CHARTS = new Map();
const COLORS = new Map
([
    ["terran", "#295a91"],
    ["protoss", "#dec93e"],
    ["zerg", "#882991"],
    ["random", "#646464"],
    ["us", "#3c3b6e"],
    ["eu", "#003399"],
    ["kr", "#141414"],
    ["cn", "#de2910"],
    ["bronze", "#b9712d"],
    ["silver", "#737373"],
    ["gold", "#ffd700"],
    ["platinum", "#a5a4a3"],
    ["diamond", "#0d4594"],
    ["master", "#00b1fb"],
    ["grandmaster", "#ef3e00"]
]);

const ROOT_CONTEXT_PATH = window.location.pathname.substring(0, window.location.pathname.indexOf("/", 2));

let currentRequests = 0;
let documentIsChanging = false;
let shouldScrollToResult = false;
let currentSeason = -1;
let currentTeamFormat;
let currentTeamType;
let currentLadder;
let currentAccount = null;
let currentFollowing = null;

window.addEventListener("load", onWindowLoad);

function onWindowLoad()
{
    enhanceModals();
    getMyInfo().then(o=>getSeasons());
        //.then(o => getLadderAll());
    enhanceLadderForm();
    enhanceSearchForm();
    enhanceMyLadderForm();
    enchanceFollowButtons();
    enhanceTabs();
    observeChartables();
    createPaginations();
    setFormCollapsibleScroll("form-ladder");
    setFormCollapsibleScroll("form-following-ladder");
    createPlayerStatsCards(document.getElementById("player-stats-container"));
    showAnchoredTabs();
}

function encodeSpace(s){ return encodeURIComponent(s).replace(/%20/g,'+'); }

function urlencodeFormData(fd){
    let s = '';

    for(const pair of fd.entries()){
        if(typeof pair[1]=='string'){
            s += (s?'&':'') + encodeSpace(pair[0])+'='+encodeSpace(pair[1]);
        }
    }
    return s;
}

function showAnchoredTabs()
{
    var url = document.location.toString();
    if (url.match('#'))
    {
        $('.nav-pills-main a[href="#' + url.split('#')[1] + '"]').tab('show');
        $('.nav-tabs-main a[href="#' + url.split('#')[1] + '"]').tab('show');
    }
}

function enhanceTabs()
{
    $('.nav-tabs-main a').on('shown.bs.tab', function (e){window.location.hash = e.target.hash; window.scrollTo(0, 0);});
    $('.nav-pills-main a').on('shown.bs.tab', function (e){window.location.hash = e.target.hash; window.scrollTo(0, 0);});
}

function enhanceModals()
{
    $("#error-session").on("hide.bs.modal", doRenewBlizzardRegistration);
    $("#error-session").on("shown.bs.modal", e=>window.setTimeout(doRenewBlizzardRegistration, 3500));
}

function enhanceLadderForm()
{
    const form = document.getElementById("form-ladder");
    form.addEventListener("submit", function(evt)
        {
            evt.preventDefault();
            getLadderAll();
            $("#form-ladder").collapse("hide");
        }
    );
}

function enhanceMyLadderForm()
{
    const form = document.getElementById("form-following-ladder");
    form.addEventListener
    (
        "submit",
         function(evt)
        {
            evt.preventDefault();
            getMyLadder(urlencodeFormData(new FormData(document.getElementById("form-following-ladder"))));
            $("#form-following-ladder").collapse("hide");
        }
    );
}

function enchanceFollowButtons()
{
    document.querySelector("#follow-button").addEventListener("click", follow);
    document.querySelector("#unfollow-button").addEventListener("click", unfollow);
}

function setFormCollapsibleScroll(id)
{
    const jCol = $(document.getElementById(id));
    jCol.on("hide.bs.collapse", function(e){documentIsChanging = true});
    jCol.on("hidden.bs.collapse", function(e){
        documentIsChanging = false
        if(shouldScrollToResult)
        {
            const scrollTo = e.currentTarget.getAttribute("data-on-success-scroll-to");
            if(scrollTo != null) scrollIntoViewById(scrollTo);
            shouldScrollToResult = false;
        }
    });
    jCol.on("show.bs.collapse", function(e){
        documentIsChanging = true;
        const scrollTo = e.currentTarget.getAttribute("data-scroll-to");
        if(scrollTo != null) scrollIntoViewById(scrollTo);
    });
    jCol.on("shown.bs.collapse", function(e){documentIsChanging = false});
}

function enhanceSearchForm()
{
    const form = document.getElementById("form-search");
    form.addEventListener("submit", function(evt)
        {
            evt.preventDefault();
            findCharactersByName();
        }
    );
}

function getLadderAll()
{
    const formParams = getFormParameters();
    getLadder(formParams);
    getLadderStats(formParams);
    getLeagueBounds(formParams);
}

function findCharactersByName()
{
    getCharacters(document.getElementById("search-player-name").value);
}

function getFormParameters(page = 0)
{
    const fd = new FormData(document.getElementById("form-ladder"));
    if (page >= 0) fd.set("page", page);
    return urlencodeFormData(fd);
}

function getLadderStats(formParams)
{
    setGeneratingStatus("begin");
    const request = "api/ladder/stats?" + formParams;
    fetch(request)
        .catch(e => setGeneratingStatus("error", e.message))
        .then(resp => {if (!resp.ok) throw new Error(resp.statusText); return resp.json();})
        .catch(e => setGeneratingStatus("error", e.message))
        .then(json => updateLadderStats(json))
        .then(o => setGeneratingStatus("success"));;
}

function updateLadderStats(searchResult)
{
    updateRaceGamesPlayed(searchResult.raceGamesPlayed);
    updateGenericTable(document.getElementById("games-played-region-table"), searchResult.regionGamesPlayed);
    updateGenericTable
    (
        document.getElementById("games-played-league-table"),
        searchResult.leagueGamesPlayed,
        (a, b)=>a[0].localeCompare(b[0]),
        function(id){return enumOfId(id, LEAGUE).name;}
    );

    updateGenericTable(document.getElementById("team-count-region-table"), searchResult.regionTeamCount);
    updateGenericTable
    (
        document.getElementById("team-count-league-table"),
        searchResult.leagueTeamCount,
        (a, b)=>a[0].localeCompare(b[0]),
        function(id){return enumOfId(id, LEAGUE).name;}
    );

    const playerCountTotal = Object.values(searchResult.regionPlayerCount)
        .reduce((a, b) => a + b, 0);
    document.getElementById("players-total-count").textContent = playerCountTotal.toLocaleString();
    updateGenericTable(document.getElementById("player-count-region-table"), searchResult.regionPlayerCount);
    updateGenericTable
    (
        document.getElementById("player-count-league-table"),
        searchResult.leaguePlayerCount,
        (a, b)=>a[0].localeCompare(b[0]),
        function(id){return enumOfId(id, LEAGUE).name;}
    );
}

function updateRaceGamesPlayed(gamesPlayed)
{
    const gamesTotal = gamesPlayed.TERRAN + gamesPlayed.PROTOSS + gamesPlayed.ZERG + gamesPlayed.RANDOM;

    document.getElementById("games-total-count").textContent =
        gamesTotal.toLocaleString() + "/" + Math.round(gamesTotal / (currentTeamFormat.memberCount * 2)).toLocaleString();
    document.getElementById("games-terran-count").textContent = gamesPlayed.TERRAN;
    document.getElementById("games-protoss-count").textContent = gamesPlayed.PROTOSS;
    document.getElementById("games-zerg-count").textContent = gamesPlayed.ZERG;
    document.getElementById("games-random-count").textContent = gamesPlayed.RANDOM;
    document.getElementById("games-played-race-table").setAttribute("data-last-updated", Date.now());
}

function updateGenericTable(table, data, sorter = null, translator = null)
{
    const headRow = table.getElementsByTagName("thead")[0].getElementsByTagName("tr")[0];
    const bodyRow = table.getElementsByTagName("tbody")[0].getElementsByTagName("tr")[0];
    removeChildren(headRow);
    removeChildren(bodyRow);
    for(const [header, value] of Object.entries(data).sort(sorter == null ? (a, b)=>b[0].localeCompare(a[0]) : sorter))
    {
        const headCell = document.createElement("th");
        const headerTranslated = translator == null ? header : translator(header);
        headCell.setAttribute("data-chart-color", headerTranslated.toLowerCase());
        headCell.appendChild(document.createTextNode(headerTranslated));
        headRow.appendChild(headCell);

        bodyRow.insertCell().appendChild(document.createTextNode(value));
    }
    table.setAttribute("data-last-updated", Date.now());
}

function getLeagueBounds(formParams)
{
    setGeneratingStatus("begin");
    const request = "api/ladder/league/bounds?" + formParams;
    fetch(request)
        .catch(e => setGeneratingStatus("error", e.message))
        .then(resp => {if (!resp.ok) throw new Error(resp.statusText); return resp.json();})
        .catch(e => setGeneratingStatus("error", e.message))
        .then(json => updateLeagueBounds(json))
        .then(o => setGeneratingStatus("success"));
}

function updateLeagueBounds(searchResult)
{
    const table = document.getElementById("league-bounds-table");
    const headers = table.getElementsByTagName("thead")[0].getElementsByTagName("tr")[0];
    const body = table.getElementsByTagName("tbody")[0];
    removeChildren(headers);
    removeChildren(body);
    if(Object.keys(searchResult).length === 0) return;
    const leagueHeader = document.createElement("th");
    leagueHeader.setAttribute("scope", "col");
    leagueHeader.textContent = "Tier";
    headers.appendChild(leagueHeader);
    for(const region of Object.keys(searchResult))
    {
        const th = document.createElement("th");
        th.setAttribute("scope", "col");
        th.appendChild(createImage("flag/", region.toLowerCase(), ["table-image", "table-image-long"]));
        headers.appendChild(th);
    }
    for(const [leagueId, leagueObj] of Object.entries(searchResult[Object.keys(searchResult)[0]]).sort((a, b)=>b[0] - a[0]))
    {
        const league = enumOfId(leagueId, LEAGUE);
        for(const tierId of Object.keys(leagueObj))
        {
            const tr = document.createElement("tr");
            const th = document.createElement("th");
            th.setAttribute("scope", "row");
            const leagueDiv = document.createElement("div");
            leagueDiv.classList.add("text-nowrap");
            leagueDiv.appendChild(createImage("league/", league.name, ["table-image", "table-image-square", "mr-1"]));
            leagueDiv.appendChild(createImage("league/", "tier-" + (1 + + tierId), ["table-image-additional"]));
            th.appendChild(leagueDiv);
            tr.appendChild(th);
            for(const region of Object.keys(searchResult))
            {
                const range = searchResult[region][leagueId][tierId];
                const td = document.createElement("td");
                td.textContent = league === LEAGUE.GRANDMASTER
                    ? "Top 200"
                    : (range[0] + "-" + range[1]);
                tr.appendChild(td);
            }
            body.appendChild(tr);
        }
    }
}

function getLadder(formParams, ratingAnchor = 99999, idAnchor = 0, forward = true, count = 1)
{
    setGeneratingStatus("begin");
    const request = `api/ladder/a/${ratingAnchor}/${idAnchor}/${forward}/${count}?` + formParams;
    fetch(request)
        .catch(error => setGeneratingStatus("error", error.message))
        .then(resp => {if (!resp.ok) throw new Error(resp.statusText); return resp.json();})
        .catch(error => setGeneratingStatus("error", error.message))
        .then(json => updateLadder(json))
        .then(o => setGeneratingStatus("success", null, "generated-info-all"));
}

function updateLadder(searchResult)
{
    currentLadder = searchResult;
    document.getElementById("teams-total-count").textContent = searchResult.meta.totalCount.toLocaleString();
    updateTeamsTable(document.getElementById("ladder"), searchResult);
    updateLadderPaginations();
    document.getElementById("generated-info-all").classList.remove("d-none");
}

function updateTeamsTable(table, searchResult)
{
    const fullMode = table.getAttribute("data-ladder-format-show") == "true";
    const includeRank = table.getAttribute("data-ladder-rank-show") == "true";
    const ladderBody = table.getElementsByTagName("tbody")[0];
    removeChildren(ladderBody);

    for(let i = 0; i < searchResult.result.length; i++)
    {
        const team = searchResult.result[i];
        const row = ladderBody.insertRow();
        if(fullMode)
        {
            const teamFormat = enumOfId(team.league.queueType, TEAM_FORMAT);
            const teamType = enumOfId(team.league.teamType, TEAM_TYPE);
            row.insertCell().appendChild(document.createTextNode(teamFormat.name + " " + teamType.name));
        }
        if(searchResult.meta != null) row.insertCell()
                .appendChild(document.createTextNode(calculateRank(searchResult, i)));
        row.insertCell().appendChild(createImage("flag/", team.region.toLowerCase(), ["table-image-long"]));
        const league = enumOfId(team.league.type, LEAGUE);
        const leagueDiv = document.createElement("div");
        leagueDiv.classList.add("text-nowrap");
        leagueDiv.appendChild(createImage("league/", league.name, ["table-image", "table-image-square", "mr-1"]));
        leagueDiv.appendChild(createImage("league/", "tier-" + (team.leagueTierType + 1), ["table-image-additional"]));
        row.insertCell().appendChild(leagueDiv);
        const membersCell = row.insertCell();
        membersCell.classList.add("complex", "cell-main");
        const mRow = document.createElement("span");
        mRow.classList.add("row", "no-gutters");
        for(const teamMember of team.members)
        {
            mRow.appendChild(createMemberInfo(team, teamMember));
        }
        membersCell.appendChild(mRow);
        row.insertCell().appendChild(document.createTextNode(team.rating));
        row.insertCell().appendChild(document.createTextNode(team.wins + team.losses));
        row.insertCell().appendChild(document.createTextNode(Math.round( team.wins / (team.wins + team.losses) * 100) ));
    }
}

function createMemberInfo(team, member)
{
    const nameElem = document.createElement("span");
    nameElem.classList.add("player-name");
    nameElem.textContent = member.character.name.substring(0, member.character.name.indexOf("#"));

    const games = new Map();
    games.set(RACE.TERRAN, typeof member.terranGamesPlayed === "undefined" ? 0 : member.terranGamesPlayed);
    games.set(RACE.PROTOSS, typeof member.protossGamesPlayed === "undefined" ? 0 : member.protossGamesPlayed);
    games.set(RACE.ZERG, typeof member.zergGamesPlayed === "undefined" ? 0 : member.zergGamesPlayed);
    games.set(RACE.RANDOM, typeof member.randomGamesPlayed === "undefined" ? 0 : member.randomGamesPlayed);
    let gamesTotal = 0;
    for(const val of games.values()) gamesTotal += val;
    const percentage = new Map();
    for(const [key, val] of games.entries())
        if(val != 0) percentage.set(key, Math.round((val / gamesTotal) * 100));
    const percentageSorted = new Map([...percentage.entries()].sort((a, b)=>b[1] - a[1]));

    const racesElem = document.createElement("span");
    racesElem.classList.add("race-percentage-container", "mr-1", "text-nowrap", "d-inline-block");
    if(percentageSorted.size > 0)
    {
        for(const [race, val] of percentageSorted.entries())
        {
            if(val == 0) continue;
            racesElem.appendChild(createImage("race/", race.name, ["table-image", "table-image-square"]));
            if(val < 100)
            {
                const racePercent = document.createElement("span");
                racePercent.classList.add("race-percentage", "race-percentage-" + race.name, "text-secondary");
                racePercent.textContent = val;
                racesElem.appendChild(racePercent);
            }
        }
    }
    else
    {
        const noRace = document.createElement("span");
        noRace.classList.add("race-percentage", "race-percentage-none", "text-secondary", "table-image", "table-image-square");
        noRace.setAttribute("title", "no specific race");
        racesElem.appendChild(noRace);
    }

    const playerLink = document.createElement("a");
    playerLink.classList.add("player-link", "w-100", "h-100", "d-inline-block");
    if(currentFollowing != null && Object.values(currentFollowing).filter(val=>val.followingAccountId == member.account.id).length > 0)
        playerLink.classList.add("text-success");
    playerLink.setAttribute("href", "#");
    playerLink.setAttribute("data-account-id", member.account.id);
    playerLink.setAttribute("data-character-id", member.character.id);
    playerLink.setAttribute("data-character-battlenet-id", member.character.battlenetId);
    playerLink.setAttribute("data-character-realm", member.character.realm);
    playerLink.setAttribute("data-character-region",  member.character.region);
    playerLink.setAttribute("data-character-battletag", member.account.battleTag);
    playerLink.addEventListener("click", showCharacterInfo);
    playerLink.appendChild(racesElem);
    playerLink.appendChild(nameElem);
    const result = document.createElement("span");
    result.classList.add("team-member-info", "col-md-" + (team.members.length > 1 ? "6" : "12"), "col-sm-12");
    result.appendChild(playerLink);
    return result;
}

function calculateRank(searchResult, i)
{
    return (searchResult.meta.page - 1) * searchResult.meta.perPage + i + 1;
}

function showCharacterInfo(e)
{
    e.preventDefault();
    const info = document.getElementById("player-info");

    const accountId = e.currentTarget.getAttribute("data-account-id");
    info.setAttribute("data-account-id", accountId);
    if(currentAccount != null && currentFollowing != null)
    {
        if(Object.values(currentFollowing).filter(val=>val.followingAccountId == accountId).length > 0)
        {
            document.querySelector("#follow-button").classList.add("d-none");
            document.querySelector("#unfollow-button").classList.remove("d-none");
        }
        else
        {
            document.querySelector("#follow-button").classList.remove("d-none");
            document.querySelector("#unfollow-button").classList.add("d-none");
        }
    }
    document.getElementById("player-info-title-name").textContent
        = e.currentTarget.getElementsByClassName("player-name")[0].textContent;
    const region = enumOfName(e.currentTarget.getAttribute("data-character-region"), REGION);
    const realm = e.currentTarget.getAttribute("data-character-realm");
    const id = e.currentTarget.getAttribute("data-character-id");
    const battlenetId = e.currentTarget.getAttribute("data-character-battlenet-id");
    const profileLink = `https://starcraft2.com/en-gb/profile/${region.code}/${realm}/${battlenetId}`;
    document.getElementById("battlenet-profile-link").setAttribute("href", profileLink);
    document.getElementById("player-info-battletag").textContent = e.currentTarget.getAttribute("data-character-battletag");
    getCharacterTeams(id).then(o => $("#player-info").modal());
    getCharacterStats(id);
}

function getCharacterTeams(id)
{
    setGeneratingStatus("begin");
    const request = "api/character/" + id + "/teams";
    return fetch(request)
        .catch(error => setGeneratingStatus("error", error.message))
        .then(resp => {if (!resp.ok) throw new Error(resp.statusText); return resp.json();})
        .catch(error => setGeneratingStatus("error", error.message))
        .then(json => updateCharacterTeams(json))
        .then(o => setGeneratingStatus("success"));
}

function updateCharacterTeams(searchResult)
{
    grouped = searchResult.reduce(function(rv, x) {
        (rv[x["season"]["id"]] = rv[x["season"]["id"]] || []).push(x);
        return rv;
    }, {});

    const navs = document.getElementById("character-teams-section").getElementsByClassName("nav-item");
    const panes = document.getElementById("character-teams-section").getElementsByClassName("tab-pane");
    let shown = false;
    let ix = 0;

    for(const nav of navs) nav.classList.add("d-none");
    const groupedEntries = Object.entries(grouped);
    for(const [season, teams] of groupedEntries)
    {
        const nav = navs[ix];
        const link = nav.getElementsByClassName("nav-link")[0];
        const pane = panes[ix];
        const linkText = teams[0].season.year + " s" + teams[0].season.number;
        link.textContent = linkText;
        pane.getElementsByTagName("h4")[0].textContent =
            teams[0].season.year + " season " + teams[0].season.number + " teams";
        if(!shown)
        {
            if(currentSeason < 0 || season == currentSeason || ix == groupedEntries.length - 1)
            {
                $(link).tab("show");
                shown = true;
            }
        }
        const table = pane.getElementsByClassName("table")[0];
        updateTeamsTable(table, {result: teams});
        nav.classList.remove("d-none");
        ix++;
    }
    updateTabSelect(document.getElementById("teams-season-select"), navs);
}

function getCharacterStats(id)
{
    setGeneratingStatus("begin");
    const request = "api/character/" + id + "/stats";
    return fetch(request)
        .catch(error => setGeneratingStatus("error", error.message))
        .then(resp => {if (!resp.ok) throw new Error(resp.statusText); return resp.json();})
        .catch(error => setGeneratingStatus("error", error.message))
        .then(json => updateCharacterStats(json))
        .then(o => setGeneratingStatus("success"));
}

function updateCharacterStats(searchResult)
{
    for(const statsSection of document.getElementsByClassName("player-stats-dynamic")) statsSection.classList.add("d-none");
    for(const stats of searchResult)
    {
        const teamFormat = enumOfId(stats.queueType, TEAM_FORMAT);
        const teamType = enumOfId(stats.teamType, TEAM_TYPE);
        const raceName = stats.race == null ? "all" : enumOfName(stats.race, RACE).name;
        const league = enumOfId(stats.leagueMax, LEAGUE);
        const card = document.getElementById("player-stats-" + teamFormat.name + "-" + teamType.name);
        const raceStats = card.getElementsByClassName("player-stats-" + raceName)[0];
        raceStats.getElementsByClassName("player-stats-" + raceName + "-mmr")[0].textContent = stats.ratingMax;
        raceStats.getElementsByClassName("player-stats-" + raceName + "-games")[0].textContent = stats.gamesPlayed;
        const leagueStats = raceStats.getElementsByClassName("player-stats-" + raceName + "-league")[0];
        removeChildren(leagueStats);
        leagueStats.appendChild(createImage("league/", league.name, ["table-image", "table-image-square"]));
        raceStats.classList.remove("d-none");
        card.classList.remove("d-none");
    }
    for(const card of document.querySelectorAll(".player-stats-section:not(.d-none)"))
    {
        const table = card.querySelector(".player-stats-table");
        const visibleRows = table.querySelectorAll("tr.player-stats-dynamic:not(.d-none)");
        if
        (
            visibleRows.length === 2
            && visibleRows[0].querySelector(".player-stats-games").textContent
                == visibleRows[1].querySelector(".player-stats-games").textContent
        )
            table.querySelector(".player-stats-all").classList.add("d-none");
        const raceCol = table.querySelectorAll("th")[0];
        const mmrCol = table.querySelectorAll("th")[1];
        sortTable(table, [mmrCol, raceCol]);
    }
}

function getCharacters(name)
{
    setGeneratingStatus("begin");
    const request = "api/characters?name=" + encodeURIComponent(name);
    return fetch(request)
        .catch(error => setGeneratingStatus("error", error.message))
        .then(resp => {if (!resp.ok) throw new Error(resp.statusText); return resp.json();})
        .catch(error => setGeneratingStatus("error", error.message))
        .then(json => updateCharacters(document.getElementById("search-table"), json))
        .then(o => {document.getElementById("search-result-all").classList.remove("d-none"); setGeneratingStatus("success");})
        .then(o => scrollIntoViewById("search-result-all"));
}

function updateCharacters(table, searchResult)
{
    const tbody = table.getElementsByTagName("tbody")[0];
    removeChildren(tbody);

    for(let i = 0; i < searchResult.length; i++)
    {
        const character = searchResult[i];
        const row = tbody.insertRow();
        row.insertCell().appendChild(createImage("flag/", character.members.character.region.toLowerCase(), ["table-image-long"]));
        row.insertCell().appendChild(createImage("league/", enumOfId(character.leagueMax, LEAGUE).name, ["table-image", "table-image-square", "mr-1"]));
        row.insertCell().appendChild(document.createTextNode(character.ratingMax));
        row.insertCell().appendChild(document.createTextNode(character.totalGamesPlayed))
        const membersCell = row.insertCell();
        membersCell.classList.add("complex", "cell-main");
        const mRow = document.createElement("span");
        mRow.classList.add("row", "no-gutters");
        const mInfo = createMemberInfo(character, character.members);
        mInfo.getElementsByClassName("player-name")[0].classList.add("c-divider");
        const bTag = document.createElement("span");
        bTag.classList.add("c-divider", "battle-tag");
        bTag.textContent = character.members.account.battleTag;
        mInfo.getElementsByClassName("player-link")[0].appendChild(bTag);
        mRow.appendChild(mInfo);
        membersCell.appendChild(mRow);
        tbody.appendChild(row);
    }
}

function getSeasons()
{
    setGeneratingStatus("begin");
    return fetch("api/seasons")
        .catch(error => setGeneratingStatus("error", error.message))
        .then(resp => {if (!resp.ok) throw new Error(resp.statusText); return resp.json();})
        .catch(error => setGeneratingStatus("error", error.message))
        .then(json => updateSeasons(json))
        .then(o => setGeneratingStatus("success"));
}

function updateSeasons(seasons)
{
    updateSeasonsTabs(seasons);
    for(const seasonPicker of document.querySelectorAll(".season-picker"))
    {
        removeChildren(seasonPicker);
        if(seasons.length > 0) currentSeason = seasons[0].id;
        for(const season of seasons)
        {
            const option = document.createElement("option");
            option.setAttribute("label", season.year + " s" + season.number);
            option.textContent = `${season.year} s${season.number}`;
            option.setAttribute("value", season.id);
            seasonPicker.appendChild(option);
        }
    }
}

function updateSeasonsTabs(seasons)
{
    const seasonPills = createTabList(seasons.length, "character-teams-season", "4");
    seasonPills.nav.classList.add("d-none");
    const teamSection = document.getElementById("character-teams-section");
    enhanceTabSelect(document.getElementById("teams-season-select"), seasonPills.nav);
    teamSection.appendChild(seasonPills.nav);
    for(const pane of seasonPills.pane.getElementsByClassName("tab-pane"))
    {
        const table = createTable(["Format", "Region", "League", "Team", "MMR", "Games", "Win%"]);
        table.getElementsByTagName("table")[0].setAttribute("data-ladder-format-show", "true");
        pane.appendChild(table);
    }
    teamSection.appendChild(seasonPills.pane);
}

function removeChildren(node)
{
    while(node.hasChildNodes())
    {
        node.removeChild(node.lastChild);
    }
}

function createImage(prefix, name, classes)
{
    const img = document.createElement("img");
    img.setAttribute("src", `${RESOURCE_PATH}icon/${prefix}${name}.svg`);
    img.setAttribute("alt", name);
    img.setAttribute("title", name);
    for(const clazz of classes)
    {
        img.classList.add(clazz);
    }
    return img;
}

function createPaginations()
{
    for(const container of document.getElementsByClassName("pagination"))
    {
        createPagination(container, PAGINATION_SIDE_BUTTON_COUNT);
    }
}

function createPagination(container, sidePageCount)
{
    let i;
    const pageCount = sidePageCount * 2 + 1 + 2 + 2;
    for (i = 0; i < pageCount; i++)
    {
        container.appendChild(createPaginationPage(1, ""));
    }
}

function createPaginationPage(pageNum, label)
{
    const li = document.createElement("li");
    li.classList.add("page-item");
    const page = document.createElement("a");
    page.setAttribute("href", "#generated-info-all");
    page.classList.add("page-link");
    page.textContent = label;
    page.setAttribute("data-page-number", pageNum);
    li.appendChild(page);
    return li;
}

function updateLadderPaginations()
{
    if(currentLadder == null || currentLadder.result.length < 1) return;
    const backwardParams = new Map();
    backwardParams.set("rating-anchor", currentLadder.result[0].rating);
    backwardParams.set("id-anchor", currentLadder.result[0].id);
    const forwardParams = new Map();
    forwardParams.set("rating-anchor", currentLadder.result[currentLadder.result.length - 1].rating);
    forwardParams.set("id-anchor", currentLadder.result[currentLadder.result.length - 1].id);
    const firstParams = new Map();
    firstParams.set("rating-anchor", 99999);
    firstParams.set("id-anchor", 1);
    const lastParams = new Map();
    lastParams.set("rating-anchor", 0);
    lastParams.set("id-anchor", 0);
    const params = {first: firstParams, last: lastParams, forward: forwardParams, backward: backwardParams};
    for(const pagination of document.getElementsByClassName("pagination-ladder"))
    {
        updatePagination(pagination, params, currentLadder.meta.page, currentLadder.meta.pageCount);
    }
}

function updatePagination(pagination, params, currentPage, lastPage)
{
    const pages = pagination.getElementsByClassName("page-link");
    updatePaginationPage(pages.item(0), params, PAGE_TYPE.FIRST, true, 1, 0, "First", currentPage != 1, false);
    updatePaginationPage(pages.item(1), params, PAGE_TYPE.GENERAL, false, 1, currentPage, "<", currentPage - 1 >= 1, false);
    updatePaginationPage(pages.item(pages.length - 1), params, PAGE_TYPE.LAST, false, 1, +lastPage + 1, "Last", currentPage != lastPage, false);
    updatePaginationPage(pages.item(pages.length - 2), params, PAGE_TYPE.GENERAL, true, 1, currentPage, ">", +currentPage + 1 <= lastPage, false);

    const dynamicCount = pages.length - 4;
    const sideCount = (dynamicCount - 1) / 2;
    const middleMin = sideCount + 1;
    const middleMax = lastPage - sideCount;
    const middleVal = currentPage < middleMin
        ? middleMin
        : currentPage > middleMax
            ? middleMax
            : currentPage;
    let leftStart = middleVal - sideCount;
    leftStart = leftStart < 1 ? 1 : leftStart;

    let curDynamicPage;
    for(let i = 2, curDynamicPage = leftStart; i < dynamicCount + 2; i++, curDynamicPage++ )
    {
        const forward = curDynamicPage > currentPage;
        const curTeam = forward ? teams[teams.length - 1] : teams[0];
        const curCount = Math.abs(curDynamicPage - currentPage);
        const active = curDynamicPage <= lastPage && curDynamicPage != currentPage;
        updatePaginationPage(pages.item(i), params, PAGE_TYPE.GENERAL, forward, curCount, currentPage, (active || curDynamicPage == currentPage) ? curDynamicPage : "", active, curDynamicPage == currentPage);
    }
}

function updatePaginationPage(page, params, pageType, forward, count, pageNumber, label, enabled, current)
{
    if(label === "")
    {
        page.parentElement.classList.add("d-none");
    }
    else
    {
        page.parentElement.classList.remove("d-none");
    }
    if(!enabled)
    {
        page.parentElement.classList.remove("enabled");
        page.parentElement.classList.add("disabled");
        page.removeEventListener("click", ladderPaginationPageClick);
    }
    else if (enabled && !page.classList.contains("enabled"))
    {
        page.parentElement.classList.add("enabled");
        page.parentElement.classList.remove("disabled");
        page.addEventListener("click", ladderPaginationPageClick)
    }
    if(!current)
    {
        page.parentElement.classList.remove("active");
    }
    else
    {
        page.parentElement.classList.add("active");
    }

    let pageParams;
    switch(pageType)
    {
        case PAGE_TYPE.FIRST:
            pageParams = params.first;
            break;
        case PAGE_TYPE.LAST:
            pageParams = params.last;
            break;
        case PAGE_TYPE.GENERAL:
            pageParams = forward ? params.forward : params.backward;
            break;
    }
    for(let [key, val] of pageParams) page.setAttribute("data-page-" + key, val);

    page.setAttribute("data-page-forward", forward);
    page.setAttribute("data-page-count", count);
    page.setAttribute("data-page-number", pageNumber);
    page.textContent = label;
}

function ladderPaginationPageClick(evt)
{
    const formParams = getFormParameters(evt.target.getAttribute("data-page-number"));
    getLadder
    (
        formParams,
        evt.target.getAttribute("data-page-rating-anchor"),
        evt.target.getAttribute("data-page-id-anchor"),
        evt.target.getAttribute("data-page-forward"),
        evt.target.getAttribute("data-page-count")
    );
}

function observeChartables()
{
    for(const chartable of document.getElementsByClassName("chartable"))
    {
        CHARTABLE_OBSERVER.observe(chartable, CHARTABLE_OBSERVER_CONFIG);
    }
}

function onChartableMutation(mutations, observer)
{
    for(const mutation of mutations)
    {
        updateChartable(mutation.target);
    }
}

function createChart(chartable)
{
    const type = chartable.getAttribute("data-chart-type");
    const stacked = chartable.getAttribute("data-chart-stacked");
    const title = chartable.getAttribute("data-chart-title");
    const tooltipPercentage = chartable.getAttribute("data-chart-tooltip-percentage");
    const tooltipSort = chartable.getAttribute("data-chart-tooltip-sort");
    const ctx = document.getElementById(chartable.getAttribute("data-chart-id")).getContext("2d");
    const data = collectChartJSData(chartable);
    decorateChartData(data, type);
    const chart = new Chart
    (
        ctx,
        {
            type: type,
            data: data,
            options:
            {
                title:
                {
                    display: title == null ? false : true,
                    text: title
                },
                scales:
                {
                    xAxes:
                    [{
                        display: false,
                        stacked: stacked === "true" ? true : false
                    }],
                    yAxes:
                    [{
                        display: false,
                       // ticks:{beginAtZero: true},
                        stacked: stacked === "true" ? true : false
                    }]
                },
                tooltips:
                {
                    mode: (data.customMeta.type === "pie" || data.customMeta === "doughnut")
                        ? "dataset"
                        : "index",
                    position: "average",
                    intersect: true,
                    callbacks:
                    {
                        ...(tooltipPercentage === "true") && {label: addTooltipPercentage}
                    },
                    ...(tooltipSort === "reverse") && {itemSort: sortTooltipReversed}
                }
            }
        }
    );
    CHARTS.set(chartable.id, chart);
}

function addTooltipPercentage(tooltipItem, data)
{
    let label;
    if(data.customMeta.type === "pie" || data.customMeta === "doughnut")
    {
        label = data.labels[tooltipItem.index];
    }
    else
    {
        label = data.datasets[tooltipItem.datasetIndex].label;
    }
    label += " "
        + data.datasets[tooltipItem.datasetIndex].data[tooltipItem.index].toLocaleString();
    let sum = 0;
    for(const dataset of data.datasets)
        for(const val of dataset.data) sum += val;
    label += "\t(" + calculatePercentage(data.datasets[tooltipItem.datasetIndex].data[tooltipItem.index], sum) + "%)";
    return label;
}

function sortTooltipReversed(a, b, data)
{
    return a.datasetIndex !== b.datasetIndex
        ? (b.datasetIndex - a.datasetIndex)
        : (b.index - a.index);
}

function decorateChartData(data, type)
{
    for (let i = 0; i < data.datasets.length; i++)
    {
        if (type === "line")
        {
            Object.defineProperty(data.datasets[i], "borderColor", { value: COLORS.get(data.customColors[i]), writable: true, enumerable: true, configurable: true });
            Object.defineProperty(data.datasets[i], "backgroundColor", { value: "rgba(0, 0, 0, 0)", writable: true, enumerable: true, configurable: true });
        }
        else if(type === "doughnut" || type === "pie")
        {
            const dataColors = [];
            const dataEmptyColors = [];
            for(let dataValIx = 0; dataValIx < data.datasets[i].data.length; dataValIx++)
            {
                dataColors.push(COLORS.get(data.customColors[dataValIx]));
                dataEmptyColors.push("rgba(0, 0, 0, 0)");
            }
            Object.defineProperty(data.datasets[i], "backgroundColor", { value: dataColors, writable: true, enumerable: true, configurable: true });
            Object.defineProperty(data.datasets[i], "borderColor", { value: dataEmptyColors, writable: true, enumerable: true, configurable: true });
        }
        else
        {
            Object.defineProperty(data.datasets[i], "backgroundColor", { value: COLORS.get(data.customColors[i]), writable: true, enumerable: true, configurable: true });
            Object.defineProperty(data.datasets[i], "borderColor", { value: "rgba(0, 0, 0, 0)", writable: true, enumerable: true, configurable: true });
        }
    }
}

function collectChartJSData(elem)
{
    const type = elem.getAttribute("data-chart-type");
    const stacked = elem.getAttribute("data-chart-stacked");
    const tableData = collectTableData(elem);
    const datasets = [];
    if(type !== "doughnut" && type !== "pie")
    {
        for (let i = 0; i < tableData.headers.length; i++)
        {
            datasets.push
            (
                {
                    label: tableData.headers[i],
                    data: tableData.values[i],
                    hidden: !hasNonZeroValues(tableData.values[i])
                }
            )
        }
    }
    else
    {
        const datasetData = [];
        for (let i = 0; i < tableData.headers.length; i++)
        {
            datasetData.push(tableData.values[i][0]);
        }
        datasets.push({data: datasetData});
    }
    const data =
    {
        labels: stacked ? [elem.getAttribute("data-chart-stacked-label")] : tableData.headers,
        datasets: datasets,
        customColors: tableData.colors,
        customMeta:
        {
            type: type
        }
    }
    return data;
}

function collectTableData(elem)
{
    let mode = elem.getAttribute("data-chart-collection-mode");
    mode = mode == null ? "body" : mode;
    const headers = [];
    const allVals = [];
    const colors = [];
    const headings = elem.getElementsByTagName("thead")[0].getElementsByTagName("tr")[0].getElementsByTagName("th");
    for (let i = 0; i < headings.length; i++)
    {
        const heading = headings[i];
        headers.push(heading.textContent);
        allVals.push([]);
        colors.push(heading.getAttribute("data-chart-color"));
    }

    const rows = mode === "foot"
        ? elem.getElementsByTagName("tfoot")[0].getElementsByTagName("tr")
        : elem.getElementsByTagName("tbody")[0].getElementsByTagName("tr");
    for (let i = 0; i < rows.length; i++)
    {
        const row = rows[i];
        const tds = row.getElementsByTagName("td");
        for (let tdix = 0; tdix < tds.length; tdix++)
        {
            const iText = tds[tdix].textContent;
            allVals[tdix].push(parseFloat(iText));
        }
    }
    return {headers: headers, values: allVals, colors: colors};
}

function updateChartable(chartable)
{
    const chart = CHARTS.get(chartable.id);
    if (chart === undefined)
    {
        createChart(chartable);
    }
    else
    {
        updateChart(chart, collectChartJSData(chartable))
    }
}

function updateChart(chart, data)
{
    if (data === null)
    {
        return;
    }
    if
    (
        chart.data.labels.length === data.labels.length
        && chart.data.labels.every(function(val, ix){val === data.labels[ix]})
    )
    {
        for (let i = 0; i < data.datasets.length; i++)
        {
            chart.data.datasets[i].label = data.datasets[i].label;
            chart.data.datasets[i].data = data.datasets[i].data;
            chart.data.datasets[i].hidden = data.datasets[i].hidden;
        }
    }
    else
    {
        decorateChartData(data, chart.config.type);
        chart.data = data;
    }
    chart.update();
}

function hasNonZeroValues(values)
{
    for (let i = 0; i < values.length; i++)
    {
        const val = values[i];
        if (!isNaN(val) && val != 0)
        {
            return true;
        }
    }
    return false;
}

function calculatePercentage(val, allVal)
{
    return Math.round((val / allVal) * 100);
}

function setGeneratingStatus(status, errorText = "Error", scrollToOnSuccess = null)
{
    switch(status)
    {
        case "begin":
            currentRequests++;
            if (currentRequests > 1) return;
            currentSeason = document.getElementById("season-picker").value;
            currentTeamFormat = enumOfFullName(document.getElementById("team-format-picker").value, TEAM_FORMAT);
            currentTeamType = enumOfName(document.getElementById("team-type-picker").value, TEAM_TYPE);
            disableElements(document.getElementsByTagName("input"), true);
            disableElements(document.getElementsByTagName("select"), true);
            disableElements(document.getElementsByTagName("button"), true);
            setPaginationsState(false);
            setElementsVisibility(document.getElementsByClassName("status-generating-begin"), true);
            setElementsVisibility(document.getElementsByClassName("status-generating-success"), false);
            setElementsVisibility(document.getElementsByClassName("status-generating-error"), false);
        break;
        case "success":
        case "error":
            currentRequests--;
            if(status === "error")
            {
                document.getElementById("error-generation-text").textContent = errorText;
                $("#error-generation").modal();
            }
            if(currentRequests > 0) return;
            disableElements(document.getElementsByTagName("input"), false);
            disableElements(document.getElementsByTagName("select"), false);
            disableElements(document.getElementsByTagName("button"), false);
            setPaginationsState(true);
            setElementsVisibility(document.getElementsByClassName("status-generating-begin"), false);
            setElementsVisibility(document.getElementsByClassName("status-generating-" + status), true);
            if(documentIsChanging)
            {
                shouldScrollToResult = true;
            }
            else
            {
                if(scrollToOnSuccess != null) scrollIntoViewById(scrollToOnSuccess);
                shouldScrollToResult = false;
            }
        break;
    }
}

function setElementsVisibility(elems, visible)
{
    for (const elem of elems)
    {
        const clazz = elem.getAttribute("data-hide-mode") === "hide"
            ? "invisible"
            : "d-none";
        if(!visible)
        {
            elem.classList.add(clazz);
        }
        else
        {
            elem.classList.remove(clazz);
        }
    }
}

function disableElements(elems, disable)
{
    for (var i = 0; i < elems.length; i++)
    {
        if (disable)
        {
            elems[i].setAttribute("disabled", "disabled");
        }
        else
        {
            elems[i].removeAttribute("disabled");
        }
    }
}

function setPaginationsState(enabled)
{

    if(enabled)
    {
        updateLadderPaginations();
    }
    else
    {
        for(const pagination of document.getElementsByClassName("pagination-ladder"))
        {
            for(const page of pagination.getElementsByClassName("page-link"))
            {
                page.parentElement.classList.remove("enabled");
                page.parentElement.classList.add("disabled");
                page.removeEventListener("click", ladderPaginationPageClick);
            }
        }
    }
}

function scrollIntoViewById(id)
{
    document.getElementById(id).scrollIntoView({behavior: "smooth"});
}

function createTable(theads, responsive = true)
{
    const table = document.createElement("table");
    const thead = document.createElement("thead");
    const thr = document.createElement("tr");
    for(const h of theads)
    {
        const th = document.createElement("th");
        th.setAttribute("span", "col");
        th.textContent = h;
        thr.appendChild(th);
    }
    thead.appendChild(thr);
    const tbody = document.createElement("tbody");
    table.appendChild(thead);
    table.appendChild(tbody);
    table.classList.add("table", "table-sm", "table-hover");
    if(responsive)
    {
        const tcontainer = document.createElement("div");
        tcontainer.classList.add("table-responsive");
        tcontainer.appendChild(table);
        return tcontainer;
    }
    return table;
}

function createTabList(count, prefix, hLevel, fade = false)
{
    const nav = document.createElement("nav");
    const panes = document.createElement("div");
    panes.classList.add("tab-content");
    const ul = document.createElement("ul");
    ul.classList.add("nav", "nav-pills", "mb-3", "justify-content-center");
    ul.setAttribute("role", "tablist");
    nav.appendChild(ul);
    nav.setAttribute("id", prefix + "-nav")
    for(let i = 0; i < count; i++)
    {
        const li = document.createElement("li");
        li.classList.add("nav-item");
        const a = document.createElement("a");
        const name = prefix + "-" + i;
        a.classList.add("nav-link");
        a.setAttribute("id", name + '-link');
        a.setAttribute("data-toggle", "pill");
        a.setAttribute("href", "#" + name);
        a.setAttribute("role", "tab");
        a.setAttribute("aria-controls", name)
        a.setAttribute("aria-selected", "false");
        $(a).on("click", function(e) {
            e.preventDefault();
            $(this).tab("show");
        });
        li.appendChild(a);
        ul.appendChild(li);

        const pane = document.createElement("section");
        pane.setAttribute("id", name);
        pane.classList.add("tab-pane");
        if(fade) pane.classList.add("fade");
        const h = document.createElement("h" + hLevel);
        pane.appendChild(h);
        panes.appendChild(pane);
    }
    return {nav: nav, pane: panes};
}

function enhanceTabSelect(select, nav)
{
    select.addEventListener("change", e=>$(document.getElementById(e.target.options[e.target.selectedIndex].getAttribute("data-tab"))).tab("show"));
    return select;
}

function updateTabSelect(select, navs)
{
    removeChildren(select);
    for (const nav of navs)
    {
        if(!nav.classList.contains("d-none"))
        {
            const link = nav.getElementsByClassName("nav-link")[0];
            const option = document.createElement("option");
            option.textContent = link.textContent;
            option.value = link.textContent;
            option.setAttribute("data-tab", link.getAttribute("id"));
            select.appendChild(option);
            if(link.getAttribute("aria-selected") == "true") select.value = option.value;
        }
    }
}

function createPlayerStatsCards(container)
{
    for(const teamFormat of Object.values(TEAM_FORMAT))
    {
        for(const teamType of Object.values(TEAM_TYPE))
        {
            container.appendChild(createPlayerStatsCard(teamFormat, teamType));
        }
    }
}

function createPlayerStatsCard(teamFormat, teamType)
{
    const card = document.createElement("div");
    card.classList.add("card", "card-equal", "player-stats-section", "player-stats-dynamic", "mb-3");
    card.setAttribute("id", "player-stats-" + teamFormat.name + "-" + teamType.name)
    const cardBody = document.createElement("div");
    cardBody.classList.add("card-body");
    card.appendChild(cardBody);
    const cardHeader = document.createElement("h4");
    cardHeader.textContent = teamFormat.name + " " + teamType.name;
    cardHeader.classList.add("card-title");
    cardBody.appendChild(cardHeader);
    const table = createTable(["Race", "MMR", "League", "Games"], false);
    table.classList.add("player-stats-table");
    const tbody = table.getElementsByTagName("tbody")[0];
    for(const race of Object.values(RACE)) tbody.appendChild(createPlayerStatsRaceRow(race.name));
    tbody.appendChild(createPlayerStatsRaceRow("all"));
    cardBody.appendChild(table);
    return card;
}

function createPlayerStatsRaceRow(raceName)
{
    const raceRow = document.createElement("tr");
    raceRow.classList.add("player-stats-" + raceName, "player-stats-dynamic");
    const raceRace = raceRow.insertCell();
    raceRace.classList.add("player-stats-race", "player-stats-" + raceName + "-race");
    if(raceName === "all")
    {
        raceRace.textContent = "All";
    }
    else
    {
        raceRace.appendChild(createImage("race/", raceName, ["table-image", "table-image-square"]));
    }
    raceRow.insertCell().classList.add("player-stats-mmr", "player-stats-" + raceName + "-mmr");
    raceRow.insertCell().classList.add("player-stats-league", "player-stats-" + raceName + "-league");
    raceRow.insertCell().classList.add("player-stats-games", "player-stats-" + raceName + "-games");
    return raceRow;
}

const tableComparer = (idxs, asc) => (a, b) =>((v1, v2) =>compareValueArrays(v1, v2))
(getCellValues(asc ? a : b, idxs), getCellValues(asc ? b : a, idxs));

function getCellValues(tr, idxs)
{
    var vals = [];
    for(const idx of idxs) vals.push(tr.children[idx].innerText || tr.children[idx].textContent);
    return vals;
}

function compareValueArrays(a, b)
{
    var compare = 0;
    for(var i = 0; i < a.length; i++)
    {
        compare = compareValues(a[i], b[i]);
        if(compare !== 0) return compare;
    }
    return compare;
}

function compareValues(v1, v2)
{
    return v1 !== '' && v2 !== '' && !isNaN(v1) && !isNaN(v2) ? v1 - v2 : v1.toString().localeCompare(v2);
}

function sortTable(table, ths)
{
    if(ths.length < 1) return;

    const tbody = table.querySelector('tbody');
    const thsArray = Array.from(ths[0].parentNode.children);
    const ixs = [];
    for(th of ths) ixs.push(thsArray.indexOf(th));
    Array.from(tbody.querySelectorAll('tr'))
        .sort(tableComparer(ixs, false))
        .forEach(tr => tbody.appendChild(tr));
}

function getMyInfo()
{
    if(!document.cookie.includes("oauth-reg")) return Promise.resolve(1);
    return getMyAccount()
        .then(e=>updateMyInfoThen());
}

function updateMyInfoThen()
{
    if (currentAccount != null)
    {
        getMyCharacters();
        getMyFollowing();
        for(e of document.querySelectorAll(".login-anonymous")) e.classList.add("d-none");
        for(e of document.querySelectorAll(".login-user")) e.classList.remove("d-none");
    }
    else
    {
        for(e of document.querySelectorAll(".login-anonymous")) e.classList.remove("d-none");
        for(e of document.querySelectorAll(".login-user")) e.classList.add("d-none");
    }
}

function getMyAccount()
{
    setGeneratingStatus("begin");
    const request = "api/my/account";
    return fetch(request)
        .then(resp => {if (!resp.ok) throw new Error(resp.status + " " + resp.statusText); return resp.json();})
        .then(json => updateMyAccount(json))
        .then(o => setGeneratingStatus("success"))
        .catch(error => onPersonalException(error));
}

function onPersonalException(error)
{
    if (error.message.startsWith("401") && document.cookie.includes("oauth-reg"))
    {
        renewBlizzardRegistration();
    }
    else
    {
        setGeneratingStatus("error", error.message);
    }
}

function updateMyAccount(account)
{
    currentAccount = account;
    document.querySelector("#login-battletag").textContent = currentAccount.battleTag;
}

function getMyCharacters()
{
    setGeneratingStatus("begin");
    return fetch("api/my/characters")
        .then(resp => {if (!resp.ok) throw new Error(resp.status + " " + resp.statusText); return resp.json();})
        .then(json => updateCharacters(document.querySelector("#personal-characters-table"), json))
        .then(o => setGeneratingStatus("success"))
        .catch(error => setGeneratingStatus("error", error.message));
}

function getMyFollowing()
{
    setGeneratingStatus("begin");
    return fetch("api/my/following")
        .then(resp => {if (!resp.ok) throw new Error(resp.status + " " + resp.statusText); return resp.json();})
        .then(json => currentFollowing = json)
        .then(o => setGeneratingStatus("success"))
        .catch(error => setGeneratingStatus("error", error.message));
}

function getMyLadder(formParams)
{
    setGeneratingStatus("begin");
    return fetch("api/my/following/ladder?" + formParams)
        .then(resp => {if (!resp.ok) throw new Error(resp.statusText); return resp.json();})
        .then(json => updateMyLadder(json))
        .then(o => setGeneratingStatus("success", null, "following-ladder"))
        .catch(error => setGeneratingStatus("error", error.message));
}

function updateMyLadder(searchResult)
{
    const result =
    {
        result: searchResult,
        meta:
        {
            page: 1,
            perPage: searchResult.length
        }
    }
    updateTeamsTable(document.getElementById("following-ladder"), result);
    document.getElementById("following-ladder-container").classList.remove("d-none");
}

function follow()
{
    setGeneratingStatus("begin");
    const profile = document.querySelector("#player-info");
    const id = profile.getAttribute("data-account-id");
    return fetch("api/my/following/" + id, {method: "POST"})
        .then
        (
            resp =>
            {
                if (!resp.ok) throw new Error(resp.statusText);
                document.querySelector("#follow-button").classList.add("d-none");
                document.querySelector("#unfollow-button").classList.remove("d-none");
                return getMyFollowing();
            }
        )
        .then(o => setGeneratingStatus("success"))
        .catch(error => setGeneratingStatus("error", error.message));
}

function unfollow()
{
    setGeneratingStatus("begin");
    const profile = document.querySelector("#player-info");
    const id = profile.getAttribute("data-account-id");
    return fetch("api/my/following/" + id, {method: "DELETE"})
        .then
        (
            resp =>
            {
                if (!resp.ok) throw new Error(resp.statusText);
                document.querySelector("#follow-button").classList.remove("d-none");
                document.querySelector("#unfollow-button").classList.add("d-none");
                return getMyFollowing();
            }
        )
        .then(o => setGeneratingStatus("success"))
        .catch(error => setGeneratingStatus("error", error.message));
}

function renewBlizzardRegistration()
{
    if(currentAccount != null)
    {
        setGeneratingStatus("success");
        $("#error-session").modal();
    }
    else
    {
        doRenewBlizzardRegistration();
    }
}

function doRenewBlizzardRegistration()
{
    setGeneratingStatus("begin");
    window.location.href=ROOT_CONTEXT_PATH + "/oauth2/authorization/" + getCookie("oauth-reg");
}

function getCookie(cname) {
  var name = cname + "=";
  var decodedCookie = decodeURIComponent(document.cookie);
  var ca = decodedCookie.split(';');
  for(var i = 0; i <ca.length; i++) {
    var c = ca[i];
    while (c.charAt(0) == ' ') {
      c = c.substring(1);
    }
    if (c.indexOf(name) == 0) {
      return c.substring(name.length, c.length);
    }
  }
  return "";
}
