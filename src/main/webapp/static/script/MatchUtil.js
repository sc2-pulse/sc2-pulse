// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

class MatchUtil
{

    static updateMatchTable(table, matches, isMainParticipant, historical, versusLinkPrefix = null)
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
                    teams.push(historical ? TeamUtil.createTeamFromSnapshot(p.team, p.teamState) : p.team);
                }
            }

            allTeams.push(...teams);
            TeamUtil.updateTeamsTable(table, {result: teams}, false, "xl");
            MatchUtil.decorateTeams(participantsGrouped, teams, tBody, rowNum, isMainParticipant, historical, versusLinkPrefix);

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

    static decorateTeams(participantsGrouped, teams, tBody, rowNum, isMainParticipant, historical, versusLinkPrefix)
    {
        const mainTeam = versusLinkPrefix ? null : MatchUtil.findMainTeam(teams, isMainParticipant);

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

            if(historical) {
                const participant = participantsGrouped.get("WIN").find(p=>p.team && p.team.id == teamId)
                    || participantsGrouped.get("LOSS").find(p=>p.team && p.team.id == teamId);
                MatchUtil.addMmrChange(tr, participant);
            }

            const decision = participantsGrouped.get("WIN") ?
               (participantsGrouped.get("WIN").find(p=>p.team && p.team.id == teamId) ? "Win" : "Loss")
               : "Loss";

            const team = teams.find(t=>t.id == teamId);
            const teamElem = tr.querySelector(":scope .team");
            const decisionClass = decision == "Win" ? "bg-success-fade-1" : "bg-danger-fade-1";
            teamElem.classList.add(decisionClass);
            if((mainTeam && teamId == mainTeam.id) || (versusLinkPrefix && team.members.find(m=>isMainParticipant({team: team, member: m})))) {
                teamElem.classList.add("font-weight-bold");
                decisionElem.classList.add("font-weight-bold", decisionClass);
            } else {
                MatchUtil.appendVersusLink(tr, mainTeam, team, versusLinkPrefix);
            }

            decisionElem.textContent = decision;
            tr.prepend(decisionElem);
            rowNum++;
        }
    }

    static createMmrChangeElem(diff)
    {
        if(!diff) return null;

        const changeElem = document.createElement("span");
        changeElem.classList.add(diff > 0 ? "text-success" : "text-danger", "rating-change");
        changeElem.textContent = Util.NUMBER_FORMAT_DIFF.format(diff);
        return changeElem;
    }

    static addMmrChange(tr, participant)
    {
        const changeElem = MatchUtil.createMmrChangeElem(participant.participant.ratingChange);
        if(!changeElem) return;

        const ratingElem = tr.querySelector(":scope .rating");
        const rating = ratingElem.textContent;
        ratingElem.innerHTML = `<span class="text-nowrap"><span>${rating}</span> </span>`;
        ratingElem.querySelector(":scope > span").appendChild(changeElem);
    }

    static findMainTeam(teams, isMainParticipant)
    {
        for(const team of teams.filter(t=>t.members))
            if(team.members.find(m=>isMainParticipant({team: team, member: m})))
                return team;
        return null;
    }

    static appendVersusLink(tr, mainTeam, versusTeam, versusLinkPrefix)
    {
        let href;
        if(versusLinkPrefix) {
            href = versusLinkPrefix + "&team2=" + encodeURIComponent(TeamUtil.getTeamLegacyUid(versusTeam));
        } else {
            href = mainTeam && versusTeam
                ? VersusUtil.getVersusUrl("matches-type")
                    + "&team1=" + encodeURIComponent(TeamUtil.getTeamLegacyUid(mainTeam))
                    + "&team2=" + encodeURIComponent(TeamUtil.getTeamLegacyUid(versusTeam))
                : null;
        }
        if(href) {
            const vsLink = VersusUtil.createEmptyVersusLink();
            vsLink.setAttribute("href", href);
            vsLink.addEventListener("click", VersusUtil.onVersusLinkClick);
            tr.querySelector(":scope .misc").prepend(vsLink);
        }
    }

    static appendUnknownMatchParticipant(tr, decisionElem, isMainParticipant)
    {
        const split = tr.getAttribute("data-team-alternative-data").split(",");
        const charId = parseInt(split[0]);
        decisionElem.textContent = split[1].toLowerCase();
        const mainParticipant = isMainParticipant(charId);
        const decisionClass = split[1] == "WIN" ? "bg-success-fade-1" : "bg-danger-fade-1";
        tr.prepend(decisionElem);
        tr.insertCell(); tr.insertCell(); tr.insertCell(); tr.insertCell();
        const teamCell = tr.insertCell();
        teamCell.classList.add("text-left", decisionClass);
        if(mainParticipant) {
            decisionElem.classList.add(decisionClass, "font-weight-bold");
            teamCell.classList.add("font-weight-bold");
        }
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
