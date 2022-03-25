// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

class MatchUtil
{

    static updateMatchTable(table, matches, isMainParticipant, historical)
    {
        const tBody = table.querySelector(":scope tbody");
        ElementUtil.removeChildren(tBody);
        const validMatches = matches;
        const allTeams = [];
        for(let i = 0; i < validMatches.length; i++)
        {
            const match = validMatches[i];
            const participantsGrouped = Util.groupBy(match.participants, p=>p.participant.decision);

            const rowNum = tBody.childNodes.length;
            const teams = [];
            const participantsSorted = match.participants.sort((a, b)=>b.participant.decision.localeCompare(a.participant.decision));
            for(const p of participantsSorted) {
                if(!p.team) {
                    teams.push({id: -1, alternativeData: p.participant.playerCharacterId + "," + p.participant.decision})
                } else if(teams.length == 0 || teams[teams.length - 1].id != p.team.id) {
                    if(historical) {
                        let teamClone = {};
                        Object.assign(teamClone, p.team);
                        teamClone.rating = p.teamState.teamState.rating;
                        teamClone.league = p.teamState.league;
                        teamClone.leagueType = p.teamState.league.type;
                        teamClone.tierType = p.teamState.tier;
                        teams.push(teamClone);
                    } else {
                        teams.push(p.team);
                    }
                }
            }

            allTeams.push(...teams);
            TeamUtil.updateTeamsTable(table, {result: teams}, false);
            MatchUtil.prependDecisions(participantsGrouped, teams, tBody, rowNum, isMainParticipant);

            const tr = tBody.childNodes[rowNum];
            tr.classList.add("section-splitter");
            const mapCell = document.createElement("td");
            mapCell.setAttribute("rowspan", teams.length);
            mapCell.textContent = match.map.name;
            tr.prepend(mapCell);
            const typeCell = document.createElement("td");
            typeCell.setAttribute("rowspan", teams.length);
            typeCell.textContent = match.match.type.replace(/_/g, "");
            tr.prepend(typeCell);
            const lengthCell = document.createElement("td");
            lengthCell.setAttribute("rowspan", teams.length);
            lengthCell.textContent = MatchUtil.generateMatchLengthString(matches, i)
            tr.prepend(lengthCell);
            const dateCell = document.createElement("td");
            dateCell.setAttribute("rowspan", teams.length);
            dateCell.textContent = Util.DATE_TIME_FORMAT.format(Util.parseIsoDateTime(match.match.date));
            tr.prepend(dateCell);
        }

        return {teams: allTeams, validMatches: validMatches};
    }

    static prependDecisions(participantsGrouped, teams, tBody, rowNum, isMainParticipant)
    {
        for(let ix = 0; ix < teams.length; ix++)
        {
            const tr = tBody.childNodes[rowNum];
            const teamId = tr.getAttribute("data-team-id");
            const decisionElem = document.createElement("td");
            decisionElem.classList.add("text-capitalize");

            if(!teamId) {
                MatchUtil.appendUnknownMatchParticipant(tr, decisionElem, isMainParticipant);
                rowNum++;
                continue;
            }

            const decision = participantsGrouped.get("WIN") ?
               (participantsGrouped.get("WIN").find(p=>p.team && p.team.id == teamId) ? "Win" : "Loss")
               : "Loss";

            const team = teams.find(t=>t.id == teamId);
            if(team.members.find(m=>isMainParticipant({team: team, member: m}))) {
                decisionElem.classList.add("font-weight-bold", "text-white", decision == "Win" ? "bg-success" : "bg-danger")
            } else {
                decisionElem.classList.add(decision == "Win" ? "text-success" : "text-danger");
            }

            decisionElem.textContent = decision;
            tr.prepend(decisionElem);
            rowNum++;
        }
    }

    static appendUnknownMatchParticipant(tr, decisionElem, isMainParticipant)
    {
        const split = tr.getAttribute("data-team-alternative-data").split(",");
        const charId = parseInt(split[0]);
        decisionElem.textContent = split[1].toLowerCase();
        if(isMainParticipant(charId)) {
            decisionElem.classList.add("font-weight-bold", "text-white", split[1] == "WIN" ? "bg-success" : "bg-danger");
        } else {
            decisionElem.classList.add(split[1] == "WIN" ? "text-success" : "text-danger");
        }
        tr.prepend(decisionElem);
        tr.insertCell(); tr.insertCell(); tr.insertCell(); tr.insertCell();
        const teamCell = tr.insertCell();
        teamCell.classList.add("text-left");
        const rowSpan = document.createElement("span");
        rowSpan.classList.add("row", "no-gutters");
        const playerLink = document.createElement("a");
        playerLink.setAttribute("data-character-id", charId);
        playerLink.setAttribute("href", `${ROOT_CONTEXT_PATH}?type=character&id=${charId}&m=1`);
        playerLink.addEventListener("click", CharacterUtil.showCharacterInfo);
        playerLink.classList.add("player-link", "col-md-12", "col-lg-12");
        playerLink.textContent = charId;
        rowSpan.appendChild(playerLink);
        teamCell.appendChild(rowSpan);
        tr.insertCell(); tr.insertCell(); tr.insertCell(); tr.insertCell();
    }

    static generateMatchLengthString(matches, i)
    {
        try
        {
            const match = matches[i];
            const matchType = EnumUtil.enumOfName(match.match.type.replace(/_/g, ""), TEAM_FORMAT);
            if(match.participants.length == matchType.memberCount * 2 && match.match.duration) {
                return Math.round(match.match.duration / 60) + "m";
            } else {
                const matchLength = MatchUtil.calculateMatchLengthSeconds(matches, i);
                return matchLength == -1 ? "" : Math.round(matchLength / 60) + "m";
            }
        }
        catch(e){}
        const matchLength = MatchUtil.calculateMatchLengthSeconds(matches, i);
        return matchLength == -1 ? "" : Math.round(matchLength / 60) + "m";;
    }

    static calculateMatchLengthSeconds(matches, i)
    {
        if(i == matches.length - 1) return -1;
        const length = (new Date(matches[i].match.date).getTime() - new Date(matches[i + 1].match.date).getTime()) / 1000
            - MATCH_DURATION_OFFSET;
        if(length < 0 || length > MatchUtil.MATCH_DURATION_MAX_SECONDS) return -1;
        return length;
    }

}

MatchUtil.MATCH_DURATION_MAX_SECONDS = 5400;
