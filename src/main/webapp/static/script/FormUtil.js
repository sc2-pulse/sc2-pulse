// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class FormUtil
{

    static getFormErrors(form)
    {
        const errors = [];
        for(const cRow of form.querySelectorAll(":scope .group-checkbox"))
        {
            if(Array.from(cRow.querySelectorAll(':scope input[type="checkbox"]')).filter(c=>c.checked).length > 0) continue;

            errors.push("You must select at least one " + cRow.getAttribute("data-name"));
        }
        FormUtil.getGreaterThenErrors(form, errors);
        return errors.join(". ");
    }

    static getGreaterThenErrors(form, errors)
    {
        for(const greaterThan of form.querySelectorAll(":scope [data-greater-than]"))
        {
            const inclusive = greaterThan.getAttribute("data-greater-than-inclusive");
            const lessThan = document.querySelector(greaterThan.getAttribute("data-greater-than"));
            if(!greaterThan.value || !lessThan.value) continue;
            const greaterThanValue = greaterThan.getAttribute("type") == "number" ? parseFloat(greaterThan.value) : greaterThan.value;
            const valid = inclusive ? greaterThanValue >= lessThan.value : greaterThanValue > lessThan.value;
            if(!valid) errors.push(`${greaterThan.getAttribute("data-name")} must be greater than ${inclusive === 'true' ? 'or equal to' : ''} ${lessThan.getAttribute("data-name")}`);
        }
    }

    static verifyForm(form, out)
    {
        const errors = FormUtil.getFormErrors(form);
        if(!errors)
        {
            out.classList.add("d-none");
            return true;
        }

        out.textContent = errors;
        out.classList.remove("d-none");
        return false;
    }

    static setFormState(form, searchParams)
    {
        for(const [key, val] of searchParams)
        {
            const input = form.querySelector(':scope [name="' + key + '"]');
            if(!input) continue;
            ElementUtil.changeInputValue(input, val);
        }

    }

    static selectAndFocusOnInput(input, preventScroll)
    {
        input.focus({preventScroll: preventScroll});
        input.select();
    }

    static setInputStatsExcludingGroup(form, group, state)
    {
        if(form.getAttribute("data-active-group") == group && form.getAttribute("data-active-state") == state) return;

        for(const input of form.querySelectorAll(":scope input")) if(input.getAttribute("data-group") != group) input.disabled = state;
        for(const select of form.querySelectorAll(":scope select")) if(select.getAttribute("data-group") != group) select.disabled = state;
        form.setAttribute("data-active-group", group);
        form.setAttribute("data-active-state", state);
    }

    static onInputGroupInput(evt)
    {
        const group = evt.target.getAttribute("data-group");
        const state = (evt.target.value && evt.target.value.length > 0) || evt.target.checked;
        FormUtil.setInputStatsExcludingGroup(evt.target.closest("form"), group, state);
    }

    static enhanceFormGroups()
    {
        for(const input of document.querySelectorAll("input[data-group]")) {
            input.addEventListener("input", FormUtil.onInputGroupInput);
            input.addEventListener("change", FormUtil.onInputGroupInput);
        }
    }

    static linkInputStateBindings()
    {
        document.querySelectorAll("input[data-state-link-id]").forEach(i=>{
            const linkedInput = document.getElementById(i.getAttribute("data-state-link-id"));
            linkedInput.addEventListener("change", FormUtil.onInputStateLinkChange);
            linkedInput.addEventListener("input", FormUtil.onInputStateLinkChange);
        });
    }

    static onInputStateLinkChange(evt)
    {
        const linkedInput = document.querySelectorAll('input[data-state-link-id="' + evt.target.id +  '"]').
            forEach(i=>FormUtil.setInputLinkState(i, evt.target));
    }

    static setInputLinkState(input, linkedInput)
    {
        const linkedValue = linkedInput.checked || linkedInput.value;
        input.disabled = !input.getAttribute("data-state-link-values").split(",").some(v=>v == linkedValue);
    }

    static initInputStateLinks()
    {
        for(const entry of Util.groupBy(document.querySelectorAll('input[data-state-link-id]'), i=>i.getAttribute("data-state-link-id")).entries()) {
            const linkedInput = document.getElementById(entry[0]);
            entry[1].forEach(input=>FormUtil.setInputLinkState(input, linkedInput));
        }
    }

    static enhanceFormConfirmations()
    {
        for(const form of document.querySelectorAll("form.confirmation"))
            form.addEventListener("submit", FormUtil.confirmFormSubmission)
    }

    static confirmFormSubmission(evt)
    {
        evt.preventDefault();
        return BootstrapUtil.showConfirmationModal(
            evt.target.getAttribute("data-confirmation-text"),
            evt.target.getAttribute("data-confirmation-description"),
            evt.target.getAttribute("data-confirmation-action-name"),
            evt.target.getAttribute("data-confirmation-action-class")
        )
            .then(confirmed=>{
                if(confirmed == true) evt.target.submit();
                return Promise.resolve(confirmed);
            });
    }

}
