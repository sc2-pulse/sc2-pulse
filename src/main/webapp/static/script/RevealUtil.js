// Copyright (C) 2023 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class RevealUtil
{

    static enhanceCtl()
    {
        const modal = document.querySelector("#modal-reveal-player");
        if(modal) {
            $(modal).on("show.bs.modal", RevealUtil.onModalShow);
            document.querySelector("#modal-reveal-player-input").addEventListener("input", e=>window.setTimeout(RevealUtil.onFilterChange, 1));
            document.querySelector("#modal-reveal-player-form").addEventListener("submit", RevealUtil.onReveal);
            document.querySelector("#modal-reveal-import-player-form").addEventListener("submit", RevealUtil.onImportProfile);
            document.querySelector("#pro-player-edit").addEventListener("click", RevealUtil.onEditProPlayer);
            document.querySelector("#pro-player-new").addEventListener("click", RevealUtil.onNewProPlayer);
            document.querySelector("#reveal-player-edit-form").addEventListener("submit", RevealUtil.onSaveProPlayer);
            document.querySelectorAll("#modal-reveal-player .log .ctl-reload")
                .forEach(e=>e.addEventListener("change", e=>window.setTimeout(RevealUtil.reloadLogEntries, 1)));
            ElementUtil.infiniteScroll(document.querySelector("#modal-reveal-player .log .container-indicator-loading-default"),
                RevealUtil.updateLogEntriesContainer);
        }
    }

    static resetModel()
    {
        Model.DATA.set(RevealUtil.MODEL_NAME, {log: {accounts: new Map(), proPlayers: new Map(), entries: []}});
    }

    static onModalShow()
    {
        RevealUtil.resetModel();
        const proPlayerId = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR)?.members?.proId;
        const form = document.querySelector("#modal-reveal-player-form");
        const submitCtl = document.querySelector('#modal-reveal-player button[form="modal-reveal-player-form"][type="submit"]');
        const filterInput = document.querySelector("#modal-reveal-player-input");
        if(proPlayerId == null) {
            filterInput.disabled = false;
            form.setAttribute("data-reveal-mode", "reveal");
            FormUtil.filterFormInputGroup(filterInput);
            submitCtl.disabled = document.querySelector("#modal-reveal-player-players").getAttribute("data-valid-option-count") == 0;
            submitCtl.textContent = "Reveal";
            submitCtl.classList.add("btn-success");
            submitCtl.classList.remove("btn-danger");
        } else {
            filterInput.disabled = true;
            form.setAttribute("data-reveal-mode", "unlink");
            FormUtil.setInputGroupFilterByValue(filterInput, proPlayerId);
            submitCtl.disabled = false;
            submitCtl.textContent = "Unlink";
            submitCtl.classList.remove("btn-success");
            submitCtl.classList.add("btn-danger");
        }
        return RevealUtil.updateLog();
    }

    static onFilterChange()
    {
        document.querySelector('#modal-reveal-player button[form="modal-reveal-player-form"][type="submit"]').disabled
            = document.querySelector("#modal-reveal-player-players").getAttribute("data-valid-option-count") == 0;
    }

    static onReveal(evt)
    {
        evt.preventDefault();
        const method = evt.target.getAttribute("data-reveal-mode") == "reveal" ? "POST" : "DELETE";
        const fd = new FormData(evt.target);
        const proPlayerId = fd.get("player");
        const character = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR).members.character;
        return RevealUtil.reveal(character.accountId, proPlayerId, method)
            .then(e=>CharacterUtil.showCharacterInfo(null, character.id, false))
            .then(e=>BootstrapUtil.hideActiveModal())
            .then(e=>BootstrapUtil.showTab("player-stats-player-tab"));
    }

    static reveal(accountId, proPlayerId, method)
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        return Session.beforeRequest()
            .then(n=>fetch(`${ROOT_CONTEXT_PATH}api/reveal/${accountId}/${proPlayerId}`, Util.addCsrfHeader({method: method})))
            .then(Session.verifyResponse)
            .then(o=>Util.setGeneratingStatus(STATUS.SUCCESS))
            .catch(error=>Session.onPersonalException(error));
    }

    static onImportProfile(evt)
    {
        evt.preventDefault();
        const fd = new FormData(evt.target);
        Util.setGeneratingStatus(STATUS.BEGIN);
        return RevealUtil.importProfile(fd.get("url"))
            .then(proPlayer=>{
                RevealUtil.renderAndSelectProPlayer(proPlayer, document.querySelector("#modal-reveal-player-players"));
                Util.setGeneratingStatus(STATUS.SUCCESS);
            })
            .catch(error=>Session.onPersonalException(error));
    }

    static importProfile(url)
    {
        const fd = new FormData();
        fd.set("url", url);
        return Session.beforeRequest()
            .then(n=>fetch(`${ROOT_CONTEXT_PATH}api/reveal/import`, Util.addCsrfHeader({method: 'POST', body: fd})))
            .then(Session.verifyJsonResponse);
    }

    static renderProPlayerInputGroup(proPlayer)
    {
        const inputGroup = ElementUtil.createFilteredInputGroup("reveal-player-" + proPlayer.id, "player", proPlayer.id);
        inputGroup.querySelector(":scope label").textContent = RevealUtil.renderProPlayer(proPlayer);
        return inputGroup;
    }

    static renderProPlayer(player)
    {
        return `${player.nickname}${player.name ? ', ' + player.name : ''}${player.country ? ' ' + Util.countryCodeToEmoji(player.country) : ''}`;
    }

    static renderAndSelectProPlayer(proPlayer, inputGroup)
    {
        const proPlayerRender = RevealUtil.renderProPlayer(proPlayer);
        const input = inputGroup.querySelector(':scope input[value="' + proPlayer.id + '"]');
        if(input != null) {
            inputGroup.querySelector(':scope label[for="' + input.id + '"]').textContent = proPlayerRender;
        } else {
            inputGroup.appendChild(RevealUtil.renderProPlayerInputGroup(proPlayer));
        }
        const filter = document.querySelector('input[data-filtered-input-group="#' + inputGroup.id + '"]');
        if(!filter.disabled) FormUtil.setInputGroupFilterByValue(filter, proPlayer.id);
    }

    static onSaveProPlayer(evt)
    {
        evt.preventDefault();
        const formData = new FormData(evt.target);
        const proPlayer = FormUtil.formDataToObject(formData, "pro-player-", true);
        if(proPlayer.name != null) proPlayer.name = proPlayer.name.replace(/\s\s+/g, ' ');
        const links = formData.getAll("link-url")
            .map(url=>url.trim())
            .filter(url=>url != "");
        const container = evt.target.closest(".container-loading");
        Util.resetLoadingIndicator(container);
        return Util.load(container, ()=>RevealUtil.saveProPlayer({proPlayer: proPlayer, links: links}), true);
    }

    static saveProPlayer(data)
    {
        return Session.beforeRequest()
            .then(n=>fetch(`${ROOT_CONTEXT_PATH}api/reveal/player/edit`, Util.addCsrfHeader({
                method: "POST",
                body: JSON.stringify(data),
                headers: {"Content-Type": "application/json"}})))
            .then(Session.verifyJsonResponse)
            .then(player=>{
                RevealUtil.renderAndSelectProPlayer(player.proPlayer, document.querySelector("#modal-reveal-player-players"));
                RevealUtil.editProPlayer(player);
                return {data: player, status: LOADING_STATUS.COMPLETE};
            });
    }

    static onEditProPlayer(evt)
    {
        const playerId = new FormData(document.querySelector("#modal-reveal-player-form")).get("player");
        if(playerId == null) {
            RevealUtil.onNewProPlayer(evt);
            return;
        }
        const container = evt.target.closest(".container-loading");
        Util.resetLoadingIndicator(container);
        Util.load(container, ()=>RevealUtil.getPlayer(playerId).then(players=>{
            const result = RevealUtil.editProPlayer(players[0]);
            $("#reveal-player-edit-form").collapse("show");
            return result;
        }));
    }

    static editProPlayer(player)
    {
        const form = document.querySelector("#reveal-player-edit-form");
        FormUtil.setFormStateFromObject(form, player.proPlayer, "pro-player-");
        FormUtil.setInputGroupFilterByValue(form.querySelector(':scope [name="country-search"]'), player.proPlayer.country);
        const links = player.links != null
            ? player.links
                .filter(link=>PRO_PLAYER_EDIT_ALLOWED_LINK_TYPES.has(link.type))
                .map(link=>link.url)
            : [];
        FormUtil.setFormStateFromArray(form, links, "link-url");
        return {data: player, status: LOADING_STATUS.COMPLETE};
    }

    static onNewProPlayer(evt)
    {
        const form = document.querySelector("#reveal-player-edit-form");
        FormUtil.resetForm(form);
        $(form).collapse("show");
    }

    static getPlayer(id)
    {
        return Session.beforeRequest()
            .then(n=>fetch(`${ROOT_CONTEXT_PATH}api/revealed/player/${id}/full`))
            .then(Session.verifyJsonResponse);
    }

    static getLog
    (
        cursor,
        accountId,
        authorAccountId,
        action,
        excludeSystemAuthor,
        limit = 30
    )
    {
        const params = new URLSearchParams();
        params.append("limit", limit);
        if(cursor) {
            params.append("idCursor", cursor.id);
            params.append("createdCursor", cursor.created);
        }
        if(accountId) params.append("accountId", accountId);
        if(authorAccountId) params.append("authorAccountId", authorAccountId);
        if(action) params.append("action", action.fullName);
        if(excludeSystemAuthor != null) params.append("excludeSystemAuthor", excludeSystemAuthor);

        return Session.beforeRequest()
            .then(n=>fetch(`${ROOT_CONTEXT_PATH}api/reveal/log?${params.toString()}`))
            .then(resp=>Session.verifyJsonResponse(resp, [200, 404]));
    }

    static blame(linkedCharacter, character, container)
    {
        if(!linkedCharacter.proPlayer) {
            container.textContent = '';
            return Promise.resolve();
        }
        return RevealUtil.getLog(null, character.accountId, null, AUDIT_LOG_ACTION.INSERT)
            .then(log=>{
                if(!log) return null;
                const entry = log.find(l=>l.table == "pro_player_account");
                if(!entry) return null;
                if(!entry.authorAccountId) return [entry, null];

                const params = new URLSearchParams();
                params.append("accountId", entry.authorAccountId);
                return Promise.all([Promise.resolve(entry), GroupUtil.getGroup(params, true)]);
            })
            .then(all=>{
                if(!all) {
                    container.textContent = "";
                } else {
                    container.textContent =
                        "Revealed by " + (all[1] ? all[1].accounts[0].battleTag : RevealUtil.LOG_SYSTEM_USER_NAME)
                        + " on " + Util.DATE_TIME_FORMAT.format(Util.parseIsoDateTime(all[0].created));
                    container.appendChild(ElementUtil.createElement("div", null, "c-divider-hr"));
                }
            });
    }

    static getRevealers()
    {
         return Session.beforeRequest()
            .then(n=>fetch(`${ROOT_CONTEXT_PATH}api/user/role/REVEALER`))
            .then(resp=>Session.verifyJsonResponse(resp, [200, 404]));
    }

    static updateLogRevealers()
    {
        const select = document.querySelector("#reveal-log-revealer");
        if(select.querySelectorAll(":scope option").length > 2) return Promise.resolve();

        return RevealUtil.getRevealers()
            .then(revealers=>{
                if(revealers){
                    revealers.sort((a, b)=>a.id - b.id)
                        .map(r=>ElementUtil.createElement("option", null, null, CharacterUtil.renderAccount(r), [["value", r.id]]))
                        .forEach(option=>select.appendChild(option));
                    Session.restoreState(select.parentElement);
                }
            });
    }

    static fillLogModel(entries) {
        const ids = {};
        for(const key of GroupUtil.PARAMETER_KEYS) ids[key] = new Set();
        for(const entry of entries) {
            entry.dataJson = JSON.parse(entry.data);
            if(entry.changedData) entry.changedDataJson = JSON.parse(entry.changedData);
            for(const [key, val] of Object.entries(entry.dataJson)) {
                if(!key.endsWith("_id")) continue;

                const pascalCaseKey = Util.snakeCaseToCamelCase(key);
                if(ids[pascalCaseKey]) ids[pascalCaseKey].add(val);
            }
            if(entry.authorAccountId) ids.accountId.add(entry.authorAccountId);
        }
        const groupParams = new URLSearchParams();
        for(const [key, idArray] of Object.entries(ids))
            for(const id of idArray)
                groupParams.append(key, id);
        const model = Model.DATA.get(RevealUtil.MODEL_NAME).log;
        return GroupUtil.getGroup(groupParams, true)
            .then(group=>{
                for(const[groupKey, groupVals] of Object.entries(group))
                    if(groupVals) groupVals.forEach(val=>model[groupKey].set(val.id || val[groupKey.substring(0, groupKey.length - 1)].id, val));
                return entries;
            });
    }

    static renderLogEntry(entry, model)
    {
        const container = ElementUtil.createElement("article", null, "entry d-flex flex-column gap-1 mb-4");
        container.appendChild(RevealUtil.renderLogEntryHeader(entry, model));
        container.appendChild(RevealUtil.renderLogEntryContent(entry, model));
        return container;
    }

    static renderLogEntryContent(entry, model)
    {
        const container = ElementUtil.createElement("div", null, "content d-flex flex-column gap-1 px-2");
        container.appendChild(RevealUtil.renderLogEntryEntities(entry, model));
        container.appendChild(RevealUtil.renderLogEntryData(entry));
        return container;
    }

    static renderLogEntryEntities(entry, model)
    {
        const container = ElementUtil.createElement("div", null, "entities d-flex flex-wrap-gap");

        for(const [key, val] of Object.entries(entry.dataJson)) {
            if(!key.endsWith("_id")) continue;

            const pascalCaseKey = Util.snakeCaseToCamelCase(key);
            const modelKey = RevealUtil.logDataKeyToModelKey(pascalCaseKey);
            if(!model[modelKey]) continue;

            const entity = model[modelKey].get(val);
            if(!entity) continue;

            const renderer = RevealUtil.LOG_ENTITY_RENDERERS.get(pascalCaseKey);
            if(!renderer) continue;

            const params = new URLSearchParams();
            params.append(pascalCaseKey, val);
            container.appendChild(GroupUtil.createGroupLink(params, renderer(entity), false));
        }
        if(container.children.length == 0) container.classList.add("d-none");
        return container;
    }

    static logDataKeyToModelKey(logDataKey)
    {
        return logDataKey.substring(0, logDataKey.length - 2) + "s";
    }

    static renderLogEntryData(entry)
    {
        const keys = RevealUtil.getLogEntryDataRenderKeys(entry);
        if(keys.length == 0) return ElementUtil.createElement("div", null, "d-none");

        const table = TableUtil.createTable([], true);
        const tbody = table.querySelector(":scope tbody");
        for(const key of keys) {
            const row = TableUtil.createSimpleRow(entry.dataJson, key, true);
            const diff = entry.changedDataJson && (typeof entry.changedDataJson[key] !== 'undefined');
            if(diff) row.children.item(1).classList.add("text-danger");
            TableUtil.insertCell(row, "text-success").textContent = diff ? String(entry.changedDataJson[key]) : "";
            tbody.appendChild(row);
        }
        return table;
    }

    static getLogEntryDataRenderKeys(entry)
    {
        switch(entry.table) {
            case "pro_player_account": {
                const action = EnumUtil.enumOfFullName(entry.action, AUDIT_LOG_ACTION);
                if(action == AUDIT_LOG_ACTION.INSERT || action == AUDIT_LOG_ACTION.DELETE)
                    return [];
                break;
            }
            case "social_media_link": {
                const action = EnumUtil.enumOfFullName(entry.action, AUDIT_LOG_ACTION);
                if(action == AUDIT_LOG_ACTION.INSERT || action == AUDIT_LOG_ACTION.DELETE)
                    return ["url"];
            }
        }
        return Object.keys(entry.dataJson);
    }

    static renderLogEntryHeader(entry, model)
    {
        const header = ElementUtil.createElement("header", null, "bg-transparent-05 d-flex flex-wrap-gap p-2");
        header.appendChild(ElementUtil.createElement("address", null, "author",
            entry.authorAccountId
                ? CharacterUtil.renderAccount(model.accounts.get(entry.authorAccountId) || {id: entry.authorAccountId})
                : RevealUtil.LOG_SYSTEM_USER_NAME));
        header.appendChild(RevealUtil.renderLogEntryAction(entry.action));
        header.appendChild(ElementUtil.createElement("span", null, "table-name text-info", entry.table))
        header.appendChild(ElementUtil.createElement("time", null, "text-secondary",
            Util.DATE_TIME_FORMAT.format(Util.parseIsoDateTime(entry.created))), [["datetime", entry.created]]);
        return header;
    }

    static renderLogEntryAction(actionText)
    {
        const action = EnumUtil.enumOfFullName(actionText, AUDIT_LOG_ACTION);
        return ElementUtil.createElement("span", null, "action " + RevealUtil.getLogActionClass(action), action.fullName);
    }

    static getLogActionClass(action)
    {
        switch(action) {
            case AUDIT_LOG_ACTION.INSERT:
                return "text-success";
            case AUDIT_LOG_ACTION.DELETE:
                return "text-danger";
            case AUDIT_LOG_ACTION.UPDATE:
                return "text-warning";
            default: return "";
        }
    }

    static updateLogEntries()
    {
        let cursor;
        const model = Model.DATA.get(RevealUtil.MODEL_NAME).log;
        if(model && model.entries && model.entries.length > 0) cursor = model.entries[model.entries.length - 1];

        const revealer = localStorage.getItem("reveal-log-revealer") || "EXCLUDE_SYSTEM";
        const revealerId = parseInt(revealer);
        const action = localStorage.getItem("reveal-log-action") || "ALL";

        return RevealUtil.getLog(
            cursor,
            null,
            !isNaN(revealerId) ? revealerId : null,
            action != "ALL" ? EnumUtil.enumOfFullName(action, AUDIT_LOG_ACTION) : null,
            revealer == "EXCLUDE_SYSTEM"
        )
            .then(entries=>{
                if(!entries) return {data: null, status: LOADING_STATUS.COMPLETE};

                const container = document.querySelector("#reveal-log-entries");
                return RevealUtil.fillLogModel(entries)
                    .then(ets=>{
                        ets.map(entry=>RevealUtil.renderLogEntry(entry, model))
                            .forEach(l=>container.appendChild(l));
                        model.entries = model.entries.concat(ets);
                        return {data: ets, status: LOADING_STATUS.NONE};
                    });
            });
    }

    static resetLogEntries()
    {
        ElementUtil.removeChildren(document.querySelector('#reveal-log-entries'));
        Util.resetLoadingIndicator(document.querySelector('#reveal-log-entries-container'));
        Model.DATA.get(RevealUtil.MODEL_NAME).log.entries = [];
    }

    static reloadLogEntries()
    {
        RevealUtil.resetLogEntries();
        return RevealUtil.updateLogEntriesContainer();
    }

    static updateLogEntriesContainer()
    {
        const container = document.querySelector('#reveal-log-entries-container');
        return Util.load(container, RevealUtil.updateLogEntries);
    }

    static updateLog()
    {
        const linkedCharacter = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH);
        const character = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR).members.character;
        const logContainer = document.querySelector('#modal-reveal-player .log');
        ElementUtil.executeTask('#modal-reveal-player', ()=>
            RevealUtil.blame(linkedCharacter, character, logContainer.querySelector(":scope .blame"))
        );
        return RevealUtil.updateLogRevealers()
            .then(RevealUtil.reloadLogEntries);
    }

}

RevealUtil.LOG_SYSTEM_USER_NAME = "System";
RevealUtil.MODEL_NAME = "reveal";
RevealUtil.LOG_ENTITY_RENDERERS = new Map([
    ["accountId", CharacterUtil.renderAccount],
    ["proPlayerId", proPlayer=>RevealUtil.renderProPlayer(proPlayer.proPlayer)]
]);