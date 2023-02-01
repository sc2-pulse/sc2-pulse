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
            document.querySelector("#modal-reveal-player-form").addEventListener("submit", RevealUtil.onFormSubmit);
        }
    }

    static onModalShow()
    {
        const currentCharacter = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH);
        const form = document.querySelector("#modal-reveal-player-form");
        const submitCtl = document.querySelector('#modal-reveal-player button[type="submit"]');
        const filterInput = document.querySelector("#modal-reveal-player-input");
        if(!currentCharacter.proPlayer.proPlayer) {
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
            document.querySelectorAll("#modal-reveal-player-players .filtered-input-container").forEach(container=>{
                const input = container.querySelector(":scope input");
                if(input.value == currentCharacter.proPlayer.proPlayer.id) {
                    container.classList.remove("d-none");
                    input.checked = true;
                } else {
                    container.classList.add("d-none");
                }
            });
            submitCtl.disabled = false;
            submitCtl.textContent = "Unlink";
            submitCtl.classList.remove("btn-success");
            submitCtl.classList.add("btn-danger");
        }

    }

    static onFilterChange()
    {
        document.querySelector('#modal-reveal-player button[type="submit"]').disabled
            = document.querySelector("#modal-reveal-player-players").getAttribute("data-valid-option-count") == 0;
    }

    static onFormSubmit(evt)
    {
        evt.preventDefault();
        const method = evt.target.getAttribute("data-reveal-mode") == "reveal" ? "POST" : "DELETE";
        const fd = new FormData(evt.target);
        const proPlayerId = fd.get("player");
        const character = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR);
        return RevealUtil.reveal(character.accountId, proPlayerId, method)
            .then(e=>CharacterUtil.updateCharacter(character.id))
            .then(e=>BootstrapUtil.hideActiveModal())
            .then(e=>BootstrapUtil.showTab("player-stats-player-tab"))
    }

    static reveal(accountId, proPlayerId, method)
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        return Session.beforeRequest()
            .then(n=>fetch(`${ROOT_CONTEXT_PATH}api/reveal/${accountId}/${proPlayerId}`, Util.addCsrfHeader({method: method})))
            .then(Session.verifyResponse)
            .then(o=>new Promise((res, rej)=>{Util.setGeneratingStatus(STATUS.SUCCESS); res();}))
            .catch(error=>Session.onPersonalException(error));
    }

}