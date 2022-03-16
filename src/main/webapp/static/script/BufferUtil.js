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
    }

    static updateTeamMmrLink()
    {
        const elem = document.querySelector("#team-buffer-mmr");
        if(BufferUtil.teamBuffer.buffer.size > 0) {
            elem.setAttribute("href", TeamUtil.getTeamMmrHistoryHref(BufferUtil.teamBuffer.buffer.values()));
            elem.classList.remove("d-none");
            elem.classList.add("d-inline-block");
        } else {
            elem.classList.add("d-none");
            elem.classList.remove("d-inline-block");
        }
    }

    static enhance()
    {
        document.querySelector("#team-buffer-clear").addEventListener("click", BufferUtil.clear);
    }

    static createToggleElement(buf, item)
    {
        const remove = buf.buffer.has(item.id);
        const toggle = ElementUtil.createTagButton("div", "table-image table-image-square background-cover team-buffer-toggle d-inline-block " + (remove ? "remove" : "add"));
        toggle.addEventListener("click", e=>Buffer.toggle(buf, e));
        return toggle;
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
        TeamUtil.updateTeamsTable(document.querySelector("#team-buffer-teams"), {result:Array.from(BufferUtil.teamBuffer.buffer.values())});
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
        ClanUtil.updateClanTable(document.querySelector("#team-buffer-clans"), Array.from(buf.buffer.values()));
        BufferUtil.updateView();
    }
);
