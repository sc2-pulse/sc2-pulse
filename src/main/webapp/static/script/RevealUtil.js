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
        }
    }

    static onModalShow()
    {
        const currentCharacter = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH);
        const form = document.querySelector("#modal-reveal-player-form");
        const submitCtl = document.querySelector('#modal-reveal-player button[form="modal-reveal-player-form"][type="submit"]');
        const filterInput = document.querySelector("#modal-reveal-player-input");
        if(!currentCharacter.proPlayer) {
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
            FormUtil.setInputGroupFilterByValue(filterInput, currentCharacter.proPlayer.proPlayer.id);
            submitCtl.disabled = false;
            submitCtl.textContent = "Unlink";
            submitCtl.classList.remove("btn-success");
            submitCtl.classList.add("btn-danger");
        }

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
        const character = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR);
        return RevealUtil.reveal(character.accountId, proPlayerId, method)
            .then(e=>CharacterUtil.updateCharacter(character.id))
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

}