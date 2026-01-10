// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class EnhancementUtil
{

    static enhance()
    {
        EnhancementUtil.enhanceSelects(document);
    }

    static enhanceSelects(container)
    {
        container.querySelectorAll("select.enhanced").forEach(original=>{
            const jqObj = $(original);
            jqObj.select2({
                allowClear: true,
                placeholder: "Select an option",
                ...(original.hasAttribute("multiple")) && {width: "100%"}
            });
            jqObj.on("select2:select select2:unselect select2:clear", EnhancementUtil.onEnhancedSelectChange);
        });
    }

    static onEnhancedSelectChange(e)
    {
        e.target.dispatchEvent(new Event("change"));
    }

}