class BufferUtil
{

    static clear()
    {
        BufferUtil.teamBuffer.clear();
        BufferUtil.clanBuffer.clear();
    }

    static updateView()
    {
        const bufferElem = document.querySelector("#team-buffer");
        document.querySelector("#team-buffer-count").textContent = BufferUtil.teamBuffer.buffer.size;
        document.querySelector("#team-buffer-clan-count").textContent = BufferUtil.clanBuffer.buffer.size;
        const size = BufferUtil.teamBuffer.buffer.size + BufferUtil.clanBuffer.buffer.size;
        if(size == 0) {
            bufferElem.classList.add("d-none");
        } else {
            bufferElem.classList.remove("d-none");
        }
        BufferUtil.updateTeamMmrLink();
        BufferUtil.updateVersusLink();
        BufferUtil.updateGroupLink();
        BufferUtil.updateCopyLink();
    }

    static updateTeamMmrLink()
    {
        const elem = document.querySelector("#team-buffer-mmr");
        if(BufferUtil.teamBuffer.buffer.size > 0) {
            elem.setAttribute("href", TeamUtil.getTeamMmrHistoryHref(Array.from(BufferUtil.teamBuffer.buffer.values())));
            elem.classList.remove("d-none");
        } else {
            elem.classList.add("d-none");
        }
    }

    static updateVersusLink()
    {
        const params = new URLSearchParams();
        for(const team of BufferUtil.teamBuffer.buffer.values()) params.append("team" + (team.bufferGroup ? team.bufferGroup : 1), TeamUtil.getTeamLegacyUid(team));
        for(const clan of BufferUtil.clanBuffer.buffer.values()) params.append("clan" + (clan.bufferGroup ? clan.bufferGroup : 1), clan.id);
        const matchType = localStorage.getItem("matches-type-versus");
        if(matchType != null && matchType != "all") params.append("matchType", matchType);
        const link = document.querySelector("#team-buffer-versus");
        if(params.getAll("team1").length + params.getAll('clan1').length > 0
            && params.getAll("team2").length + params.getAll('clan2').length > 0) {
            link.setAttribute("href", `${ROOT_CONTEXT_PATH}?type=versus&m=1&${params.toString()}`);
            link.classList.remove("d-none");
        } else {
            link.classList.add("d-none");
        }
    }

    static updateGroupLink()
    {
        const params = new URLSearchParams();
        const link = document.querySelector("#team-buffer-group");
        for(const clan of BufferUtil.clanBuffer.buffer.values()) params.append("clanId", clan.id);
        if(params.getAll("clanId").length > 0) {
            link.setAttribute("href", `${ROOT_CONTEXT_PATH}?type=group&m=1&${params.toString()}`);
            link.classList.remove("d-none");
        } else {
            link.classList.add("d-none");
        }
    }

    static updateCopyLink()
    {
        const teamActions = document.querySelectorAll("#team-buffer .action-team");
        if(BufferUtil.teamBuffer.buffer.size > 0) {
            teamActions.forEach(a=>a.classList.remove("disabled"));
        } else {
            teamActions.forEach(a=>a.classList.add("disabled"));
        }
    }

    static copyCharacterId(evt)
    {
        evt.preventDefault();
        const str = Array.from(BufferUtil.teamBuffer.buffer.values())
            .flatMap(t=>t.members)
            .map(m=>m.character.id)
            .join(",");
        return navigator.clipboard.writeText(str);
    }

    static copyClanId(evt)
    {
        evt.preventDefault();
        const teamClanIds = Array.from(BufferUtil.teamBuffer.buffer.values())
            .flatMap(t=>t.members)
            .map(m=>m.clan)
            .filter(c=>c)
            .map(c=>c.id);
        const clanIds = Array.from(BufferUtil.clanBuffer.buffer.values())
            .map(c=>c.id);
        const str = teamClanIds.concat(clanIds)
            .join(",");
        return navigator.clipboard.writeText(str);
    }

    static copyLegacyId(evt)
    {
        evt.preventDefault();
        const str = Array.from(BufferUtil.teamBuffer.buffer.values())
            .map(t=>t.legacyId)
            .join(",");
        return navigator.clipboard.writeText(str);
    }

    static copyCharacterRaceId(evt)
    {
        evt.preventDefault();
        const str = Array.from(BufferUtil.teamBuffer.buffer.values())
            .flatMap(t=>t.members)
            .map(m=>m.character.id + "" + TeamUtil.getFavoriteRace(m).code)
            .join(",");
        return navigator.clipboard.writeText(str);
    }

    static copyTeamLegacyUid(evt)
    {
        evt.preventDefault();
        const str = Array.from(BufferUtil.teamBuffer.buffer.values())
            .flatMap(team=>team.legacyUid)
            .join(",");
        return navigator.clipboard.writeText(str);
    }

    static enhance()
    {
        document.querySelector("#team-buffer-clear").addEventListener("click", BufferUtil.clear);
        document.querySelector("#team-buffer-versus").addEventListener("click", VersusUtil.onVersusLinkClick);
        document.querySelector("#team-buffer-group").addEventListener("click", GroupUtil.onGroupLinkClick);
        document.querySelector("#team-buffer-copy-character-id").addEventListener("click", BufferUtil.copyCharacterId);
        document.querySelector("#team-buffer-copy-legacy-id").addEventListener("click", BufferUtil.copyLegacyId);
        document.querySelector("#team-buffer-copy-character-race-id").addEventListener("click", BufferUtil.copyCharacterRaceId);
        document.querySelector("#team-buffer-copy-team-legacy-uid").addEventListener("click", BufferUtil.copyTeamLegacyUid);
        document.querySelector("#team-buffer-copy-clan-id").addEventListener("click", BufferUtil.copyClanId);
    }

    static createToggleElement(buf, item)
    {
        const remove = buf.buffer.has(item.id);
        const toggle = ElementUtil.createTagButton("div", "table-image table-image-square background-cover team-buffer-toggle d-inline-block " + (remove ? "remove" : "add"));
        toggle.addEventListener("click", e=>Buffer.toggle(buf, e));
        return toggle;
    }

    static appendGroupElements(table)
    {
        for(const tr of table.querySelectorAll(":scope tbody tr")) {
            const miscTd = tr.children[tr.children.length - 1];
            miscTd.prepend(BufferUtil.createGroupSelect(BufferUtil.getTeamOrClanFromElement(tr)));
        }
    }

    static createGroupSelect(item)
    {
        const select = BufferUtil.GROUP_SELECT_ELEMENT.cloneNode(true);
        select.value = item.bufferGroup ? item.bufferGroup : 1;
        select.addEventListener("change", e=>{
            BufferUtil.getTeamOrClanFromElement(e.target).bufferGroup = e.target.value;
            BufferUtil.updateVersusLink();
        });
        return select;
    }

    static getTeamOrClanFromElement(elem)
    {
        const tr = elem.closest("tr");
        if(tr.getAttribute("data-team-id")) {
            return TeamUtil.getTeamFromElement(elem);
        } else {
            return ClanUtil.getClanFromElement(elem);
        }
    }

}

BufferUtil.teamBuffer = new Buffer("team-buffer-toggle", "data-team-id",
    (t)=>TeamUtil.getTeamFromElement(t),
    (buf)=>{Model.DATA.get(VIEW.TEAM_BUFFER).set(VIEW_DATA.SEARCH, {result: Array.from(buf.buffer.values())});},
    (buf)=>{
        const bufferElem = document.querySelector("#team-buffer-teams");
        if(buf.buffer.size == 0) {
            bufferElem.classList.add("d-none");
        } else {
            bufferElem.classList.remove("d-none");
        }
        TeamUtil.updateTeamsTable(bufferElem, {result:Array.from(BufferUtil.teamBuffer.buffer.values())});
        BufferUtil.appendGroupElements(bufferElem);
        BufferUtil.updateView();
    }
);

BufferUtil.clanBuffer = new Buffer("team-buffer-toggle", "data-clan-id",
    (t)=>ClanUtil.getClanFromElement(t),
    (buf)=>{Model.DATA.get(VIEW.CLAN_BUFFER).set(VIEW_DATA.SEARCH, {searchResult: {result: Array.from(buf.buffer.values())}});},
    (buf)=>{
        const bufferElem = document.querySelector("#team-buffer-clans");
        if(buf.buffer.size == 0) {
            bufferElem.classList.add("d-none");
        } else {
            bufferElem.classList.remove("d-none");
        }
        ClanUtil.updateClanTable(bufferElem, Array.from(buf.buffer.values()));
        BufferUtil.appendGroupElements(bufferElem);
        BufferUtil.updateView();
    }
);

BufferUtil.GROUP_SELECT_ELEMENT = document.createElement("select");
BufferUtil.GROUP_SELECT_ELEMENT.classList.add("form-control", "width-initial", "d-inline-block", "mr-3", "buffer-group");
BufferUtil.GROUP_SELECT_ELEMENT.innerHTML =
    `<option value="1" selected="selected">G1</option>
    <option value="2">G2</option>`;
