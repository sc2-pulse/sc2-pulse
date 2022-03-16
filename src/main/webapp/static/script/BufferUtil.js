class BufferUtil
{

    static clear()
    {
        BufferUtil.teamBuffer.clear();
    }

    static updateView()
    {
        const bufferElem = document.querySelector("#team-buffer");
        if(BufferUtil.teamBuffer.buffer.size == 0) {
            bufferElem.classList.add("d-none");
        } else {
            bufferElem.classList.remove("d-none");
        }
        BufferUtil.updateTeamMmrLink();
    }

    static updateTeamMmrLink()
    {
        document.querySelector("#team-buffer-mmr").setAttribute("href", TeamUtil.getTeamMmrHistoryHref(BufferUtil.teamBuffer.buffer.values()));
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
    (buf)=>{Model.DATA.get(VIEW.TEAM_BUFFER).set(VIEW_DATA.SEARCH, {result:Array.from(buf.buffer.values())});},
    (buf)=>{
        const bufferElem = document.querySelector("#team-buffer-teams");
        document.querySelector("#team-buffer-count").textContent = buf.buffer.size;
        if(buf.buffer.size == 0) {
            bufferElem.classList.add("d-none");
        } else {
            bufferElem.classList.remove("d-none");
        }
        TeamUtil.updateTeamsTable(document.querySelector("#team-buffer-teams"), {result:Array.from(BufferUtil.teamBuffer.buffer.values())});
        BufferUtil.updateView();
    }
);