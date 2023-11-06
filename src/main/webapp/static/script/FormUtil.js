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

    static formDataToObject(formData, prefix, trim = false)
    {
        const result = {};
        for(const [key, val] of formData.entries()) {
            if(key.startsWith(prefix)) {
                const trimmedVal = trim && typeof val === 'string' ? val.trim() : val;
                result[key.substring(prefix.length)] = trimmedVal == "" ? null : trimmedVal;
            }
        }
        return result;
    }

    static setFormStateFromObject(form, object, prefix)
    {
        Object.entries(object).forEach((entry)=>{
            const input = form.querySelector(':scope [name="' + prefix + entry[0] + '"]');
            if(input == null) return;

            if(input.getAttribute("type") == "radio") {
                const radio = form.querySelector(':scope [name="' + prefix + entry[0] + '"][value="' + entry[1] + '"]');
                if(radio) ElementUtil.changeInputValue(radio, true);
            } else {
                ElementUtil.changeInputValue(input, entry[1] == null ? "" : entry[1]);
            }
        })
    }

    static setFormStateFromArray(form, values, name)
    {
        const cloneCtl = Array.from(form.querySelectorAll(":scope .clone-ctl"))
            .find(ctl=>document.querySelector("#" + ctl.getAttribute("data-clone-source") + ' [name="' + name + '"]') != null);
        document.querySelectorAll(
            "#" + cloneCtl.getAttribute("data-clone-destination")
            + " ." + cloneCtl.getAttribute("data-clone-class")
            + ":not(#" + cloneCtl.getAttribute("data-clone-source") + ")"
        )
            .forEach(e=>e.remove());
        for(const value of values) ElementUtil.changeInputValue(
            ElementUtil.cloneElement(cloneCtl).querySelector(':scope [name="' + name + '"]'), value);
    }

    static resetForm(form)
    {
        form.querySelectorAll(":scope .clone-ctl").forEach(ctl=>{
            form.querySelectorAll(":scope ." + ctl.getAttribute("data-clone-class")
                + ":not(#" + ctl.getAttribute("data-clone-source")  + ")")
                    .forEach(elem=>elem.remove())
        });
        form.reset();
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
        document.querySelectorAll("[data-state-link-id]").forEach(i=>{
            const linkedInput = document.getElementById(i.getAttribute("data-state-link-id"));
            linkedInput.addEventListener("change", FormUtil.onInputStateLinkChange);
            linkedInput.addEventListener("input", FormUtil.onInputStateLinkChange);
        });
    }

    static onInputStateLinkChange(evt)
    {
        const linkedInput = document.querySelectorAll('[data-state-link-id="' + evt.target.id +  '"]').
            forEach(i=>FormUtil.setInputLinkState(i, evt.target));
    }

    static setInputLinkState(input, linkedInput)
    {
        const linkedValue = linkedInput.checked || linkedInput.value;
        input.disabled = !input.getAttribute("data-state-link-values").split(",").some(v=>v == linkedValue);
    }

    static initInputStateLinks()
    {
        for(const entry of Util.groupBy(document.querySelectorAll('[data-state-link-id]'), i=>i.getAttribute("data-state-link-id")).entries()) {
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

    static enhanceFormInputGroupFilters()
    {
        document.querySelectorAll(".filtered-input-filter").forEach(i=>{
            i.addEventListener("input", FormUtil.onFormInputGroupFilter);
            i.addEventListener("keydown", FormUtil.onFormInputGroupFilterKeyDown)
        });
        document.querySelectorAll(".filtered-input-group input").forEach(i=>i.addEventListener("click", FormUtil.onFormInputGroupInputClick));
    }

    static onFormInputGroupFilter(evt)
    {
        FormUtil.filterFormInputGroup(evt.target);
    }

    static filterFormInputGroup(filterInput)
    {
        let term = filterInput.value.toLowerCase();
        if(filterInput.getAttribute("data-trim") == "true") term = term.trim();
        document.querySelectorAll(filterInput.getAttribute("data-filtered-input-group")).forEach(group=>{
            let validOptionCount = 0;
            group.querySelectorAll(":scope .filtered-input-container").forEach(container=>{
                if(term.length > 0 && container.querySelector(":scope label").textContent.toLowerCase().includes(term)) {
                  container.classList.remove("d-none");
                  validOptionCount++;
                } else {
                  container.classList.add("d-none");
                }
            });
            FormUtil.setFormInputGroupActiveInput(group, FormUtil.getActiveOrFirstFilteredInputGroupOption(group));
            group.setAttribute("data-valid-option-count", validOptionCount);
            if(validOptionCount > 0) {
                group.classList.remove("d-none");
            } else {
                group.classList.add("d-none");
            }
        });
    }

    static getActiveOrFirstFilteredInputGroupOption(group)
    {
        const inputs = Array.from(group.querySelectorAll(":scope .filtered-input-container:not(.d-none) input"));
        if(inputs.length == 0) return null;

        return inputs.find(input=>input.checked) || inputs[0];
    }

    static setFormInputGroupActiveInput(group, input)
    {
        const filter = document.querySelector('.filtered-input-filter[data-filtered-input-group="#' + group.id + '"]');
        if(input != null) {
            input.checked = true;
            group.setAttribute("data-active-option", input.value);
            filter.classList.remove("text-danger");
            filter.removeAttribute("pattern");
        } else {
            const activeOption = group.getAttribute("data-active-option");
            if(activeOption != null) group.querySelector(':scope input[value="' + activeOption + '"').checked = false;
            filter.classList.add("text-danger");
            filter.setAttribute("pattern", "");
        }
    }

    static getFormInputGroupLabelByValue(group, value)
    {
        if(value == null) return null;
        const inputId = group.querySelector(':scope input[value="' + value + '"]').id;
        return document.querySelector('label[for="' + inputId + '"]').textContent;
    }

    static setInputGroupFilterByValue(filter, value, hideGroup = true)
    {
        const groupSelector = filter.getAttribute("data-filtered-input-group");
        const group = document.querySelector(groupSelector);
        filter.value = FormUtil.getFormInputGroupLabelByValue(group, value);
        filter.dispatchEvent(new Event('input', { 'bubbles': true }));
        if(hideGroup && group.getAttribute("data-valid-option-count") == "1")
            document.querySelectorAll(groupSelector).forEach(group=>group.classList.add("d-none"));
    }

    static onFormInputGroupInputClick(evt)
    {
        FormUtil.setInputGroupFilterByValue(
            document.querySelector('[data-filtered-input-group="#' + evt.target.closest(".filtered-input-group").id + '"]'),
            evt.target.value);
    }

    static navigateInputGroupOptions(group, down)
    {
        const validInputs = group.querySelectorAll(":scope .filtered-input-container:not(.d-none) input");
        if(validInputs.length == 0) return;

        const activeIx = Array.from(validInputs).findIndex(input=>input.checked);
        const nextIx = down ? Math.min(activeIx + 1, validInputs.length - 1) : Math.max(activeIx - 1, 0);
        FormUtil.setFormInputGroupActiveInput(group, validInputs[nextIx]);
    }

    static onFormInputGroupFilterKeyDown(evt)
    {
        const group = document.querySelector(evt.target.getAttribute("data-filtered-input-group"));
        switch(evt.key)
        {
            case "ArrowDown":
                FormUtil.navigateInputGroupOptions(group, true);
                break;
            case "ArrowUp":
                FormUtil.navigateInputGroupOptions(group, false);
                break;
            case "Enter":
                evt.preventDefault();
                const activeOption = Array.from(group.querySelectorAll(":scope .filtered-input-container:not(.d-none) input"))
                    .find(input=>input.checked);
                if(activeOption != null) FormUtil.setInputGroupFilterByValue(evt.target, activeOption.value);
                break;
        }
    }

}
